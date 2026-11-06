package dev.droidos.shell.apps.feeds

import java.net.URI
import java.net.URLDecoder
import java.util.Locale
import java.util.concurrent.TimeUnit

/** One parsed entry. Kept here so the engine can dedupe and label without Android types. */
data class FeedItem(
    val title: String,
    val link: String,
    val summary: String,
    val source: String,
    val publishedAt: Long
)

object FeedEngine {

    private val TAG = Regex("<[^>]*>")
    private val SPACE = Regex("\\s+")

    /** Accepts bare hosts by assuming https, and rejects anything that is not http(s). */
    fun normaliseUrl(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val withScheme = if (trimmed.contains("://")) trimmed else "https://$trimmed"
        return runCatching {
            val uri = URI(withScheme)
            val scheme = uri.scheme?.lowercase(Locale.US) ?: return null
            if (scheme != "http" && scheme != "https") return null
            if (uri.host.isNullOrBlank()) return null
            uri.toString()
        }.getOrNull()
    }

    fun hostLabel(url: String): String = runCatching {
        URI(url).host?.removePrefix("www.") ?: url
    }.getOrDefault(url)

    fun stripHtml(input: String): String {
        val withoutTags = TAG.replace(input, " ")
        return decodeEntities(withoutTags).replace(SPACE, " ").trim()
    }

    fun summarise(input: String, limit: Int = 220): String {
        val plain = stripHtml(input)
        return if (plain.length <= limit) plain else plain.take(limit - 3).trimEnd() + "..."
    }

    private fun decodeEntities(input: String): String = input
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&#x27;", "'")
        .replace("&apos;", "'")
        .replace("&mdash;", "-")
        .replace("&ndash;", "-")

    fun relativeTime(epochMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
        if (epochMillis <= 0L) return "undated"
        val delta = nowMillis - epochMillis
        if (delta < 0L) return "just now"
        val minutes = TimeUnit.MILLISECONDS.toMinutes(delta)
        val hours = TimeUnit.MILLISECONDS.toHours(delta)
        val days = TimeUnit.MILLISECONDS.toDays(delta)
        return when {
            minutes < 1 -> "just now"
            minutes < 60 -> "${minutes}m ago"
            hours < 24 -> "${hours}h ago"
            days < 7 -> "${days}d ago"
            days < 365 -> "${days / 7}w ago"
            else -> "${days / 365}y ago"
        }
    }

    /** Newest first, dropping repeated links while keeping the first occurrence. */
    fun dedupe(items: List<FeedItem>): List<FeedItem> {
        val seen = HashSet<String>()
        return items
            .filter { it.link.isNotBlank() && seen.add(it.link) }
            .sortedByDescending { it.publishedAt }
    }

    fun looksLikeFeed(url: String): Boolean {
        val lower = url.lowercase(Locale.US)
        return lower.contains("rss") || lower.contains("atom") || lower.contains("feed") ||
            lower.endsWith(".xml")
    }

    fun decodeRedirect(url: String): String = runCatching {
        URLDecoder.decode(url, "UTF-8")
    }.getOrDefault(url)
}
