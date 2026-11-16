package com.paeffgen.plexcloud.backup

import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.paeffgen.plexcloud.PlexCloudApp
import com.paeffgen.plexcloud.data.BackupFolder
import com.paeffgen.plexcloud.data.ROOT
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

/**
 * Automatische Sicherung: neue Fotos/Videos aus der Galerie und neue Dateien aus gewählten Ordnern
 * werden als Uploads eingereiht (der eigentliche Upload ist der UploadWorker, mit WLAN-Regel und Wiederholung).
 * Läuft stündlich und zusätzlich, sobald die Galerie sich ändert.
 */
class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val app get() = applicationContext as PlexCloudApp

    // Stündlich, „Galerie geändert“ und „Jetzt sichern“ sind getrennte Aufträge und könnten gleichzeitig laufen:
    // dann läsen beide denselben Stand und reihten dieselben Fotos doppelt ein. Deshalb immer nur einer.
    override suspend fun doWork(): Result = RUN_LOCK.withLock { run() }

    private suspend fun run(): Result {
        if (app.auth.tokens.value == null) return Result.success()
        val s = app.settings
        val photos = s.photoBackup.first()
        val videos = s.videoBackup.first()
        val folders = s.folders.first()
        if (!photos && folders.isEmpty()) return Result.success()
        val wifiOnly = s.wifiOnly.first()
        val remote = RemoteFolders(app.api)
        var count = 0L
        return try {
            if (!s.moved.first()) { Umzug.run(app.api, remote); s.setMoved() }
            if (!s.years.first()) { Jahresordner.run(app.api, remote); s.setYears() }
            if (photos && hasMediaPermission(applicationContext)) count += backupMedia(remote, videos, wifiOnly)
            for (f in folders) count += backupFolder(remote, f, wifiOnly)
            s.setLastBackup(System.currentTimeMillis(), count)
            Result.success()
        } catch (e: java.io.IOException) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        } finally {
            // nächste Änderung der Galerie wieder abwarten; ANHÄNGEN, denn REPLACE würde diesen laufenden Auftrag abbrechen
            if (photos) watchGallery(applicationContext, ExistingWorkPolicy.APPEND_OR_REPLACE)
        }
    }

    /** Galerie: alles mit DATE_ADDED nach dem gemerkten Stand, älteste zuerst, höchstens 500 je Lauf */
    private suspend fun backupMedia(remote: RemoteFolders, videos: Boolean, wifiOnly: Boolean): Long {
        var since = app.settings.photoSince.first()
        val albums = app.settings.albums.first()
        var n = 0L
        val sources = buildList {
            add(MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            if (videos) add(MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
        }
        val found = mutableListOf<Item>()
        for (src in sources) {
            val cols = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.MIME_TYPE, MediaStore.MediaColumns.DATE_ADDED, MediaStore.MediaColumns.DATE_TAKEN)
            val albumFilter = if (albums.isEmpty()) "" else " AND ${MediaStore.MediaColumns.BUCKET_ID} IN (${albums.joinToString(",") { "?" }})"
            applicationContext.contentResolver.query(src, cols, "${MediaStore.MediaColumns.DATE_ADDED} > ?$albumFilter", arrayOf(since.toString()) + albums,
                "${MediaStore.MediaColumns.DATE_ADDED} ASC")?.use { c ->
                while (c.moveToNext() && found.size < 500) {
                    val size = c.getLong(2)
                    if (size <= 0) continue // noch nicht fertig geschrieben: nächster Lauf
                    val added = c.getLong(4)
                    val taken = c.getLong(5).takeIf { it > 0 } ?: (added * 1000)
                    found += Item(ContentUris.withAppendedId(src, c.getLong(0)), c.getString(1) ?: "Datei", c.getString(3) ?: "application/octet-stream", size, added, taken)
                }
            }
        }
        for (it in found.sortedBy { it.added }) {
            val parent = remote.ensure(BackupPaths.mediaPath(it.takenMs, video = it.mime.startsWith("video/")))
            // Schon oben (gleicher Name und gleiche Größe im Zielordner) oder schon eingereiht: überspringen
            if (remote.has(parent, it.name, it.size)) { since = maxOf(since, it.added); continue }
            app.uploads.enqueue(it.uri, it.name, it.mime, it.size, parent, wifiOnly, backup = true)
            remote.remember(parent, it.name, it.size)
            since = maxOf(since, it.added)
            n++
        }
        app.settings.setPhotoSince(since)
        return n
    }

    /** Gewählter Ordner (Dateiauswähler des Systems): alle Dateien, die seit dem letzten Lauf geändert wurden, bis 4 Ebenen tief */
    private suspend fun backupFolder(remote: RemoteFolders, f: BackupFolder, wifiOnly: Boolean): Long {
        val tree = DocumentFile.fromTreeUri(applicationContext, Uri.parse(f.uri)) ?: return 0
        if (!tree.canRead()) return 0 // Zugriff entzogen: in „System“ neu wählen
        val start = System.currentTimeMillis()
        var n = 0L
        suspend fun walk(dir: DocumentFile, sub: List<String>, depth: Int) {
            for (child in dir.listFiles()) {
                val name = child.name ?: continue
                if (name.startsWith(".")) continue
                if (child.isDirectory) { if (depth < 4) walk(child, sub + name, depth + 1); continue }
                if (child.lastModified() <= f.sinceMs || child.length() <= 0) continue
                val parent = remote.ensure(BackupPaths.folderPath(tree.name ?: "Ordner", sub))
                if (remote.has(parent, name, child.length())) continue
                app.uploads.enqueue(child.uri, name, child.type ?: "application/octet-stream", child.length(), parent, wifiOnly, backup = true)
                remote.remember(parent, name, child.length())
                n++
            }
        }
        walk(tree, emptyList(), 0)
        app.settings.putFolder(f.copy(sinceMs = start))
        return n
    }

    private data class Item(val uri: Uri, val name: String, val mime: String, val size: Long, val added: Long, val takenMs: Long)

    companion object {
        private val RUN_LOCK = Mutex()
        private const val PERIODIC = "backup_hourly"
        private const val ON_CHANGE = "backup_on_change"
        private const val NOW = "backup_now"

        fun hasMediaPermission(context: Context): Boolean {
            val perms = when {
                Build.VERSION.SDK_INT >= 34 -> listOf(android.Manifest.permission.READ_MEDIA_IMAGES, android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
                Build.VERSION.SDK_INT >= 33 -> listOf(android.Manifest.permission.READ_MEDIA_IMAGES)
                else -> listOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            return perms.any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
        }

        /** Stündlich sichern (nur mit Netz) und sofort, wenn die Galerie sich ändert */
        fun schedule(context: Context) {
            val wm = WorkManager.getInstance(context)
            wm.enqueueUniquePeriodicWork(
                PERIODIC, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<BackupWorker>(1, TimeUnit.HOURS)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build(),
            )
            watchGallery(context, ExistingWorkPolicy.KEEP)
        }

        /** Einmal-Auftrag, der startet, sobald sich Fotos oder Videos ändern (danach neu einplanen) */
        fun watchGallery(context: Context, policy: ExistingWorkPolicy) {
            val c = Constraints.Builder()
                .addContentUriTrigger(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true)
                .addContentUriTrigger(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true)
                .setTriggerContentUpdateDelay(10, TimeUnit.SECONDS)
                .setTriggerContentMaxDelay(2, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(ON_CHANGE, policy, OneTimeWorkRequestBuilder<BackupWorker>().setConstraints(c).build())
        }

        fun runNow(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.KEEP, OneTimeWorkRequestBuilder<BackupWorker>().build())
        }

        /** Laufenden Sofort-Lauf stoppen (beim Abbrechen) */
        fun stopRunning(context: Context) { WorkManager.getInstance(context).cancelUniqueWork(NOW); WorkManager.getInstance(context).cancelUniqueWork(ON_CHANGE) }

        fun cancel(context: Context) {
            val wm = WorkManager.getInstance(context)
            wm.cancelUniqueWork(PERIODIC); wm.cancelUniqueWork(ON_CHANGE)
        }
    }
}

/** Ordnerpfad in PlexCloud finden oder anlegen; merkt sich gefundene Ordner für den Lauf */
class RemoteFolders(private val api: com.paeffgen.plexcloud.data.PlexCloudApi) {
    private val cache = HashMap<String, String>()
    /** Inhalt je Zielordner: "name|größe", einmal je Lauf geladen */
    private val contents = HashMap<String, MutableSet<String>>()

    suspend fun has(folderId: String, name: String, size: Long): Boolean {
        val set = contents.getOrPut(folderId) { api.listing(folderId).items.filter { !it.folder }.map { "${it.name.lowercase()}|${it.size}" }.toMutableSet() }
        return "${name.lowercase()}|$size" in set
    }

    /** wie [has], aber „IMG_1 (2).jpg“ zählt wie „IMG_1.jpg“ */
    suspend fun hasLike(folderId: String, name: String, size: Long): Boolean {
        has(folderId, name, size)
        val want = "${BackupPaths.baseName(name)}|$size"
        return contents[folderId]!!.any { e -> "${BackupPaths.baseName(e.substringBeforeLast('|'))}|${e.substringAfterLast('|')}" == want }
    }

    fun remember(folderId: String, name: String, size: Long) { contents[folderId]?.add("${name.lowercase()}|$size") }

    suspend fun ensure(path: List<String>): String {
        var parent = ROOT
        val key = StringBuilder()
        for (name in path) {
            key.append('/').append(name)
            val hit = cache[key.toString()]
            if (hit != null) { parent = hit; continue }
            val existing = api.listing(parent).items.firstOrNull { it.folder && it.name.equals(name, ignoreCase = true) }
            parent = existing?.itemId ?: api.createFolder(name, parent).itemId.also { contents[it] = mutableSetOf() }
            cache[key.toString()] = parent
        }
        return parent
    }
}

/**
 * Einmaliger Umzug aus der alten Ablage (bis 0.2.5): Handy-Sicherung/<Gerät>/Fotos/<Monat> → Bilder/<Monat> (Videos → Videos/<Monat>),
 * andere gesicherte Ordner → Dokumente. Was im Ziel schon liegt (gleicher Name ohne „ (2)“, gleiche Größe), kommt in den Papierkorb.
 * Zum Schluss wandert der leere alte Ordner (samt unfertiger Uploads) in den Papierkorb. Bricht etwas ab, läuft es beim nächsten Mal weiter.
 */
object Umzug {
    suspend fun run(api: com.paeffgen.plexcloud.data.PlexCloudApi, remote: RemoteFolders): Int {
        var moved = 0
        for (root in api.listing(ROOT).items.filter { it.folder && BackupPaths.isOldRoot(it.name) }) {
            for (device in api.listing(root.itemId).items.filter { it.folder }) {
                for (kind in api.listing(device.itemId).items.filter { it.folder }) {
                    if (kind.name != "Fotos") { api.move(kind.itemId, remote.ensure(listOf(BackupPaths.DOCS))); continue }
                    for (month in api.listing(kind.itemId).items.filter { it.folder }) {
                        for (f in api.listing(month.itemId).items.filter { !it.folder }) {
                            val target = remote.ensure(listOf(if (f.contentType.startsWith("video/")) BackupPaths.VIDEOS else BackupPaths.PHOTOS, BackupPaths.monthToYear(month.name) ?: month.name))
                            if (remote.hasLike(target, f.name, f.size)) api.trash(f.itemId)
                            else { api.move(f.itemId, target); remote.remember(target, f.name, f.size); moved++ }
                        }
                    }
                }
            }
            api.trash(root.itemId)
        }
        return moved
    }
}

/**
 * Einmaliger Umzug der Monatsordner (bis 0.2.7) in Jahresordner: Bilder/2026-11 → Bilder/2026, ebenso unter Videos.
 * Doppelte (gleicher Name ohne „ (2)“, gleiche Größe) kommen in den Papierkorb, der leere Monatsordner danach auch.
 */
object Jahresordner {
    suspend fun run(api: com.paeffgen.plexcloud.data.PlexCloudApi, remote: RemoteFolders): Int {
        var moved = 0
        for (top in api.listing(ROOT).items.filter { it.folder && (it.name.equals(BackupPaths.PHOTOS, true) || it.name.equals(BackupPaths.VIDEOS, true)) }) {
            for (month in api.listing(top.itemId).items.filter { it.folder }) {
                val year = BackupPaths.monthToYear(month.name) ?: continue
                val target = remote.ensure(listOf(top.name, year))
                for (f in api.listing(month.itemId).items) {
                    if (!f.folder && remote.hasLike(target, f.name, f.size)) api.trash(f.itemId)
                    else { api.move(f.itemId, target); if (!f.folder) remote.remember(target, f.name, f.size); moved++ }
                }
                api.trash(month.itemId)
            }
        }
        return moved
    }
}

/** Ein Album der Galerie (Kamera, Screenshots, WhatsApp …) mit Anzahl Fotos und Videos */
data class Album(val id: String, val name: String, val photos: Int, val videos: Int)

object Albums {
    /** Alle Alben, größte zuerst; „Camera“ steht immer oben */
    fun list(context: Context): List<Album> {
        val map = LinkedHashMap<String, IntArray>()
        val names = HashMap<String, String>()
        listOf(MediaStore.Images.Media.EXTERNAL_CONTENT_URI to 0, MediaStore.Video.Media.EXTERNAL_CONTENT_URI to 1).forEach { (src, slot) ->
            runCatching {
                context.contentResolver.query(src, arrayOf(MediaStore.MediaColumns.BUCKET_ID, MediaStore.MediaColumns.BUCKET_DISPLAY_NAME), null, null, null)?.use { c ->
                    while (c.moveToNext()) {
                        val id = c.getString(0) ?: continue
                        names.getOrPut(id) { c.getString(1) ?: "Ohne Namen" }
                        map.getOrPut(id) { IntArray(2) }[slot]++
                    }
                }
            }
        }
        return map.map { (id, n) -> Album(id, names[id] ?: id, n[0], n[1]) }
            .sortedWith(compareByDescending<Album> { it.name.equals("Camera", true) || it.name.equals("Kamera", true) }.thenByDescending { it.photos + it.videos })
    }
}
