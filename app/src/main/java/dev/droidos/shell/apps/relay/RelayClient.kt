package dev.droidos.shell.apps.relay

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Talks to the official Discord REST API with a bot token the user creates themselves.
 * Nothing is simulated: every list comes from a live request, and failures surface verbatim.
 */
class RelayClient {

    data class Guild(val id: String, val name: String)

    data class Channel(val id: String, val name: String, val type: Int)

    data class Message(
        val id: String,
        val author: String,
        val bot: Boolean,
        val content: String,
        val timestamp: String
    )

    suspend fun verifyToken(token: String): Result<String> =
        request(token, "GET", RelayEngine.mePath()).mapCatching { body ->
            JSONObject(body).optString("username", "bot")
        }

    suspend fun guilds(token: String): Result<List<Guild>> =
        request(token, "GET", RelayEngine.guildsPath()).mapCatching { body ->
            val array = JSONArray(body)
            buildList {
                for (index in 0 until array.length()) {
                    val guild = array.optJSONObject(index) ?: continue
                    val id = guild.optString("id")
                    if (id.isBlank()) continue
                    add(Guild(id, guild.optString("name", "Server")))
                }
            }.sortedBy { it.name.lowercase() }
        }

    suspend fun channels(token: String, guildId: String): Result<List<Channel>> =
        request(token, "GET", RelayEngine.channelsPath(guildId)).mapCatching { body ->
            val array = JSONArray(body)
            buildList {
                for (index in 0 until array.length()) {
                    val channel = array.optJSONObject(index) ?: continue
                    val type = channel.optInt("type", -1)
                    if (!RelayEngine.isTextChannel(type)) continue
                    val id = channel.optString("id")
                    if (id.isBlank()) continue
                    add(Channel(id, channel.optString("name", "channel"), type))
                }
            }
        }

    suspend fun messages(token: String, channelId: String): Result<List<Message>> =
        request(token, "GET", RelayEngine.messagesPath(channelId)).mapCatching { body ->
            val array = JSONArray(body)
            buildList {
                for (index in 0 until array.length()) {
                    val message = array.optJSONObject(index) ?: continue
                    val author = message.optJSONObject("author") ?: continue
                    add(
                        Message(
                            id = message.optString("id"),
                            author = author.optString("username", "unknown"),
                            bot = author.optBoolean("bot"),
                            content = message.optString("content"),
                            timestamp = message.optString("timestamp")
                        )
                    )
                }
            }
        }

    suspend fun send(token: String, channelId: String, content: String): Result<Unit> {
        RelayEngine.validateMessage(content)?.let { return Result.failure(IllegalArgumentException(it)) }
        val payload = JSONObject().put("content", content).toString()
        return request(token, "POST", RelayEngine.sendPath(channelId), payload).map { }
    }

    private suspend fun request(
        token: String,
        method: String,
        path: String,
        body: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        if (token.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Add a bot token first"))
        }
        runCatching {
            val connection = (URL(RelayEngine.API_BASE + path).openConnection() as HttpURLConnection)
                .apply {
                    requestMethod = method
                    connectTimeout = 12_000
                    readTimeout = 15_000
                    setRequestProperty("Authorization", RelayEngine.authHeader(token))
                    setRequestProperty("User-Agent", RelayEngine.USER_AGENT)
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
                    val retry = runCatching { JSONObject(text).optDouble("retry_after") }.getOrNull()
                    throw IllegalStateException(RelayEngine.errorMessage(status, retry))
                }
                text
            } finally {
                connection.disconnect()
            }
        }
    }
}
