package dev.droidos.shell.settings

import android.content.Intent
import android.media.AudioManager
import android.media.RingtoneManager
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.droidos.shell.core.ServiceLocator
import dev.droidos.shell.shell.controls.AudioControls
import dev.droidos.shell.shell.controls.ControlResult
import dev.droidos.shell.shell.controls.VolumeScale
import dev.droidos.shell.ui.components.GlassCard
import dev.droidos.shell.ui.components.MessageBanner
import dev.droidos.shell.ui.components.ScreenScaffold
import dev.droidos.shell.ui.components.SectionLabel
import dev.droidos.shell.ui.components.SettingRow
import dev.droidos.shell.ui.components.SwitchRow

/**
 * Sound settings built on AudioManager: each slider moves the real hardware mixer for that
 * stream. Do Not Disturb needs the platform's policy access and is routed through the official
 * grant dialog when it is missing.
 */
@Composable
fun SoundSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val audio = remember { AudioControls(context) }
    val controls = remember { ServiceLocator.controls }

    var media by remember { mutableFloatStateOf(volumeFraction(audio, AudioManager.STREAM_MUSIC)) }
    var ring by remember { mutableFloatStateOf(volumeFraction(audio, AudioManager.STREAM_RING)) }
    var alarm by remember { mutableFloatStateOf(volumeFraction(audio, AudioManager.STREAM_ALARM)) }
    var dnd by remember { mutableStateOf(controls.isDndEnabled()) }
    var message by remember { mutableStateOf<String?>(null) }

    fun report(result: ControlResult) {
        result.needsUserAction?.let { action -> runCatching { context.startActivity(action) } }
        if (!result.applied) message = result.message
    }

    fun openPicker(type: Int) {
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, type)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val opened = runCatching { context.startActivity(intent) }
        if (opened.isFailure) message = "This build has no ringtone picker available"
    }

    ScreenScaffold(
        title = "Sound & vibration",
        subtitle = "Streams, interruptions and tones",
        onBack = onBack
    ) {
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

            item { SectionLabel("Volumes") }
            item {
                GlassCard {
                    Column {
                        VolumeSlider(
                            label = "Media",
                            stream = AudioManager.STREAM_MUSIC,
                            fraction = media,
                            audio = audio,
                            onChange = { media = it },
                            onReport = { report(it) }
                        )
                        VolumeSlider(
                            label = "Ring",
                            stream = AudioManager.STREAM_RING,
                            fraction = ring,
                            audio = audio,
                            onChange = { ring = it },
                            onReport = { report(it) }
                        )
                        VolumeSlider(
                            label = "Alarm",
                            stream = AudioManager.STREAM_ALARM,
                            fraction = alarm,
                            audio = audio,
                            onChange = { alarm = it },
                            onReport = { report(it) }
                        )
                    }
                }
            }

            item { SectionLabel("Interruptions") }
            item {
                GlassCard {
                    SwitchRow(
                        icon = Icons.Rounded.DoNotDisturbOn,
                        title = "Do Not Disturb",
                        subtitle = if (controls.hasDndAccess()) {
                            "Silences everything except alarms and priority contacts"
                        } else {
                            "Needs Do Not Disturb access - turn it on to grant it"
                        },
                        checked = dnd,
                        onCheckedChange = { enabled ->
                            report(controls.setDnd(enabled))
                            dnd = controls.isDndEnabled()
                        }
                    )
                }
            }

            item { SectionLabel("Tones") }
            item {
                GlassCard {
                    Column {
                        SettingRow(
                            icon = Icons.Rounded.MusicNote,
                            title = "Ringtone",
                            subtitle = "Opens the system picker for the incoming call tone",
                            onClick = { openPicker(RingtoneManager.TYPE_RINGTONE) },
                            trailing = { Chevron() }
                        )
                        SettingRow(
                            icon = Icons.Rounded.Notifications,
                            title = "Notification sound",
                            subtitle = "Opens the system picker for notification tones",
                            onClick = { openPicker(RingtoneManager.TYPE_NOTIFICATION) },
                            trailing = { Chevron() }
                        )
                        SettingRow(
                            icon = Icons.Rounded.Alarm,
                            title = "Alarm sound",
                            subtitle = "The tone DroidOS Clock rings when an alarm fires",
                            onClick = { openPicker(RingtoneManager.TYPE_ALARM) },
                            trailing = { Chevron() }
                        )
                    }
                }
            }

            item { SectionLabel("Firmware panel") }
            item {
                GlassCard {
                    SettingRow(
                        icon = Icons.Rounded.Settings,
                        title = "Open Android sound settings",
                        subtitle = "Vibration patterns and per-app audio the platform keeps to itself",
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_SOUND_SETTINGS)
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

private fun volumeFraction(audio: AudioControls, stream: Int): Float =
    VolumeScale.percent(audio.currentVolume(stream), audio.maxVolume(stream))

@Composable
private fun VolumeSlider(
    label: String,
    stream: Int,
    fraction: Float,
    audio: AudioControls,
    onChange: (Float) -> Unit,
    onReport: (ControlResult) -> Unit
) {
    var muted by remember(stream) { mutableStateOf(audio.isMuted(stream)) }
    val max = audio.maxVolume(stream)

    Column(Modifier.padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.width(64.dp)
            )
            Text(
                if (max <= 0) "Not available" else VolumeScale.label(VolumeScale.index(fraction, max), max),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (muted) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp,
                contentDescription = if (muted) "Unmute $label" else "Mute $label",
                tint = if (muted) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(20.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .clickable {
                        onReport(audio.setMuted(stream, !muted))
                        muted = audio.isMuted(stream)
                    }
            )
        }
        Slider(
            value = fraction,
            onValueChange = onChange,
            onValueChangeFinished = { onReport(audio.setVolume(stream, fraction)) },
            enabled = max > 0
        )
    }
}
