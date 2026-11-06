package dev.droidos.shell.apps.cleaner

import java.util.Locale

/** One labelled slice of storage, used for both folder and media breakdowns. */
data class SizeBucket(val label: String, val bytes: Long, val files: Int)

object StorageMath {

    private val UNITS = listOf("B", "KB", "MB", "GB", "TB", "PB")

    /** Binary units with one decimal above bytes, e.g. 1.4 GB. */
    fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        var value = bytes.toDouble()
        var unit = 0
        while (value >= 1024.0 && unit < UNITS.lastIndex) {
            value /= 1024.0
            unit++
        }
        val text = if (unit == 0) value.toLong().toString() else String.format(Locale.US, "%.1f", value)
        return "$text ${UNITS[unit]}"
    }

    fun percentUsed(usedBytes: Long, totalBytes: Long): Float {
        if (totalBytes <= 0L) return 0f
        return (usedBytes.toDouble() / totalBytes.toDouble()).toFloat().coerceIn(0f, 1f)
    }

    fun percentLabel(usedBytes: Long, totalBytes: Long): String {
        if (totalBytes <= 0L) return "--"
        return "${(percentUsed(usedBytes, totalBytes) * 100f).toInt()}%"
    }

    fun total(buckets: List<SizeBucket>): Long = buckets.sumOf { it.bytes }

    /** Biggest first, empty slices dropped, capped so the list stays readable. */
    fun rank(buckets: List<SizeBucket>, limit: Int = 8): List<SizeBucket> =
        buckets.filter { it.bytes > 0L }.sortedByDescending { it.bytes }.take(limit)

    fun freedMessage(freedBytes: Long, removedFiles: Int): String = when {
        removedFiles == 0 -> "Nothing to remove - those folders were already empty"
        freedBytes <= 0L -> "Removed $removedFiles ${plural(removedFiles, "file")} but no space was reclaimed"
        else -> "Freed ${formatBytes(freedBytes)} across $removedFiles ${plural(removedFiles, "file")}"
    }

    fun shareOf(bytes: Long, totalBytes: Long): Float {
        if (totalBytes <= 0L) return 0f
        return (bytes.toDouble() / totalBytes.toDouble()).toFloat().coerceIn(0f, 1f)
    }

    private fun plural(count: Int, word: String) = if (count == 1) word else "${word}s"
}
