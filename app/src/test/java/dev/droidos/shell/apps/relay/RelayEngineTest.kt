package dev.droidos.shell.apps.relay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class RelayEngineTest {

    private val now = Instant.parse("2026-11-05T18:00:00Z")

    @Test
    fun botTokensUseTheBotPrefix() {
        assertEquals("Bot abc.def", RelayEngine.authHeader("  abc.def "))
    }

    @Test
    fun endpointsMatchTheDocumentedRoutes() {
        assertEquals("/users/@me", RelayEngine.mePath())
        assertEquals("/users/@me/guilds", RelayEngine.guildsPath())
        assertEquals("/guilds/123/channels", RelayEngine.channelsPath("123"))
        assertEquals("/channels/456/messages?limit=50", RelayEngine.messagesPath("456"))
        assertEquals("/channels/456/messages?limit=100", RelayEngine.messagesPath("456", limit = 500))
        assertEquals("/channels/456/messages?limit=1", RelayEngine.messagesPath("456", limit = 0))
        assertEquals("/channels/456/messages", RelayEngine.sendPath("456"))
    }

    @Test
    fun onlyTextBearingChannelsPass() {
        assertTrue(RelayEngine.isTextChannel(0))
        assertTrue(RelayEngine.isTextChannel(5))
        assertFalse(RelayEngine.isTextChannel(2))
        assertFalse(RelayEngine.isTextChannel(4))
    }

    @Test
    fun messageRulesMatchDiscord() {
        assertNull(RelayEngine.validateMessage("hello"))
        assertNull(RelayEngine.validateMessage("x".repeat(2000)))
        assertEquals("Type a message first", RelayEngine.validateMessage("   "))
        assertTrue(RelayEngine.validateMessage("x".repeat(2001))!!.contains("2000"))
    }

    @Test
    fun failuresAreExplained() {
        assertTrue(RelayEngine.errorMessage(401).contains("bot token"))
        assertTrue(RelayEngine.errorMessage(403).contains("Read Message History"))
        assertTrue(RelayEngine.errorMessage(404).contains("refresh", ignoreCase = true))
        assertTrue(RelayEngine.errorMessage(429, 1.5).contains("1.5s"))
        assertTrue(RelayEngine.errorMessage(502).contains("server error"))
    }

    @Test
    fun timestampsBecomeRelativeLabels() {
        assertEquals("just now", RelayEngine.relativeTime("2026-11-05T17:59:30+00:00", now))
        assertEquals("5m ago", RelayEngine.relativeTime("2026-11-05T17:55:00+00:00", now))
        assertEquals("2h ago", RelayEngine.relativeTime("2026-11-05T16:00:00+00:00", now))
        assertEquals("3d ago", RelayEngine.relativeTime("2026-11-02T18:00:00+00:00", now))
        assertEquals("1 Oct 2026", RelayEngine.relativeTime("2026-10-01T09:00:00+00:00", now))
        assertEquals("unknown time", RelayEngine.relativeTime("not a timestamp", now))
    }

    @Test
    fun previewsFlattenNewlinesAndCapLength() {
        assertEquals("one two", RelayEngine.preview("one\ntwo"))
        val long = "a".repeat(300)
        val preview = RelayEngine.preview(long, limit = 20)
        assertEquals(20, preview.length)
        assertTrue(preview.endsWith("..."))
    }

    @Test
    fun botsAreLabelled() {
        assertEquals("relayd [bot]", RelayEngine.authorLabel("relayd", bot = true))
        assertEquals("donovan", RelayEngine.authorLabel("donovan", bot = false))
    }
}
