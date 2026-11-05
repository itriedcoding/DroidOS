package dev.droidos.shell.apps.compass

import android.hardware.SensorManager

/**
 * Display maths for the compass: normalising, 16-point cardinal names and circular smoothing.
 * The rotation-vector to azimuth conversion itself comes from `SensorManager`, which owns the
 * device's coordinate conventions, so there is no second opinion to get wrong here.
 */
object CompassEngine {

    private val POINTS = listOf(
        "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
        "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"
    )

    fun normalize(degrees: Float): Float {
        val value = degrees % 360f
        return if (value < 0f) value + 360f else value
    }

    fun cardinal(degrees: Float): String {
        val index = (((normalize(degrees) + 11.25f) / 22.5f).toInt()) % POINTS.size
        return POINTS[index]
    }

    /** Shortest signed turn from [from] to [to], in (-180, 180]. */
    fun shortestDelta(from: Float, to: Float): Float {
        var delta = (to - from) % 360f
        if (delta > 180f) delta -= 360f
        if (delta < -180f) delta += 360f
        return delta
    }

    /**
     * Smooths across the 359 -> 0 wrap. A plain average would swing the needle 180 degrees there,
     * so the step is taken along the shortest arc instead.
     */
    fun smooth(previous: Float?, current: Float, factor: Float = 0.2f): Float {
        if (previous == null) return normalize(current)
        val step = shortestDelta(previous, current) * factor.coerceIn(0f, 1f)
        return normalize(previous + step)
    }

    fun accuracyLabel(accuracy: Int): String = when (accuracy) {
        SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> "High"
        SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> "Medium"
        SensorManager.SENSOR_STATUS_ACCURACY_LOW -> "Low"
        else -> "Unreliable"
    }
}
