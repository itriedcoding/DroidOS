package dev.droidos.shell.shell.controls

import org.junit.Assert.assertEquals
import org.junit.Test

class VolumeScaleTest {

    @Test
    fun `bounds map to the ends of the slider`() {
        assertEquals(0f, VolumeScale.percent(0, 15), 0.0001f)
        assertEquals(1f, VolumeScale.percent(15, 15), 0.0001f)
    }

    @Test
    fun `fractions land on the nearest step`() {
        assertEquals(8, VolumeScale.index(0.5f, 15))
        assertEquals(7, VolumeScale.index(0.45f, 15))
        assertEquals(15, VolumeScale.index(1f, 15))
    }

    @Test
    fun `out of range input is clamped instead of overflowing`() {
        assertEquals(0, VolumeScale.index(-0.4f, 15))
        assertEquals(15, VolumeScale.index(3.5f, 15))
        assertEquals(0f, VolumeScale.percent(-9, 15), 0.0001f)
        assertEquals(1f, VolumeScale.percent(99, 15), 0.0001f)
    }

    @Test
    fun `a stream without steps never divides by zero`() {
        assertEquals(0, VolumeScale.index(0.7f, 0))
        assertEquals(0f, VolumeScale.percent(4, 0), 0.0001f)
        assertEquals(0, VolumeScale.index(0.7f, -3))
    }

    @Test
    fun `labels report the real percentage`() {
        assertEquals("50%", VolumeScale.label(5, 10))
        assertEquals("100%", VolumeScale.label(10, 10))
        assertEquals("0%", VolumeScale.label(0, 10))
    }
}
