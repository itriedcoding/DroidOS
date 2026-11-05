package dev.droidos.shell.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.droidos.shell.core.Fmt
import dev.droidos.shell.ui.components.GlassCard
import dev.droidos.shell.ui.components.ScreenScaffold
import dev.droidos.shell.ui.components.SectionLabel
import dev.droidos.shell.ui.components.SettingRow
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.TimeZone

private val DATE_LINE = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")

/**
 * Reports the real clock configuration from the platform. Android reserves the auto-time and
 * time-zone switches for the system, so those are read here and changed in the official panel;
 * every DroidOS clock re-reads the format on each draw, so a change applies immediately.
 */
@Composable
fun DateTimeSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val resolver = context.contentResolver
    val is24Hour = remember { android.text.format.DateFormat.is24HourFormat(context) }
    val autoTime = remember {
        runCatching { Settings.Global.getInt(resolver, Settings.Global.AUTO_TIME, 0) == 1 }.getOrDefault(false)
    }
    val autoZone = remember {
        runCatching { Settings.Global.getInt(resolver, Settings.Global.AUTO_TIME_ZONE, 0) == 1 }.getOrDefault(false)
    }
    val zone = remember { TimeZone.getDefault() }
    var now by remember { mutableStateOf(LocalDateTime.now()) }

    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(1_000)
        }
    }

    ScreenScaffold(
        title = "Date & time",
        subtitle = "Clock format, time source and time zone",
        onBack = onBack
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            item {
                GlassCard {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            Fmt.clockTime(now.hour, now.minute, is24Hour),
                            fontSize = 46.sp,
                            fontWeight = FontWeight.Light,
                            letterSpacing = (-1.5).sp
                        )
                        Text(
                            DATE_LINE.format(now),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item { SectionLabel("Current configuration") }
            item {
                GlassCard {
                    Column {
                        StatusRow("Clock format", if (is24Hour) "24-hour" else "12-hour")
                        StatusRow("Automatic time", if (autoTime) "On, provided by the network" else "Off, set manually")
                        StatusRow("Automatic time zone", if (autoZone) "On" else "Off")
                        StatusRow("Time zone", "${zone.id}  (${zone.getDisplayName(false, TimeZone.SHORT)})")
                    }
                }
            }

            item { SectionLabel("Controls") }
            item {
                GlassCard {
                    SettingRow(
                        icon = Icons.Rounded.Schedule,
                        title = "Open Android date & time",
                        subtitle = "Android reserves these switches for the system panel; DroidOS follows whatever you set there",
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_DATE_SETTINGS)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        },
                        trailing = { Chevron() }
                    )
                }
            }

            item {
                Text(
                    "The lock screen, home clock and Clock app all read this format when they draw, so a change " +
                        "in the panel applies without restarting the shell.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp)
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
