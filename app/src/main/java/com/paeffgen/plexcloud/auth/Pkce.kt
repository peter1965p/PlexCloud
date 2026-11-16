package com.paeffgen.plexcloud.auth

import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** OAuth 2.0 mit PKCE (RFC 7636): die App hat kein Client-Secret, stattdessen beweist sie mit dem Verifier, dass sie den Code angefordert hat. */
object Pkce {
    private val random = SecureRandom()
    private val b64 = Base64.getUrlEncoder().withoutPadding()

    fun randomToken(bytes: Int = 32): String = ByteArray(bytes).also(random::nextBytes).let(b64::encodeToString)

    /** 43–128 Zeichen aus [A-Za-z0-9-._~] – 64 Zufallsbytes ergeben 86 Zeichen */
    fun verifier(): String = randomToken(64)

    fun challenge(verifier: String): String =
        b64.encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
}

/** Nur lesen, nicht prüfen: geprüft wird das Token vom Server. Die App braucht daraus Ablaufzeit und E-Mail. */
object Jwt {
    fun payload(token: String): JSONObject? = runCatching {
        val part = token.split('.')[1]
        JSONObject(String(Base64.getUrlDecoder().decode(part.padEnd((part.length + 3) / 4 * 4, '=')), Charsets.UTF_8))
    }.getOrNull()

    /** Ablauf in Millisekunden seit 1970, 0 wenn unbekannt */
    fun expiresAtMs(token: String): Long = (payload(token)?.optLong("exp") ?: 0L) * 1000
}
