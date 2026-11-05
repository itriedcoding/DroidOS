package dev.droidos.shell.data.model

/** Accent palettes bundled with the theme engine. */
enum class AccentColor(val label: String) {
    HOT_PINK("Hot pink"),
    AZURE("Azure"),
    VIOLET("Violet"),
    EMERALD("Emerald"),
    AMBER("Amber"),
    ROSE("Rose"),
    GRAPHITE("Graphite");

    companion object {
        fun fromId(id: String?): AccentColor =
            entries.firstOrNull { it.name == id } ?: HOT_PINK
    }
}

enum class ThemeMode(val label: String) {
    SYSTEM("Follow system"),
    LIGHT("Light"),
    DARK("Dark");

    companion object {
        fun fromId(id: String?): ThemeMode = entries.firstOrNull { it.name == id } ?: DARK
    }
}

enum class WallpaperStyle(val label: String) {
    AURORA("Aurora"),
    NEBULA("Nebula"),
    EMBER("Ember"),
    MINT("Mint"),
    SLATE("Slate");

    companion object {
        fun fromId(id: String?): WallpaperStyle = entries.firstOrNull { it.name == id } ?: AURORA
    }
}

/** Immutable snapshot of every user preference the shell persists. */
data class AppSettings(
    val accent: AccentColor = AccentColor.HOT_PINK,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val amoledBlack: Boolean = true,
    val wallpaper: WallpaperStyle = WallpaperStyle.AURORA,
    val gridColumns: Int = 0, // 0 = responsive default
    val lockEnabled: Boolean = false,
    val pinSalt: String? = null,
    val pinHash: String? = null,
    val biometricEnabled: Boolean = false,
    val failedUnlocks: Int = 0,
    val lockoutUntil: Long = 0L,
    val lockedApps: Set<String> = emptySet(),
    val firewallEnabled: Boolean = false,
    val firewallBlocked: Set<String> = emptySet(),
    val pinnedSignerDigest: String? = null,
    val firstRunComplete: Boolean = false,
    val onboardingNotificationAsked: Boolean = false,
    val lastUpdateCheck: Long = 0L,
    val updateChannelUrl: String = "",
    val showSecondsInClock: Boolean = false,
    val autoUpdateCheck: Boolean = true
)
