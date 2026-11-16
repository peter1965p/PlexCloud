package com.paeffgen.plexcloud.data

import org.json.JSONArray
import org.json.JSONObject

const val ROOT = "root"

data class CloudItem(
    val itemId: String,
    val folder: Boolean,
    val name: String,
    val parentId: String,
    val size: Long,
    val contentType: String,
    val previewable: Boolean,
    val links: Int,
    val portal: Boolean,
    val updatedAt: String,
    val thumbUrl: String?,
    val count: Int?,
) {
    val kind: Kind get() = when {
        folder -> Kind.Folder
        contentType.startsWith("image/") -> Kind.Photo
        contentType == "application/pdf" -> Kind.Pdf
        contentType.startsWith("video/") -> Kind.Video
        else -> Kind.File
    }

    enum class Kind { Folder, Photo, Pdf, Video, File }
}

data class Crumb(val itemId: String, val name: String)

data class Usage(
    val used: Long,
    val limit: Long?,
    val maxFile: Long,
    val plan: String,
    val downloaded: Long,
    val downloadLimit: Long?,
    val files: Int,
)

data class Listing(val folderId: String, val items: List<CloudItem>, val path: List<Crumb>, val usage: Usage)

data class LinkInfo(val typ: String, val id: String, val label: String, val portal: Boolean, val email: Boolean) {
    val typLabel: String get() = when (typ) { "project" -> "Projekt"; "contact" -> "Kontakt"; "company" -> "Firma"; "deal" -> "Deal"; else -> typ }
    val badge: String get() = when (typ) { "project" -> "PRJ"; "contact" -> "KON"; "company" -> "FIR"; "deal" -> "DEL"; else -> "???" }
}

/** Antworten der Plexora-API lesen. Rein und ohne Android, damit auf dem PC testbar. */
object Parse {
    private fun JSONObject.str(k: String) = if (isNull(k)) "" else optString(k)
    private fun JSONObject.longOrNull(k: String): Long? = if (!has(k) || isNull(k)) null else optLong(k)

    fun item(j: JSONObject) = CloudItem(
        itemId = j.str("itemId"),
        folder = j.str("kind") == "folder",
        name = j.str("name"),
        parentId = j.str("parentId").ifEmpty { ROOT },
        size = j.optLong("size"),
        contentType = j.str("contentType"),
        previewable = j.optBoolean("previewable"),
        links = j.optInt("links"),
        portal = j.optBoolean("portal"),
        updatedAt = j.str("updatedAt"),
        thumbUrl = j.str("thumbUrl").ifEmpty { null },
        count = if (j.has("count") && !j.isNull("count")) j.optInt("count") else null,
    )

    fun usage(j: JSONObject?) = Usage(
        used = j?.optLong("used") ?: 0,
        limit = j?.longOrNull("limit"),
        maxFile = j?.optLong("maxFile") ?: 0,
        plan = j?.str("plan").orEmpty(),
        downloaded = j?.optLong("downloaded") ?: 0,
        downloadLimit = j?.longOrNull("downloadLimit"),
        files = j?.optInt("files") ?: 0,
    )

    fun listing(j: JSONObject): Listing {
        val items = j.optJSONArray("items") ?: JSONArray()
        val path = j.optJSONArray("pfad") ?: JSONArray()
        return Listing(
            folderId = j.str("ordner").ifEmpty { ROOT },
            items = (0 until items.length()).map { item(items.getJSONObject(it)) },
            path = (0 until path.length()).map { path.getJSONObject(it).let { p -> Crumb(p.str("itemId"), p.str("name")) } },
            usage = usage(j.optJSONObject("usage")),
        )
    }

    fun links(j: JSONObject): List<LinkInfo> {
        val a = j.optJSONArray("links") ?: return emptyList()
        return (0 until a.length()).map { a.getJSONObject(it) }.map {
            LinkInfo(it.str("typ"), it.str("id"), it.str("label"), it.optBoolean("portal"), it.optBoolean("email"))
        }
    }

    /** Fehlertext aus einer Fehlerantwort von Nitro ({ message } oder { statusMessage }) */
    fun errorMessage(body: String?, status: Int): String {
        val j = runCatching { JSONObject(body ?: "") }.getOrNull()
        val m = j?.str("message")?.ifEmpty { null } ?: j?.str("statusMessage")?.ifEmpty { null }
        return m ?: when (status) {
            401 -> "Bitte neu anmelden."
            403 -> "Keine Berechtigung."
            404 -> "Nicht gefunden."
            429 -> "Zu viele Anfragen. Bitte kurz warten."
            in 500..599 -> "Plexora ist gerade gestört (Fehler $status)."
            else -> "Fehler $status"
        }
    }
}
