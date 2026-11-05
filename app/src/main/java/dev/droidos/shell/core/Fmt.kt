package dev.droidos.shell.core

import java.util.Locale
import kotlin.math.abs

/** Pure formatting helpers (no Android dependencies) so they stay unit-testable. */
object Fmt {

    private val BYTE_UNITS = arrayOf("B", "KB", "MB", "GB", "TB")

    fun bytes(value: Long): String {
        if (value <= 0L) return "0 B"
        var size = value.toDouble()
        var unit = 0
        while (size >= 1024.0 && unit < BYTE_UNITS.lastIndex) {
            size /= 1024.0
            unit++
        }
        return if (unit == 0) {
            "${value} B"
        } else {
            String.format(Locale.US, "%.1f %s", size, BYTE_UNITS[unit])
        }
    }

    fun percent(fraction: Float): String {
        val clamped = fraction.coerceIn(0f, 1f)
        return "${(clamped * 100).toInt()}%"
    }

    fun duration(millis: Long): String {
        if (millis < 0) return "--"
        val totalSeconds = millis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return when {
            hours > 0 -> String.format(Locale.US, "%dh %02dm", hours, minutes)
            minutes > 0 -> String.format(Locale.US, "%dm %02ds", minutes, seconds)
            else -> "${seconds}s"
        }
    }

    fun relativeTime(thenMs: Long, nowMs: Long): String {
        val delta = nowMs - thenMs
        if (delta < 0) return "now"
        val seconds = delta / 1000
        return when {
            seconds < 60 -> "now"
            seconds < 3600 -> "${seconds / 60}m ago"
            seconds < 86_400 -> "${seconds / 3600}h ago"
            seconds < 604_800 -> "${seconds / 86_400}d ago"
            else -> "${seconds / 604_800}w ago"
        }
    }

    fun clockTime(hourOfDay: Int, minute: Int, use24Hour: Boolean): String {
        val minutePart = String.format(Locale.US, "%02d", minute)
        if (use24Hour) return String.format(Locale.US, "%02d:%s", hourOfDay, minutePart)
        val suffix = if (hourOfDay < 12) "AM" else "PM"
        val hour = when {
            hourOfDay % 12 == 0 -> 12
            else -> hourOfDay % 12
        }
        return "$hour:$minutePart $suffix"
    }

    fun truncateMiddle(value: String, max: Int): String {
        if (value.length <= max || max < 8) return value
        val keepFront = (max - 3) / 2
        val keepBack = max - 3 - keepFront
        return value.take(keepFront) + "..." + value.takeLast(keepBack)
    }

    /** Compact date used for file listings. */
    fun date(epochMillis: Long): String {
        if (epochMillis <= 0L) return "--"
        val format = java.text.SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        return format.format(java.util.Date(epochMillis))
    }

    fun signedPercent(delta: Float): String {
        val pct = (abs(delta) * 100).toInt()
        return if (delta >= 0) "+$pct%" else "-$pct%"
    }
}
