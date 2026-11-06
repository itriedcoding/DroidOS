package dev.droidos.shell.apps.level

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.droidos.shell.ui.components.EmptyState
import dev.droidos.shell.ui.components.GlassCard
import dev.droidos.shell.ui.components.ScreenScaffold
import dev.droidos.shell.ui.components.SectionLabel

@Composable
fun LevelScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val manager = remember { context.getSystemService(SensorManager::class.java) }
    val sensor = remember {
        manager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }

    var tilt by remember { mutableStateOf<Tilt?>(null) }
    var registered by remember { mutableStateOf(sensor != null) }
    val smoothed = remember { FloatArray(3) }
    val primed = remember { booleanArrayOf(false) }

    DisposableEffect(sensor) {
        val active = sensor
        if (active == null) {
            registered = false
            onDispose { }
        } else {
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    // Light low-pass filter: raw sensor noise makes the bubble jitter.
                    if (!primed[0]) {
                        event.values.copyInto(smoothed, 0, 0, 3)
                        primed[0] = true
                    } else {
                        for (index in 0..2) {
                            smoothed[index] += (event.values[index] - smoothed[index]) * 0.22f
                        }
                    }
                    tilt = LevelMath.tilt(smoothed)
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            val ok = manager?.registerListener(listener, active, SensorManager.SENSOR_DELAY_GAME) == true
            registered = ok
            onDispose { manager?.unregisterListener(listener) }
        }
    }

    val accent = MaterialTheme.colorScheme.primary
    val current = tilt

    ScreenScaffold(
        title = "Level",
        subtitle = sensor?.name ?: "No gravity sensor",
        onBack = onBack
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            if (sensor == null || !registered) {
                EmptyState(
                    icon = Icons.Rounded.Straighten,
                    title = if (sensor == null) "No gravity sensor" else "Sensor unavailable",
                    message = if (sensor == null) {
                        "This device reports neither TYPE_GRAVITY nor TYPE_ACCELEROMETER, so no " +
                            "angle can be measured. Nothing is simulated in its place."
                    } else {
                        "The sensor exists but the platform refused registration, which usually means " +
                            "an active sensor policy is blocking it."
                    }
                )
                return@Column
            }

            Spacer(Modifier.height(8.dp))
            GlassCard {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Canvas(
                        Modifier
                            .fillMaxWidth()
                            .height(270.dp)
                    ) {
                        val radius = size.minDimension / 2f - 16.dp.toPx()
                        val centre = Offset(size.width / 2f, size.height / 2f)
                        drawCircle(Color.White.copy(alpha = 0.05f), radius, centre)
                        drawCircle(
                            Color.White.copy(alpha = 0.16f),
                            radius,
                            centre,
                            style = Stroke(width = 1.5.dp.toPx())
                        )
                        drawCircle(
                            Color.White.copy(alpha = 0.10f),
                            radius * 0.4f,
                            centre,
                            style = Stroke(width = 1.dp.toPx())
                        )
                        drawLine(
                            Color.White.copy(alpha = 0.12f),
                            Offset(centre.x - radius, centre.y),
                            Offset(centre.x + radius, centre.y),
                            1.dp.toPx()
                        )
                        drawLine(
                            Color.White.copy(alpha = 0.12f),
                            Offset(centre.x, centre.y - radius),
                            Offset(centre.x, centre.y + radius),
                            1.dp.toPx()
                        )
                        if (current != null) {
                            val (bx, by) = LevelMath.bubbleOffset(current)
                            drawCircle(
                                accent.copy(alpha = 0.22f),
                                radius * 0.2f,
                                Offset(centre.x + bx * radius * 0.8f, centre.y - by * radius * 0.8f)
                            )
                            drawCircle(
                                accent,
                                radius * 0.14f,
                                Offset(centre.x + bx * radius * 0.8f, centre.y - by * radius * 0.8f)
                            )
                        }
                    }

                    Text(
                        current?.let { LevelMath.direction(it) } ?: "Waiting for the sensor",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (current != null && LevelMath.isLevel(current)) accent
                        else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        if (current == null) "No readings yet"
                        else "Within 1.5\u00B0 counts as level",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            SectionLabel("Axes")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AxisCard("Pitch", current?.let { LevelMath.formatDegrees(it.pitch) } ?: "--", Modifier.weight(1f))
                AxisCard("Roll", current?.let { LevelMath.formatDegrees(it.roll) } ?: "--", Modifier.weight(1f))
            }

            SectionLabel("How this is measured")
            GlassCard {
                Text(
                    "Pitch and roll come from the device's gravity vector: pitch = atan2(-x, \u221A(y\u00B2+z\u00B2)) " +
                        "and roll = atan2(y, z). TYPE_GRAVITY is used when the device has it, otherwise the " +
                        "accelerometer, which also picks up movement. Readings are low-pass filtered at 0.22 " +
                        "so the bubble settles instead of shaking.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AxisCard(label: String, value: String, modifier: Modifier = Modifier) {
    GlassCard(modifier) {
        Column(Modifier.fillMaxWidth()) {
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
