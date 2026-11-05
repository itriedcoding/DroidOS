package dev.droidos.shell.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.droidos.shell.data.model.AccentColor
import dev.droidos.shell.data.model.AppSettings
import dev.droidos.shell.data.model.ThemeMode
import dev.droidos.shell.data.model.WallpaperStyle

/** The three colours each accent contributes to the scheme. */
data class AccentPalette(val primary: Color, val secondary: Color, val glow: Color)

fun paletteFor(accent: AccentColor): AccentPalette = when (accent) {
    AccentColor.AZURE -> AccentPalette(Color(0xFF5B8CFF), Color(0xFF8FB4FF), Color(0xFF6FE3FF))
    AccentColor.VIOLET -> AccentPalette(Color(0xFFA87BFF), Color(0xFFC9A8FF), Color(0xFFE0A6FF))
    AccentColor.EMERALD -> AccentPalette(Color(0xFF3BD6A6), Color(0xFF77E8C4), Color(0xFF9BFFD9))
    AccentColor.AMBER -> AccentPalette(Color(0xFFFFB454), Color(0xFFFFD08A), Color(0xFFFFE0A3))
    AccentColor.ROSE -> AccentPalette(Color(0xFFFF7C9C), Color(0xFFFFAFC3), Color(0xFFFFC7D6))
    AccentColor.GRAPHITE -> AccentPalette(Color(0xFF9BA8C2), Color(0xFFC3CDE0), Color(0xFFD6E0F0))
}

/** Wallpaper gradients are painted by the shell itself, so no bitmap assets are needed. */
fun wallpaperBrush(style: WallpaperStyle, dark: Boolean): Brush {
    val colors = if (dark) {
        when (style) {
            WallpaperStyle.AURORA -> listOf(Color(0xFF071128), Color(0xFF0C2340), Color(0xFF08101F))
            WallpaperStyle.NEBULA -> listOf(Color(0xFF140A29), Color(0xFF2A1050), Color(0xFF0B0618))
            WallpaperStyle.EMBER -> listOf(Color(0xFF1B0C0C), Color(0xFF3A1710), Color(0xFF120707))
            WallpaperStyle.MINT -> listOf(Color(0xFF04171A), Color(0xFF0A3034), Color(0xFF041013))
            WallpaperStyle.SLATE -> listOf(Color(0xFF0D1017), Color(0xFF1A2030), Color(0xFF0A0C12))
        }
    } else {
        when (style) {
            WallpaperStyle.AURORA -> listOf(Color(0xFFE7F0FF), Color(0xFFC9DDFF), Color(0xFFF4F8FF))
            WallpaperStyle.NEBULA -> listOf(Color(0xFFF1E9FF), Color(0xFFDCCBFF), Color(0xFFF8F4FF))
            WallpaperStyle.EMBER -> listOf(Color(0xFFFFEFE6), Color(0xFFFFD6C2), Color(0xFFFFF7F2))
            WallpaperStyle.MINT -> listOf(Color(0xFFE6FFF8), Color(0xFFC4F5E7), Color(0xFFF3FFFC))
            WallpaperStyle.SLATE -> listOf(Color(0xFFF0F2F7), Color(0xFFDCE1EC), Color(0xFFF8F9FC))
        }
    }
    return Brush.verticalGradient(colors)
}

private val LocalWallpaper = staticCompositionLocalOf<Brush> {
    Brush.verticalGradient(listOf(Color(0xFF05060A), Color(0xFF101828)))
}

val DroidWallpaper: Brush
    @Composable get() = LocalWallpaper.current

private val DroidTypography = Typography().let { base ->
    base.copy(
        displayLarge = base.displayLarge.copy(fontWeight = FontWeight.Light, letterSpacing = (-1).sp),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp)
    )
}

private fun darkScheme(palette: AccentPalette, amoled: Boolean) = darkColorScheme(
    primary = palette.primary,
    onPrimary = Color(0xFF06101F),
    primaryContainer = palette.primary.copy(alpha = 0.20f),
    onPrimaryContainer = palette.primary,
    secondary = palette.secondary,
    onSecondary = Color(0xFF06101F),
    secondaryContainer = palette.secondary.copy(alpha = 0.16f),
    onSecondaryContainer = palette.secondary,
    tertiary = palette.glow,
    background = if (amoled) Color(0xFF04050A) else Color(0xFF0A0D15),
    onBackground = Color(0xFFE9EDF7),
    surface = if (amoled) Color(0xFF080A11) else Color(0xFF10131D),
    onSurface = Color(0xFFE9EDF7),
    surfaceVariant = Color(0xFF191E2C),
    onSurfaceVariant = Color(0xFFA7B2C8),
    surfaceContainer = Color(0xFF11151F),
    outline = Color(0xFF2A3142),
    outlineVariant = Color(0xFF1D2331),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF20070A),
    errorContainer = Color(0xFF3A1216),
    onErrorContainer = Color(0xFFFFB3B3)
)

private fun lightScheme(palette: AccentPalette) = lightColorScheme(
    primary = palette.primary.copy(alpha = 0.92f),
    onPrimary = Color.White,
    primaryContainer = palette.primary.copy(alpha = 0.16f),
    onPrimaryContainer = Color(0xFF101828),
    secondary = palette.secondary,
    onSecondary = Color(0xFF101828),
    secondaryContainer = palette.secondary.copy(alpha = 0.20f),
    onSecondaryContainer = Color(0xFF101828),
    tertiary = palette.glow,
    background = Color(0xFFF7F9FD),
    onBackground = Color(0xFF10141F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF10141F),
    surfaceVariant = Color(0xFFE8EDF6),
    onSurfaceVariant = Color(0xFF4C566B),
    surfaceContainer = Color(0xFFF1F4FA),
    outline = Color(0xFFCBD4E4),
    outlineVariant = Color(0xFFDDE4EF),
    error = Color(0xFFC0392B),
    onError = Color.White,
    errorContainer = Color(0xFFFFE1DD),
    onErrorContainer = Color(0xFF5C120C)
)

@Composable
fun DroidOsTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val palette = paletteFor(settings.accent)
    val scheme = remember(dark, settings.accent, settings.amoledBlack) {
        if (dark) darkScheme(palette, settings.amoledBlack) else lightScheme(palette)
    }
    val wallpaper = remember(settings.wallpaper, dark) { wallpaperBrush(settings.wallpaper, dark) }

    CompositionLocalProvider(LocalWallpaper provides wallpaper) {
        MaterialTheme(colorScheme = scheme, typography = DroidTypography, content = content)
    }
}

/** Consistent translucent surface treatment used by every card in the shell. */
object DroidSurfaces {
    val cardAlpha: Float = 0.055f
    val borderAlpha: Float = 0.09f
}
