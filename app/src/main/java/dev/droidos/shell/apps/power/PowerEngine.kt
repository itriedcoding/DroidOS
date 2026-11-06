package dev.droidos.shell.apps.power

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Battery maths and labels. The status, health and plugged constants mirror the platform values
 * but are declared locally so this stays a plain JVM class and can be tested.
 */
object PowerEngine {

    const val STATUS_UNKNOWN = 1
    const val STATUS_CHARGING = 2
    const val STATUS_DISCHARGING = 3
    const val STATUS_NOT_CHARGING = 4
    const val STATUS_FULL = 5

    const val HEALTH_UNKNOWN = 1
    const val HEALTH_GOOD = 2
    const val HEALTH_OVERHEAT = 3
    const val HEALTH_DEAD = 4
    const val HEALTH_OVER_VOLTAGE = 5
    const val HEALTH_FAILURE = 6
    const val HEALTH_COLD = 7

    const val PLUGGED_AC = 1
    const val PLUGGED_USB = 2
    const val PLUGGED_WIRELESS = 4
    const val PLUGGED_DOCK = 8

    fun statusLabel(status: Int): String = when (status) {
        STATUS_CHARGING -> "Charging"
        STATUS_DISCHARGING -> "Discharging"
        STATUS_NOT_CHARGING -> "Plugged in, not charging"
        STATUS_FULL -> "Full"
        else -> "Unknown"
    }

    fun healthLabel(health: Int): String = when (health) {
        HEALTH_GOOD -> "Good"
        HEALTH_OVERHEAT -> "Overheating"
        HEALTH_DEAD -> "Dead"
        HEALTH_OVER_VOLTAGE -> "Over voltage"
        HEALTH_FAILURE -> "Failure"
        HEALTH_COLD -> "Too cold"
        else -> "Unknown"
    }

    /** The plugged extra is a bitmask, so several sources can be true at once. */
    fun plugLabel(plugged: Int): String {
        if (plugged == 0) return "Unplugged"
        val sources = buildList {
            if (plugged and PLUGGED_AC != 0) add("AC")
            if (plugged and PLUGGED_USB != 0) add("USB")
            if (plugged and PLUGGED_WIRELESS != 0) add("Wireless")
            if (plugged and PLUGGED_DOCK != 0) add("Dock")
        }
        return if (sources.isEmpty()) "External" else sources.joinToString(" + ")
    }

    fun temperatureCelsius(tenths: Int): Double = tenths / 10.0

    fun formatTemperature(tenths: Int): String =
        String.format(Locale.US, "%.1f °C", temperatureCelsius(tenths))

    fun formatVoltage(millivolts: Int): String =
        String.format(Locale.US, "%.3f V", millivolts / 1000.0)

    fun formatCurrent(microamps: Long): String {
        val milliamps = microamps / 1000.0
        return String.format(Locale.US, "%+.0f mA", milliamps)
    }

    fun formatCharge(microampHours: Long): String = when {
        microampHours <= 0L -> "unavailable"
        microampHours >= 1_000_000L -> String.format(Locale.US, "%.2f Ah", microampHours / 1_000_000.0)
        else -> String.format(Locale.US, "%.0f mAh", microampHours / 1000.0)
    }

    /**
     * Hours until empty from the remaining charge and the instantaneous current. The platform
     * reports current as negative while discharging, so the magnitude is what matters. Null when
     * the device cannot report one of the two values.
     */
    fun hoursRemaining(chargeCounterUah: Long, currentNowUa: Long): Double? {
        val current = abs(currentNowUa)
        if (current <= 0L || chargeCounterUah <= 0L) return null
        return chargeCounterUah / current.toDouble()
    }

    fun formatHours(hours: Double?): String = when {
        hours == null || hours <= 0.0 -> "--"
        hours >= 1.0 -> String.format(Locale.US, "%.1f h", hours)
        else -> "${(hours * 60).roundToInt()} min"
    }

    fun powerWatts(voltageMillivolts: Int, currentUa: Long): Double =
        (voltageMillivolts / 1000.0) * (abs(currentUa) / 1_000_000.0)

    fun formatWatts(voltageMillivolts: Int, currentUa: Long): String {
        if (voltageMillivolts <= 0 || currentUa == 0L) return "--"
        return String.format(Locale.US, "%.2f W", powerWatts(voltageMillivolts, currentUa))
    }

    fun levelColorBand(level: Int): Band = when {
        level <= 15 -> Band.LOW
        level <= 40 -> Band.MEDIUM
        else -> Band.HEALTHY
    }

    enum class Band { LOW, MEDIUM, HEALTHY }
}
