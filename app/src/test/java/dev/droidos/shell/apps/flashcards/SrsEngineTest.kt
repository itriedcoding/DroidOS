package dev.droidos.shell.apps.flashcards

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SrsEngineTest {

    private val today = LocalDate.of(2026, 11, 6)

    @Test
    fun easeFactorsMatchTheSm2Table() {
        assertEquals(2.6, SrsEngine.easeFactor(2.5, 5), 0.0001)
        assertEquals(2.5, SrsEngine.easeFactor(2.5, 4), 0.0001)
        assertEquals(2.36, SrsEngine.easeFactor(2.5, 3), 0.0001)
        assertEquals(2.18, SrsEngine.easeFactor(2.5, 2), 0.0001)
        assertEquals(1.7, SrsEngine.easeFactor(2.5, 0), 0.0001)
        assertEquals(1.3, SrsEngine.easeFactor(1.3, 0), 0.0001)
    }

    @Test
    fun firstSuccessfulReviewSchedulesTomorrow() {
        val review = SrsEngine.review(SrsEngine.CardState(), quality = 5, today = today)
        assertEquals(1, review.state.repetitions)
        assertEquals(1, review.state.intervalDays)
        assertEquals(today.plusDays(1), review.dueDate)
        assertEquals(2.6, review.state.easeFactor, 0.0001)
    }

    @Test
    fun secondReviewJumpsToSixDays() {
        val first = SrsEngine.review(SrsEngine.CardState(), 5, today)
        val second = SrsEngine.review(first.state, 5, first.dueDate)
        assertEquals(2, second.state.repetitions)
        assertEquals(6, second.state.intervalDays)
        assertEquals(first.dueDate.plusDays(6), second.dueDate)
    }

    @Test
    fun laterReviewsMultiplyByTheEaseFactor() {
        val state = SrsEngine.CardState(easeFactor = 2.7, intervalDays = 6, repetitions = 2)
        val review = SrsEngine.review(state, 5, today)
        assertEquals(2.8, review.state.easeFactor, 0.0001)
        assertEquals(17, review.state.intervalDays) // 6 * 2.8 = 16.8, rounded
        assertEquals(today.plusDays(17), review.dueDate)
    }

    @Test
    fun aFailedReviewResetsTheCard() {
        val state = SrsEngine.CardState(easeFactor = 2.8, intervalDays = 17, repetitions = 4)
        val review = SrsEngine.review(state, 2, today)
        assertEquals(0, review.state.repetitions)
        assertEquals(1, review.state.intervalDays)
        assertEquals(today.plusDays(1), review.dueDate)
        assertTrue(review.state.easeFactor < state.easeFactor)
    }

    @Test
    fun outOfRangeGradesAreClamped() {
        assertEquals(SrsEngine.easeFactor(2.5, 5), SrsEngine.easeFactor(2.5, 99), 0.0001)
        assertEquals(SrsEngine.easeFactor(2.5, 0), SrsEngine.easeFactor(2.5, -3), 0.0001)
    }

    @Test
    fun dueChecksCompareWholeDays() {
        assertTrue(SrsEngine.isDue(today, today))
        assertTrue(SrsEngine.isDue(today.minusDays(3), today))
        assertFalse(SrsEngine.isDue(today.plusDays(1), today))
        assertEquals(-3L, SrsEngine.daysUntil(today.minusDays(3), today))
    }

    @Test
    fun dueLabelsReadNaturally() {
        assertEquals("due today", SrsEngine.dueLabel(today, today))
        assertEquals("due tomorrow", SrsEngine.dueLabel(today.plusDays(1), today))
        assertEquals("in 5 days", SrsEngine.dueLabel(today.plusDays(5), today))
        assertEquals("2d overdue", SrsEngine.dueLabel(today.minusDays(2), today))
        assertEquals("Easy", SrsEngine.gradeLabel(5))
        assertEquals("Again", SrsEngine.gradeLabel(1))
    }
}
