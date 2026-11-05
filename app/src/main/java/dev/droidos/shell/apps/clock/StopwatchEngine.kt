package dev.droidos.shell.apps.clock

/**
 * Stopwatch state as a pure value: elapsed time is derived from the wall clock, so it stays
 * correct across pauses without any per-tick accumulation error.
 */
data class StopwatchState(
    val accumulatedMillis: Long = 0L,
    val startedAtMillis: Long? = null,
    val laps: List<Long> = emptyList()
) {
    val running: Boolean get() = startedAtMillis != null

    fun elapsed(nowMillis: Long): Long {
        val live = startedAtMillis?.let { (nowMillis - it).coerceAtLeast(0L) } ?: 0L
        return accumulatedMillis + live
    }

    fun start(nowMillis: Long): StopwatchState =
        if (running) this else copy(startedAtMillis = nowMillis)

    fun stop(nowMillis: Long): StopwatchState =
        if (!running) this else copy(accumulatedMillis = elapsed(nowMillis), startedAtMillis = null)

    fun reset(): StopwatchState = StopwatchState()

    fun lap(nowMillis: Long): StopwatchState {
        if (!running) return this
        return copy(laps = laps + elapsed(nowMillis))
    }
}

object StopwatchFormat {

    /** mm:ss.cs, promoted to h:mm:ss.cs once the stopwatch passes an hour. */
    fun elapsed(millis: Long): String {
        val safe = millis.coerceAtLeast(0L)
        val totalSeconds = safe / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        val centis = (safe % 1000) / 10
        return if (hours > 0) {
            "%d:%02d:%02d.%02d".format(hours, minutes, seconds, centis)
        } else {
            "%02d:%02d.%02d".format(minutes, seconds, centis)
        }
    }
}
