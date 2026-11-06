package dev.droidos.shell.apps.music

import java.util.Locale
import kotlin.random.Random

/** One row from the device's media library. Pure data so the queue logic stays testable. */
data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long
)

enum class RepeatMode(val label: String) {
    OFF("Repeat off"),
    ALL("Repeat all"),
    ONE("Repeat one")
}

object MusicEngine {

    fun formatDuration(durationMs: Long): String {
        if (durationMs <= 0L) return "0:00"
        val totalSeconds = durationMs / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%d:%02d", minutes, seconds)
        }
    }

    /** Case-insensitive match on title, artist or album. Blank query keeps everything. */
    fun filter(tracks: List<Track>, query: String): List<Track> {
        val text = query.trim().lowercase(Locale.US)
        if (text.isEmpty()) return tracks
        return tracks.filter { track ->
            track.title.lowercase(Locale.US).contains(text) ||
                track.artist.lowercase(Locale.US).contains(text) ||
                track.album.lowercase(Locale.US).contains(text)
        }
    }

    fun sortByTitle(tracks: List<Track>): List<Track> =
        tracks.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { track: Track -> track.title })

    /**
     * Next track index, or -1 when playback should stop. Automatic advance honours REPEAT_ONE by
     * staying put; a manual skip still moves on. A manual skip past the end with repeat off stays
     * on the last track rather than silently wrapping.
     */
    fun advance(
        current: Int,
        size: Int,
        shuffle: Boolean,
        repeat: RepeatMode,
        automatic: Boolean,
        random: Random = Random.Default
    ): Int {
        if (size <= 0) return -1
        if (size == 1) return 0
        if (automatic && repeat == RepeatMode.ONE) return current
        if (shuffle) {
            val pick = random.nextInt(size)
            return if (pick == current) (pick + 1) % size else pick
        }
        val next = current + 1
        if (next < size) return next
        return when {
            repeat == RepeatMode.OFF && automatic -> -1
            repeat == RepeatMode.OFF -> size - 1
            else -> 0
        }
    }

    /** Previous index; from the first track this wraps only when repeat is on. */
    fun rewind(current: Int, size: Int, shuffle: Boolean, repeat: RepeatMode, random: Random = Random.Default): Int {
        if (size <= 0) return -1
        if (size == 1) return 0
        if (shuffle) {
            val pick = random.nextInt(size)
            return if (pick == current) (pick + 1) % size else pick
        }
        if (current <= 0) return if (repeat == RepeatMode.ALL) size - 1 else 0
        return current - 1
    }

    fun progress(positionMs: Long, durationMs: Long): Float {
        if (durationMs <= 0L) return 0f
        return (positionMs.toDouble() / durationMs.toDouble()).toFloat().coerceIn(0f, 1f)
    }

    fun nextRepeatMode(current: RepeatMode): RepeatMode = when (current) {
        RepeatMode.OFF -> RepeatMode.ALL
        RepeatMode.ALL -> RepeatMode.ONE
        RepeatMode.ONE -> RepeatMode.OFF
    }

    fun emptyMessage(query: String): String =
        if (query.isBlank()) "No music found on this device yet."
        else "Nothing matches \"${query.trim()}\"."
}
