package dev.droidos.shell.apps.timelab

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import kotlin.math.abs

object TimeMath {

    private val ISO_DAY = DateTimeFormatter.ISO_LOCAL_DATE

    /**
     * Accepts Unix seconds, Unix milliseconds, ISO-8601 instants ("2026-11-05T09:30:00Z") and
     * local date-times without an offset, which are read in the given zone. Returns null for
     * anything else instead of guessing.
     */
    fun parseInstant(input: String, zone: ZoneId = ZoneId.systemDefault()): Long? {
        val text = input.trim()
        if (text.isEmpty()) return null
        text.toLongOrNull()?.let { value ->
            // Ten digits (or fewer, with a plausible magnitude) reads as seconds.
            return if (abs(value) < 100_000_000_000L) value * 1000L else value
        }
        runCatching { return Instant.parse(text).toEpochMilli() }
        runCatching { return ZonedDateTime.parse(text).toInstant().toEpochMilli() }
        runCatching {
            return LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()
        }
        runCatching {
            return LocalDate.parse(text, ISO_DAY).atStartOfDay(zone).toInstant().toEpochMilli()
        }
        return null
    }

    fun parseDate(input: String): LocalDate? = try {
        LocalDate.parse(input.trim(), ISO_DAY)
    } catch (e: DateTimeParseException) {
        null
    }

    fun isoLabel(epochMillis: Long, zone: ZoneId): String =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US)
            .withZone(zone)
            .format(Instant.ofEpochMilli(epochMillis))

    fun isoTLabel(epochMillis: Long, zone: ZoneId): String =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
            .withZone(zone)
            .format(Instant.ofEpochMilli(epochMillis))

    fun weekdayLabel(epochMillis: Long, zone: ZoneId): String =
        DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.US)
            .withZone(zone)
            .format(Instant.ofEpochMilli(epochMillis))

    /** "UTC+05:30" style label for a zone at a given instant, so DST is respected. */
    fun offsetLabel(zone: ZoneId, atMillis: Long): String {
        val offset = zone.rules.getOffset(Instant.ofEpochMilli(atMillis))
        if (offset.totalSeconds == 0) return "UTC"
        val totalMinutes = offset.totalSeconds / 60
        val sign = if (totalMinutes < 0) "-" else "+"
        val hours = abs(totalMinutes) / 60
        val minutes = abs(totalMinutes) % 60
        return "UTC$sign${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}"
    }

    /** Same moment, expressed in another zone. */
    fun toZone(epochMillis: Long, target: ZoneId): String = isoLabel(epochMillis, target)

    /** Offset between two local dates in whole days, later minus earlier. */
    fun daysBetween(from: LocalDate, to: LocalDate): Long =
        Duration.between(from.atStartOfDay(), to.atStartOfDay()).toDays()

    fun humanDuration(totalMillis: Long): String {
        if (totalMillis == 0L) return "0s"
        var remaining = abs(totalMillis) / 1000L
        val days = remaining / 86_400L
        remaining %= 86_400L
        val hours = remaining / 3_600L
        remaining %= 3_600L
        val minutes = remaining / 60L
        val seconds = remaining % 60L
        val parts = mutableListOf<String>()
        if (days > 0) parts += "${days}d"
        if (hours > 0) parts += "${hours}h"
        if (minutes > 0) parts += "${minutes}m"
        if (seconds > 0) parts += "${seconds}s"
        val sign = if (totalMillis < 0) "-" else ""
        return sign + parts.joinToString(" ")
    }

    fun utc(millis: Long): ZonedDateTime = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC)

    /** Zones worth listing first: the device's own, UTC, and a few common ones. */
    fun suggestedZones(system: ZoneId): List<ZoneId> = buildList {
        add(system)
        listOf("UTC", "America/New_York", "America/Los_Angeles", "Europe/London", "Europe/Berlin", "Asia/Tokyo", "Asia/Kolkata", "Australia/Sydney")
            .mapNotNull { runCatching { ZoneId.of(it) }.getOrNull() }
            .forEach { if (it !in this) add(it) }
    }

    fun allZoneIds(): List<String> = ZoneId.getAvailableZoneIds().sorted()

    fun searchZones(query: String, limit: Int = 40): List<String> {
        val text = query.trim().lowercase(Locale.US)
        if (text.isEmpty()) return suggestedZones(ZoneId.systemDefault()).map { it.id }
        return allZoneIds().asSequence()
            .filter { it.lowercase(Locale.US).contains(text) }
            .take(limit)
            .toList()
    }
}
