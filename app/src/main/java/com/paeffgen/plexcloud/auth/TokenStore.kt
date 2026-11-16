package com.paeffgen.plexcloud.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.json.JSONObject
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class Tokens(val idToken: String, val accessToken: String, val refreshToken: String) {
    val email: String get() = Jwt.payload(idToken)?.optString("email").orEmpty()
}

/**
 * Tokens liegen nur verschlüsselt auf dem Handy: AES-256-GCM mit einem Schlüssel im Android-Keystore,
 * der das Gerät nie verlässt (nicht exportierbar, nicht im Backup). Ohne den Schlüssel sind die Daten wertlos.
 */
class TokenStore(context: Context) {
    private val prefs = context.getSharedPreferences("auth", Context.MODE_PRIVATE)

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generateKey()
        }
    }

    fun load(): Tokens? = runCatching {
        val blob = prefs.getString(FIELD, null) ?: return null
        val raw = Base64.getDecoder().decode(blob)
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw, 0, 12))
        val json = JSONObject(String(cipher.doFinal(raw, 12, raw.size - 12), Charsets.UTF_8))
        Tokens(json.getString("id"), json.getString("access"), json.getString("refresh"))
    }.getOrElse { clear(); null }

    fun save(t: Tokens) {
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val plain = JSONObject().put("id", t.idToken).put("access", t.accessToken).put("refresh", t.refreshToken).toString()
        val sealed = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        prefs.edit().putString(FIELD, Base64.getEncoder().encodeToString(sealed)).apply()
    }

    fun clear() = prefs.edit().remove(FIELD).apply()

    // Für den Rückweg aus dem Browser: Verifier und State der laufenden Anmeldung (nur bis zum Callback gültig)
    fun savePending(verifier: String, state: String) =
        prefs.edit().putString("pkce_verifier", verifier).putString("pkce_state", state).apply()

    fun takePending(): Pair<String, String>? {
        val v = prefs.getString("pkce_verifier", null) ?: return null
        val s = prefs.getString("pkce_state", null) ?: return null
        prefs.edit().remove("pkce_verifier").remove("pkce_state").apply()
        return v to s
    }

    private companion object {
        const val ALIAS = "plexcloud_tokens"
        const val FIELD = "tokens"
        const val TRANSFORM = "AES/GCM/NoPadding"
    }
}
