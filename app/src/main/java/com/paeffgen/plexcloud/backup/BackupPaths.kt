package com.paeffgen.plexcloud.backup

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Wohin die Sicherung legt, rein und ohne Android (testbar) */
object BackupPaths {
    /** Ziele in PlexCloud: Fotos nach Bilder, Videos nach Videos, gesicherte Ordner nach Dokumente */
    const val PHOTOS = "Bilder"
    const val VIDEOS = "Videos"
    const val DOCS = "Dokumente"
    /** Ziel bis 0.2.5: Handy-Sicherung/<Gerät>/Fotos/<Monat> (wird beim Umzug aufgelöst) */
    const val OLD_ROOT = "Handy-Sicherung"
    private val month = DateTimeFormatter.ofPattern("yyyy-MM")

    /**
     * Gerätename als Ordnername. Zuerst der Name aus den Einstellungen des Handys („Galaxy A16 5G“),
     * denn Samsung meldet als Modell nur die Typnummer („SM-A166B“). Sonst Hersteller + Modell, ohne doppelten Hersteller.
     */
    fun deviceName(manufacturer: String, model: String, userName: String? = null): String {
        userName?.let(::clean)?.takeIf { it.isNotEmpty() }?.let { return it }
        val m = model.trim()
        val raw = if (m.lowercase().startsWith(manufacturer.trim().lowercase())) m else "${manufacturer.trim().replaceFirstChar { it.uppercase() }} $m"
        return clean(raw).ifEmpty { "Android" }
    }

    /** Fotos: Bilder/2026-11, Videos: Videos/2026-11 (Aufnahmemonat) */
    fun mediaPath(takenMs: Long, video: Boolean = false, zone: ZoneId = ZoneId.systemDefault()): List<String> =
        listOf(if (video) VIDEOS else PHOTOS, month.format(Instant.ofEpochMilli(takenMs).atZone(zone)))

    /** Gesicherter Ordner: Dokumente/<Ordnername>/<Unterordner …> */
    fun folderPath(folderName: String, sub: List<String> = emptyList()): List<String> =
        listOf(DOCS, clean(folderName).ifEmpty { "Ordner" }) + sub.map { clean(it).ifEmpty { "_" } }

    /** alter Sicherungsordner, auch die Doppelgänger „Handy-Sicherung (2)“ */
    fun isOldRoot(name: String) = name == OLD_ROOT || Regex("^${OLD_ROOT} \\(\\d+\\)$").matches(name)

    /** „IMG_1 (2).jpg“ → „img_1.jpg“: PlexCloud hängt bei gleichem Namen „ (2)“ an */
    fun baseName(name: String) = name.lowercase().replace(Regex(" \\(\\d+\\)(?=(\\.[^.]*)?$)"), "")

    /** Zeichen entfernen, die PlexCloud in Namen nicht mag, und auf 120 Zeichen kürzen */
    fun clean(s: String): String = s.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), " ").replace(Regex("\\s+"), " ").trim().take(120)
}
