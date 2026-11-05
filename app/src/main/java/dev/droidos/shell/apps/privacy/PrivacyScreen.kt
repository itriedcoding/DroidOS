package dev.droidos.shell.apps.privacy

import android.Manifest
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.droidos.shell.core.ServiceLocator
import dev.droidos.shell.ui.components.GlassCard
import dev.droidos.shell.ui.components.ScreenScaffold
import dev.droidos.shell.ui.components.SectionLabel
import dev.droidos.shell.ui.components.SwitchRow

/**
 * Live privacy indicators driven by OS callbacks, plus real microphone mute and a list of
 * which packages hold camera/microphone access.
 */
@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    val monitor = remember { ServiceLocator.privacy }
    val state by monitor.state.collectAsStateWithLifecycle()
    var cameraHolders by remember { mutableStateOf<List<String>>(emptyList()) }
    var micHolders by remember { mutableStateOf<List<String>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        monitor.start()
        onDispose { monitor.stop() }
    }

    LaunchedEffect(Unit) {
        cameraHolders = ServiceLocator.permissionAudit.permissionHolders(Manifest.permission.CAMERA)
        micHolders = ServiceLocator.permissionAudit.permissionHolders(Manifest.permission.RECORD_AUDIO)
    }

    ScreenScaffold(
        title = "Privacy",
        subtitle = "Real sensor signals from the Android framework",
        onBack = onBack
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            if (message != null) {
                item {
                    dev.droidos.shell.ui.components.MessageBanner(message.orEmpty())
                    Spacer(Modifier.height(10.dp))
                }
            }

            item { SectionLabel("Right now") }
            item {
                GlassCard {
                    Column {
                        IndicatorRow(
                            active = state.cameraInUse,
                            activeLabel = "Camera in use",
                            idleLabel = "Camera idle",
                            icon = { tint ->
                                Icon(Icons.Rounded.Videocam, contentDescription = null, tint = tint)
                            }
                        )
                        Spacer(Modifier.height(12.dp))
                        IndicatorRow(
                            active = state.microphoneInUse,
                            activeLabel = "Microphone in use",
                            idleLabel = "Microphone idle",
                            icon = { tint ->
                                Icon(Icons.Rounded.Mic, contentDescription = null, tint = tint)
                            }
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "These indicators come from CameraManager availability callbacks and " +
                                "AudioManager recording callbacks, so they report activity from any app " +
                                "on the device - not just DroidOS.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        // Only a device image with the platform service can attribute a capture to
                        // a package; elsewhere this line is simply absent.
                        state.attributedPackage?.let { pkg ->
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Platform attribution: ${ServiceLocator.apps.labelFor(pkg)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            item { SectionLabel("Microphone") }
            item {
                GlassCard {
                    SwitchRow(
                        icon = if (state.microphoneMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                        title = "Mute microphone",
                        subtitle = if (state.microphoneMuted) {
                            "Microphone is muted system-wide"
                        } else {
                            "Silence recording at the audio service"
                        },
                        checked = state.microphoneMuted,
                        onCheckedChange = { muted ->
                            val applied = monitor.setMicrophoneMuted(muted)
                            if (!applied) {
                                message = "The system rejected the microphone mute request"
                            }
                        }
                    )
                }
            }

            item { SectionLabel("Camera access") }
            item {
                GlassCard {
                    Text(
                        if (cameraHolders.isEmpty()) {
                            "No user-installed app holds the camera permission."
                        } else {
                            "${cameraHolders.size} apps hold camera permission:"
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    cameraHolders.take(10).forEach { pkg ->
                        Text(
                            "- ${ServiceLocator.apps.labelFor(pkg)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item { SectionLabel("Microphone access") }
            item {
                GlassCard {
                    Text(
                        if (micHolders.isEmpty()) {
                            "No user-installed app holds the microphone permission."
                        } else {
                            "${micHolders.size} apps hold microphone permission:"
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    micHolders.take(10).forEach { pkg ->
                        Text(
                            "- ${ServiceLocator.apps.labelFor(pkg)}",
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

@Composable
private fun IndicatorRow(
    active: Boolean,
    activeLabel: String,
    idleLabel: String,
    icon: @Composable (androidx.compose.ui.graphics.Color) -> Unit
) {
    val tint = if (active) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) { icon(tint) }
        Text(
            if (active) activeLabel else idleLabel,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Box(
            Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(if (active) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline)
        )
        Spacer(Modifier.width(2.dp))
    }
}
