package dev.droidos.shell.apps.recorder

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object RecorderEngine {

    const val BITRATE = 96_000
    const val SAMPLE_RATE = 44_100

    fun formatElapsed(millis: Long): String {
        if (millis <= 0L) return "0:00"
        val seconds = millis / 1000L
        val hours = seconds / 3600L
        val minutes = (seconds % 3600L) / 60L
        val remainder = seconds % 60L
        return if (hours > 0L) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, remainder)
        } else {
            String.format(Locale.US, "%d:%02d", minutes, remainder)
        }
    }

    /** Deterministic file name for a recording, e.g. rec-20261105-162530.m4a. */
    fun fileName(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        "rec-" + DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.US)
            .withZone(zone)
            .format(Instant.ofEpochMilli(epochMillis)) + ".m4a"

    fun humanSize(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        if (bytes < 1024L) return "$bytes B"
        val kilobytes = bytes / 1024.0
        if (kilobytes < 1024.0) return String.format(Locale.US, "%.1f KB", kilobytes)
        val megabytes = kilobytes / 1024.0
        return String.format(Locale.US, "%.1f MB", megabytes)
    }

    /** AAC at a known bitrate, so the estimate is a real arithmetic figure, not a guess. */
    fun estimateBytes(bitrateBitsPerSecond: Int, seconds: Int): Long {
        if (bitrateBitsPerSecond <= 0 || seconds <= 0) return 0L
        return bitrateBitsPerSecond.toLong() / 8L * seconds.toLong()
    }
}
