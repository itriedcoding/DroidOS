package dev.droidos.shell.apps.timelab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class TimeMathTest {

    @Test
    fun acceptsEpochSecondsAndMillis() {
        assertEquals(1_700_000_000_000L, TimeMath.parseInstant("1700000000"))
        assertEquals(1_700_000_000_123L, TimeMath.parseInstant("1700000000123"))
        assertEquals(1_700_000_000_000L, TimeMath.parseInstant("  1700000000  "))
    }

    @Test
    fun acceptsIsoInstantsDatesAndLocalDateTimes() {
        val expected = Instant.parse("2026-11-05T09:30:00Z").toEpochMilli()
        assertEquals(expected, TimeMath.parseInstant("2026-11-05T09:30:00Z"))
        assertEquals(expected, TimeMath.parseInstant("2026-11-05T09:30:00", ZoneOffset.UTC))
        assertEquals(
            Instant.parse("2026-11-05T00:00:00Z").toEpochMilli(),
            TimeMath.parseInstant("2026-11-05", ZoneOffset.UTC)
        )
    }

    @Test
    fun rejectsUnreadableInput() {
        assertNull(TimeMath.parseInstant(""))
        assertNull(TimeMath.parseInstant("yesterday"))
        assertNull(TimeMath.parseInstant("2026-13-01T00:00:00Z"))
    }

    @Test
    fun offsetsRespectHalfHoursAndDst() {
        val at = Instant.parse("2026-11-05T09:30:00Z").toEpochMilli()
        assertEquals("UTC+05:30", TimeMath.offsetLabel(ZoneId.of("Asia/Kolkata"), at))
        assertEquals("UTC", TimeMath.offsetLabel(ZoneOffset.UTC, at))
        assertEquals("UTC-05:00", TimeMath.offsetLabel(ZoneId.of("America/New_York"), at))
    }

    @Test
    fun formatsLabelsInTheGivenZone() {
        val at = Instant.parse("2026-11-05T09:30:00Z").toEpochMilli()
        assertEquals("2026-11-05T09:30:00Z", TimeMath.isoTLabel(at, ZoneOffset.UTC))
        assertEquals("2026-11-05 09:30:00", TimeMath.isoLabel(at, ZoneOffset.UTC))
        assertEquals("2026-11-05 15:00:00", TimeMath.isoLabel(at, ZoneId.of("Asia/Kolkata")))
    }

    @Test
    fun humanDurationKeepsUsefulUnits() {
        assertEquals("0s", TimeMath.humanDuration(0))
        assertEquals("45s", TimeMath.humanDuration(45_000))
        assertEquals("1d 1h 1m 1s", TimeMath.humanDuration(90_061_000))
        assertEquals("-1h", TimeMath.humanDuration(-3_600_000))
        assertEquals("2h", TimeMath.humanDuration(7_200_000))
    }

    @Test
    fun daysBetweenCountsWholeDays() {
        assertEquals(30L, TimeMath.daysBetween(LocalDate.of(2026, 11, 5), LocalDate.of(2026, 12, 5)))
        assertEquals(-30L, TimeMath.daysBetween(LocalDate.of(2026, 12, 5), LocalDate.of(2026, 11, 5)))
        assertEquals(0L, TimeMath.daysBetween(LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 5)))
    }

    @Test
    fun parsesOnlyRealDates() {
        assertEquals(LocalDate.of(2026, 11, 5), TimeMath.parseDate("2026-11-05"))
        assertNull(TimeMath.parseDate("2026-02-30"))
        assertNull(TimeMath.parseDate("05/11/2026"))
    }

    @Test
    fun zoneSearchFindsIdsAndFallsBackToSuggestions() {
        assertTrue(TimeMath.searchZones("tokyo").contains("Asia/Tokyo"))
        assertTrue(TimeMath.searchZones("").isNotEmpty())
        assertTrue(TimeMath.searchZones("   ").isNotEmpty())
        assertTrue(TimeMath.searchZones("not-a-zone").isEmpty())
        assertTrue(TimeMath.allZoneIds().contains("Europe/Berlin"))
    }
}
