package dev.droidos.shell.apps.music.spotify

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Authorization Code + PKCE against Spotify's accounts service. There is no client secret,
 * because a public Android client cannot keep one. Tokens live in [SecretStore].
 */
class SpotifyAuth(private val context: Context, private val secrets: SecretStore) {

    fun clientId(): String = secrets.get(KEY_CLIENT_ID).orEmpty()

    fun saveClientId(value: String) {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) secrets.remove(KEY_CLIENT_ID) else secrets.put(KEY_CLIENT_ID, trimmed)
    }

    fun hasClientId(): Boolean = clientId().isNotBlank()

    fun redirectUri(): String =
        secrets.get(KEY_REDIRECT)?.takeIf { it.isNotBlank() } ?: SpotifyEngine.DEFAULT_REDIRECT

    fun saveRedirectUri(value: String) {
        val trimmed = value.trim()
        secrets.put(KEY_REDIRECT, trimmed.ifBlank { SpotifyEngine.DEFAULT_REDIRECT })
    }

    fun isConnected(): Boolean = !secrets.get(KEY_REFRESH).isNullOrBlank()

    /** Browser intent for the consent screen, with the PKCE verifier kept server-side of the app. */
    fun beginAuthorization(): Intent {
        val verifier = SpotifyEngine.codeVerifier()
        val state = SpotifyEngine.codeVerifier(length = 32)
        secrets.put(KEY_PENDING_VERIFIER, verifier)
        secrets.put(KEY_PENDING_STATE, state)
        val url = SpotifyEngine.authorizationUrl(
            clientId = clientId(),
            redirectUri = redirectUri(),
            challenge = SpotifyEngine.codeChallenge(verifier),
            state = state
        )
        return Intent(Intent.ACTION_VIEW, Uri.parse(url))
    }

    suspend fun completeAuthorization(uri: String): Result<Unit> = withContext(Dispatchers.IO) {
        val error = SpotifyEngine.authorizationError(uri)
        if (error != null) {
            return@withContext Result.failure(IllegalStateException("Spotify authorization was denied: $error"))
        }
        if (!SpotifyEngine.matchesRedirect(uri, redirectUri())) {
            return@withContext Result.failure(IllegalStateException("That redirect does not match the registered URI"))
        }
        val receivedState = SpotifyEngine.authorizationState(uri)
        val expectedState = secrets.get(KEY_PENDING_STATE)
        if (expectedState != null && receivedState != expectedState) {
            return@withContext Result.failure(IllegalStateException("Authorization state did not match; start again"))
        }
        val code = SpotifyEngine.authorizationCode(uri)
            ?: return@withContext Result.failure(IllegalStateException("Spotify returned no authorization code"))
        val verifier = secrets.get(KEY_PENDING_VERIFIER)
            ?: return@withContext Result.failure(IllegalStateException("No PKCE verifier is pending; start again"))
        postForm(
            SpotifyEngine.TOKEN_ENDPOINT,
            SpotifyEngine.tokenRequestBody(code, redirectUri(), clientId(), verifier)
        ).fold(
            onSuccess = { json ->
                storeTokens(json)
                secrets.remove(KEY_PENDING_VERIFIER)
                secrets.remove(KEY_PENDING_STATE)
                Result.success(Unit)
            },
            onFailure = { Result.failure(it) }
        )
    }

    /** Cached token, refreshed shortly before it expires. Null means "not connected". */
    suspend fun validAccessToken(): String? = withContext(Dispatchers.IO) {
        val refreshToken = secrets.get(KEY_REFRESH) ?: return@withContext null
        val expiresAt = secrets.get(KEY_EXPIRES)?.toLongOrNull() ?: 0L
        if (!SpotifyEngine.isExpired(expiresAt, System.currentTimeMillis())) {
            return@withContext secrets.get(KEY_ACCESS)
        }
        if (!hasClientId()) return@withContext null
        postForm(
            SpotifyEngine.TOKEN_ENDPOINT,
            SpotifyEngine.refreshRequestBody(refreshToken, clientId())
        ).fold(
            onSuccess = { json ->
                storeTokens(json)
                secrets.get(KEY_ACCESS)
            },
            onFailure = {
                signOut()
                null
            }
        )
    }

    fun expiresAtMillis(): Long = secrets.get(KEY_EXPIRES)?.toLongOrNull() ?: 0L

    fun signOut() {
        secrets.remove(KEY_ACCESS)
        secrets.remove(KEY_REFRESH)
        secrets.remove(KEY_EXPIRES)
        secrets.remove(KEY_PENDING_VERIFIER)
        secrets.remove(KEY_PENDING_STATE)
    }

    private fun storeTokens(json: JSONObject) {
        val access = json.optString("access_token")
        if (access.isBlank()) return
        secrets.put(KEY_ACCESS, access)
        val refresh = json.optString("refresh_token")
        if (refresh.isNotBlank()) secrets.put(KEY_REFRESH, refresh)
        val expiresIn = json.optLong("expires_in", 3600L).coerceAtLeast(60L)
        secrets.put(KEY_EXPIRES, (System.currentTimeMillis() + expiresIn * 1000L).toString())
    }

    private fun postForm(url: String, body: String): Result<JSONObject> = runCatching {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 12_000
            readTimeout = 12_000
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            setRequestProperty("Accept", "application/json")
        }
        try {
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val detail = runCatching { JSONObject(text).optString("error_description") }
                    .getOrDefault("")
                    .takeIf { it.isNotBlank() }
                throw IllegalStateException(
                    SpotifyEngine.errorMessage(status) + if (detail != null) " ($detail)" else ""
                )
            }
            JSONObject(text)
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val KEY_CLIENT_ID = "spotify.clientId"
        const val KEY_REDIRECT = "spotify.redirectUri"
        const val KEY_ACCESS = "spotify.accessToken"
        const val KEY_REFRESH = "spotify.refreshToken"
        const val KEY_EXPIRES = "spotify.expiresAt"
        const val KEY_PENDING_VERIFIER = "spotify.pendingVerifier"
        const val KEY_PENDING_STATE = "spotify.pendingState"
    }
}

/** Hands the OAuth redirect from the activity to whichever screen is waiting for it. */
object SpotifyAuthBridge {
    @Volatile
    private var listener: ((String) -> Unit)? = null

    fun register(handler: (String) -> Unit) {
        listener = handler
    }

    fun unregister() {
        listener = null
    }

    fun deliver(uri: String?) {
        val value = uri ?: return
        if (!value.startsWith("droidos://", ignoreCase = true)) return
        listener?.invoke(value)
    }
}
