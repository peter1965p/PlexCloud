package com.paeffgen.plexcloud.auth

import android.net.Uri
import com.paeffgen.plexcloud.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class AuthException(message: String) : IOException(message)

/**
 * Anmeldung über die Cognito-Anmeldeseite von Plexora (Google oder E-Mail + Passwort), Authorization Code + PKCE.
 * Die App sieht nie ein Passwort: das wird nur im Browser bei Cognito eingegeben.
 */
class AuthRepository(private val store: TokenStore) {
    private val mutex = Mutex()
    private val _tokens = MutableStateFlow(store.load())
    val tokens: StateFlow<Tokens?> = _tokens

    enum class Provider(val cognito: String) { Google("Google"), Email("COGNITO") }

    /** Adresse der Anmeldeseite; Verifier und State werden bis zum Callback gemerkt. */
    fun loginUri(provider: Provider): Uri {
        val verifier = Pkce.verifier()
        val state = Pkce.randomToken(16)
        store.savePending(verifier, state)
        return Uri.Builder().scheme("https").authority(BuildConfig.COGNITO_DOMAIN).path("/oauth2/authorize")
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", BuildConfig.COGNITO_CLIENT_ID)
            .appendQueryParameter("redirect_uri", BuildConfig.REDIRECT_URI)
            .appendQueryParameter("scope", "openid email profile")
            .appendQueryParameter("identity_provider", provider.cognito)
            .appendQueryParameter("state", state)
            .appendQueryParameter("code_challenge", Pkce.challenge(verifier))
            .appendQueryParameter("code_challenge_method", "S256")
            .build()
    }

    /** Rückkehr von plexcloud://auth/callback?code=…&state=… */
    suspend fun handleRedirect(uri: Uri) {
        val (verifier, state) = store.takePending() ?: throw AuthException("Keine laufende Anmeldung. Bitte nochmal tippen.")
        uri.getQueryParameter("error")?.let { throw AuthException(uri.getQueryParameter("error_description") ?: it) }
        if (uri.getQueryParameter("state") != state) throw AuthException("Anmeldung abgelehnt: ungültiger Rückweg.")
        val code = uri.getQueryParameter("code") ?: throw AuthException("Anmeldung ohne Code zurückgekommen.")
        val json = tokenRequest(
            "grant_type" to "authorization_code",
            "client_id" to BuildConfig.COGNITO_CLIENT_ID,
            "code" to code,
            "redirect_uri" to BuildConfig.REDIRECT_URI,
            "code_verifier" to verifier,
        )
        val t = Tokens(json.getString("id_token"), json.getString("access_token"), json.getString("refresh_token"))
        store.save(t)
        _tokens.value = t
    }

    /** Gültiges ID-Token; erneuert es eine Minute vor Ablauf mit dem Refresh-Token. */
    suspend fun idToken(forceRefresh: Boolean = false): String = mutex.withLock {
        val t = _tokens.value ?: throw AuthException("Nicht angemeldet")
        if (!forceRefresh && Jwt.expiresAtMs(t.idToken) - 60_000 > System.currentTimeMillis()) return t.idToken
        val json = try {
            tokenRequest("grant_type" to "refresh_token", "client_id" to BuildConfig.COGNITO_CLIENT_ID, "refresh_token" to t.refreshToken)
        } catch (e: AuthException) {
            // Refresh-Token abgelaufen oder widerrufen: neu anmelden
            signOutLocal(); throw e
        }
        val neu = t.copy(idToken = json.getString("id_token"), accessToken = json.getString("access_token"))
        store.save(neu)
        _tokens.value = neu
        neu.idToken
    }

    /** Abmelden: Refresh-Token bei Cognito widerrufen (best effort), dann alles lokal löschen. */
    suspend fun signOut() {
        val t = _tokens.value
        signOutLocal()
        if (t != null) runCatching {
            post("/oauth2/revoke", "token" to t.refreshToken, "client_id" to BuildConfig.COGNITO_CLIENT_ID)
        }
    }

    private fun signOutLocal() { store.clear(); _tokens.value = null }

    private suspend fun tokenRequest(vararg form: Pair<String, String>): JSONObject {
        val (code, body) = post("/oauth2/token", *form)
        if (code != 200) {
            val msg = runCatching { JSONObject(body).optString("error") }.getOrNull().orEmpty()
            throw AuthException(if (msg == "invalid_grant") "Anmeldung abgelaufen. Bitte neu anmelden." else "Anmeldung fehlgeschlagen ($code${if (msg.isNotEmpty()) ", $msg" else ""})")
        }
        return JSONObject(body)
    }

    private suspend fun post(path: String, vararg form: Pair<String, String>): Pair<Int, String> = withContext(Dispatchers.IO) {
        val c = URL("https://${BuildConfig.COGNITO_DOMAIN}$path").openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = 15_000
            c.readTimeout = 20_000
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            c.outputStream.use { o -> o.write(form.joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }.toByteArray()) }
            val code = c.responseCode
            code to ((if (code < 400) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: "")
        } finally {
            c.disconnect()
        }
    }
}
