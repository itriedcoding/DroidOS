package dev.droidos.shell.apps.metronome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MetronomeEngineTest {

    @Test
    fun intervalsFollowSixtyThousandOverBpm() {
        assertEquals(500L, MetronomeEngine.intervalMillis(120))
        assertEquals(1_000L, MetronomeEngine.intervalMillis(60))
        assertEquals(400L, MetronomeEngine.intervalMillis(150))
    }

    @Test
    fun intervalsAreClampedToTheSupportedRange() {
        assertEquals(2_000L, MetronomeEngine.intervalMillis(10))
        assertEquals(200L, MetronomeEngine.intervalMillis(999))
        assertEquals(200L, MetronomeEngine.intervalMillis(MetronomeEngine.MAX_BPM))
        assertEquals(500_000_000L, MetronomeEngine.intervalNanos(120))
    }

    @Test
    fun bpmAndBeatsClamp() {
        assertEquals(30, MetronomeEngine.clampBpm(0))
        assertEquals(300, MetronomeEngine.clampBpm(1_000))
        assertEquals(1, MetronomeEngine.clampBeats(0))
        assertEquals(12, MetronomeEngine.clampBeats(40))
    }

    @Test
    fun theFirstBeatOfEachBarIsAccented() {
        assertTrue(MetronomeEngine.isAccent(0, 4))
        assertTrue(MetronomeEngine.isAccent(4, 4))
        assertTrue(MetronomeEngine.isAccent(-4, 4))
        assertTrue(!MetronomeEngine.isAccent(3, 4))
        assertTrue(MetronomeEngine.isAccent(0, 1))
    }

    @Test
    fun tapTempoAveragesPlausibleGaps() {
        assertEquals(120, MetronomeEngine.tapToBpm(listOf(0, 500, 1_000, 1_500)))
        assertEquals(120, MetronomeEngine.tapToBpm(listOf(0, 500, 6_000, 6_500)))
        assertNull(MetronomeEngine.tapToBpm(listOf(0)))
        assertNull(MetronomeEngine.tapToBpm(emptyList()))
        assertNull(MetronomeEngine.tapToBpm(listOf(0, 50)))
    }

    @Test
    fun tempoNamesMatchTheUsualBands() {
        assertEquals("Largo", MetronomeEngine.tempoName(50))
        assertEquals("Adagio", MetronomeEngine.tempoName(70))
        assertEquals("Andante", MetronomeEngine.tempoName(90))
        assertEquals("Moderato", MetronomeEngine.tempoName(110))
        assertEquals("Allegro", MetronomeEngine.tempoName(140))
        assertEquals("Presto", MetronomeEngine.tempoName(200))
    }

    @Test
    fun steppingStaysInsideTheRange() {
        assertEquals(125, MetronomeEngine.step(120, 5))
        assertEquals(300, MetronomeEngine.step(298, 5))
        assertEquals(30, MetronomeEngine.step(32, -5))
    }
}
