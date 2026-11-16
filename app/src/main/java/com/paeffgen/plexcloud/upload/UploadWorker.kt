package com.paeffgen.plexcloud.upload

import android.content.Context
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.paeffgen.plexcloud.PlexCloudApp
import com.paeffgen.plexcloud.R
import com.paeffgen.plexcloud.data.ApiException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Lädt eine Datei nach PlexCloud: anmelden (/uploads) → direkt nach S3 (ein PUT oder Teil für Teil) → abschließen.
 * Die Datei läuft nie durch Plexora selbst. Nebenbei: SHA-256 über alle Bytes und bei Fotos eine Miniatur.
 */
class UploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val app get() = applicationContext as PlexCloudApp

    override suspend fun doWork(): Result {
        val uri = Uri.parse(inputData.getString(K_URI) ?: return Result.failure())
        val name = inputData.getString(K_NAME) ?: return Result.failure()
        val mime = inputData.getString(K_MIME) ?: "application/octet-stream"
        val size = inputData.getLong(K_SIZE, -1)
        val parentId = inputData.getString(K_PARENT) ?: return Result.failure()
        val deleteAfter = inputData.getBoolean(K_DELETE, false)
        if (size < 0) return fail("Größe der Datei unbekannt")

        runCatching { setForeground(foregroundInfo(name, 0)) } // klappt nicht immer aus dem Hintergrund: dann eben ohne
        return try {
            val start = app.api.startUpload(name, size, mime, parentId)
            val itemId = start.getString("itemId")
            val sha = MessageDigest.getInstance("SHA-256")
            val meter = Meter(size)
            open(uri).use { input ->
                if (start.optBoolean("multipart")) uploadParts(itemId, input, size, start.getLong("partSize"), start.getInt("parts"), sha, meter)
                else put(start.getString("url"), input, size, mime, sha, meter, part = 1, parts = 1)
            }
            app.api.completeUpload(itemId)
            if (mime.startsWith("image/")) runCatching { thumbnail(itemId, uri) }
            if (deleteAfter) runCatching { File(uri.path!!).delete() }
            Result.success(workDataOf(K_SHA to sha.digest().joinToString("") { "%02x".format(it) }, K_ITEM to itemId))
        } catch (e: ApiException) {
            // 4xx (Kontingent, Dateigröße, …) wird durch Wiederholen nicht besser
            if (e.status in 400..499 && e.status != 401 && e.status != 429) fail(e.message ?: "Fehler ${e.status}")
            else if (runAttemptCount < 3) Result.retry() else fail(e.message ?: "Fehler ${e.status}")
        } catch (e: IOException) {
            if (runAttemptCount < 3) Result.retry() else fail(e.message ?: "Verbindung unterbrochen")
        }
    }

    private fun fail(msg: String) = Result.failure(workDataOf(K_ERROR to msg))

    private fun open(uri: Uri): InputStream =
        applicationContext.contentResolver.openInputStream(uri) ?: throw IOException("Datei nicht lesbar")

    private suspend fun uploadParts(itemId: String, input: InputStream, size: Long, partSize: Long, parts: Int, sha: MessageDigest, meter: Meter) {
        var n = 1
        while (n <= parts) {
            // Links in Paketen zu höchstens 100 holen, sie gelten 15 Minuten
            val batch = (n..minOf(parts, n + 19)).toList()
            val urls = app.api.partUrls(itemId, batch)
            for (p in batch) {
                val len = minOf(partSize, size - (p - 1) * partSize)
                put(urls[p] ?: throw IOException("Kein Link für Teil $p"), LimitedStream(input, len), len, null, sha, meter, p, parts)
            }
            n = batch.last() + 1
        }
    }

    /** Ein PUT nach S3 mit fester Länge (gestreamt, kein Puffer im Speicher) */
    private suspend fun put(url: String, input: InputStream, len: Long, mime: String?, sha: MessageDigest, meter: Meter, part: Int, parts: Int) =
        withContext(Dispatchers.IO) {
            val c = URL(url).openConnection() as HttpURLConnection
            try {
                c.requestMethod = "PUT"
                c.doOutput = true
                c.connectTimeout = 15_000
                c.readTimeout = 60_000
                c.setFixedLengthStreamingMode(len)
                if (mime != null) c.setRequestProperty("Content-Type", mime)
                c.outputStream.use { out ->
                    val buf = ByteArray(256 * 1024)
                    var left = len
                    while (left > 0) {
                        val r = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                        if (r < 0) throw IOException("Datei kürzer als angemeldet")
                        out.write(buf, 0, r)
                        sha.update(buf, 0, r)
                        left -= r
                        meter.add(r)?.let { report(it, part, parts) }
                    }
                }
                if (c.responseCode !in 200..299) throw IOException("Speicher hat Teil $part abgelehnt (${c.responseCode})")
            } finally {
                c.disconnect()
            }
        }

    private suspend fun report(m: Meter.Snapshot, part: Int, parts: Int) {
        setProgress(workDataOf(P_DONE to m.done, P_TOTAL to m.total, P_SPEED to m.bytesPerSec, P_PART to part, P_PARTS to parts))
        runCatching { setForeground(foregroundInfo(inputData.getString(K_NAME).orEmpty(), (m.done * 100 / maxOf(1, m.total)).toInt())) }
    }

    /** Miniatur für die Dateiliste: längste Seite 320 px, WebP (ab Android 11) oder JPEG, höchstens 200 KB */
    private suspend fun thumbnail(itemId: String, uri: Uri) = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 320) sample *= 2
        val bmp = open(uri).use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return@withContext
        val scale = 320f / maxOf(bmp.width, bmp.height)
        val small = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
        val (format, type) = if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY to "image/webp" else @Suppress("DEPRECATION") Bitmap.CompressFormat.JPEG to "image/jpeg"
        val bytes = ByteArrayOutputStream().also { small.compress(format, 72, it) }.toByteArray()
        if (bytes.size > 200 * 1024) return@withContext
        val r: JSONObject = app.api.startThumb(itemId, bytes.size, type)
        val c = URL(r.getString("url")).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "PUT"; c.doOutput = true
            c.setFixedLengthStreamingMode(bytes.size)
            c.setRequestProperty("Content-Type", type)
            c.outputStream.use { it.write(bytes) }
            c.responseCode
        } finally { c.disconnect() }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo(inputData.getString(K_NAME).orEmpty(), 0)

    private fun foregroundInfo(name: String, pct: Int): ForegroundInfo {
        Notifications.ensureChannels(applicationContext)
        val n = NotificationCompat.Builder(applicationContext, Notifications.UPLOADS)
            .setSmallIcon(R.drawable.ic_upload)
            .setContentTitle("Lade hoch: $name")
            .setProgress(100, pct, pct == 0)
            .setOngoing(true)
            .setSilent(true)
            .build()
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(id.hashCode(), n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(id.hashCode(), n)
    }

    companion object {
        const val TAG = "upload"
        const val K_URI = "uri"; const val K_NAME = "name"; const val K_MIME = "mime"; const val K_SIZE = "size"
        const val K_PARENT = "parent"; const val K_DELETE = "delete"; const val K_SHA = "sha256"; const val K_ITEM = "itemId"; const val K_ERROR = "error"
        const val P_DONE = "done"; const val P_TOTAL = "total"; const val P_SPEED = "speed"; const val P_PART = "part"; const val P_PARTS = "parts"
    }
}

/** Liest höchstens [limit] Bytes aus dem darunterliegenden Strom, ohne ihn zu schließen (für die Teile eines Uploads). */
private class LimitedStream(private val inner: InputStream, private var limit: Long) : InputStream() {
    override fun read(): Int = if (limit <= 0) -1 else inner.read().also { if (it >= 0) limit-- }
    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (limit <= 0) return -1
        val r = inner.read(b, off, minOf(len.toLong(), limit).toInt())
        if (r > 0) limit -= r
        return r
    }
    override fun close() = Unit
}
