package com.paeffgen.plexcloud.upload

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Ein Upload, wie ihn die Liste zeigt */
data class UploadRow(
    val id: UUID,
    val name: String,
    val backup: Boolean,
    val size: Long,
    val state: WorkInfo.State,
    val done: Long,
    val speed: Long,
    val part: Int,
    val parts: Int,
    val sha256: String?,
    val error: String?,
)

const val TAG_BACKUP = "backup"

class Uploads(private val context: Context) {
    private val wm get() = WorkManager.getInstance(context)

    val rows: Flow<List<UploadRow>> = wm.getWorkInfosByTagFlow(UploadWorker.TAG).map { list ->
        list.map { w ->
            val name = w.tags.firstOrNull { it.startsWith("n:") }?.removePrefix("n:").orEmpty()
            val size = w.tags.firstOrNull { it.startsWith("s:") }?.removePrefix("s:")?.toLongOrNull() ?: 0
            UploadRow(
                id = w.id, name = name, backup = TAG_BACKUP in w.tags, size = size, state = w.state,
                done = if (w.state == WorkInfo.State.SUCCEEDED) size else w.progress.getLong(UploadWorker.P_DONE, 0),
                speed = w.progress.getLong(UploadWorker.P_SPEED, 0),
                part = w.progress.getInt(UploadWorker.P_PART, 0),
                parts = w.progress.getInt(UploadWorker.P_PARTS, 0),
                sha256 = w.outputData.getString(UploadWorker.K_SHA),
                error = w.outputData.getString(UploadWorker.K_ERROR),
            )
        }.sortedWith(compareBy<UploadRow> { it.state.isFinished }.thenBy { it.name })
    }

    /** Eine Datei aus dem App-Cache (Kamera-Foto, Scan oder Kopie einer geteilten Datei) einreihen; wird nach dem Upload gelöscht */
    fun enqueue(file: File, name: String, mime: String, parentId: String, wifiOnly: Boolean) =
        enqueue(Uri.fromFile(file), name, mime, file.length(), parentId, wifiOnly, deleteAfter = true)

    /** Eine Datei über ihre Adresse einreihen (auch content:// aus der Galerie oder einem gesicherten Ordner, ohne Kopie) */
    fun enqueue(uri: Uri, name: String, mime: String, size: Long, parentId: String, wifiOnly: Boolean, deleteAfter: Boolean = false, backup: Boolean = false) {
        val req = OneTimeWorkRequestBuilder<UploadWorker>()
            .setInputData(
                workDataOf(
                    UploadWorker.K_URI to uri.toString(),
                    UploadWorker.K_NAME to name,
                    UploadWorker.K_MIME to mime,
                    UploadWorker.K_SIZE to size,
                    UploadWorker.K_PARENT to parentId,
                    UploadWorker.K_DELETE to deleteAfter,
                ),
            )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(backup)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 20, TimeUnit.SECONDS)
            .addTag(UploadWorker.TAG)
            .apply { if (backup) addTag(TAG_BACKUP) }
            .addTag("n:$name")
            .addTag("s:$size")
            .build()
        wm.enqueue(req)
    }

    fun cancel(id: UUID) { wm.cancelWorkById(id) }

    /** Fertige und fehlgeschlagene Einträge aus der Liste nehmen */
    fun clearFinished() { wm.pruneWork() }

    /**
     * Geteilte Datei in den Cache kopieren: Leserechte an fremden content://-Adressen gelten nur,
     * solange die Activity lebt. Der Upload im Hintergrund braucht eine eigene Kopie.
     */
    suspend fun copyShared(uri: Uri): Triple<File, String, String>? = withContext(Dispatchers.IO) {
        val cr = context.contentResolver
        val name = cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: uri.lastPathSegment ?: "Datei"
        val mime = cr.getType(uri) ?: "application/octet-stream"
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val target = File(dir, "${UUID.randomUUID()}-${name.replace(Regex("[^A-Za-z0-9._ -]"), "_").take(80)}")
        val input = cr.openInputStream(uri) ?: return@withContext null
        input.use { i -> target.outputStream().use { i.copyTo(it, 256 * 1024) } }
        Triple(target, name, mime)
    }
}
