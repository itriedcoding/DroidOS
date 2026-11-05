package dev.droidos.shell.apps.sensors

import android.hardware.Sensor
import java.util.Locale

/**
 * Names, units and number formatting for sensor readings. Pure apart from the `android.hardware`
 * type constants, which are compile-time ints, so it is unit-tested on the JVM.
 */
object SensorFormat {

    fun label(type: Int): String = when (type) {
        Sensor.TYPE_ACCELEROMETER -> "Accelerometer"
        Sensor.TYPE_GYROSCOPE -> "Gyroscope"
        Sensor.TYPE_MAGNETIC_FIELD -> "Magnetometer"
        Sensor.TYPE_LIGHT -> "Light"
        Sensor.TYPE_PROXIMITY -> "Proximity"
        Sensor.TYPE_PRESSURE -> "Barometer"
        Sensor.TYPE_RELATIVE_HUMIDITY -> "Humidity"
        Sensor.TYPE_AMBIENT_TEMPERATURE -> "Temperature"
        Sensor.TYPE_STEP_COUNTER -> "Step counter"
        Sensor.TYPE_STEP_DETECTOR -> "Step detector"
        Sensor.TYPE_ROTATION_VECTOR -> "Rotation vector"
        Sensor.TYPE_GRAVITY -> "Gravity"
        Sensor.TYPE_LINEAR_ACCELERATION -> "Linear acceleration"
        Sensor.TYPE_GAME_ROTATION_VECTOR -> "Game rotation vector"
        Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED -> "Magnetometer (raw)"
        Sensor.TYPE_GYROSCOPE_UNCALIBRATED -> "Gyroscope (raw)"
        Sensor.TYPE_SIGNIFICANT_MOTION -> "Significant motion"
        Sensor.TYPE_HEART_RATE -> "Heart rate"
        else -> "Sensor $type"
    }

    fun unit(type: Int): String = when (type) {
        Sensor.TYPE_ACCELEROMETER,
        Sensor.TYPE_GRAVITY,
        Sensor.TYPE_LINEAR_ACCELERATION -> "m/s²"
        Sensor.TYPE_GYROSCOPE,
        Sensor.TYPE_GYROSCOPE_UNCALIBRATED -> "rad/s"
        Sensor.TYPE_MAGNETIC_FIELD,
        Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED -> "µT"
        Sensor.TYPE_LIGHT -> "lx"
        Sensor.TYPE_PROXIMITY -> "cm"
        Sensor.TYPE_PRESSURE -> "hPa"
        Sensor.TYPE_RELATIVE_HUMIDITY -> "%"
        Sensor.TYPE_AMBIENT_TEMPERATURE -> "°C"
        Sensor.TYPE_STEP_COUNTER,
        Sensor.TYPE_STEP_DETECTOR -> "steps"
        Sensor.TYPE_HEART_RATE -> "bpm"
        else -> ""
    }

    /** Steps are integers; everything else keeps three decimals, which is the real resolution we get. */
    fun format(values: FloatArray, type: Int): List<String> {
        val steps = type == Sensor.TYPE_STEP_COUNTER || type == Sensor.TYPE_STEP_DETECTOR
        return values.map { value ->
            if (steps) String.format(Locale.US, "%.0f", value)
            else String.format(Locale.US, "%.3f", value)
        }
    }

    /** A sensor's values in a stable, human readable order. */
    fun describe(values: FloatArray, type: Int): String {
        val parts = format(values, type)
        val suffix = unit(type)
        return if (suffix.isEmpty()) parts.joinToString("  ")
        else parts.joinToString("  ") { "$it $suffix" }
    }
}
