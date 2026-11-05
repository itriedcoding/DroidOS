package dev.droidos.shell.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Accessibility
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.DisplaySettings
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.droidos.shell.shell.Screen
import dev.droidos.shell.shell.ShellViewModel
import dev.droidos.shell.ui.components.GlassCard
import dev.droidos.shell.ui.components.ScreenScaffold
import dev.droidos.shell.ui.components.SectionLabel
import dev.droidos.shell.ui.components.SettingRow

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenScreen: (Screen) -> Unit,
    viewModel: ShellViewModel
) {
    ScreenScaffold(
        title = "Settings",
        subtitle = "Shell and device settings",
        onBack = onBack
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            item { SectionLabel("Personalisation") }
            item {
                GlassCard {
                    Column {
                        SettingRow(
                            icon = Icons.Rounded.Palette,
                            title = "Appearance",
                            subtitle = "Accent, theme, wallpaper, grid",
                            onClick = { onOpenScreen(Screen.APPEARANCE) },
                            trailing = { Chevron() }
                        )
                        SettingRow(
                            icon = Icons.Rounded.Bluetooth,
                            title = "Wi-Fi & Bluetooth",
                            subtitle = "Connection state and controls",
                            onClick = { onOpenScreen(Screen.NETWORK) },
                            trailing = { Chevron() }
                        )
                    }
                }
            }

            item { SectionLabel("Device") }
            item {
                GlassCard {
                    Column {
                        SettingRow(
                            icon = Icons.Rounded.BatteryFull,
                            title = "Battery",
                            subtitle = "Charge, health, temperature",
                            onClick = { onOpenScreen(Screen.BATTERY) },
                            trailing = { Chevron() }
                        )
                        SettingRow(
                            icon = Icons.Rounded.Storage,
                            title = "Storage",
                            subtitle = "Internal capacity and usage",
                            onClick = { onOpenScreen(Screen.STORAGE) },
                            trailing = { Chevron() }
                        )
                        SettingRow(
                            icon = Icons.Rounded.DisplaySettings,
                            title = "Display",
                            subtitle = "Brightness, screen timeout, rotation, theme",
                            onClick = { onOpenScreen(Screen.DISPLAY) },
                            trailing = { Chevron() }
                        )
                        SettingRow(
                            icon = Icons.Rounded.VolumeUp,
                            title = "Sound & vibration",
                            subtitle = "Stream volumes, tones, Do Not Disturb",
                            onClick = { onOpenScreen(Screen.SOUND) },
                            trailing = { Chevron() }
                        )
                        SettingRow(
                            icon = Icons.Rounded.LocationOn,
                            title = "Location",
                            subtitle = "Access state and apps that can request it",
                            onClick = { onOpenScreen(Screen.LOCATION) },
                            trailing = { Chevron() }
                        )
                        SettingRow(
                            icon = Icons.Rounded.Schedule,
                            title = "Date & time",
                            subtitle = "Clock format, time source, time zone",
                            onClick = { onOpenScreen(Screen.DATETIME) },
                            trailing = { Chevron() }
                        )
                        SettingRow(
                            icon = Icons.Rounded.Accessibility,
                            title = "Accessibility",
                            subtitle = "Screen readers, display and touch adjustments",
                            onClick = { onOpenScreen(Screen.ACCESSIBILITY) },
                            trailing = { Chevron() }
                        )
                    }
                }
            }

            item { SectionLabel("Apps") }
            item {
                GlassCard {
                    Column {
                        SettingRow(
                            icon = Icons.Rounded.Android,
                            title = "Installed apps",
                            subtitle = "Inspect, launch, uninstall, protect",
                            onClick = { onOpenScreen(Screen.APPS) },
                            trailing = { Chevron() }
                        )
                        SettingRow(
                            icon = Icons.Rounded.VerifiedUser,
                            title = "Permission audit",
                            subtitle = "Which apps hold sensitive access",
                            onClick = { onOpenScreen(Screen.PERMISSIONS) },
                            trailing = { Chevron() }
                        )
                    }
                }
            }

            item { SectionLabel("Security & privacy") }
            item {
                GlassCard {
                    Column {
                        SettingRow(
                            icon = Icons.Rounded.Security,
                            title = "Security centre",
                            subtitle = "Lockscreen, app lock, integrity",
                            onClick = { onOpenScreen(Screen.SECURITY) },
                            trailing = { Chevron() }
                        )
                        SettingRow(
                            icon = Icons.Rounded.Visibility,
                            title = "Privacy",
                            subtitle = "Live camera and microphone indicators",
                            onClick = { onOpenScreen(Screen.PRIVACY) },
                            trailing = { Chevron() }
                        )
                        SettingRow(
                            icon = Icons.Rounded.BugReport,
                            title = "Security scan",
                            subtitle = "Heuristic risk assessment of installed apps",
                            onClick = { onOpenScreen(Screen.SCANNER) },
                            trailing = { Chevron() }
                        )
                    }
                }
            }

            item { SectionLabel("System") }
            item {
                GlassCard {
                    Column {
                        SettingRow(
                            icon = Icons.Rounded.SystemUpdate,
                            title = "System update",
                            subtitle = "Version, security patch, signed updates",
                            onClick = { onOpenScreen(Screen.UPDATES) },
                            trailing = { Chevron() }
                        )
                        SettingRow(
                            icon = Icons.Rounded.Settings,
                            title = "Diagnostics",
                            subtitle = "Logs, crash reports, error history",
                            onClick = { onOpenScreen(Screen.DIAGNOSTICS) },
                            trailing = { Chevron() }
                        )
                        SettingRow(
                            icon = Icons.Rounded.Info,
                            title = "About DroidOS",
                            subtitle = "Build, device and legal",
                            onClick = { onOpenScreen(Screen.ABOUT) },
                            trailing = { Chevron() }
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
internal fun Chevron() {
    Icon(
        Icons.Rounded.ChevronRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
