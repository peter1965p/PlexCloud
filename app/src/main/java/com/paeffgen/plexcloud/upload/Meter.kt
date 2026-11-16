package com.paeffgen.plexcloud.upload

/**
 * Fortschritt und Tempo eines Uploads. Meldet höchstens alle [everyMs] einen Stand,
 * das Tempo ist ein gleitender Mittelwert (sonst springt die Anzeige).
 */
class Meter(val total: Long, private val everyMs: Long = 400, private val clock: () -> Long = System::currentTimeMillis) {
    data class Snapshot(val done: Long, val total: Long, val bytesPerSec: Long)

    private var done = 0L
    private var lastAt = clock()
    private var lastDone = 0L
    private var speed = 0.0

    /** Bytes dazuzählen; liefert einen Stand, wenn es Zeit für eine Meldung ist (oder am Ende). */
    fun add(bytes: Int): Snapshot? {
        done += bytes
        val now = clock()
        val dt = now - lastAt
        if (dt < everyMs && done < total) return null
        if (dt > 0) {
            val current = (done - lastDone) * 1000.0 / dt
            speed = if (speed == 0.0) current else speed * 0.7 + current * 0.3
        }
        lastAt = now
        lastDone = done
        return Snapshot(done, total, speed.toLong())
    }
}
