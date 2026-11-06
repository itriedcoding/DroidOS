package dev.droidos.shell.apps.level

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.sqrt

/** Pitch and roll in degrees, derived from a gravity vector in m/s^2. */
data class Tilt(val pitch: Float, val roll: Float) {
    /** Hardest of the two axes is what the user is judging the surface by. */
    val worst: Float get() = max(abs(pitch), abs(roll))
}

object LevelMath {

    /** Below this the phone is in free fall and there is no meaningful angle. */
    private const val MIN_GRAVITY = 0.5f

    /**
     * Pitch is the tilt toward/away from the user, roll is left/right. Both come from the
     * gravity vector; returns null while the device is effectively weightless.
     */
    fun tilt(gravity: FloatArray): Tilt? {
        if (gravity.size < 3) return null
        val x = gravity[0]
        val y = gravity[1]
        val z = gravity[2]
        val magnitude = sqrt(x * x + y * y + z * z)
        if (magnitude < MIN_GRAVITY) return null
        val pitch = Math.toDegrees(atan2(-x.toDouble(), sqrt((y * y + z * z).toDouble()))).toFloat()
        val roll = Math.toDegrees(atan2(y.toDouble(), z.toDouble())).toFloat()
        return Tilt(pitch, roll)
    }

    fun isLevel(tilt: Tilt, toleranceDegrees: Float = 1.5f): Boolean =
        tilt.worst <= toleranceDegrees

    /**
     * Bubble position in a unit circle: x follows roll, y follows pitch. Both are clamped so
     * the bubble sticks to the rim instead of flying off when the phone is on its side.
     */
    fun bubbleOffset(tilt: Tilt, rangeDegrees: Float = 20f): Pair<Float, Float> {
        val range = if (rangeDegrees <= 0f) 20f else rangeDegrees
        val x = (tilt.roll / range).coerceIn(-1f, 1f)
        val y = (tilt.pitch / range).coerceIn(-1f, 1f)
        return x to y
    }

    /** Human description of which way the device is leaning. */
    fun direction(tilt: Tilt, toleranceDegrees: Float = 1.5f): String {
        if (isLevel(tilt, toleranceDegrees)) return "Level"
        val vertical = when {
            tilt.pitch > toleranceDegrees -> "Back"
            tilt.pitch < -toleranceDegrees -> "Forward"
            else -> null
        }
        val horizontal = when {
            tilt.roll > toleranceDegrees -> "right"
            tilt.roll < -toleranceDegrees -> "left"
            else -> null
        }
        return listOfNotNull(vertical, horizontal).joinToString(" ").ifBlank { "Tilted" }
    }

    fun formatDegrees(value: Float): String {
        val rounded = Math.round(value * 10f) / 10f
        val sign = if (rounded > 0f) "+" else ""
        return "$sign$rounded" + "\u00B0"
    }
}
