package dev.droidos.shell.apps.generator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.log2

class GeneratorEngineTest {

    private val allSets = setOf(SymbolSet.LOWER, SymbolSet.UPPER, SymbolSet.DIGITS, SymbolSet.SYMBOLS)

    @Test
    fun everySelectedPoolIsRepresented() {
        val options = PasswordOptions(length = 24, sets = allSets)
        repeat(25) {
            val value = GeneratorEngine.generate(options)
            assertEquals(24, value.length)
            assertTrue(value.any { char -> char in SymbolSet.LOWER.pool })
            assertTrue(value.any { char -> char in SymbolSet.UPPER.pool })
            assertTrue(value.any { char -> char in SymbolSet.DIGITS.pool })
            assertTrue(value.any { char -> char in SymbolSet.SYMBOLS.pool })
        }
    }

    @Test
    fun onlyTheSelectedPoolIsUsed() {
        val value = GeneratorEngine.generate(PasswordOptions(length = 12, sets = setOf(SymbolSet.DIGITS)))
        assertTrue(value.all { it in SymbolSet.DIGITS.pool })
    }

    @Test
    fun lookAlikeGlyphsStayOutOfThePools() {
        assertTrue('l' !in SymbolSet.LOWER.pool)
        assertTrue('O' !in SymbolSet.UPPER.pool)
        assertTrue('0' !in SymbolSet.DIGITS.pool)
        assertTrue('1' !in SymbolSet.DIGITS.pool)
    }

    @Test
    fun lengthIsClampedToTheSupportedRange() {
        assertEquals(
            GeneratorEngine.MIN_LENGTH,
            GeneratorEngine.generate(PasswordOptions(length = 1, sets = allSets)).length
        )
        assertEquals(
            GeneratorEngine.MAX_LENGTH,
            GeneratorEngine.generate(PasswordOptions(length = 500, sets = allSets)).length
        )
    }

    @Test
    fun nothingSelectedProducesNoPassword() {
        assertEquals("", GeneratorEngine.generate(PasswordOptions(length = 12, sets = emptySet())))
        assertEquals(0.0, GeneratorEngine.entropyBits(PasswordOptions(length = 12, sets = emptySet())), 1e-9)
    }

    @Test
    fun entropyIsLengthTimesLog2OfThePool() {
        val options = PasswordOptions(length = 10, sets = setOf(SymbolSet.LOWER))
        assertEquals(10 * log2(25.0), GeneratorEngine.entropyBits(options), 0.001)
        assertEquals(25, GeneratorEngine.pool(options).length)
        assertTrue(GeneratorEngine.entropyBits(PasswordOptions(length = 20, sets = allSets)) > 100.0)
    }

    @Test
    fun strengthLabelsStepUpWithEntropy() {
        assertEquals("Nothing selected", GeneratorEngine.strengthLabel(0.0))
        assertEquals("Weak", GeneratorEngine.strengthLabel(30.0))
        assertEquals("Reasonable", GeneratorEngine.strengthLabel(60.0))
        assertEquals("Strong", GeneratorEngine.strengthLabel(85.0))
        assertEquals("Very strong", GeneratorEngine.strengthLabel(140.0))
    }

    @Test
    fun diceStayWithinTheirFacesAndAreRepeatableWhenSeeded() {
        val rolls = GeneratorEngine.rollDice(5, 6)
        assertEquals(5, rolls.size)
        assertTrue(rolls.all { it in 1..6 })
        assertEquals(
            GeneratorEngine.rollDice(3, 6, Random(7)),
            GeneratorEngine.rollDice(3, 6, Random(7))
        )
        assertEquals(20, GeneratorEngine.rollDice(100, 6).size)
        assertTrue(GeneratorEngine.rollDice(4, 1).all { it in 1..2 })
        assertEquals(10, GeneratorEngine.diceTotal(listOf(2, 3, 5)))
    }
}
