package dev.droidos.shell.apps.flashcards

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/**
 * SM-2 scheduling, the algorithm Anki grew out of. Grades run 0-5; anything below 3 lapses the
 * card back to one day.
 */
object SrsEngine {

    const val MIN_EASE = 1.3

    data class CardState(
        val easeFactor: Double = 2.5,
        val intervalDays: Int = 0,
        val repetitions: Int = 0
    )

    data class Review(val state: CardState, val dueDate: LocalDate)

    fun easeFactor(current: Double, quality: Int): Double {
        val grade = quality.coerceIn(0, 5)
        val updated = current + (0.1 - (5 - grade) * (0.08 + (5 - grade) * 0.02))
        val clamped = maxOf(MIN_EASE, updated)
        return (clamped * 100.0).roundToInt() / 100.0
    }

    fun review(state: CardState, quality: Int, today: LocalDate = LocalDate.now()): Review {
        val grade = quality.coerceIn(0, 5)
        val ease = easeFactor(state.easeFactor, grade)
        val repetitions = if (grade < 3) 0 else state.repetitions + 1
        val interval = when {
            grade < 3 -> 1
            repetitions == 1 -> 1
            repetitions == 2 -> 6
            else -> (state.intervalDays * ease).roundToInt().coerceAtLeast(1)
        }
        return Review(
            state = CardState(easeFactor = ease, intervalDays = interval, repetitions = repetitions),
            dueDate = today.plusDays(interval.toLong())
        )
    }

    fun isDue(dueDate: LocalDate, today: LocalDate): Boolean = !dueDate.isAfter(today)

    fun daysUntil(dueDate: LocalDate, today: LocalDate): Long =
        ChronoUnit.DAYS.between(today, dueDate)

    fun dueLabel(dueDate: LocalDate, today: LocalDate): String {
        val days = daysUntil(dueDate, today)
        return when {
            days < 0 -> "${-days}d overdue"
            days == 0L -> "due today"
            days == 1L -> "due tomorrow"
            else -> "in $days days"
        }
    }

    fun gradeLabel(quality: Int): String = when (quality.coerceIn(0, 5)) {
        0 -> "Blackout"
        1 -> "Again"
        2 -> "Hard miss"
        3 -> "Hard"
        4 -> "Good"
        else -> "Easy"
    }
}
