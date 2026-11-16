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
import java.util.concurrent.TimeUnit

/**
 * Automatische Sicherung: neue Fotos/Videos aus der Galerie und neue Dateien aus gewählten Ordnern
 * werden als Uploads eingereiht (der eigentliche Upload ist der UploadWorker, mit WLAN-Regel und Wiederholung).
 * Läuft stündlich und zusätzlich, sobald die Galerie sich ändert.
 */
class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val app get() = applicationContext as PlexCloudApp

    override suspend fun doWork(): Result {
        if (app.auth.tokens.value == null) return Result.success()
        val s = app.settings
        val photos = s.photoBackup.first()
        val videos = s.videoBackup.first()
        val folders = s.folders.first()
        if (!photos && folders.isEmpty()) return Result.success()
        val wifiOnly = s.wifiOnly.first()
        val device = BackupPaths.deviceName(Build.MANUFACTURER, Build.MODEL, runCatching { android.provider.Settings.Global.getString(applicationContext.contentResolver, android.provider.Settings.Global.DEVICE_NAME) }.getOrNull())
        val remote = RemoteFolders(app.api)
        var count = 0L
        return try {
            if (photos && hasMediaPermission(applicationContext)) count += backupMedia(remote, device, videos, wifiOnly)
            for (f in folders) count += backupFolder(remote, device, f, wifiOnly)
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
    private suspend fun backupMedia(remote: RemoteFolders, device: String, videos: Boolean, wifiOnly: Boolean): Long {
        var since = app.settings.photoSince.first()
        var n = 0L
        val sources = buildList {
            add(MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            if (videos) add(MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
        }
        val found = mutableListOf<Item>()
        for (src in sources) {
            val cols = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.MIME_TYPE, MediaStore.MediaColumns.DATE_ADDED, MediaStore.MediaColumns.DATE_TAKEN)
            applicationContext.contentResolver.query(src, cols, "${MediaStore.MediaColumns.DATE_ADDED} > ?", arrayOf(since.toString()),
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
            val parent = remote.ensure(BackupPaths.mediaPath(device, it.takenMs))
            app.uploads.enqueue(it.uri, it.name, it.mime, it.size, parent, wifiOnly, backup = true)
            since = maxOf(since, it.added)
            n++
        }
        app.settings.setPhotoSince(since)
        return n
    }

    /** Gewählter Ordner (Dateiauswähler des Systems): alle Dateien, die seit dem letzten Lauf geändert wurden, bis 4 Ebenen tief */
    private suspend fun backupFolder(remote: RemoteFolders, device: String, f: BackupFolder, wifiOnly: Boolean): Long {
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
                val parent = remote.ensure(BackupPaths.folderPath(device, tree.name ?: "Ordner", sub))
                app.uploads.enqueue(child.uri, name, child.type ?: "application/octet-stream", child.length(), parent, wifiOnly, backup = true)
                n++
            }
        }
        walk(tree, emptyList(), 0)
        app.settings.putFolder(f.copy(sinceMs = start))
        return n
    }

    private data class Item(val uri: Uri, val name: String, val mime: String, val size: Long, val added: Long, val takenMs: Long)

    companion object {
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

        fun cancel(context: Context) {
            val wm = WorkManager.getInstance(context)
            wm.cancelUniqueWork(PERIODIC); wm.cancelUniqueWork(ON_CHANGE)
        }
    }
}

/** Ordnerpfad in PlexCloud finden oder anlegen; merkt sich gefundene Ordner für den Lauf */
class RemoteFolders(private val api: com.paeffgen.plexcloud.data.PlexCloudApi) {
    private val cache = HashMap<String, String>()

    suspend fun ensure(path: List<String>): String {
        var parent = ROOT
        val key = StringBuilder()
        for (name in path) {
            key.append('/').append(name)
            val hit = cache[key.toString()]
            if (hit != null) { parent = hit; continue }
            val existing = api.listing(parent).items.firstOrNull { it.folder && it.name.equals(name, ignoreCase = true) }
            parent = existing?.itemId ?: api.createFolder(name, parent).itemId
            cache[key.toString()] = parent
        }
        return parent
    }
}
