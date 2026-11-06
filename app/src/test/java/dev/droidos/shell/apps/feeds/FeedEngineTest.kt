package dev.droidos.shell.apps.feeds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedEngineTest {

    @Test
    fun urlsAreNormalisedAndValidated() {
        assertEquals("https://example.com/feed.xml", FeedEngine.normaliseUrl("example.com/feed.xml"))
        assertEquals("http://example.com/rss", FeedEngine.normaliseUrl("  http://example.com/rss "))
        assertNull(FeedEngine.normaliseUrl(""))
        assertNull(FeedEngine.normaliseUrl("ftp://example.com/feed"))
        assertNull(FeedEngine.normaliseUrl("https://"))
    }

    @Test
    fun hostsAreReadable() {
        assertEquals("example.com", FeedEngine.hostLabel("https://www.example.com/feed.xml"))
        assertEquals("news.site.org", FeedEngine.hostLabel("https://news.site.org/rss"))
    }

    @Test
    fun htmlIsFlattened() {
        assertEquals(
            "Hello world",
            FeedEngine.stripHtml("<p>Hello <b>world</b></p>")
        )
        assertEquals("A & B", FeedEngine.stripHtml("A &amp; B"))
        assertEquals("Tom's feed", FeedEngine.stripHtml("Tom&#39;s feed"))
        assertEquals("one two", FeedEngine.stripHtml("one\n\n   two"))
    }

    @Test
    fun summariesAreCapped() {
        val long = "<p>" + "a".repeat(400) + "</p>"
        val summary = FeedEngine.summarise(long, limit = 50)
        assertEquals(50, summary.length)
        assertTrue(summary.endsWith("..."))
        assertEquals("short", FeedEngine.summarise("<p>short</p>", limit = 50))
    }

    @Test
    fun relativeTimesCoverTheScale() {
        val now = 1_791_331_200_000L
        assertEquals("just now", FeedEngine.relativeTime(now - 10_000, now))
        assertEquals("5m ago", FeedEngine.relativeTime(now - 5 * 60_000L, now))
        assertEquals("3h ago", FeedEngine.relativeTime(now - 3 * 3_600_000L, now))
        assertEquals("2d ago", FeedEngine.relativeTime(now - 2 * 86_400_000L, now))
        assertEquals("3w ago", FeedEngine.relativeTime(now - 21 * 86_400_000L, now))
        assertEquals("2y ago", FeedEngine.relativeTime(now - 800L * 86_400_000L, now))
        assertEquals("undated", FeedEngine.relativeTime(0L, now))
    }

    @Test
    fun duplicatesAreDroppedAndOrderIsNewestFirst() {
        val items = listOf(
            FeedItem("old", "https://a/1", "", "a", 100L),
            FeedItem("new", "https://a/2", "", "a", 300L),
            FeedItem("dupe", "https://a/1", "", "b", 200L),
            FeedItem("blank", "", "", "b", 400L)
        )
        val deduped = FeedEngine.dedupe(items)
        assertEquals(2, deduped.size)
        assertEquals("new", deduped[0].title)
        assertEquals("old", deduped[1].title)
    }

    @Test
    fun feedishUrlsAreRecognised() {
        assertTrue(FeedEngine.looksLikeFeed("https://example.com/rss"))
        assertTrue(FeedEngine.looksLikeFeed("https://example.com/atom.xml"))
        assertEquals(false, FeedEngine.looksLikeFeed("https://example.com/about"))
    }
}
