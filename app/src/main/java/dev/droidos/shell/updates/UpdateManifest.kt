package dev.droidos.shell.updates

import org.json.JSONObject

/**
 * Update channel manifest published alongside the project and fetched over HTTPS.
 * Parsing and version comparison are pure so they are unit-tested.
 */
data class UpdateManifest(
    val versionName: String,
    val versionCode: Int,
    val apkUrl: String,
    val sha256: String,
    val notes: String,
    val mandatory: Boolean
) {
    companion object {
        fun parse(json: String): UpdateManifest {
            val root = JSONObject(json)
            val apkUrl = root.optString("apkUrl", "")
            require(apkUrl.startsWith("https://")) { "Update URL must use HTTPS" }
            val sha = root.optString("sha256", "").lowercase()
            require(sha.length == 64 && sha.all { it in "0123456789abcdef" }) { "Invalid sha256" }
            return UpdateManifest(
                versionName = root.optString("versionName", "0.0.0"),
                versionCode = root.optInt("versionCode", 0),
                apkUrl = apkUrl,
                sha256 = sha,
                notes = root.optString("notes", ""),
                mandatory = root.optBoolean("mandatory", false)
            )
        }
    }
}

/**
 * A release as published on the project's releases page, parsed from the GitHub releases API.
 *
 * The release page is the human-facing source of truth for what exists; the manifest is the
 * source of truth for the checksum, because the API does not publish one. Both are fetched and
 * cross-checked, so the "Check for updates" screen shows what the release page shows.
 */
data class ReleaseInfo(
    val tag: String,
    val versionName: String,
    val notes: String,
    val pageUrl: String,
    val publishedAt: String,
    val assetName: String,
    val assetUrl: String,
    val assetBytes: Long
)

object GitHubRelease {

    private val VERSION_TAG = Regex("""^v?(\d+(?:\.\d+)*)$""")

    /** Parses one release object, rejecting drafts, non-version tags and non-HTTPS assets. */
    fun parse(json: String): ReleaseInfo {
        val root = JSONObject(json)
        require(!root.optBoolean("draft", false)) { "the newest release is still a draft" }

        val tag = root.optString("tag_name", "")
        val match = VERSION_TAG.matchEntire(tag)
            ?: throw IllegalArgumentException("tag '$tag' is not a version")

        val pageUrl = root.optString("html_url", "")
        require(pageUrl.startsWith("https://")) { "the release page URL must use HTTPS" }

        val assets = root.optJSONArray("assets")
            ?: throw IllegalArgumentException("the release has no assets")
        var asset: JSONObject? = null
        for (index in 0 until assets.length()) {
            val candidate = assets.getJSONObject(index)
            if (candidate.optString("name").endsWith(".apk")) {
                asset = candidate
                break
            }
        }
        val apk = asset ?: throw IllegalArgumentException("the release has no APK asset")

        val url = apk.optString("browser_download_url", "")
        require(url.startsWith("https://")) { "the APK asset URL must use HTTPS" }

        return ReleaseInfo(
            tag = tag,
            versionName = match.groupValues[1],
            notes = root.optString("body", "").trim(),
            pageUrl = pageUrl,
            publishedAt = root.optString("published_at", ""),
            assetName = apk.optString("name"),
            assetUrl = url,
            assetBytes = apk.optLong("size", 0L)
        )
    }
}

/**
 * The release page and the manifest must describe the same build before anything is downloaded.
 * Pure, so `UpdateIntegrityTest` covers it without a network or a device.
 */
object UpdateIntegrity {

    /** Returns null when both sources agree, otherwise the reason they do not. */
    fun disagreement(manifest: UpdateManifest, release: ReleaseInfo): String? {
        if (Versions.compare(manifest.versionName, release.versionName) != VersionRelation.SAME) {
            return "the release page offers ${release.versionName} but the manifest says " +
                "${manifest.versionName}"
        }
        if (manifest.apkUrl.trimEnd('/') != release.assetUrl.trimEnd('/')) {
            return "the manifest and the release page point at different APK files"
        }
        return null
    }
}

enum class VersionRelation { NEWER, SAME, OLDER }

object Versions {

    /** Compares dotted numeric versions such as 1.10.2 against 1.9.0. */
    fun compare(left: String, right: String): VersionRelation {
        val a = left.split('.').map { it.toIntOrNull() ?: 0 }
        val b = right.split('.').map { it.toIntOrNull() ?: 0 }
        val size = maxOf(a.size, b.size)
        for (index in 0 until size) {
            val x = a.getOrElse(index) { 0 }
            val y = b.getOrElse(index) { 0 }
            if (x != y) return if (x > y) VersionRelation.NEWER else VersionRelation.OLDER
        }
        return VersionRelation.SAME
    }
}
