package dev.droidos.shell.settings

import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Accessibility
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.droidos.shell.ui.components.GlassCard
import dev.droidos.shell.ui.components.ScreenScaffold
import dev.droidos.shell.ui.components.SectionLabel
import dev.droidos.shell.ui.components.SettingRow
import java.util.Locale

/**
 * Reads the real accessibility state the OS reports (service enabled, touch exploration,
 * font scale, animation scale) and links to the official per-setting screens. DroidOS does
 * not fake accessibility toggles it cannot actually apply.
 */
@Composable
fun AccessibilityScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val manager = remember { context.getSystemService(android.view.accessibility.AccessibilityManager::class.java) }
    val fontScale = remember { context.resources.configuration.fontScale }
    val animatorScale = remember {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }.getOrDefault(1f)
    }
    val serviceEnabled = manager?.isEnabled ?: false
    val touchExploration = manager?.isTouchExplorationEnabled ?: false
    val enabledServices = remember(serviceEnabled) {
        manager?.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            ?.mapNotNull { it.resolveInfo?.serviceInfo?.name }
            ?: emptyList()
    }

    ScreenScaffold(
        title = "Accessibility",
        subtitle = "State reported by Android accessibility services",
        onBack = onBack
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            item { SectionLabel("Active services") }
            item {
                GlassCard {
                    Column {
                        InfoRow("Accessibility", if (serviceEnabled) "Enabled" else "Disabled")
                        InfoRow("Touch exploration", if (touchExploration) "On" else "Off")
                        InfoRow("Font scale", String.format(Locale.US, "%.2fx", fontScale))
                        InfoRow("Animation scale", String.format(Locale.US, "%.1fx", animatorScale))
                        if (enabledServices.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Running: ${enabledServices.joinToString { it.substringAfterLast('.') }}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item { SectionLabel("Adjust") }
            item {
                GlassCard {
                    Column {
                        SettingRow(
                            icon = Icons.Rounded.Accessibility,
                            title = "Accessibility services",
                            subtitle = "Screen readers, switch access, captions",
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        android.content.Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                    )
                                }
                            },
                            trailing = { Chevron() }
                        )
                        SettingRow(
                            icon = Icons.Rounded.FormatSize,
                            title = "Display size and text",
                            subtitle = "Font size and display scaling",
                            onClick = {
                                runCatching {
                                    context.startActivity(android.content.Intent(Settings.ACTION_DISPLAY_SETTINGS))
                                }
                            },
                            trailing = { Chevron() }
                        )
                        SettingRow(
                            icon = Icons.Rounded.TouchApp,
                            title = "Touch and hold delay",
                            subtitle = "Interaction timing",
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        android.content.Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                    )
                                }
                            },
                            trailing = { Chevron() }
                        )
                    }
                }
            }

            item { SectionLabel("Shell behaviour") }
            item {
                GlassCard {
                    Text(
                        "Every DroidOS control declares its accessibility role and label, all text scales " +
                            "with the system font size, touch targets are at least 44dp, and the shell " +
                            "respects the system reduce-motion animation scale.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
