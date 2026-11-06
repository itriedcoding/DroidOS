package dev.droidos.shell.apps.speedtest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThroughputMeterTest {

    @Test
    fun convertsBytesAndTimeToBitsPerSecond() {
        assertEquals(8_000_000.0, ThroughputMeter.bitsPerSecond(1_000_000L, 1_000L), 0.001)
        assertEquals(0.0, ThroughputMeter.bitsPerSecond(0L, 1_000L), 0.001)
        assertEquals(0.0, ThroughputMeter.bitsPerSecond(1_000L, 0L), 0.001)
        assertEquals(1_000_000.0, ThroughputMeter.bytesPerSecond(2_000_000L, 2_000L), 0.001)
    }

    @Test
    fun formatsRatesAcrossThreeMagnitudes() {
        assertEquals("8.0 Mbps", ThroughputMeter.formatRate(8_000_000.0))
        assertEquals("128 kbps", ThroughputMeter.formatRate(128_000.0))
        assertEquals("500 bps", ThroughputMeter.formatRate(500.0))
        assertEquals("0 bps", ThroughputMeter.formatRate(0.0))
        assertEquals("1.5 MB/s", ThroughputMeter.formatByteRate(1.5 * 1024 * 1024))
    }

    @Test
    fun mediansHandleOddEvenAndEmptyLists() {
        assertEquals(2.0, ThroughputMeter.median(listOf(3.0, 1.0, 2.0)), 1e-9)
        assertEquals(2.5, ThroughputMeter.median(listOf(4.0, 1.0, 3.0, 2.0)), 1e-9)
        assertEquals(0.0, ThroughputMeter.median(emptyList()), 1e-9)
    }

    @Test
    fun progressIsClampedToThePayload() {
        assertEquals(0.5f, ThroughputMeter.progress(5_000L, 10_000L), 1e-6f)
        assertEquals(1f, ThroughputMeter.progress(20_000L, 10_000L), 1e-6f)
        assertEquals(0f, ThroughputMeter.progress(10L, 0L), 1e-6f)
    }

    @Test
    fun labelsDescribeTheMeasurementHonestly() {
        assertEquals("1.50 s", ThroughputMeter.formatSeconds(1_500L))
        assertTrue(ThroughputMeter.summariseComplete(true).contains("sustained"))
        assertTrue(ThroughputMeter.summariseComplete(false).contains("Stopped early"))
    }
}
