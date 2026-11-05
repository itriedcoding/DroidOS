package dev.droidos.shell.firewall

import dev.droidos.shell.core.Fmt
import dev.droidos.shell.updates.VersionRelation
import dev.droidos.shell.updates.Versions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FirewallRulesTest {

    private val installed = listOf(
        "com.android.chrome",
        "com.example.bank",
        "dev.droidos.shell",
        "com.example.mail"
    )

    @Test
    fun `blocked apps are captured by the tunnel`() {
        val allowed = FirewallRules.allowedPackages(installed, setOf("com.example.bank"), "dev.droidos.shell")
        assertFalse(allowed.contains("com.example.bank"))
        assertTrue(allowed.contains("com.android.chrome"))
    }

    @Test
    fun `the shell itself is always excluded so it keeps network access`() {
        val allowed = FirewallRules.allowedPackages(installed, emptySet(), "dev.droidos.shell")
        assertFalse(allowed.contains("dev.droidos.shell"))
    }

    @Test
    fun `nothing allowed when everything is blocked`() {
        val allowed = FirewallRules.allowedPackages(installed, installed.toSet(), "dev.droidos.shell")
        assertTrue(allowed.isEmpty())
    }

    @Test
    fun `duplicates are collapsed and order is stable`() {
        val withDuplicates = listOf("b.app", "a.app", "b.app")
        assertEquals(listOf("a.app", "b.app"), FirewallRules.allowedPackages(withDuplicates, emptySet(), "self"))
    }

    @Test
    fun `summary counts blocked apps`() {
        assertEquals("2 of 10 installed apps denied network access", FirewallRules.summary(10, 2))
    }

    @Test
    fun `version comparison handles numeric segments`() {
        assertEquals(VersionRelation.NEWER, Versions.compare("1.10.2", "1.9.0"))
        assertEquals(VersionRelation.OLDER, Versions.compare("1.0.0", "1.0.1"))
        assertEquals(VersionRelation.SAME, Versions.compare("2.3.4", "2.3.4"))
        assertEquals(VersionRelation.SAME, Versions.compare("1.0", "1.0.0"))
    }

    @Test
    fun `byte formatting scales units`() {
        assertEquals("0 B", Fmt.bytes(0))
        assertEquals("512 B", Fmt.bytes(512))
        assertTrue(Fmt.bytes(1536).startsWith("1.5"))
        assertTrue(Fmt.bytes(5L * 1024 * 1024 * 1024).endsWith("GB"))
    }

    @Test
    fun `relative time is human readable`() {
        val now = 1_000_000_000L
        assertEquals("now", Fmt.relativeTime(now, now))
        assertEquals("2m ago", Fmt.relativeTime(now - 120_000, now))
        assertEquals("3h ago", Fmt.relativeTime(now - 3 * 3_600_000, now))
        assertEquals("2d ago", Fmt.relativeTime(now - 2 * 86_400_000, now))
    }
}
