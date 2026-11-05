package dev.droidos.shell.core

import android.os.Build
import dev.droidos.shell.BuildConfig

/** Single source of truth for version and device identity shown in Settings/Diagnostics. */
object BuildInfo {
    const val VERSION_NAME: String = BuildConfig.VERSION_NAME
    const val VERSION_CODE: Int = BuildConfig.VERSION_CODE

    /** Update channel manifest published with the project (source of the checksum). */
    const val UPDATE_CHANNEL_URL: String =
        "https://raw.githubusercontent.com/itriedcoding/DroidOS/main/updates/channel.json"

    /** Latest published release, read by Settings -> System update. */
    const val RELEASES_API_URL: String =
        "https://api.github.com/repos/itriedcoding/DroidOS/releases/latest"

    /** Human-facing release list, opened from the update screen. */
    const val RELEASES_PAGE_URL: String = "https://github.com/itriedcoding/DroidOS/releases"

    val DEVICE_SUMMARY: String
        get() = "${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})"

    val ANDROID_VERSION: String
        get() = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

    val SECURITY_PATCH: String
        get() = if (Build.VERSION.SECURITY_PATCH.isNullOrBlank()) "unknown" else Build.VERSION.SECURITY_PATCH

    val BUILD_FINGERPRINT: String
        get() = Build.FINGERPRINT
}
