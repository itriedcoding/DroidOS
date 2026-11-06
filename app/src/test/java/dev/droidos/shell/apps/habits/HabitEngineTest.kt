package dev.droidos.shell.apps.habits

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HabitEngineTest {

    private val today = LocalDate.of(2026, 11, 6)

    @Test
    fun streaksCountBackwardsFromToday() {
        val days = setOf(today, today.minusDays(1), today.minusDays(2), today.minusDays(5))
        assertEquals(3, HabitEngine.streak(days, today))
        assertEquals(0, HabitEngine.streak(emptySet(), today))
        assertEquals(1, HabitEngine.streak(setOf(today), today))
    }

    @Test
    fun aStreakSurvivesUntilTodayEnds() {
        val yesterday = today.minusDays(1)
        assertEquals(2, HabitEngine.streak(setOf(yesterday, yesterday.minusDays(1)), today))
        assertEquals(0, HabitEngine.streak(setOf(today.minusDays(2)), today))
    }

    @Test
    fun bestStreakFindsTheLongestRun() {
        val days = setOf(
            today.minusDays(10), today.minusDays(9), today.minusDays(8), today.minusDays(7),
            today.minusDays(3), today.minusDays(2)
        )
        assertEquals(4, HabitEngine.bestStreak(days))
        assertEquals(0, HabitEngine.bestStreak(emptySet()))
        assertEquals(1, HabitEngine.bestStreak(setOf(today)))
    }

    @Test
    fun completionUsesTheTrailingWindow() {
        val days = setOf(today, today.minusDays(1), today.minusDays(2))
        assertEquals(0.5, HabitEngine.completion(days, today, window = 6), 0.0001)
        assertEquals(50, HabitEngine.completionPercent(days, today, window = 6))
        assertEquals(0.0, HabitEngine.completion(days, today, window = 0), 0.0001)
    }

    @Test
    fun summaryWordingCoversTheEdges() {
        assertEquals("No habits yet", HabitEngine.summary(0, 0))
        assertEquals("All 3 done today", HabitEngine.summary(3, 3))
        assertEquals("0 of 4 done today", HabitEngine.summary(4, 0))
        assertEquals("2 of 4 done today", HabitEngine.summary(4, 2))
    }

    @Test
    fun streakLabelsReadNaturally() {
        assertEquals("Not started", HabitEngine.streakLabel(0))
        assertEquals("1 day streak", HabitEngine.streakLabel(1))
        assertEquals("9 day streak", HabitEngine.streakLabel(9))
    }

    @Test
    fun keysRoundTrip() {
        assertEquals("2026-11-06", HabitEngine.dateKey(today))
        assertEquals(today, HabitEngine.parseKey("2026-11-06"))
        assertNull(HabitEngine.parseKey("not-a-date"))
        assertNull(HabitEngine.parseKey(null))
    }

    @Test
    fun namesAreNormalisedAndValidated() {
        assertEquals("Drink water", HabitEngine.normaliseName("  Drink   water "))
        assertNull(HabitEngine.validateName("Read"))
        assertTrue(HabitEngine.validateName("   ")!!.contains("name"))
        assertTrue(HabitEngine.validateName("x".repeat(81))!!.contains("80"))
        assertEquals("2 habits", HabitEngine.plural(2))
        assertEquals("1 habit", HabitEngine.plural(1))
    }
}
