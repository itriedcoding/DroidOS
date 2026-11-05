package dev.droidos.shell.apps.compass

import org.junit.Assert.assertEquals
import org.junit.Test

class CompassEngineTest {

    @Test
    fun normaliseWrapsIntoOneTurn() {
        assertEquals(330f, CompassEngine.normalize(-30f), 1e-4f)
        assertEquals(10f, CompassEngine.normalize(370f), 1e-4f)
        assertEquals(0f, CompassEngine.normalize(360f), 1e-4f)
        assertEquals(180f, CompassEngine.normalize(-180f), 1e-4f)
    }

    @Test
    fun cardinalNamesUseTheSixteenPointRose() {
        assertEquals("N", CompassEngine.cardinal(0f))
        assertEquals("N", CompassEngine.cardinal(349f))
        assertEquals("NNE", CompassEngine.cardinal(22.5f))
        assertEquals("E", CompassEngine.cardinal(90f))
        assertEquals("S", CompassEngine.cardinal(180f))
        assertEquals("W", CompassEngine.cardinal(270f))
        assertEquals("NW", CompassEngine.cardinal(315f))
    }

    @Test
    fun shortestDeltaCrossesNorthInBothDirections() {
        assertEquals(20f, CompassEngine.shortestDelta(350f, 10f), 1e-4f)
        assertEquals(-20f, CompassEngine.shortestDelta(10f, 350f), 1e-4f)
        assertEquals(90f, CompassEngine.shortestDelta(0f, 90f), 1e-4f)
        assertEquals(180f, CompassEngine.shortestDelta(0f, 180f), 1e-4f)
    }

    @Test
    fun smoothingTakesTheShortPathAcrossNorth() {
        assertEquals(45f, CompassEngine.smooth(0f, 90f, 0.5f), 1e-4f)
        assertEquals(0f, CompassEngine.smooth(350f, 10f, 0.5f), 1e-4f)
        assertEquals(250f, CompassEngine.smooth(300f, 200f, 0.5f), 1e-4f)
    }

    @Test
    fun firstSampleIsUsedAsIs() {
        assertEquals(123f, CompassEngine.smooth(null, 123f), 1e-4f)
        assertEquals(300f, CompassEngine.smooth(null, -60f), 1e-4f)
    }

    @Test
    fun accuracyUsesTheSensorStatusValues() {
        assertEquals("High", CompassEngine.accuracyLabel(3))
        assertEquals("Medium", CompassEngine.accuracyLabel(2))
        assertEquals("Low", CompassEngine.accuracyLabel(1))
        assertEquals("Unreliable", CompassEngine.accuracyLabel(0))
    }
}
