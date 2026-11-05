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
import androidx.compose.ui.geometry.Offset
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
    AccentColor.HOT_PINK -> AccentPalette(Color(0xFFFF2D78), Color(0xFFFF8AB8), Color(0xFFFF5C9E))
    AccentColor.AZURE -> AccentPalette(Color(0xFF5B8CFF), Color(0xFF8FB4FF), Color(0xFF6FE3FF))
    AccentColor.VIOLET -> AccentPalette(Color(0xFFA87BFF), Color(0xFFC9A8FF), Color(0xFFE0A6FF))
    AccentColor.EMERALD -> AccentPalette(Color(0xFF3BD6A6), Color(0xFF77E8C4), Color(0xFF9BFFD9))
    AccentColor.AMBER -> AccentPalette(Color(0xFFFFB454), Color(0xFFFFD08A), Color(0xFFFFE0A3))
    AccentColor.ROSE -> AccentPalette(Color(0xFFFF7C9C), Color(0xFFFFAFC3), Color(0xFFFFC7D6))
    AccentColor.GRAPHITE -> AccentPalette(Color(0xFF9BA8C2), Color(0xFFC3CDE0), Color(0xFFD6E0F0))
}

/**
 * Wallpaper gradients are painted by the shell itself, so no bitmap assets are needed.
 * Dark wallpapers are all built on true black with a hot-pink bloom, which is the shell's
 * default look; the variants only move the bloom.
 */
fun wallpaperBrush(style: WallpaperStyle, dark: Boolean): Brush {
    val colors = if (dark) {
        when (style) {
            WallpaperStyle.AURORA -> listOf(Color(0xFF000000), Color(0xFF24030F), Color(0xFF070003))
            WallpaperStyle.NEBULA -> listOf(Color(0xFF050004), Color(0xFF2B0620), Color(0xFF0C0008))
            WallpaperStyle.EMBER -> listOf(Color(0xFF0A0002), Color(0xFF30070E), Color(0xFF120003))
            WallpaperStyle.MINT -> listOf(Color(0xFF000605), Color(0xFF0B2420), Color(0xFF000807))
            WallpaperStyle.SLATE -> listOf(Color(0xFF000000), Color(0xFF140B10), Color(0xFF050304))
        }
    } else {
        when (style) {
            WallpaperStyle.AURORA -> listOf(Color(0xFFFFF7FA), Color(0xFFFFDCE8), Color(0xFFFFFFFF))
            WallpaperStyle.NEBULA -> listOf(Color(0xFFFFF5FC), Color(0xFFFBD3EA), Color(0xFFFFFFFF))
            WallpaperStyle.EMBER -> listOf(Color(0xFFFFF8F4), Color(0xFFFFDED2), Color(0xFFFFFFFF))
            WallpaperStyle.MINT -> listOf(Color(0xFFF4FFFB), Color(0xFFD3F5EA), Color(0xFFFFFFFF))
            WallpaperStyle.SLATE -> listOf(Color(0xFFF7F5F6), Color(0xFFE8E1E5), Color(0xFFFCFBFC))
        }
    }
    return Brush.verticalGradient(colors)
}

private val LocalWallpaper = staticCompositionLocalOf<Brush> {
    Brush.verticalGradient(listOf(Color(0xFF000000), Color(0xFF24030F)))
}

val DroidWallpaper: Brush
    @Composable get() = LocalWallpaper.current

/** Gradients used across the shell: one accent family, no per-screen invention. */
object DroidGradients {

    /** Hot pink to light pink, for accent rules, progress bars and hero surfaces. */
    @Composable
    fun accent(): Brush = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.secondary
        )
    )

    /** Deeper diagonal for primary actions, so white labels stay legible. */
    @Composable
    fun action(): Brush = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.82f),
            MaterialTheme.colorScheme.tertiary
        ),
        start = Offset.Zero,
        end = Offset.Infinite
    )

    /** Pink bloom behind the top of a screen, over black. */
    @Composable
    fun bloom(): Brush = Brush.radialGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
            Color.Transparent
        )
    )

    /** Card fill: near-black with a faint pink wash at the top edge. */
    @Composable
    fun card(): Brush = Brush.verticalGradient(
        listOf(
            MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            MaterialTheme.colorScheme.primary.copy(alpha = 0.07f),
            MaterialTheme.colorScheme.surface.copy(alpha = 0.86f)
        )
    )
}

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
    onPrimary = Color.White,
    primaryContainer = palette.primary.copy(alpha = 0.22f),
    onPrimaryContainer = Color(0xFFFFE3EE),
    secondary = palette.secondary,
    onSecondary = Color(0xFF1A0009),
    secondaryContainer = palette.secondary.copy(alpha = 0.18f),
    onSecondaryContainer = Color(0xFFFFDCEA),
    tertiary = palette.glow,
    // True black: the shell is meant to look like it is painted on the display, not on a card.
    background = Color(0xFF000000),
    onBackground = Color.White,
    surface = if (amoled) Color(0xFF070405) else Color(0xFF0C0709),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF171013),
    onSurfaceVariant = Color(0xFFBDB2B8),
    surfaceContainer = Color(0xFF100A0D),
    outline = Color(0xFF3A2229),
    outlineVariant = Color(0xFF231519),
    error = Color(0xFFFF6B8A),
    onError = Color(0xFF21000A),
    errorContainer = Color(0xFF3D0716),
    onErrorContainer = Color(0xFFFFC2D0)
)

private fun lightScheme(palette: AccentPalette) = lightColorScheme(
    primary = palette.primary.copy(alpha = 0.96f),
    onPrimary = Color.White,
    primaryContainer = palette.primary.copy(alpha = 0.14f),
    onPrimaryContainer = Color(0xFF2A0010),
    secondary = palette.secondary,
    onSecondary = Color(0xFF2A0010),
    secondaryContainer = palette.secondary.copy(alpha = 0.22f),
    onSecondaryContainer = Color(0xFF2A0010),
    tertiary = palette.glow,
    background = Color(0xFFFFFBFD),
    onBackground = Color(0xFF1A1013),
    surface = Color.White,
    onSurface = Color(0xFF1A1013),
    surfaceVariant = Color(0xFFF6E9EF),
    onSurfaceVariant = Color(0xFF5C4A52),
    surfaceContainer = Color(0xFFFDF2F7),
    outline = Color(0xFFE3CCD7),
    outlineVariant = Color(0xFFF0DEE6),
    error = Color(0xFFC0234A),
    onError = Color.White,
    errorContainer = Color(0xFFFFE0E7),
    onErrorContainer = Color(0xFF5C0018)
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
