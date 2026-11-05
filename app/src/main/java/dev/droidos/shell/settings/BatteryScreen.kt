package dev.droidos.shell.settings

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
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.droidos.shell.core.Fmt
import dev.droidos.shell.core.ServiceLocator
import dev.droidos.shell.system.DeviceStatsRepository
import dev.droidos.shell.ui.components.GlassCard
import dev.droidos.shell.ui.components.RingStat
import dev.droidos.shell.ui.components.ScreenScaffold
import dev.droidos.shell.ui.components.SectionLabel
import dev.droidos.shell.ui.components.SettingRow
import java.util.Locale

@Composable
fun BatteryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val battery by ServiceLocator.deviceStats.batteryFlow()
        .collectAsStateWithLifecycle(initialValue = DeviceStatsRepository.Battery())

    ScreenScaffold(
        title = "Battery",
        subtitle = if (battery.charging) "Charging via ${battery.plugged}" else "On battery",
        onBack = onBack
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            item {
                GlassCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RingStat(
                            fraction = battery.percent / 100f,
                            value = "${battery.percent}%",
                            caption = if (battery.charging) "charging" else "remaining",
                            size = 150,
                            strokeWidth = 14
                        )
                    }
                }
            }

            item { SectionLabel("Health") }
            item {
                GlassCard {
                    Column {
                        InfoLine("Status", if (battery.charging) "Charging (${battery.plugged})" else "Discharging")
                        InfoLine("Health", battery.health)
                        InfoLine("Technology", battery.technology)
                        InfoLine(
                            "Temperature",
                            String.format(Locale.US, "%.1f C", battery.temperatureC)
                        )
                        InfoLine("Voltage", "${battery.voltageMv} mV")
                        if (battery.currentNowMicroAmps != 0L) {
                            InfoLine(
                                "Current now",
                                String.format(Locale.US, "%.0f mA", battery.currentNowMicroAmps / 1000f)
                            )
                        }
                        if (battery.chargeCounterMicroAh > 0) {
                            InfoLine(
                                "Charge counter",
                                String.format(Locale.US, "%.0f mAh", battery.chargeCounterMicroAh / 1000f)
                            )
                        }
                    }
                }
            }

            item { SectionLabel("Power management") }
            item {
                GlassCard {
                    Column {
                        SettingRow(
                            icon = Icons.Rounded.BatteryChargingFull,
                            title = "Battery saver",
                            subtitle = "Configure the system power saving mode",
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        android.content.Intent(android.provider.Settings.ACTION_BATTERY_SAVER_SETTINGS)
                                    )
                                }
                            },
                            trailing = { Chevron() }
                        )
                        SettingRow(
                            icon = Icons.Rounded.PowerSettingsNew,
                            title = "Battery optimisation",
                            subtitle = "Exempt apps that must run in the background",
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        android.content.Intent(
                                            android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
                                        )
                                    )
                                }
                            },
                            trailing = { Chevron() }
                        )
                    }
                }
            }

            item { SectionLabel("Battery use") }
            item {
                GlassCard {
                    Text(
                        "DroidOS itself is measured by the same system battery stats as any other app. " +
                            "The shell avoids background work: no polling services, no wake locks, and the " +
                            "privacy monitors only run while the shell is on screen.",
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
private fun InfoLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
