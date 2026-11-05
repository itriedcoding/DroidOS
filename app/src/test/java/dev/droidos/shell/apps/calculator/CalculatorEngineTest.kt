package dev.droidos.shell.apps.calculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculatorEngineTest {

    private fun value(expression: String): Double {
        val result = CalculatorEngine.evaluate(expression)
        assertTrue("Expected value for '$expression' but got $result", result is CalculatorEngine.EvalResult.Value)
        return (result as CalculatorEngine.EvalResult.Value).value
    }

    private fun error(expression: String): String {
        val result = CalculatorEngine.evaluate(expression)
        assertTrue("Expected error for '$expression' but got $result", result is CalculatorEngine.EvalResult.Error)
        return (result as CalculatorEngine.EvalResult.Error).message
    }

    @Test
    fun `addition and subtraction are left associative`() {
        assertEquals(5.0, value("10-3-2"), 1e-9)
        assertEquals(9.0, value("2+3+4"), 1e-9)
    }

    @Test
    fun `multiplication binds tighter than addition`() {
        assertEquals(14.0, value("2+3*4"), 1e-9)
        assertEquals(20.0, value("(2+3)*4"), 1e-9)
    }

    @Test
    fun `division and modulo work`() {
        assertEquals(2.5, value("10/4"), 1e-9)
        assertEquals(1.0, value("10%3"), 1e-9)
    }

    @Test
    fun `power is right associative`() {
        assertEquals(512.0, value("2^3^2"), 1e-9)
    }

    @Test
    fun `unary minus applies to expressions`() {
        assertEquals(-6.0, value("-2*3"), 1e-9)
        assertEquals(4.0, value("7+-(3)"), 1e-9)
    }

    @Test
    fun `decimals and leading dot parse`() {
        assertEquals(1.5, value(".5+1"), 1e-9)
        assertEquals(0.1 + 0.2, value("0.1+0.2"), 1e-9)
    }

    @Test
    fun `division by zero is reported`() {
        assertEquals("Division by zero", error("1/0"))
    }

    @Test
    fun `unbalanced parentheses are reported`() {
        assertTrue(error("(1+2").isNotBlank())
        assertTrue(error("1+2)").isNotBlank())
    }

    @Test
    fun `garbage input is rejected`() {
        assertTrue(error("hello").isNotBlank())
        assertTrue(error("").isNotBlank())
        assertTrue(error("2++").isNotBlank())
    }

    @Test
    fun `format renders integers without decimals`() {
        assertEquals("3", CalculatorEngine.format(3.0))
        assertEquals("0", CalculatorEngine.format(0.0))
        assertEquals("2.5", CalculatorEngine.format(2.5))
    }

    @Test
    fun `display symbols are normalised`() {
        assertEquals(6.0, value("2×3"), 1e-9)
        assertEquals(3.0, value("9÷3"), 1e-9)
        assertEquals(1.0, value("4−3"), 1e-9)
    }
}
