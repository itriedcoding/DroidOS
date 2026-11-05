package dev.droidos.shell.apps.convert

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConversionEngineTest {

    private fun unit(category: ConversionEngine.Category, id: String): ConversionEngine.Unit =
        ConversionEngine.units(category).first { it.id == id }

    private fun assertClose(expected: Double, actual: Double, tolerance: Double = 1e-9) {
        assertEquals(expected, actual, tolerance)
    }

    @Test
    fun temperatureUsesTheOffsetForm() {
        val c = unit(ConversionEngine.Category.TEMPERATURE, "c")
        val f = unit(ConversionEngine.Category.TEMPERATURE, "f")
        val k = unit(ConversionEngine.Category.TEMPERATURE, "k")

        assertClose(212.0, ConversionEngine.convert(100.0, c, f), 1e-9)
        assertClose(0.0, ConversionEngine.convert(32.0, f, c), 1e-9)
        assertClose(273.15, ConversionEngine.convert(0.0, c, k), 1e-9)
        assertClose(-40.0, ConversionEngine.convert(-40.0, c, f), 1e-9)
    }

    @Test
    fun lengthAndDataUseExactFactors() {
        assertClose(
            1000.0,
            ConversionEngine.convert(
                1.0,
                unit(ConversionEngine.Category.LENGTH, "km"),
                unit(ConversionEngine.Category.LENGTH, "m")
            )
        )
        assertClose(
            1073741824.0,
            ConversionEngine.convert(
                1.0,
                unit(ConversionEngine.Category.DATA, "gib"),
                unit(ConversionEngine.Category.DATA, "b")
            ),
            1e-3
        )
        assertClose(
            1000.0,
            ConversionEngine.convert(
                1.0,
                unit(ConversionEngine.Category.DATA, "kb"),
                unit(ConversionEngine.Category.DATA, "b")
            )
        )
    }

    @Test
    fun speedAndTimeConvertThroughTheirBase() {
        assertClose(
            27.777777777,
            ConversionEngine.convert(
                100.0,
                unit(ConversionEngine.Category.SPEED, "kmh"),
                unit(ConversionEngine.Category.SPEED, "mps")
            ),
            1e-6
        )
        assertClose(
            3600.0,
            ConversionEngine.convert(
                1.0,
                unit(ConversionEngine.Category.TIME, "h"),
                unit(ConversionEngine.Category.TIME, "s")
            )
        )
    }

    @Test
    fun roundingIsPreservedExactly() {
        assertClose(
            1.0,
            ConversionEngine.convert(
                1.0,
                unit(ConversionEngine.Category.MASS, "lb"),
                unit(ConversionEngine.Category.MASS, "lb")
            )
        )
    }

    @Test
    fun formatKeepsUsefulDigitsWithoutZeros() {
        assertEquals("1,000", ConversionEngine.format(1000.0))
        assertEquals("0.5", ConversionEngine.format(0.5))
        assertEquals("27.7778", ConversionEngine.format(27.7777777))
        assertEquals("0", ConversionEngine.format(0.0))
        assertEquals("-3.25", ConversionEngine.format(-3.25))
        assertEquals("-", ConversionEngine.format(Double.NaN))
    }

    @Test
    fun parseAcceptsGroupedDigitsAndRejectsWords() {
        assertEquals(1200.5, ConversionEngine.parse("1,200.5") ?: 0.0, 1e-9)
        assertEquals(42.0, ConversionEngine.parse(" +42 ") ?: 0.0, 1e-9)
        assertNull(ConversionEngine.parse("abc"))
        assertNull(ConversionEngine.parse("Infinity"))
    }
}
