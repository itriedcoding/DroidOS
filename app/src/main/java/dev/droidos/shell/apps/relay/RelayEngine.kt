package dev.droidos.shell.apps.relay

import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Pure helpers for the Relay bridge: endpoint layout, message rules and the wording of failures.
 * Relay is an independent client of the official Discord HTTP API, driven by a bot token.
 */
object RelayEngine {

    const val API_BASE = "https://discord.com/api/v10"
    const val USER_AGENT = "DiscordBot (https://github.com/itriedcoding/DroidOS, 1.9.0)"
    const val MAX_MESSAGE = 2000

    /** Guild text (0) and announcement (5) channels are the ones a bot token can message. */
    private val TEXT_CHANNELS = setOf(0, 5)

    private val HISTORY = DateTimeFormatter.ofPattern("d MMM yyyy")

    fun authHeader(token: String): String = "Bot ${token.trim()}"

    fun mePath(): String = "/users/@me"

    fun guildsPath(): String = "/users/@me/guilds"

    fun channelsPath(guildId: String): String = "/guilds/$guildId/channels"

    fun messagesPath(channelId: String, limit: Int = 50): String =
        "/channels/$channelId/messages?limit=${limit.coerceIn(1, 100)}"

    fun sendPath(channelId: String): String = "/channels/$channelId/messages"

    fun isTextChannel(type: Int): Boolean = type in TEXT_CHANNELS

    fun validateMessage(content: String): String? = when {
        content.isBlank() -> "Type a message first"
        content.length > MAX_MESSAGE ->
            "Discord caps messages at $MAX_MESSAGE characters; this one is ${content.length}"
        else -> null
    }

    fun errorMessage(status: Int, retryAfterSeconds: Double? = null): String = when (status) {
        401 -> "Discord rejected the bot token. Copy it again from the Bot page of your application."
        403 -> "The bot cannot read there: invite it to the server and grant Read Message History."
        404 -> "Discord does not know that channel or server. Refresh the lists."
        429 -> buildString {
            append("Discord rate limited this client")
            retryAfterSeconds?.takeUnless { it.isNaN() }?.let { append("; retry in ${"%.1f".format(it)}s") }
        }
        in 500..599 -> "Discord returned a server error ($status). Try again shortly."
        else -> "Discord returned HTTP $status"
    }

    fun parseTimestamp(iso: String): Instant? =
        runCatching { OffsetDateTime.parse(iso).toInstant() }
            .recoverCatching { Instant.parse(iso) }
            .getOrNull()

    fun relativeTime(iso: String, now: Instant = Instant.now()): String {
        val moment = parseTimestamp(iso) ?: return "unknown time"
        val seconds = Duration.between(moment, now).seconds
        return when {
            seconds < 0 -> moment.atZone(ZoneOffset.UTC).format(HISTORY)
            seconds < 60 -> "just now"
            seconds < 3_600 -> "${seconds / 60}m ago"
            seconds < 86_400 -> "${seconds / 3_600}h ago"
            seconds < 7 * 86_400 -> "${seconds / 86_400}d ago"
            else -> moment.atZone(ZoneOffset.UTC).format(HISTORY)
        }
    }

    fun preview(content: String, limit: Int = 160): String {
        val flat = content.replace("\r", " ").replace("\n", " ").trim()
        return if (flat.length <= limit) flat else flat.take(limit - 3).trimEnd() + "..."
    }

    fun authorLabel(username: String, bot: Boolean): String =
        if (bot) "$username [bot]" else username
}
