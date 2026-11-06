package dev.droidos.shell.apps.level

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LevelMathTest {

    @Test
    fun flatOnItsBackIsZeroOnBothAxes() {
        val tilt = LevelMath.tilt(floatArrayOf(0f, 0f, 9.81f))!!
        assertEquals(0f, tilt.pitch, 1e-3f)
        assertEquals(0f, tilt.roll, 1e-3f)
    }

    @Test
    fun standingUpIsNinetyDegreesPitch() {
        val tilt = LevelMath.tilt(floatArrayOf(9.81f, 0f, 0f))!!
        assertEquals(-90f, tilt.pitch, 1e-3f)
        assertEquals(0f, tilt.roll, 1e-3f)
    }

    @Test
    fun onItsSideIsNinetyDegreesRoll() {
        val tilt = LevelMath.tilt(floatArrayOf(0f, 9.81f, 0f))!!
        assertEquals(0f, tilt.pitch, 1e-3f)
        assertEquals(90f, tilt.roll, 1e-3f)
    }

    @Test
    fun weightlessInputHasNoAngle() {
        assertNull(LevelMath.tilt(floatArrayOf(0f, 0f, 0f)))
        assertNull(LevelMath.tilt(floatArrayOf(0.1f, 0.1f, 0.1f)))
        assertNull(LevelMath.tilt(floatArrayOf(1f, 2f)))
    }

    @Test
    fun levelToleranceUsesTheWorstAxis() {
        assertTrue(LevelMath.isLevel(Tilt(0.4f, -1.2f)))
        assertFalse(LevelMath.isLevel(Tilt(3f, 0f)))
        assertFalse(LevelMath.isLevel(Tilt(0f, -2.5f)))
        assertTrue(LevelMath.isLevel(Tilt(2.9f, 0f), toleranceDegrees = 3f))
    }

    @Test
    fun bubbleFollowsAnglesAndClampsToTheRim() {
        val (x1, y1) = LevelMath.bubbleOffset(Tilt(pitch = 20f, roll = 0f))
        assertEquals(0f, x1, 1e-4f)
        assertEquals(1f, y1, 1e-4f)

        val (x2, y2) = LevelMath.bubbleOffset(Tilt(pitch = 0f, roll = -10f))
        assertEquals(-0.5f, x2, 1e-4f)
        assertEquals(0f, y2, 1e-4f)

        val (x3, y3) = LevelMath.bubbleOffset(Tilt(pitch = 90f, roll = -90f))
        assertEquals(-1f, x3, 1e-4f)
        assertEquals(1f, y3, 1e-4f)
    }

    @Test
    fun directionNamesTheLean() {
        assertEquals("Level", LevelMath.direction(Tilt(0.1f, -0.2f)))
        assertEquals("Forward right", LevelMath.direction(Tilt(-5f, 5f)))
        assertEquals("Back left", LevelMath.direction(Tilt(5f, -6f)))
        assertEquals("Back", LevelMath.direction(Tilt(8f, 0.2f)))
    }

    @Test
    fun degreesAreSignedAndRoundedToOnePlace() {
        assertEquals("+12.3\u00B0", LevelMath.formatDegrees(12.34f))
        assertEquals("-4.5\u00B0", LevelMath.formatDegrees(-4.46f))
        assertEquals("0.0\u00B0", LevelMath.formatDegrees(0f))
    }
}
