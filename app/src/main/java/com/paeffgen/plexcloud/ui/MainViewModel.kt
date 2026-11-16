package com.paeffgen.plexcloud.ui

import android.app.Application
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.paeffgen.plexcloud.PlexCloudApp
import com.paeffgen.plexcloud.backup.BackupWorker
import com.paeffgen.plexcloud.data.BackupFolder
import android.content.Intent
import com.paeffgen.plexcloud.auth.AuthRepository
import com.paeffgen.plexcloud.data.CloudItem
import com.paeffgen.plexcloud.data.Crumb
import com.paeffgen.plexcloud.data.LinkInfo
import com.paeffgen.plexcloud.data.Listing
import com.paeffgen.plexcloud.data.ROOT
import com.paeffgen.plexcloud.data.DEFAULT_ACCENT
import com.paeffgen.plexcloud.update.CheckResult
import com.paeffgen.plexcloud.update.UpdateInfo
import com.paeffgen.plexcloud.update.UpdateInstaller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo, val needsPermission: Boolean = false) : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState
    data class Failed(val message: String) : UpdateState
}

enum class Tab { Files, Uploads, Recent, System }
enum class Filter { All, Photos, Pdf, Linked }

data class FolderState(
    val folderId: String = ROOT,
    val listing: Listing? = null,
    val loading: Boolean = true,
    val error: String? = null,
)

data class DetailState(val item: CloudItem, val path: List<Crumb>, val links: List<LinkInfo>? = null, val error: String? = null, val busy: Boolean = false)

sealed interface UiEvent {
    data class OpenUrl(val url: String) : UiEvent
    data class Share(val uri: Uri, val mime: String) : UiEvent
    data class Toast(val text: String) : UiEvent
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as PlexCloudApp
    val api = app.api

    val tokens = app.auth.tokens
    val accent = app.settings.accent.stateIn(viewModelScope, SharingStarted.Eagerly, DEFAULT_ACCENT)
    val wifiOnly = app.settings.wifiOnly.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val autoUpdate = app.settings.autoUpdate.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val uploads = app.uploads.rows.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val latencies = api.latencies
    val lastSync = api.lastSync
    val currentVersion = app.updates.currentVersion.toString()
    val photoBackup = app.settings.photoBackup.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val videoBackup = app.settings.videoBackup.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val backupFolders = app.settings.folders.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val lastBackup = app.settings.lastBackup.stateIn(viewModelScope, SharingStarted.Eagerly, 0L to 0L)
    val backupAlbums = app.settings.albums.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    private val _albums = MutableStateFlow<List<com.paeffgen.plexcloud.backup.Album>>(emptyList())
    /** Alben der Galerie (geladen, wenn der Auswahl-Dialog aufgeht) */
    val albums: StateFlow<List<com.paeffgen.plexcloud.backup.Album>> = _albums

    private val _tab = MutableStateFlow(Tab.Files)
    val tab: StateFlow<Tab> = _tab
    private val _folder = MutableStateFlow(FolderState())
    val folder: StateFlow<FolderState> = _folder
    private val _recent = MutableStateFlow(FolderState(loading = false))
    val recent: StateFlow<FolderState> = _recent
    private val _filter = MutableStateFlow(Filter.All)
    val filter: StateFlow<Filter> = _filter
    private val _detail = MutableStateFlow<DetailState?>(null)
    val detail: StateFlow<DetailState?> = _detail
    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError
    private val _update = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val update: StateFlow<UpdateState> = _update

    private val events = Channel<UiEvent>(Channel.BUFFERED)
    val uiEvents = events.receiveAsFlow()

    /** Kamera-Foto, das gerade aufgenommen wird (Datei im Cache) */
    private var pendingPhoto: File? = null

    init {
        viewModelScope.launch { tokens.collect { if (it != null) load(ROOT) } }
        // Sicherung nach einem Neustart des Handys oder einem App-Update wieder einplanen
        viewModelScope.launch {
            if (app.settings.photoBackup.first() || app.settings.folders.first().isNotEmpty()) BackupWorker.schedule(app)
        }
    }

    // ── Automatische Sicherung ──
    fun loadAlbums() = viewModelScope.launch {
        _albums.value = withContext(Dispatchers.IO) { com.paeffgen.plexcloud.backup.Albums.list(app) }
    }

    /** Auswahl ändern, ohne den Stand zurückzusetzen (gilt für neue Fotos) */
    fun setAlbums(ids: Set<String>, includeExisting: Boolean = false) = viewModelScope.launch {
        app.settings.setAlbums(ids)
        if (includeExisting) {
            app.settings.setPhotoSince(0L)
            BackupWorker.runNow(app)
            events.send(UiEvent.Toast("Fehlende Fotos werden nachgeladen, vorhandene übersprungen"))
        }
    }

    /**
     * Sicherung abbrechen: wartende Uploads stoppen und den Stand auf jetzt setzen,
     * sonst holt der nächste Lauf die übrigen alten Fotos doch noch. Ab jetzt nur neue Fotos (wenn die Sicherung an bleibt).
     */
    fun cancelBackup() = viewModelScope.launch {
        BackupWorker.stopRunning(app)
        app.uploads.cancelBackupUploads()
        app.settings.setPhotoSince(System.currentTimeMillis() / 1000)
        if (app.settings.photoBackup.first()) BackupWorker.watchGallery(app, androidx.work.ExistingWorkPolicy.KEEP)
        events.send(UiEvent.Toast("Sicherung abgebrochen. Hochgeladenes bleibt in PlexCloud, ab jetzt nur neue Fotos"))
    }

    /** includeExisting: auch alle vorhandenen Fotos, sonst nur ab jetzt; albums: gewählte Alben (leer = alle) */
    fun enablePhotoBackup(includeExisting: Boolean, albums: Set<String> = emptySet()) = viewModelScope.launch {
        app.settings.setAlbums(albums)
        app.settings.setPhotoBackup(true, if (includeExisting) 0L else System.currentTimeMillis() / 1000)
        BackupWorker.schedule(app)
        BackupWorker.runNow(app)
        events.send(UiEvent.Toast(if (includeExisting) "Sicherung läuft: alle Fotos werden hochgeladen" else "Neue Fotos werden ab jetzt gesichert"))
    }

    fun disablePhotoBackup() = viewModelScope.launch {
        app.settings.setPhotoBackup(false, null)
        if (app.settings.folders.first().isEmpty()) BackupWorker.cancel(app)
    }

    fun setVideoBackup(on: Boolean) = viewModelScope.launch { app.settings.setVideoBackup(on) }

    /** Ordner aus dem Dateiauswähler des Systems: Zugriff dauerhaft merken, vorhandene Dateien gleich mitsichern */
    fun addBackupFolder(uri: Uri) = viewModelScope.launch {
        runCatching { app.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            .onFailure { events.send(UiEvent.Toast("Kein dauerhafter Zugriff auf diesen Ordner")); return@launch }
        app.settings.putFolder(BackupFolder(uri.toString(), 0L))
        BackupWorker.schedule(app)
        BackupWorker.runNow(app)
        events.send(UiEvent.Toast("Ordner wird gesichert"))
    }

    fun removeBackupFolder(uri: String) = viewModelScope.launch {
        runCatching { app.contentResolver.releasePersistableUriPermission(Uri.parse(uri), Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        app.settings.removeFolder(uri)
        if (!app.settings.photoBackup.first() && app.settings.folders.first().isEmpty()) BackupWorker.cancel(app)
    }

    fun backupNow() { BackupWorker.runNow(app); viewModelScope.launch { events.send(UiEvent.Toast("Sicherung gestartet")) } }

    /** Fertiger Scan (PDF vom Dokumentenscanner): in den Cache kopieren und in den aktuellen Ordner hochladen */
    fun scannedPdf(uri: Uri, pages: Int) = viewModelScope.launch {
        val name = "Scan_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.GERMANY).format(Date())}.pdf"
        val f = withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(app.cacheDir, "camera").apply { mkdirs() }
                File(dir, name).also { out -> app.contentResolver.openInputStream(uri)!!.use { i -> out.outputStream().use { i.copyTo(it) } } }
            }.getOrNull()
        }
        if (f == null || f.length() == 0L) { events.send(UiEvent.Toast("Scan konnte nicht gelesen werden")); return@launch }
        app.uploads.enqueue(f, name, "application/pdf", _folder.value.folderId, wifiOnly.value)
        events.send(UiEvent.Toast("$pages ${if (pages == 1) "Seite" else "Seiten"} als PDF hochladen"))
        _tab.value = Tab.Uploads
    }

    // ── Anmeldung ──
    fun loginUri(provider: AuthRepository.Provider): Uri { _loginError.value = null; return app.auth.loginUri(provider) }

    fun handleRedirect(uri: Uri) = viewModelScope.launch {
        runCatching { app.auth.handleRedirect(uri) }.onFailure { _loginError.value = it.message }
    }

    fun signOut() = viewModelScope.launch {
        app.auth.signOut()
        _folder.value = FolderState(); _detail.value = null; _tab.value = Tab.Files
    }

    // ── Navigation ──
    fun select(t: Tab) {
        _tab.value = t
        if (t == Tab.Recent) loadRecent()
    }

    fun setFilter(f: Filter) { _filter.value = f }

    fun open(item: CloudItem) {
        if (item.folder) load(item.itemId) else showDetail(item)
    }

    fun openCrumb(folderId: String) = load(folderId)

    /** Zurück-Taste: Detail schließen, dann Ordner hoch, dann Tab „Dateien“. false = App verlassen */
    fun back(): Boolean {
        if (_detail.value != null) { _detail.value = null; return true }
        if (_tab.value != Tab.Files) { _tab.value = Tab.Files; return true }
        val path = _folder.value.listing?.path.orEmpty()
        if (_folder.value.folderId != ROOT) { load(if (path.size >= 2) path[path.size - 2].itemId else ROOT); return true }
        return false
    }

    fun refresh() = load(_folder.value.folderId)

    private fun load(folderId: String) = viewModelScope.launch {
        _folder.value = _folder.value.copy(folderId = folderId, loading = true, error = null)
        runCatching { api.listing(folderId) }
            .onSuccess { _folder.value = FolderState(folderId, it, loading = false) }
            .onFailure { _folder.value = _folder.value.copy(loading = false, error = it.message) }
    }

    private fun loadRecent() = viewModelScope.launch {
        _recent.value = _recent.value.copy(loading = true, error = null)
        runCatching { api.recent() }
            .onSuccess { _recent.value = FolderState(ROOT, it, loading = false) }
            .onFailure { _recent.value = _recent.value.copy(loading = false, error = it.message) }
    }

    fun createFolder(name: String) = viewModelScope.launch {
        runCatching { api.createFolder(name.trim(), _folder.value.folderId) }
            .onSuccess { refresh() }
            .onFailure { events.send(UiEvent.Toast(it.message ?: "Fehler")) }
    }

    // ── Datei-Details ──
    private fun showDetail(item: CloudItem) {
        _detail.value = DetailState(item, _folder.value.listing?.path.orEmpty())
        viewModelScope.launch {
            runCatching { api.links(item.itemId) }
                .onSuccess { l -> _detail.value = _detail.value?.takeIf { it.item.itemId == item.itemId }?.copy(links = l) }
                .onFailure { e -> _detail.value = _detail.value?.copy(links = emptyList(), error = e.message) }
        }
    }

    fun closeDetail() { _detail.value = null }

    /** Kundenportal für einen verknüpften Kontakt an/aus (setzt die ganze Liste neu, wie die Web-Version) */
    fun setPortal(link: LinkInfo, on: Boolean) = viewModelScope.launch {
        val d = _detail.value ?: return@launch
        val links = d.links ?: return@launch
        val neu = links.map { if (it.typ == link.typ && it.id == link.id) it.copy(portal = on) else it }
        _detail.value = d.copy(links = neu, busy = true)
        runCatching { api.setLinks(d.item.itemId, neu) }
            .onSuccess { _detail.value = _detail.value?.copy(busy = false); refresh() }
            .onFailure { e -> _detail.value = _detail.value?.copy(links = links, busy = false); events.send(UiEvent.Toast(e.message ?: "Fehler")) }
    }

    fun view(item: CloudItem) = viewModelScope.launch {
        runCatching { api.downloadUrl(item.itemId, inline = item.previewable) }
            .onSuccess { events.send(UiEvent.OpenUrl(it)) }
            .onFailure { events.send(UiEvent.Toast(it.message ?: "Fehler")) }
    }

    /** Über den Download-Manager von Android in „Downloads“ speichern */
    fun download(item: CloudItem) = viewModelScope.launch {
        runCatching {
            val url = api.downloadUrl(item.itemId, inline = false)
            val dm = app.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(
                DownloadManager.Request(Uri.parse(url))
                    .setTitle(item.name)
                    .setMimeType(item.contentType.ifEmpty { null })
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "PlexCloud/${item.name}"),
            )
        }.onSuccess { events.send(UiEvent.Toast("Wird in Downloads/PlexCloud gespeichert")) }
            .onFailure { events.send(UiEvent.Toast(it.message ?: "Fehler")) }
    }

    /** Teilen: erst in den Cache laden (signierte Links laufen nach 5 Minuten ab), dann als Datei weitergeben */
    fun share(item: CloudItem) = viewModelScope.launch {
        _detail.value = _detail.value?.copy(busy = true)
        runCatching {
            val url = api.downloadUrl(item.itemId, inline = false)
            withContext(Dispatchers.IO) {
                val dir = File(app.cacheDir, "shared/out").apply { deleteRecursively(); mkdirs() }
                val f = File(dir, item.name.replace(Regex("[/\\\\]"), "_"))
                URL(url).openStream().use { i -> f.outputStream().use { i.copyTo(it) } }
                f
            }
        }.onSuccess { f ->
            events.send(UiEvent.Share(FileProvider.getUriForFile(app, "${app.packageName}.files", f), item.contentType.ifEmpty { "*/*" }))
        }.onFailure { events.send(UiEvent.Toast(it.message ?: "Fehler")) }
        _detail.value = _detail.value?.copy(busy = false)
    }

    // ── Hochladen ──
    fun newPhotoUri(): Uri {
        val dir = File(app.cacheDir, "camera").apply { mkdirs() }
        val f = File(dir, "IMG_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.GERMANY).format(Date())}.jpg")
        pendingPhoto = f
        return FileProvider.getUriForFile(app, "${app.packageName}.files", f)
    }

    fun photoTaken(ok: Boolean) {
        val f = pendingPhoto ?: return
        pendingPhoto = null
        if (!ok || f.length() == 0L) { f.delete(); return }
        app.uploads.enqueue(f, f.name, "image/jpeg", _folder.value.folderId, wifiOnly.value)
        _tab.value = Tab.Uploads
    }

    /** Dateien aus der Auswahl oder per „Teilen“ aus anderen Apps */
    fun uploadUris(uris: List<Uri>, parentId: String = _folder.value.folderId) = viewModelScope.launch {
        if (uris.isEmpty()) return@launch
        var n = 0
        for (u in uris) {
            val copy = runCatching { app.uploads.copyShared(u) }.getOrNull() ?: continue
            app.uploads.enqueue(copy.first, copy.second, copy.third, parentId, wifiOnly.value)
            n++
        }
        if (n < uris.size) events.send(UiEvent.Toast("${uris.size - n} Datei(en) nicht lesbar"))
        _tab.value = Tab.Uploads
    }

    fun cancelUpload(id: java.util.UUID) = app.uploads.cancel(id)
    fun clearFinishedUploads() = app.uploads.clearFinished()
    fun retryFailedUploads() = viewModelScope.launch {
        val n = withContext(Dispatchers.IO) { app.uploads.retryFailed(wifiOnly.value) }
        events.send(UiEvent.Toast(if (n == 0) "Nichts zum Wiederholen (ältere Fehler ohne gemerkte Datei bitte neu sichern)" else "$n Dateien werden erneut gesendet"))
    }

    // ── Einstellungen ──
    fun setAccent(argb: Long) = viewModelScope.launch { app.settings.setAccent(argb) }
    fun setWifiOnly(on: Boolean) = viewModelScope.launch { app.settings.setWifiOnly(on) }
    fun setAutoUpdate(on: Boolean) = viewModelScope.launch { app.settings.setAutoUpdate(on) }

    // ── Updates (wie MHD Safety) ──
    fun checkForUpdates(silent: Boolean = false) {
        val s = _update.value
        if (s is UpdateState.Checking || s is UpdateState.Downloading) return
        viewModelScope.launch {
            if (silent && !app.settings.autoUpdate.first()) return@launch
            _update.value = UpdateState.Checking
            _update.value = when (val r = app.updates.check()) {
                is CheckResult.UpdateAvailable -> UpdateState.Available(r.info)
                CheckResult.UpToDate -> if (silent) UpdateState.Idle else UpdateState.UpToDate
                is CheckResult.Failed -> if (silent) UpdateState.Idle else UpdateState.Failed(r.message)
            }
        }
    }

    fun installUpdate() {
        val info = (_update.value as? UpdateState.Available)?.info ?: return
        if (!UpdateInstaller.canInstall(app)) {
            _update.value = UpdateState.Available(info, needsPermission = true)
            UpdateInstaller.openInstallPermissionSettings(app)
            return
        }
        viewModelScope.launch {
            _update.value = UpdateState.Downloading(info, 0f)
            runCatching { app.updates.download(info) { p -> _update.value = UpdateState.Downloading(info, p) } }
                .onSuccess { apk -> _update.value = UpdateState.Idle; UpdateInstaller.install(app, apk) }
                .onFailure { _update.value = UpdateState.Failed(it.message ?: "Download fehlgeschlagen") }
        }
    }

    fun dismissUpdate() { _update.value = UpdateState.Idle }
}
