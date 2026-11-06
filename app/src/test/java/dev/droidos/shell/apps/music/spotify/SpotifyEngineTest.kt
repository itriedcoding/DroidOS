package dev.droidos.shell.apps.music.spotify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SpotifyEngineTest {

    @Test
    fun matchesTheRfc7636TestVector() {
        val verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
        assertEquals(
            "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            SpotifyEngine.codeChallenge(verifier)
        )
    }

    @Test
    fun verifiersStayInsideThePkceAlphabet() {
        val verifier = SpotifyEngine.codeVerifier(Random(7))
        assertEquals(64, verifier.length)
        assertTrue(verifier.all { it.isLetterOrDigit() || it in "-._~" })
        assertNotEquals(verifier, SpotifyEngine.codeVerifier(Random(8)))
        assertEquals(43, SpotifyEngine.codeVerifier(Random(1), length = 43).length)
        assertEquals(128, SpotifyEngine.codeVerifier(Random(1), length = 128).length)
        assertTrue(runCatching { SpotifyEngine.codeVerifier(length = 20) }.isFailure)
    }

    @Test
    fun authorizationUrlCarriesEveryRequiredParameter() {
        val url = SpotifyEngine.authorizationUrl(
            clientId = "client id",
            redirectUri = "droidos://spotify-callback",
            challenge = "challenge",
            state = "state-1"
        )
        assertTrue(url.startsWith(SpotifyEngine.AUTHORIZE_ENDPOINT))
        assertTrue(url.contains("response_type=code"))
        assertTrue(url.contains("client_id=client+id"))
        assertTrue(url.contains("code_challenge_method=S256"))
        assertTrue(url.contains("redirect_uri=droidos%3A%2F%2Fspotify-callback"))
        assertTrue(url.contains("state=state-1"))
        assertTrue(url.contains("scope=user-read-private+user-read-playback-state+user-modify-playback-state"))
    }

    @Test
    fun parsesRedirectParameters() {
        assertEquals(
            "AQD-123",
            SpotifyEngine.authorizationCode("droidos://spotify-callback?code=AQD-123&state=xyz")
        )
        assertEquals(
            "xyz",
            SpotifyEngine.authorizationState("droidos://spotify-callback?code=AQD-123&state=xyz")
        )
        assertEquals(
            "access_denied",
            SpotifyEngine.authorizationError("droidos://spotify-callback?error=access_denied")
        )
        assertNull(SpotifyEngine.authorizationCode("droidos://spotify-callback"))
        assertEquals("a b", SpotifyEngine.redirectParameter("droidos://x?q=a+b", "q"))
    }

    @Test
    fun redirectMatchingIgnoresTheQuery() {
        assertTrue(SpotifyEngine.matchesRedirect("droidos://spotify-callback?code=1", "droidos://spotify-callback"))
        assertFalse(SpotifyEngine.matchesRedirect("droidos://other?code=1", "droidos://spotify-callback"))
    }

    @Test
    fun tokenBodiesEncodeTheirValues() {
        val body = SpotifyEngine.tokenRequestBody(
            code = "code",
            redirectUri = "droidos://spotify-callback",
            clientId = "id",
            verifier = "verifier-with-._~"
        )
        assertTrue(body.contains("grant_type=authorization_code"))
        // URLEncoder leaves -._ alone but percent-encodes the tilde of the PKCE alphabet.
        assertTrue(body.contains("code_verifier=verifier-with-._%7E"))
        assertTrue(body.contains("redirect_uri=droidos%3A%2F%2Fspotify-callback"))

        val refresh = SpotifyEngine.refreshRequestBody("refresh-1", "id")
        assertTrue(refresh.contains("grant_type=refresh_token"))
        assertTrue(refresh.contains("refresh_token=refresh-1"))
    }

    @Test
    fun expiryHonoursTheSafetySkew() {
        assertFalse(SpotifyEngine.isExpired(expiresAtMillis = 1_000_000, nowMillis = 900_000))
        assertTrue(SpotifyEngine.isExpired(expiresAtMillis = 1_000_000, nowMillis = 940_000))
        assertTrue(SpotifyEngine.isExpired(expiresAtMillis = 0, nowMillis = 1))
    }

    @Test
    fun statusesBecomeUsefulSentences() {
        assertTrue(SpotifyEngine.errorMessage(401).contains("Reconnect"))
        assertTrue(SpotifyEngine.errorMessage(403).contains("Premium"))
        assertTrue(SpotifyEngine.errorMessage(404).contains("device"))
        assertTrue(SpotifyEngine.errorMessage(429).contains("rate limiting"))
        assertTrue(SpotifyEngine.errorMessage(503).contains("server error"))
    }
}
