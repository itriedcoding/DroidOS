package dev.droidos.shell.apps.music.spotify

import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Base64
import kotlin.random.Random

/**
 * Pure Spotify protocol logic: PKCE, the authorize URL, redirect parsing and error wording.
 * Kept free of Android APIs so the parts that are easy to get subtly wrong are unit-tested.
 */
object SpotifyEngine {

    const val AUTHORIZE_ENDPOINT = "https://accounts.spotify.com/authorize"
    const val TOKEN_ENDPOINT = "https://accounts.spotify.com/api/token"
    const val API_BASE = "https://api.spotify.com/v1"

    /** Custom schemes are still accepted by Spotify; localhost aliases and plain HTTP are not. */
    const val DEFAULT_REDIRECT = "droidos://spotify-callback"

    val SCOPES = listOf(
        "user-read-private",
        "user-read-playback-state",
        "user-modify-playback-state"
    )

    private const val VERIFIER_ALPHABET =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"

    /** PKCE verifier: 43-128 characters from the unreserved set, as RFC 7636 requires. */
    fun codeVerifier(random: Random = Random.Default, length: Int = 64): String {
        require(length in 43..128) { "PKCE verifiers are 43-128 characters" }
        return buildString(length) {
            repeat(length) { append(VERIFIER_ALPHABET[random.nextInt(VERIFIER_ALPHABET.length)]) }
        }
    }

    /** S256 challenge: base64url(SHA-256(verifier)) without padding. */
    fun codeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    fun authorizationUrl(
        clientId: String,
        redirectUri: String,
        challenge: String,
        state: String
    ): String = buildString {
        append(AUTHORIZE_ENDPOINT)
        append("?response_type=code")
        append("&client_id=").append(encode(clientId))
        append("&redirect_uri=").append(encode(redirectUri))
        append("&scope=").append(encode(SCOPES.joinToString(" ")))
        append("&state=").append(encode(state))
        append("&code_challenge_method=S256")
        append("&code_challenge=").append(encode(challenge))
    }

    fun redirectParameter(uri: String, name: String): String? {
        val query = uri.substringAfter('?', "")
        if (query.isEmpty()) return null
        return query.split('&').firstNotNullOfOrNull { pair ->
            val key = pair.substringBefore('=', "")
            if (key == name) decode(pair.substringAfter('=', "")) else null
        }
    }

    fun authorizationCode(uri: String): String? = redirectParameter(uri, "code")

    fun authorizationError(uri: String): String? = redirectParameter(uri, "error")

    fun authorizationState(uri: String): String? = redirectParameter(uri, "state")

    /** Compares scheme://host/path, ignoring the query and any explicitly registered port. */
    fun matchesRedirect(uri: String, redirectUri: String): Boolean {
        fun base(value: String): String = value.substringBefore('?').substringBefore('#').trimEnd('/')
        return base(uri).equals(base(redirectUri), ignoreCase = true)
    }

    fun tokenRequestBody(
        code: String,
        redirectUri: String,
        clientId: String,
        verifier: String
    ): String = form(
        "grant_type" to "authorization_code",
        "code" to code,
        "redirect_uri" to redirectUri,
        "client_id" to clientId,
        "code_verifier" to verifier
    )

    fun refreshRequestBody(refreshToken: String, clientId: String): String =
        form(
            "grant_type" to "refresh_token",
            "refresh_token" to refreshToken,
            "client_id" to clientId
        )

    private fun form(vararg pairs: Pair<String, String>): String =
        pairs.joinToString("&") { (key, value) -> "$key=${encode(value)}" }

    fun isExpired(expiresAtMillis: Long, nowMillis: Long, skewMillis: Long = 60_000L): Boolean =
        expiresAtMillis <= 0L || nowMillis >= expiresAtMillis - skewMillis

    /** Plain-language outcome for the statuses the Web API actually returns. */
    fun errorMessage(status: Int): String = when (status) {
        401 -> "Spotify rejected the access token. Reconnect the account."
        403 -> "Spotify refused this action. Playback control requires a Premium account."
        404 -> "No active Spotify device. Start playback in the Spotify app, then try again."
        429 -> "Spotify is rate limiting this app. Wait a few seconds and retry."
        in 500..599 -> "Spotify returned a server error ($status). Try again shortly."
        else -> "Spotify returned HTTP $status"
    }

    fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    fun decode(value: String): String = runCatching { URLDecoder.decode(value, "UTF-8") }
        .getOrDefault(value)
}
