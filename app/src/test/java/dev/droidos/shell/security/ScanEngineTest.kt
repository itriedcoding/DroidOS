package dev.droidos.shell.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanEngineTest {

    private val now = 1_700_000_000_000L

    private fun facts(
        packageName: String = "com.example.app",
        installer: String? = "com.android.vending",
        system: Boolean = false,
        targetSdk: Int = 35,
        permissions: Set<String> = emptySet(),
        accessibility: Boolean = false,
        deviceAdmin: Boolean = false,
        debuggable: Boolean = false,
        firstInstallTime: Long = now - 1_000_000L
    ) = PackageFacts(
        packageName = packageName,
        label = "Example",
        installerPackage = installer,
        isSystem = system,
        targetSdk = targetSdk,
        requestedPermissions = permissions,
        hasAccessibilityService = accessibility,
        isDeviceAdmin = deviceAdmin,
        isDebuggable = debuggable,
        firstInstallTime = firstInstallTime
    )

    @Test
    fun `store installed app with no risky permissions is clean`() {
        val result = ScanEngine.evaluate(facts(), now)
        assertEquals(0, result.score)
        assertEquals(Severity.SAFE, result.risk)
        assertTrue(result.findings.isEmpty())
    }

    @Test
    fun `system app is not treated as sideloaded`() {
        val result = ScanEngine.evaluate(facts(installer = null, system = true), now)
        assertEquals(0, result.score)
    }

    @Test
    fun `sideloaded app alone is low risk`() {
        val result = ScanEngine.evaluate(facts(installer = "com.random.installer"), now)
        assertEquals(Severity.LOW, result.risk)
        assertTrue(result.findings.any { it.ruleId == "installer.untrusted" })
    }

    @Test
    fun `debuggable sideloaded app with accessibility is high risk`() {
        val result = ScanEngine.evaluate(
            facts(
                installer = "com.random.installer",
                accessibility = true,
                debuggable = true
            ),
            now
        )
        assertEquals(Severity.HIGH, result.risk)
        assertTrue(result.score >= 50)
        assertTrue(result.findings.any { it.ruleId == "surface.accessibility" })
        assertTrue(result.findings.any { it.ruleId == "build.debuggable" })
        assertTrue(result.findings.any { it.ruleId == "installer.untrusted.control" })
    }

    @Test
    fun `trusted installer with accessibility stays medium`() {
        val result = ScanEngine.evaluate(facts(accessibility = true), now)
        assertEquals(Severity.MEDIUM, result.risk)
    }

    @Test
    fun `sms access from a store app is flagged`() {
        val result = ScanEngine.evaluate(
            facts(permissions = setOf("android.permission.READ_SMS")),
            now
        )
        assertTrue(result.findings.any { it.ruleId == "perm.messaging" })
    }

    @Test
    fun `surveillance permission trio is flagged`() {
        val result = ScanEngine.evaluate(
            facts(
                permissions = setOf(
                    "android.permission.CAMERA",
                    "android.permission.RECORD_AUDIO",
                    "android.permission.ACCESS_FINE_LOCATION"
                )
            ),
            now
        )
        assertTrue(result.findings.any { it.ruleId == "perm.surveillance_combo" })
    }

    @Test
    fun `old target sdk is flagged`() {
        val result = ScanEngine.evaluate(facts(targetSdk = 28), now)
        assertTrue(result.findings.any { it.ruleId == "build.outdated_sdk" })
    }

    @Test
    fun `recent sideload note expires`() {
        val old = now - 10L * 24 * 60 * 60 * 1000
        val result = ScanEngine.evaluate(
            facts(installer = "com.random.installer", firstInstallTime = old),
            now
        )
        assertFalse(result.findings.any { it.ruleId == "install.fresh_sideload" })
    }

    @Test
    fun `score is capped at one hundred`() {
        val result = ScanEngine.evaluate(
            facts(
                installer = "com.random.installer",
                accessibility = true,
                deviceAdmin = true,
                debuggable = true,
                targetSdk = 28,
                permissions = setOf(
                    "android.permission.REQUEST_INSTALL_PACKAGES",
                    "android.permission.SYSTEM_ALERT_WINDOW",
                    "android.permission.READ_SMS",
                    "android.permission.SEND_SMS",
                    "android.permission.CAMERA",
                    "android.permission.RECORD_AUDIO",
                    "android.permission.ACCESS_FINE_LOCATION"
                )
            ),
            now
        )
        assertEquals(100, result.score)
        assertEquals(Severity.HIGH, result.risk)
    }

    @Test
    fun `scanAll orders the riskiest first and drops clean apps`() {
        val results = ScanEngine.scanAll(
            listOf(
                facts(packageName = "clean.app"),
                facts(packageName = "sideloaded.app", installer = "com.random.installer"),
                facts(packageName = "dangerous.app", installer = "com.random.installer", debuggable = true, accessibility = true)
            ),
            now
        )
        assertEquals(2, results.size)
        assertEquals("dangerous.app", results.first().packageName)
    }

    @Test
    fun `risk bands map from score`() {
        assertEquals(Severity.SAFE, ScanEngine.riskFor(0))
        assertEquals(Severity.LOW, ScanEngine.riskFor(1))
        assertEquals(Severity.MEDIUM, ScanEngine.riskFor(20))
        assertEquals(Severity.HIGH, ScanEngine.riskFor(50))
    }
}
