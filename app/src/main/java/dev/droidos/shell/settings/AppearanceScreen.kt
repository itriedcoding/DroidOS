package dev.droidos.shell.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Brightness4
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.droidos.shell.core.ServiceLocator
import dev.droidos.shell.data.model.AccentColor
import dev.droidos.shell.data.model.ThemeMode
import dev.droidos.shell.data.model.WallpaperStyle
import dev.droidos.shell.shell.ShellViewModel
import dev.droidos.shell.ui.components.GlassCard
import dev.droidos.shell.ui.components.ScreenScaffold
import dev.droidos.shell.ui.components.SectionLabel
import dev.droidos.shell.ui.components.SwitchRow
import dev.droidos.shell.ui.theme.paletteFor
import dev.droidos.shell.ui.theme.wallpaperBrush
import kotlinx.coroutines.launch

@Composable
fun AppearanceScreen(onBack: () -> Unit, viewModel: ShellViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val repository = ServiceLocator.settings

    ScreenScaffold(
        title = "Appearance",
        subtitle = "Theme, accent and wallpaper",
        onBack = onBack
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            item { SectionLabel("Accent colour") }
            item {
                GlassCard {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            AccentColor.entries.forEach { accent ->
                                val palette = paletteFor(accent)
                                val selected = settings.accent == accent
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(46.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(palette.primary.copy(alpha = if (selected) 1f else 0.25f))
                                        .then(
                                            if (selected) Modifier.border(
                                                2.dp,
                                                Color.White.copy(alpha = 0.85f),
                                                RoundedCornerShape(14.dp)
                                            ) else Modifier
                                        )
                                        .clickable {
                                            scope.launch { repository.setAccent(accent) }
                                        }
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            settings.accent.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item { SectionLabel("Mode") }
            item {
                GlassCard {
                    Column {
                        ThemeMode.entries.forEach { mode ->
                            val selected = settings.themeMode == mode
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { scope.launch { repository.setThemeMode(mode) } }
                                    .padding(vertical = 12.dp, horizontal = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (selected) MaterialTheme.colorScheme.primary
                                            else Color.Transparent
                                        )
                                        .border(
                                            2.dp,
                                            if (selected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.outline,
                                            CircleShape
                                        )
                                )
                                Spacer(Modifier.width(14.dp))
                                Text(mode.label, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                        SwitchRow(
                            icon = Icons.Rounded.Brightness4,
                            title = "Pure black background",
                            subtitle = "Uses AMOLED-friendly black surfaces in dark mode",
                            checked = settings.amoledBlack,
                            onCheckedChange = { scope.launch { repository.setAmoled(it) } }
                        )
                    }
                }
            }

            item { SectionLabel("Wallpaper") }
            item {
                GlassCard {
                    Column {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            WallpaperStyle.entries.forEach { style ->
                                val selected = settings.wallpaper == style
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(56.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(wallpaperBrush(style, dark = true))
                                            .then(
                                                if (selected) Modifier.border(
                                                    2.dp,
                                                    MaterialTheme.colorScheme.primary,
                                                    RoundedCornerShape(14.dp)
                                                ) else Modifier
                                            )
                                            .clickable { scope.launch { repository.setWallpaper(style) } }
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        style.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item { SectionLabel("Home grid") }
            item {
                GlassCard {
                    Column {
                        Text(
                            if (settings.gridColumns == 0) "Responsive (recommended)"
                            else "${settings.gridColumns} columns",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            (0..5).forEach { columns ->
                                val selected = settings.gridColumns == columns
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
                                        )
                                        .clickable { scope.launch { repository.setGridColumns(columns) } },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        if (columns == 0) "Auto" else "$columns",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (selected) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Column preference applies to the app drawer and installed-app grid.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
