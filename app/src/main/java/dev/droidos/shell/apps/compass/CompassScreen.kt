package dev.droidos.shell.apps.compass

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.droidos.shell.ui.components.GlassCard
import dev.droidos.shell.ui.components.MessageBanner
import dev.droidos.shell.ui.components.ScreenScaffold
import dev.droidos.shell.ui.components.SectionLabel
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun CompassScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val manager = remember {
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    }
    val sensor = remember { manager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) }

    var heading by remember { mutableStateOf<Float?>(null) }
    var accuracy by remember { mutableStateOf(SensorManager.SENSOR_STATUS_UNRELIABLE) }
    var failure by remember { mutableStateOf<String?>(null) }

    DisposableEffect(sensor) {
        val currentManager = manager
        if (sensor == null || currentManager == null) {
            onDispose { }
        } else {
            val rotation = FloatArray(9)
            val orientation = FloatArray(3)
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    SensorManager.getRotationMatrixFromVector(rotation, event.values)
                    SensorManager.getOrientation(rotation, orientation)
                    val degrees = Math.toDegrees(orientation[0].toDouble()).toFloat()
                    heading = CompassEngine.smooth(heading, degrees)
                }

                override fun onAccuracyChanged(changed: Sensor?, value: Int) {
                    accuracy = value
                }
            }
            val registered = try {
                currentManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
            } catch (e: SecurityException) {
                failure = "The system refused the rotation vector sensor: ${e.message}"
                false
            }
            if (!registered && failure == null) {
                failure = "The system would not register a listener for ${sensor.name}"
            }
            onDispose { runCatching { currentManager.unregisterListener(listener) } }
        }
    }

    ScreenScaffold(
        title = "Compass",
        subtitle = sensor?.name ?: "No rotation vector sensor",
        onBack = onBack
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (sensor == null) {
                Spacer(Modifier.height(40.dp))
                Text("No heading sensor", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    "This device does not report Android's rotation vector sensor, so there is no " +
                        "heading to show. Nothing is simulated in its place.",
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }

            Spacer(Modifier.height(8.dp))
            Box(Modifier.size(250.dp), contentAlignment = Alignment.Center) {
                CompassDial(heading ?: 0f)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                heading?.let { "${it.roundToInt()}°" } ?: "--",
                style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 42.sp),
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                heading?.let { CompassEngine.cardinal(it) } ?: "waiting for a sample",
                fontSize = 18.sp
            )

            Spacer(Modifier.height(16.dp))
            SectionLabel("Sensor")
            GlassCard {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    InfoRow("Sensor", sensor.name)
                    InfoRow("Accuracy", CompassEngine.accuracyLabel(accuracy))
                    InfoRow("Vendor", sensor.vendor)
                    InfoRow("Power", "${sensor.power} mA")
                }
            }

            failure?.let {
                Spacer(Modifier.height(12.dp))
                MessageBanner(it, isError = true)
            }

            Spacer(Modifier.height(12.dp))
            Text(
                "Azimuth comes from SensorManager's rotation vector, smoothed along the shortest arc " +
                    "so the needle does not spin at north. If the reading drifts, move the phone in a " +
                    "figure eight to recalibrate the magnetometer.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 12.sp)
    }
}

@Composable
private fun CompassDial(heading: Float) {
    val primary = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension / 2f - 10.dp.toPx()
        drawCircle(
            color = primary.copy(alpha = 0.20f),
            radius = radius,
            center = center,
            style = Stroke(width = 2.dp.toPx())
        )
        for (step in 0 until 12) {
            val angle = Math.toRadians(step * 30.0)
            val major = step % 3 == 0
            val innerRadius = radius - if (major) 16.dp.toPx() else 8.dp.toPx()
            drawLine(
                color = if (major) primary else muted,
                start = Offset(
                    center.x + (innerRadius * sin(angle)).toFloat(),
                    center.y - (innerRadius * cos(angle)).toFloat()
                ),
                end = Offset(
                    center.x + (radius * sin(angle)).toFloat(),
                    center.y - (radius * cos(angle)).toFloat()
                ),
                strokeWidth = (if (major) 3.dp else 1.5.dp).toPx(),
                cap = StrokeCap.Round
            )
        }
        rotate(degrees = -heading, pivot = center) {
            val tip = Offset(center.x, center.y - radius + 26.dp.toPx())
            drawLine(primary, center, tip, strokeWidth = 5.dp.toPx(), cap = StrokeCap.Round)
            drawCircle(primary, radius = 7.dp.toPx(), center = tip)
            drawCircle(muted, radius = 5.dp.toPx(), center = center)
        }
    }
}
