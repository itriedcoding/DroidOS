package dev.droidos.shell.apps.speedtest

import java.util.Locale

/** Pure maths behind the speed test: throughput, medians and labels. */
object ThroughputMeter {

    /** Bits per second from a byte count and an elapsed time. Zero for unusable input. */
    fun bitsPerSecond(bytes: Long, millis: Long): Double {
        if (bytes <= 0L || millis <= 0L) return 0.0
        return bytes.toDouble() * 8.0 * 1000.0 / millis.toDouble()
    }

    fun bytesPerSecond(bytes: Long, millis: Long): Double {
        if (bytes <= 0L || millis <= 0L) return 0.0
        return bytes.toDouble() * 1000.0 / millis.toDouble()
    }

    fun formatRate(bitsPerSecond: Double): String {
        if (bitsPerSecond <= 0.0 || bitsPerSecond.isNaN() || bitsPerSecond.isInfinite()) return "0 bps"
        val mbps = bitsPerSecond / 1_000_000.0
        if (mbps >= 1.0) return String.format(Locale.US, "%.1f Mbps", mbps)
        val kbps = bitsPerSecond / 1_000.0
        if (kbps >= 1.0) return String.format(Locale.US, "%.0f kbps", kbps)
        return String.format(Locale.US, "%.0f bps", bitsPerSecond)
    }

    fun formatByteRate(bytesPerSecond: Double): String {
        if (bytesPerSecond <= 0.0) return "0 B/s"
        return if (bytesPerSecond >= 1024.0 * 1024.0) {
            String.format(Locale.US, "%.1f MB/s", bytesPerSecond / (1024.0 * 1024.0))
        } else if (bytesPerSecond >= 1024.0) {
            String.format(Locale.US, "%.0f KB/s", bytesPerSecond / 1024.0)
        } else {
            String.format(Locale.US, "%.0f B/s", bytesPerSecond)
        }
    }

    fun median(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2.0
    }

    fun formatSeconds(millis: Long): String = String.format(Locale.US, "%.2f s", millis / 1000.0)

    fun progress(bytes: Long, limit: Long): Float {
        if (limit <= 0L) return 0f
        return (bytes.toDouble() / limit.toDouble()).toFloat().coerceIn(0f, 1f)
    }

    fun summariseComplete(complete: Boolean): String =
        if (complete) "Full payload transferred - this is the sustained rate" else "Stopped early - rate covers the bytes actually received"
}
