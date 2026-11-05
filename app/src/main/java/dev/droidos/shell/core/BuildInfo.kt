package dev.droidos.shell.core

import android.os.Build
import dev.droidos.shell.BuildConfig

/** Single source of truth for version and device identity shown in Settings/Diagnostics. */
object BuildInfo {
    const val VERSION_NAME: String = BuildConfig.VERSION_NAME
    const val VERSION_CODE: Int = BuildConfig.VERSION_CODE

    /** Update channel manifest published with the project. */
    const val UPDATE_CHANNEL_URL: String =
        "https://raw.githubusercontent.com/itriedcoding/DroidOS/main/updates/channel.json"

    val DEVICE_SUMMARY: String
        get() = "${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})"

    val ANDROID_VERSION: String
        get() = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

    val SECURITY_PATCH: String
        get() = if (Build.VERSION.SECURITY_PATCH.isNullOrBlank()) "unknown" else Build.VERSION.SECURITY_PATCH

    val BUILD_FINGERPRINT: String
        get() = Build.FINGERPRINT
}
