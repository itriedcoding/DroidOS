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
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.droidos.shell.core.ServiceLocator
import dev.droidos.shell.shell.controls.ControlResult
import dev.droidos.shell.ui.components.GlassCard
import dev.droidos.shell.ui.components.MessageBanner
import dev.droidos.shell.ui.components.ScreenScaffold
import dev.droidos.shell.ui.components.SectionLabel
import dev.droidos.shell.ui.components.SettingRow
import dev.droidos.shell.ui.components.SwitchRow
import kotlinx.coroutines.delay

@Composable
fun NetworkScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val controls = remember { ServiceLocator.controls }
    val stats = remember { ServiceLocator.deviceStats }
    var wifi by remember { mutableStateOf(controls.isWifiEnabled()) }
    var bluetooth by remember { mutableStateOf(controls.isBluetoothEnabled()) }
    var network by remember { mutableStateOf(stats.network()) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            wifi = controls.isWifiEnabled()
            bluetooth = controls.isBluetoothEnabled()
            network = stats.network()
            delay(2000)
        }
    }

    fun handle(result: ControlResult) {
        if (result.needsUserAction != null) {
            runCatching { context.startActivity(result.needsUserAction) }
        }
        message = when {
            result.applied -> null
            else -> result.message
        }
        wifi = controls.isWifiEnabled()
        bluetooth = controls.isBluetoothEnabled()
    }

    ScreenScaffold(
        title = "Wi-Fi & Bluetooth",
        subtitle = "Live radio state read from the system",
        onBack = onBack
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            if (message != null) {
                item {
                    MessageBanner(message.orEmpty())
                    Spacer(Modifier.height(10.dp))
                }
            }

            item { SectionLabel("Connection") }
            item {
                GlassCard {
                    Column {
                        SettingRow(
                            icon = Icons.Rounded.Wifi,
                            title = network.transport,
                            subtitle = when {
                                !network.connected -> "No internet connection"
                                network.validated -> "Connected and validated"
                                else -> "Connected, waiting for internet"
                            }
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Text(
                                if (network.metered) "Metered" else "Unmetered",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item { SectionLabel("Radios") }
            item {
                GlassCard {
                    Column {
                        SwitchRow(
                            icon = Icons.Rounded.Wifi,
                            title = "Wi-Fi",
                            subtitle = if (controls.isWifiSupported()) {
                                if (wifi) "On" else "Off"
                            } else "No Wi-Fi adapter detected",
                            checked = wifi,
                            enabled = controls.isWifiSupported(),
                            onCheckedChange = { handle(controls.setWifi(it)) }
                        )
                        SwitchRow(
                            icon = Icons.Rounded.Bluetooth,
                            title = "Bluetooth",
                            subtitle = if (controls.isBluetoothSupported()) {
                                if (bluetooth) "On" else "Off"
                            } else "No Bluetooth adapter detected",
                            checked = bluetooth,
                            enabled = controls.isBluetoothSupported(),
                            onCheckedChange = { handle(controls.setBluetooth(it)) }
                        )
                    }
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Android ${android.os.Build.VERSION.RELEASE} restricts direct radio switching to system " +
                        "apps. When the platform refuses, DroidOS opens the official system panel so the " +
                        "change always completes - it never reports a state that did not happen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item { SectionLabel("System panels") }
            item {
                GlassCard {
                    Column {
                        SettingRow(
                            icon = Icons.Rounded.OpenInNew,
                            title = "Wi-Fi settings",
                            subtitle = "Networks, saved credentials, advanced",
                            onClick = {
                                runCatching {
                                    context.startActivity(android.content.Intent(android.provider.Settings.ACTION_WIFI_SETTINGS))
                                }
                            },
                            trailing = { Chevron() }
                        )
                        SettingRow(
                            icon = Icons.Rounded.OpenInNew,
                            title = "Bluetooth settings",
                            subtitle = "Pair and manage devices",
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        android.content.Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
                                    )
                                }
                            },
                            trailing = { Chevron() }
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
