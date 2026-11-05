package dev.droidos.shell.apps.clock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class StopwatchEngineTest {

    @Test
    fun `elapsed time follows the clock, not tick counts`() {
        val started = StopwatchState().start(1_000L)
        assertTrue(started.running)
        assertEquals(5_000L, started.elapsed(6_000L))
    }

    @Test
    fun `pausing freezes the total and resuming continues from it`() {
        val paused = StopwatchState().start(1_000L).stop(6_000L)
        assertFalse(paused.running)
        assertEquals(5_000L, paused.elapsed(60_000L))

        val resumed = paused.start(10_000L)
        assertEquals(7_000L, resumed.elapsed(12_000L))
    }

    @Test
    fun `starting a running stopwatch keeps the original start`() {
        val state = StopwatchState().start(1_000L).start(4_000L)
        assertEquals(1_000L, state.startedAtMillis)
    }

    @Test
    fun `laps record the elapsed time at the moment they are taken`() {
        val state = StopwatchState().start(0L).lap(2_000L).lap(5_000L)
        assertEquals(listOf(2_000L, 5_000L), state.laps)
    }

    @Test
    fun `a lap while stopped is ignored`() {
        val state = StopwatchState().lap(2_000L)
        assertTrue(state.laps.isEmpty())
    }

    @Test
    fun `reset clears elapsed time and laps`() {
        val state = StopwatchState().start(0L).lap(1_000L).stop(2_000L).reset()
        assertEquals(0L, state.elapsed(9_999L))
        assertTrue(state.laps.isEmpty())
        assertFalse(state.running)
    }

    @Test
    fun `formatting shows centiseconds and promotes hours`() {
        assertEquals("00:00.00", StopwatchFormat.elapsed(0L))
        assertEquals("01:05.25", StopwatchFormat.elapsed(65_250L))
        assertEquals("1:00:00.00", StopwatchFormat.elapsed(3_600_000L))
        assertEquals("00:00.00", StopwatchFormat.elapsed(-500L))
    }
}

class AlarmScheduleTest {

    private val utc: TimeZone = TimeZone.getTimeZone("UTC")

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance(utc).apply {
            clear()
            set(year, month, day, hour, minute, 0)
        }.timeInMillis

    @Test
    fun `a time later today is used as-is`() {
        val now = at(2026, Calendar.NOVEMBER, 5, 9, 0)
        val next = AlarmSchedule.nextTrigger(now, 10, 0, emptySet(), utc)
        assertEquals(3_600_000L, next - now)
    }

    @Test
    fun `a time already passed rolls to tomorrow`() {
        val now = at(2026, Calendar.NOVEMBER, 5, 9, 0)
        val next = AlarmSchedule.nextTrigger(now, 8, 30, emptySet(), utc)
        assertEquals(84_600_000L, next - now)
    }

    @Test
    fun `a weekday alarm skips to the next matching day`() {
        val now = at(2026, Calendar.NOVEMBER, 5, 9, 0) // Thursday
        val next = AlarmSchedule.nextTrigger(now, 8, 30, setOf(Calendar.MONDAY), utc)
        val calendar = Calendar.getInstance(utc).apply { timeInMillis = next }
        assertEquals(Calendar.MONDAY, calendar.get(Calendar.DAY_OF_WEEK))
        assertEquals(8, calendar.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, calendar.get(Calendar.MINUTE))
        assertTrue(next > now)
    }

    @Test
    fun `a daisy alarm firing now moves a week ahead rather than re-firing`() {
        val now = at(2026, Calendar.NOVEMBER, 5, 9, 0)
        val next = AlarmSchedule.nextTrigger(now, 9, 0, setOf(Calendar.THURSDAY), utc)
        assertEquals(7L * 24L * 60L * 60L * 1000L, next - now)
    }
}
