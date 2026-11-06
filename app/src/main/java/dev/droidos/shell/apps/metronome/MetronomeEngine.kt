package dev.droidos.shell.apps.metronome

object MetronomeEngine {

    const val MIN_BPM = 30
    const val MAX_BPM = 300
    const val DEFAULT_BPM = 100
    const val MIN_BEATS = 1
    const val MAX_BEATS = 12

    fun clampBpm(bpm: Int): Int = bpm.coerceIn(MIN_BPM, MAX_BPM)

    fun clampBeats(beats: Int): Int = beats.coerceIn(MIN_BEATS, MAX_BEATS)

    fun intervalMillis(bpm: Int): Long = (60_000.0 / clampBpm(bpm)).toLong()

    fun intervalNanos(bpm: Int): Long = (60_000_000_000.0 / clampBpm(bpm)).toLong()

    /** Beat 0 of every bar is the accented click. */
    fun isAccent(beat: Int, beatsPerBar: Int): Boolean =
        Math.floorMod(beat, clampBeats(beatsPerBar)) == 0

    /**
     * Tempo from a list of tap timestamps in milliseconds. Gaps outside 200..2000 ms are ignored
     * (that range is 30..300 BPM), so a pause between attempts cannot poison the average.
     */
    fun tapToBpm(taps: List<Long>): Int? {
        if (taps.size < 2) return null
        val gaps = taps.zipWithNext { earlier, later -> later - earlier }
            .filter { it in 200L..2000L }
        if (gaps.isEmpty()) return null
        val average = gaps.average()
        if (average <= 0.0) return null
        return clampBpm(Math.round(60_000.0 / average).toInt())
    }

    fun tempoName(bpm: Int): String {
        val value = clampBpm(bpm)
        return when {
            value < 60 -> "Largo"
            value < 76 -> "Adagio"
            value < 108 -> "Andante"
            value < 120 -> "Moderato"
            value < 168 -> "Allegro"
            else -> "Presto"
        }
    }

    fun step(bpm: Int, delta: Int): Int = clampBpm(bpm + delta)
}
