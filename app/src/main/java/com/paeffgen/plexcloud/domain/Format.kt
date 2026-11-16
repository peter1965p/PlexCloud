package com.paeffgen.plexcloud.domain

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Anzeigeformate wie in der Web-Version von PlexCloud: deutsche Kommas, KB/MB/GB zur Basis 1024 */
object Format {
    private val de = Locale.GERMANY

    fun bytes(b: Long): String = when {
        b < 1024 -> "$b B"
        b < 1024L * 1024 -> "${b / 1024} KB"
        b < 1024L * 1024 * 1024 -> String.format(de, "%.1f MB", b / 1024.0 / 1024)
        else -> String.format(de, "%.2f GB", b / 1024.0 / 1024 / 1024)
    }

    /** GB mit einer Nachkommastelle, ohne Einheit („12,4“) */
    fun gb(b: Long): String = String.format(de, "%.1f", b / 1024.0 / 1024 / 1024)

    fun speed(bytesPerSec: Long): String = bytes(bytesPerSec) + "/s"

    /** Restzeit als m:ss oder h:mm:ss */
    fun eta(seconds: Long): String {
        if (seconds < 0) return "—"
        val h = seconds / 3600; val m = seconds % 3600 / 60; val s = seconds % 60
        return if (h > 0) String.format(de, "%d:%02d:%02d", h, m, s) else String.format(de, "%d:%02d", m, s)
    }

    private val day = DateTimeFormatter.ofPattern("dd.MM.", de)
    private val full = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", de)

    fun shortDate(iso: String, zone: ZoneId = ZoneId.systemDefault()): String =
        runCatching { day.format(Instant.parse(iso).atZone(zone)) }.getOrDefault("")

    fun dateTime(iso: String, zone: ZoneId = ZoneId.systemDefault()): String =
        runCatching { full.format(Instant.parse(iso).atZone(zone)) }.getOrDefault("")

    /** „vor 4 s“, „vor 3 min“, „vor 2 h“ */
    fun ago(ms: Long, now: Long = System.currentTimeMillis()): String {
        if (ms <= 0) return "—"
        val s = (now - ms) / 1000
        return when {
            s < 60 -> "vor ${maxOf(0, s)} s"
            s < 3600 -> "vor ${s / 60} min"
            else -> "vor ${s / 3600} h"
        }
    }

    fun hex(argb: Long): String = String.format("#%06X", argb and 0xFFFFFF)
}
