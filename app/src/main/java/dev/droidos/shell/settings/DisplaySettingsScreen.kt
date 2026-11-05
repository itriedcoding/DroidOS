package dev.droidos.shell.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Brightness4
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.droidos.shell.core.ServiceLocator
import dev.droidos.shell.data.model.ThemeMode
import dev.droidos.shell.shell.ShellViewModel
import dev.droidos.shell.shell.controls.ControlResult
import dev.droidos.shell.shell.controls.DisplayControls
import dev.droidos.shell.ui.components.GlassCard
import dev.droidos.shell.ui.components.MessageBanner
import dev.droidos.shell.ui.components.ScreenScaffold
import dev.droidos.shell.ui.components.SectionLabel
import dev.droidos.shell.ui.components.SettingRow
import dev.droidos.shell.ui.components.SwitchRow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val TIMEOUT_CHOICES = listOf(
    "15s" to 15_000L,
    "30s" to 30_000L,
    "1m" to 60_000L,
    "5m" to 300_000L,
    "10m" to 600_000L
)

/**
 * The display settings Android usually buries in its own app, implemented here with the real
 * Settings.System writes. Anything the platform keeps for itself (colour mode, refresh rate) is
 * labelled as such and opens the official panel instead of pretending to change it.
 */
@Composable
fun DisplaySettingsScreen(onBack: () -> Unit, viewModel: ShellViewModel) {
    val context = LocalContext.current
    val display = remember { DisplayControls(context) }
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val repository = ServiceLocator.settings

    var brightness by remember { mutableFloatStateOf(display.brightness() / 255f) }
    var timeout by remember { mutableStateOf(display.screenTimeoutMillis()) }
    var rotationLocked by remember { mutableStateOf(!display.isAutoRotateEnabled()) }
    var canWrite by remember { mutableStateOf(display.canWrite()) }
    var message by remember { mutableStateOf<String?>(null) }

    fun report(result: ControlResult) {
        result.needsUserAction?.let { action -> runCatching { context.startActivity(action) } }
        if (!result.applied) message = result.message
        canWrite = display.canWrite()
    }

    ScreenScaffold(title = "Display", subtitle = "Brightness, timeout, rotation and theme", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            if (message != null) {
                item {
                    MessageBanner(message.orEmpty())
                    Spacer(Modifier.height(8.dp))
                }
            }

            if (!canWrite) {
                item {
                    GlassCard {
                        Column {
                            Text("Modify settings access is needed", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Brightness, timeout and rotation live in system settings. Android only lets an app " +
                                    "write them after you grant the special access below.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "Grant modify-system-settings",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable {
                                    runCatching { context.startActivity(display.writeAccessIntent()) }
                                }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            item { SectionLabel("Brightness") }
            item {
                GlassCard {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${(brightness * 100).roundToInt()}%",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.width(64.dp)
                            )
                            Slider(
                                value = brightness,
                                onValueChange = { brightness = it },
                                onValueChangeFinished = {
                                    report(display.setBrightness((brightness * 255).roundToInt()))
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Text(
                            "Writes Settings.System.SCREEN_BRIGHTNESS and takes the display out of adaptive mode.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item { SectionLabel("Screen timeout") }
            item {
                GlassCard {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TIMEOUT_CHOICES.forEach { (label, millis) ->
                            val active = timeout == millis
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                                        else MaterialTheme.colorScheme.surface.copy(alpha = 0.62f)
                                    )
                                    .clickable {
                                        report(display.setScreenTimeoutMillis(millis))
                                        timeout = display.screenTimeoutMillis()
                                    }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (active) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            item { SectionLabel("Rotation") }
            item {
                GlassCard {
                    SwitchRow(
                        icon = Icons.Rounded.ScreenRotation,
                        title = "Lock rotation",
                        subtitle = "Pins the current orientation until you unlock it again",
                        checked = rotationLocked,
                        onCheckedChange = { locked ->
                            report(display.setRotationLocked(locked))
                            rotationLocked = !display.isAutoRotateEnabled()
                        }
                    )
                }
            }

            item { SectionLabel("Theme") }
            item {
                GlassCard {
                    Column {
                        ThemeMode.entries.forEach { mode ->
                            val selected = settings.themeMode == mode
                            Row(
                                modifier = Modifier
                                    .padding(vertical = 10.dp)
                                    .clickable { scope.launch { repository.setThemeMode(mode) } },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    mode.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                if (selected) {
                                    Text(
                                        "Selected",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                        SwitchRow(
                            icon = Icons.Rounded.Brightness4,
                            title = "Pure black background",
                            subtitle = "AMOLED-friendly surfaces in dark mode",
                            checked = settings.amoledBlack,
                            onCheckedChange = { scope.launch { repository.setAmoled(it) } }
                        )
                    }
                }
            }

            item { SectionLabel("Firmware panel") }
            item {
                GlassCard {
                    SettingRow(
                        icon = Icons.Rounded.Settings,
                        title = "Open Android display settings",
                        subtitle = "Colour mode, refresh rate and panel options the platform keeps to itself",
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_DISPLAY_SETTINGS)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        },
                        trailing = { Chevron() }
                    )
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
