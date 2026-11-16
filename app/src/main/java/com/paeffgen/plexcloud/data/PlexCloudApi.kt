package com.paeffgen.plexcloud.data

import com.paeffgen.plexcloud.BuildConfig
import com.paeffgen.plexcloud.auth.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class ApiException(val status: Int, message: String) : IOException(message)

/** Zugriff auf /api/plexcloud der Plexora-API mit dem ID-Token aus Cognito. Misst nebenbei die Antwortzeiten. */
class PlexCloudApi(private val auth: AuthRepository) {
    private val _latencies = MutableStateFlow<List<Long>>(emptyList())
    /** Die letzten 30 Antwortzeiten in ms (für die Anzeige unter „System“) */
    val latencies: StateFlow<List<Long>> = _latencies
    private val _lastSync = MutableStateFlow(0L)
    val lastSync: StateFlow<Long> = _lastSync

    suspend fun listing(folderId: String = ROOT): Listing =
        Parse.listing(call("GET", "/items", query = mapOf("ordner" to folderId)))

    suspend fun recent(): Listing = Parse.listing(call("GET", "/items", query = mapOf("ansicht" to "zuletzt")))

    suspend fun createFolder(name: String, parentId: String): CloudItem =
        Parse.item(call("POST", "/folders", JSONObject().put("name", name).put("parentId", parentId)).getJSONObject("item"))

    suspend fun rename(itemId: String, name: String) { call("PATCH", "/items/${enc(itemId)}", JSONObject().put("name", name)) }

    suspend fun trash(itemId: String) { call("DELETE", "/items/${enc(itemId)}") }

    /** Signierter Link, 5 Minuten gültig. inline = im Browser ansehen statt herunterladen. */
    suspend fun downloadUrl(itemId: String, inline: Boolean): String =
        call("GET", "/items/${enc(itemId)}/download", query = if (inline) mapOf("ansehen" to "1") else emptyMap()).getString("url")

    suspend fun links(itemId: String): List<LinkInfo> = Parse.links(call("GET", "/items/${enc(itemId)}/links"))

    suspend fun setLinks(itemId: String, links: List<LinkInfo>) {
        val arr = JSONArray()
        links.forEach { l -> arr.put(JSONObject().put("typ", l.typ).put("id", l.id).apply { if (l.portal) put("portal", true) }) }
        call("PUT", "/items/${enc(itemId)}/links", JSONObject().put("links", arr))
    }

    // ── Upload (vom UploadWorker benutzt) ──
    suspend fun startUpload(name: String, size: Long, contentType: String, parentId: String): JSONObject =
        call("POST", "/uploads", JSONObject().put("name", name).put("size", size).put("contentType", contentType).put("parentId", parentId))

    suspend fun partUrls(itemId: String, parts: List<Int>): Map<Int, String> {
        val urls = call("POST", "/items/${enc(itemId)}/teile", JSONObject().put("teile", JSONArray(parts))).getJSONObject("urls")
        return urls.keys().asSequence().associate { it.toInt() to urls.getString(it) }
    }

    suspend fun completeUpload(itemId: String): JSONObject = call("POST", "/items/${enc(itemId)}/complete", JSONObject())

    suspend fun startThumb(itemId: String, size: Int, contentType: String): JSONObject =
        call("POST", "/items/${enc(itemId)}/miniatur", JSONObject().put("size", size).put("contentType", contentType))

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private suspend fun call(method: String, path: String, body: JSONObject? = null, query: Map<String, String> = emptyMap()): JSONObject {
        return try {
            send(method, path, body, query, auth.idToken())
        } catch (e: ApiException) {
            // Token kurz vor Ablauf vom Server abgelehnt: einmal erneuern und wiederholen
            if (e.status != 401) throw e
            send(method, path, body, query, auth.idToken(forceRefresh = true))
        }
    }

    private suspend fun send(method: String, path: String, body: JSONObject?, query: Map<String, String>, token: String): JSONObject =
        withContext(Dispatchers.IO) {
            val qs = if (query.isEmpty()) "" else "?" + query.entries.joinToString("&") { "${enc(it.key)}=${enc(it.value)}" }
            val c = URL("${BuildConfig.API_BASE}/api/plexcloud$path$qs").openConnection() as HttpURLConnection
            val start = System.nanoTime()
            try {
                c.requestMethod = method
                c.connectTimeout = 15_000
                c.readTimeout = 30_000
                c.setRequestProperty("Authorization", "Bearer $token")
                c.setRequestProperty("Accept", "application/json")
                c.setRequestProperty("User-Agent", "PlexCloud-Android/${BuildConfig.VERSION_NAME}")
                if (body != null) {
                    c.doOutput = true
                    c.setRequestProperty("Content-Type", "application/json")
                    c.outputStream.use { it.write(body.toString().toByteArray()) }
                }
                val code = c.responseCode
                val text = (if (code < 400) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }
                val ms = (System.nanoTime() - start) / 1_000_000
                _latencies.value = (_latencies.value + ms).takeLast(30)
                if (code >= 400) throw ApiException(code, Parse.errorMessage(text, code))
                _lastSync.value = System.currentTimeMillis()
                if (text.isNullOrBlank()) JSONObject() else JSONObject(text)
            } finally {
                c.disconnect()
            }
        }
}
