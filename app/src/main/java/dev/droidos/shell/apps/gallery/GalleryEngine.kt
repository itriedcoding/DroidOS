package dev.droidos.shell.apps.gallery

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Grid and label maths for the gallery, kept free of MediaStore so it can be tested. */
object GalleryEngine {

    private val DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
    private val DATE_TIME = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.getDefault())

    /** Roughly one tile per 110dp, clamped so the grid never degenerates on odd screens. */
    fun columns(widthDp: Int): Int = (widthDp / 110).coerceIn(2, 6)

    fun isImage(mimeType: String?): Boolean = mimeType?.startsWith("image/") == true

    fun takenLabel(epochSeconds: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        if (epochSeconds <= 0L) return "No capture date"
        val dateTime = Instant.ofEpochSecond(epochSeconds).atZone(zone)
        return dateTime.format(DATE)
    }

    fun takenLabelWithTime(epochSeconds: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        if (epochSeconds <= 0L) return "No capture date"
        return Instant.ofEpochSecond(epochSeconds).atZone(zone).format(DATE_TIME)
    }

    fun positionLabel(index: Int, total: Int): String {
        if (total <= 0) return "0 of 0"
        return "${(index + 1).coerceIn(1, total)} of $total"
    }

    fun byteSize(bytes: Long): String = when {
        bytes <= 0L -> "unknown size"
        bytes >= 1L shl 30 -> String.format(Locale.US, "%.2f GB", bytes / (1L shl 30).toDouble())
        bytes >= 1L shl 20 -> String.format(Locale.US, "%.1f MB", bytes / (1L shl 20).toDouble())
        bytes >= 1L shl 10 -> String.format(Locale.US, "%d KB", bytes / (1L shl 10))
        else -> "$bytes B"
    }

    /** Bucket name for a MediaStore relative path such as "DCIM/Camera/frame.jpg". */
    fun folderOf(relativePath: String?): String {
        val cleaned = relativePath?.trim().orEmpty()
        if (cleaned.isEmpty()) return "All images"
        val parts = cleaned.split('/').filter { it.isNotBlank() }
        if (parts.size <= 1) return "All images"
        return parts.dropLast(1).last()
    }

    /** A sample size that keeps a decode near the requested pixel budget. */
    fun sampleSize(width: Int, height: Int, targetPx: Int): Int {
        if (width <= 0 || height <= 0 || targetPx <= 0) return 1
        var sample = 1
        while (width / (sample * 2) >= targetPx && height / (sample * 2) >= targetPx && sample < 32) {
            sample *= 2
        }
        return sample
    }
}
