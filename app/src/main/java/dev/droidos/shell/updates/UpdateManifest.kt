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
