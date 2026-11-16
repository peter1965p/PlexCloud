package com.paeffgen.plexcloud.backup

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Wohin die Sicherung legt, rein und ohne Android (testbar) */
object BackupPaths {
    const val ROOT_NAME = "Handy-Sicherung"
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

    /** Fotos und Videos: Handy-Sicherung/<Gerät>/Fotos/2026-11 */
    fun mediaPath(device: String, takenMs: Long, zone: ZoneId = ZoneId.systemDefault()): List<String> =
        listOf(ROOT_NAME, device, "Fotos", month.format(Instant.ofEpochMilli(takenMs).atZone(zone)))

    /** Ordner: Handy-Sicherung/<Gerät>/<Ordnername>/<Unterordner …> */
    fun folderPath(device: String, folderName: String, sub: List<String> = emptyList()): List<String> =
        listOf(ROOT_NAME, device, clean(folderName).ifEmpty { "Ordner" }) + sub.map { clean(it).ifEmpty { "_" } }

    /** Zeichen entfernen, die PlexCloud in Namen nicht mag, und auf 120 Zeichen kürzen */
    fun clean(s: String): String = s.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), " ").replace(Regex("\\s+"), " ").trim().take(120)
}
