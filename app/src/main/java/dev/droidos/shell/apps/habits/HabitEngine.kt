package dev.droidos.shell.apps.habits

import java.time.LocalDate
import java.util.Locale

/** Streak and completion maths; no Android or database types so it is directly testable. */
object HabitEngine {

    fun dateKey(date: LocalDate): String = date.toString()

    fun parseKey(key: String?): LocalDate? = runCatching { LocalDate.parse(key) }.getOrNull()

    /**
     * Consecutive marked days ending today, or ending yesterday when today has not been marked
     * yet - a streak should not read as broken until the day is actually over.
     */
    fun streak(days: Set<LocalDate>, today: LocalDate): Int {
        if (days.isEmpty()) return 0
        var cursor = if (days.contains(today)) today else today.minusDays(1)
        var count = 0
        while (days.contains(cursor)) {
            count++
            cursor = cursor.minusDays(1)
        }
        return count
    }

    fun bestStreak(days: Set<LocalDate>): Int {
        if (days.isEmpty()) return 0
        val sorted = days.sorted()
        var best = 1
        var run = 1
        for (index in 1 until sorted.size) {
            run = if (sorted[index - 1].plusDays(1) == sorted[index]) run + 1 else 1
            if (run > best) best = run
        }
        return best
    }

    /** Fraction of the trailing window that is marked, 0..1. */
    fun completion(days: Set<LocalDate>, today: LocalDate, window: Int = 30): Double {
        if (window <= 0) return 0.0
        val hits = (0 until window).count { days.contains(today.minusDays(it.toLong())) }
        return hits.toDouble() / window
    }

    fun completionPercent(days: Set<LocalDate>, today: LocalDate, window: Int = 30): Int =
        (completion(days, today, window) * 100).toInt()

    fun summary(total: Int, doneToday: Int): String = when {
        total == 0 -> "No habits yet"
        doneToday == total -> "All $total done today"
        doneToday == 0 -> "0 of $total done today"
        else -> "$doneToday of $total done today"
    }

    fun streakLabel(streak: Int): String = when {
        streak <= 0 -> "Not started"
        streak == 1 -> "1 day streak"
        else -> "$streak day streak"
    }

    fun normaliseName(raw: String): String = raw.trim().replace(Regex("\\s+"), " ")

    fun validateName(raw: String): String? = when {
        normaliseName(raw).isEmpty() -> "Give the habit a name"
        normaliseName(raw).length > 80 -> "Keep the name under 80 characters"
        else -> null
    }

    fun plural(count: Int): String = if (count == 1) "1 habit" else String.format(Locale.US, "%d habits", count)
}
