package dev.droidos.shell.apps.feeds

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Fetches a feed over HTTP and parses RSS 2.0 or Atom with the platform pull parser.
 * Auto-discovery is deliberately skipped: the URL must point at the feed itself.
 */
class FeedClient {

    suspend fun fetch(feedUrl: String): Result<List<FeedItem>> = withContext(Dispatchers.IO) {
        val normalized = FeedEngine.normaliseUrl(feedUrl)
            ?: return@withContext Result.failure(IllegalArgumentException("That is not a usable feed URL"))
        runCatching {
            val connection = (URL(normalized).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 12_000
                readTimeout = 15_000
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml")
                setRequestProperty("User-Agent", "DroidOS/1.9.0 (+https://github.com/itriedcoding/DroidOS)")
            }
            try {
                val status = connection.responseCode
                if (status !in 200..299) {
                    throw IllegalStateException(
                        when (status) {
                            401, 403 -> "That feed refused the request ($status). It may need a login."
                            404 -> "That feed URL returned 404"
                            429 -> "That feed is rate limiting requests"
                            in 500..599 -> "That feed's server failed ($status)"
                            else -> "Feed returned HTTP $status"
                        }
                    )
                }
                val source = FeedEngine.hostLabel(normalized)
                connection.inputStream.use { parse(it, source) }
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun parse(stream: InputStream, source: String): List<FeedItem> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(stream, null)

        val items = mutableListOf<FeedItem>()
        var inEntry = false
        var title = ""
        var link = ""
        var summary = ""
        var published = ""
        var depthGuard = 0

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (parser.name.lowercase(Locale.US)) {
                        "item", "entry" -> {
                            inEntry = true
                            title = ""
                            link = ""
                            summary = ""
                            published = ""
                        }
                        "link" -> if (inEntry) {
                            val href = parser.getAttributeValue(null, "href")
                            val rel = parser.getAttributeValue(null, "rel")
                            if (!href.isNullOrBlank() && (rel == null || rel == "alternate")) {
                                link = href
                            }
                        }
                        "title" -> if (inEntry) title = readText(parser)
                        "description", "summary", "content" -> if (inEntry) {
                            summary = readText(parser)
                        }
                        "pubdate", "published", "updated", "date" -> if (inEntry) {
                            published = readText(parser)
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name.equals("item", true) || parser.name.equals("entry", true)) {
                        inEntry = false
                        if (title.isNotBlank() || link.isNotBlank()) {
                            items += FeedItem(
                                title = FeedEngine.stripHtml(title).ifBlank { "(untitled)" },
                                link = link.trim(),
                                summary = FeedEngine.summarise(summary),
                                source = source,
                                publishedAt = parseDate(published)
                            )
                        }
                    }
                }
            }
            event = parser.next()
            if (++depthGuard > 200_000) break
        }
        return items
    }

    private fun readText(parser: XmlPullParser): String {
        val builder = StringBuilder()
        var depth = 1
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> builder.append(parser.text ?: "")
                XmlPullParser.END_DOCUMENT -> return builder.toString()
            }
        }
        return builder.toString()
    }

    private fun parseDate(raw: String): Long {
        val text = raw.trim()
        if (text.isEmpty()) return 0L
        val iso = runCatching { OffsetDateTime.parse(text).toInstant() }.getOrNull()
        if (iso != null) return iso.toEpochMilli()
        val zoned = runCatching { ZonedDateTime.parse(text).toInstant() }.getOrNull()
        if (zoned != null) return zoned.toEpochMilli()
        val instant = runCatching { Instant.parse(text) }.getOrNull()
        if (instant != null) return instant.toEpochMilli()
        val rfc = runCatching {
            ZonedDateTime.parse(text, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()
        }.getOrNull()
        if (rfc != null) return rfc.toEpochMilli()
        return 0L
    }
}
