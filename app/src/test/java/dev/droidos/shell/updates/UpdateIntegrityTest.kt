package dev.droidos.shell.updates

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateIntegrityTest {

    private fun manifest(version: String, url: String) = UpdateManifest(
        versionName = version,
        versionCode = 5,
        apkUrl = url,
        sha256 = "a".repeat(64),
        notes = "",
        mandatory = false
    )

    private fun release(version: String, url: String) = ReleaseInfo(
        tag = "v$version",
        versionName = version,
        notes = "",
        pageUrl = "https://github.com/itriedcoding/DroidOS/releases/tag/v$version",
        publishedAt = "2026-11-05T18:50:01Z",
        assetName = "droidos-$version-release.apk",
        assetUrl = url,
        assetBytes = 3_754_282L
    )

    @Test
    fun agreeingSourcesAreAccepted() {
        val url = "https://github.com/itriedcoding/DroidOS/releases/download/v1.4.0/droidos-1.4.0-release.apk"
        assertNull(UpdateIntegrity.disagreement(manifest("1.4.0", url), release("1.4.0", url)))
    }

    @Test
    fun versionMismatchIsRejected() {
        val reason = UpdateIntegrity.disagreement(
            manifest("1.5.0", "https://example.test/a.apk"),
            release("1.4.0", "https://example.test/a.apk")
        )
        assertNotNull(reason)
        assertTrue(reason!!.contains("1.5.0"))
    }

    @Test
    fun differentAssetsAreRejected() {
        val reason = UpdateIntegrity.disagreement(
            manifest("1.4.0", "https://example.test/one.apk"),
            release("1.4.0", "https://example.test/two.apk")
        )
        assertNotNull(reason)
    }

    @Test
    fun trailingSlashDoesNotCauseAFalseRejection() {
        assertNull(
            UpdateIntegrity.disagreement(
                manifest("1.4.0", "https://example.test/one.apk/"),
                release("1.4.0", "https://example.test/one.apk")
            )
        )
    }
}
