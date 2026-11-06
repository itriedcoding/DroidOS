package dev.droidos.shell.apps.recorder

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class RecorderEngineTest {

    @Test
    fun formatsElapsedTime() {
        assertEquals("0:00", RecorderEngine.formatElapsed(0))
        assertEquals("0:09", RecorderEngine.formatElapsed(9_400))
        assertEquals("1:05", RecorderEngine.formatElapsed(65_000))
        assertEquals("1:02:03", RecorderEngine.formatElapsed(3_723_000))
    }

    @Test
    fun fileNamesAreDeterministic() {
        val at = Instant.parse("2026-11-05T16:25:30Z").toEpochMilli()
        assertEquals("rec-20261105-162530.m4a", RecorderEngine.fileName(at, ZoneOffset.UTC))
        assertEquals("rec-20261105-162530.m4a", RecorderEngine.fileName(at, ZoneOffset.UTC))
    }

    @Test
    fun humanSizeUsesBinaryUnits() {
        assertEquals("0 B", RecorderEngine.humanSize(0))
        assertEquals("512 B", RecorderEngine.humanSize(512))
        assertEquals("1.0 KB", RecorderEngine.humanSize(1024))
        assertEquals("5.0 MB", RecorderEngine.humanSize(5 * 1024L * 1024L))
    }

    @Test
    fun byteEstimateFollowsTheConfiguredBitrate() {
        assertEquals(720_000L, RecorderEngine.estimateBytes(96_000, 60))
        assertEquals(0L, RecorderEngine.estimateBytes(0, 60))
        assertEquals(0L, RecorderEngine.estimateBytes(96_000, 0))
    }
}
