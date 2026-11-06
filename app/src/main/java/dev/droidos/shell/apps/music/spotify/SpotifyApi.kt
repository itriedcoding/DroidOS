package dev.droidos.shell.apps.music.spotify

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * The subset of the Spotify Web API this shell actually uses: search, playback state and
 * transport control. Playback happens in the Spotify app on an active device; this shell is a
 * remote control, which is why the API answers 404 when nothing is active.
 */
class SpotifyApi(private val auth: SpotifyAuth) {

    data class Item(val uri: String, val name: String, val artists: String, val durationMs: Long)

    data class Playback(
        val playing: Boolean,
        val title: String,
        val artists: String,
        val progressMs: Long,
        val durationMs: Long,
        val device: String
    )

    data class Device(val name: String, val active: Boolean, val restricted: Boolean)

    suspend fun search(query: String): Result<List<Item>> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return Result.success(emptyList())
        return call("GET", "/search?type=track&limit=25&q=" + SpotifyEngine.encode(trimmed))
            .mapCatching { body ->
                val items = JSONObject(body).getJSONObject("tracks").getJSONArray("items")
                buildList {
                    for (index in 0 until items.length()) {
                        val track = items.optJSONObject(index) ?: continue
                        val uri = track.optString("uri")
                        if (uri.isBlank()) continue
                        add(
                            Item(
                                uri = uri,
                                name = track.optString("name", "Untitled"),
                                artists = artistNames(track.optJSONArray("artists")),
                                durationMs = track.optLong("duration_ms")
                            )
                        )
                    }
                }
            }
    }

    suspend fun playback(): Result<Playback?> = call("GET", "/me/player").mapCatching { body ->
        if (body.isBlank()) return@mapCatching null
        val json = JSONObject(body)
        val item = json.optJSONObject("item")
        Playback(
            playing = json.optBoolean("is_playing"),
            title = item?.optString("name").orEmpty(),
            artists = artistNames(item?.optJSONArray("artists")),
            progressMs = json.optLong("progress_ms"),
            durationMs = item?.optLong("duration_ms") ?: 0L,
            device = json.optJSONObject("device")?.optString("name").orEmpty()
        )
    }

    suspend fun devices(): Result<List<Device>> = call("GET", "/me/player/devices").mapCatching { body ->
        val devices = JSONObject(body).optJSONArray("devices")
        buildList {
            if (devices != null) {
                for (index in 0 until devices.length()) {
                    val device = devices.optJSONObject(index) ?: continue
                    add(
                        Device(
                            name = device.optString("name", "Unknown device"),
                            active = device.optBoolean("is_active"),
                            restricted = device.optBoolean("is_restricted")
                        )
                    )
                }
            }
        }
    }

    suspend fun play(uri: String): Result<Unit> =
        call("PUT", "/me/player/play", """{"uris":["$uri"]}""").map { }

    suspend fun pause(): Result<Unit> = call("PUT", "/me/player/pause").map { }

    suspend fun skipNext(): Result<Unit> = call("POST", "/me/player/next").map { }

    suspend fun skipPrevious(): Result<Unit> = call("POST", "/me/player/previous").map { }

    private suspend fun call(
        method: String,
        path: String,
        body: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val token = auth.validAccessToken()
            ?: return@withContext Result.failure(IllegalStateException("Connect a Spotify account first"))
        runCatching {
            val connection = (URL(SpotifyEngine.API_BASE + path).openConnection() as HttpURLConnection)
                .apply {
                    requestMethod = method
                    connectTimeout = 12_000
                    readTimeout = 15_000
                    setRequestProperty("Authorization", "Bearer $token")
                    setRequestProperty("Accept", "application/json")
                    if (body != null) {
                        doOutput = true
                        setRequestProperty("Content-Type", "application/json")
                    }
                }
            try {
                if (body != null) {
                    connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                }
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (status !in 200..299) {
                    throw IllegalStateException(SpotifyEngine.errorMessage(status))
                }
                text
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun artistNames(artists: org.json.JSONArray?): String {
        if (artists == null) return "Unknown artist"
        val names = buildList {
            for (index in 0 until artists.length()) {
                artists.optJSONObject(index)?.optString("name")
                    ?.takeIf { it.isNotBlank() }
                    ?.let { add(it) }
            }
        }
        return names.joinToString(", ").ifBlank { "Unknown artist" }
    }
}
