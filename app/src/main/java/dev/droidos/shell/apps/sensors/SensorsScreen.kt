package dev.droidos.shell.apps.sensors

import android.hardware.Sensor
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.droidos.shell.ui.components.GlassCard
import dev.droidos.shell.ui.components.ScreenScaffold
import dev.droidos.shell.ui.components.SectionLabel

private sealed interface Reading {
    data object Waiting : Reading
    data class Values(val values: FloatArray, val count: Int) : Reading
    data class Failed(val message: String) : Reading
}

@Composable
fun SensorsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { SensorRepository(context) }
    val sensors = remember { repository.available() }
    var selected by remember { mutableStateOf(sensors.firstOrNull()) }

    ScreenScaffold(
        title = "Sensors",
        subtitle = if (sensors.isEmpty()) "Nothing reported" else "${sensors.size} reported by the system",
        onBack = onBack
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            if (sensors.isEmpty()) {
                Column(Modifier.fillMaxWidth().padding(top = 40.dp)) {
                    Text("No sensors", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "This device's SensorManager returned an empty list, so there is nothing to read. " +
                            "Most phones report accelerometer, gyroscope and light here.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }
                return@Column
            }

            selected?.let { sensor ->
                LiveReading(repository, sensor)
                Spacer(Modifier.height(14.dp))
            }

            SectionLabel("All sensors")
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(sensors, key = { it.name }) { sensor ->
                    SensorRow(
                        sensor = sensor,
                        selected = sensor.name == selected?.name,
                        onSelect = { selected = sensor }
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveReading(repository: SensorRepository, sensor: Sensor) {
    val reading by produceState<Reading>(Reading.Waiting, sensor) {
        value = Reading.Waiting
        var count = 0
        try {
            repository.stream(sensor).collect { values ->
                count += 1
                value = Reading.Values(values, count)
            }
        } catch (e: Exception) {
            value = Reading.Failed(e.message ?: e::class.java.simpleName)
        }
    }

    GlassCard {
        Column(Modifier.fillMaxWidth()) {
            Text(SensorFormat.label(sensor.type), style = MaterialTheme.typography.titleMedium)
            Text(
                sensor.name,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            when (val current = reading) {
                is Reading.Values -> {
                    Text(
                        SensorFormat.describe(current.values, sensor.type),
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${current.count} samples delivered",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                is Reading.Failed -> Text(
                    current.message,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.error
                )
                Reading.Waiting -> Text(
                    "Waiting for the first sample…",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "vendor ${sensor.vendor} · v${sensor.version} · range " +
                    "${formatNumber(sensor.maximumRange)} · resolution ${formatNumber(sensor.resolution)} · " +
                    "${formatNumber(sensor.power)} mA",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SensorRow(sensor: Sensor, selected: Boolean, onSelect: () -> Unit) {
    GlassCard(onClick = onSelect) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text(SensorFormat.label(sensor.type), fontSize = 14.sp)
                Text(
                    sensor.name,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                if (selected) "live" else "tap",
                fontSize = 11.sp,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatNumber(value: Float): String =
    if (value == 0f) "0" else String.format(java.util.Locale.US, "%.3g", value)
