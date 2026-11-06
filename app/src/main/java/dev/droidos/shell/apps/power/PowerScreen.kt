package dev.droidos.shell.apps.power

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.ElectricBolt
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import dev.droidos.shell.ui.components.GlassCard
import dev.droidos.shell.ui.components.RingStat
import dev.droidos.shell.ui.components.RowDivider
import dev.droidos.shell.ui.components.ScreenScaffold
import dev.droidos.shell.ui.components.SectionLabel
import dev.droidos.shell.ui.components.SettingRow

private data class BatterySnapshot(
    val level: Int = 0,
    val status: Int = PowerEngine.STATUS_UNKNOWN,
    val health: Int = PowerEngine.HEALTH_UNKNOWN,
    val plugged: Int = 0,
    val temperatureTenths: Int = 0,
    val voltageMv: Int = 0,
    val chargeUah: Long = 0L,
    val currentUa: Long = 0L
)

@Composable
fun PowerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var snapshot by remember { mutableStateOf(readSnapshot(context)) }

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                if (intent != null) snapshot = merge(snapshot, intent, receiverContext ?: context)
            }
        }
        val sticky = ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        if (sticky != null) snapshot = merge(snapshot, sticky, context)
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    val hours = PowerEngine.hoursRemaining(snapshot.chargeUah, snapshot.currentUa)
    val charging = snapshot.status == PowerEngine.STATUS_CHARGING ||
        snapshot.status == PowerEngine.STATUS_FULL

    ScreenScaffold(
        title = "Power",
        subtitle = "Live battery telemetry",
        onBack = onBack
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                RingStat(
                    fraction = snapshot.level / 100f,
                    value = "${snapshot.level}%",
                    caption = PowerEngine.statusLabel(snapshot.status)
                )
            }

            SectionLabel("Battery")
            GlassCard {
                Column(Modifier.fillMaxWidth()) {
                    SettingRow(
                        icon = Icons.Rounded.ElectricBolt,
                        title = "Power source",
                        subtitle = PowerEngine.plugLabel(snapshot.plugged)
                    )
                    RowDivider()
                    SettingRow(
                        icon = Icons.Rounded.Thermostat,
                        title = "Temperature",
                        subtitle = "Battery cell reading",
                        trailing = {
                            Text(
                                PowerEngine.formatTemperature(snapshot.temperatureTenths),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    )
                    RowDivider()
                    SettingRow(
                        icon = Icons.Rounded.HealthAndSafety,
                        title = "Health",
                        subtitle = PowerEngine.healthLabel(snapshot.health)
                    )
                    RowDivider()
                    SettingRow(
                        icon = Icons.Rounded.Speed,
                        title = "Voltage",
                        subtitle = "Terminal voltage",
                        trailing = {
                            Text(
                                PowerEngine.formatVoltage(snapshot.voltageMv),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    )
                    RowDivider()
                    SettingRow(
                        icon = Icons.Rounded.Speed,
                        title = "Current",
                        subtitle = "Positive while charging",
                        trailing = {
                            Text(
                                PowerEngine.formatCurrent(snapshot.currentUa),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    )
                    RowDivider()
                    SettingRow(
                        icon = Icons.Rounded.AccessTime,
                        title = "Remaining",
                        subtitle = if (charging) {
                            "Estimates stop while charging"
                        } else {
                            "From charge counter and current draw"
                        },
                        trailing = {
                            Text(
                                if (charging) "--" else PowerEngine.formatHours(hours),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    )
                }
            }

            SectionLabel("Capacity")
            GlassCard {
                Column(Modifier.fillMaxWidth()) {
                    SettingRow(
                        icon = Icons.Rounded.BatteryChargingFull,
                        title = "Charge counter",
                        subtitle = "Reported charge in the cell",
                        trailing = {
                            Text(
                                PowerEngine.formatCharge(snapshot.chargeUah),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    )
                    RowDivider()
                    SettingRow(
                        icon = Icons.Rounded.Speed,
                        title = "Instantaneous power",
                        subtitle = "Voltage x current",
                        trailing = {
                            Text(
                                PowerEngine.formatWatts(snapshot.voltageMv, snapshot.currentUa),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            GlassCard {
                Text(
                    "Every figure comes from BatteryManager and the ACTION_BATTERY_CHANGED broadcast. " +
                        "Devices that do not expose a charge counter or current reading show -- rather " +
                        "than an invented estimate.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun merge(previous: BatterySnapshot, intent: Intent, context: Context): BatterySnapshot {
    val updated = previous.copy(
        level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, previous.level),
        status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, previous.status),
        health = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, previous.health),
        plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, previous.plugged),
        temperatureTenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, previous.temperatureTenths),
        voltageMv = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, previous.voltageMv)
    )
    return updated.copy(
        chargeUah = readCharge(context) ?: updated.chargeUah,
        currentUa = readCurrent(context) ?: updated.currentUa
    )
}

private fun readSnapshot(context: Context): BatterySnapshot {
    val manager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
    val level = manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
    return BatterySnapshot(
        level = level.coerceIn(0, 100),
        chargeUah = readCharge(context) ?: 0L,
        currentUa = readCurrent(context) ?: 0L
    )
}

private fun readCharge(context: Context): Long? = runCatching {
    val manager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        ?: return null
    manager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER).takeIf { it > 0L }
}.getOrNull()

private fun readCurrent(context: Context): Long? = runCatching {
    val manager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        ?: return null
    manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        .toLong()
        .takeIf { it != 0L && it != Int.MIN_VALUE.toLong() }
}.getOrNull()
