package dev.droidos.shell.apps.generator

import java.security.SecureRandom
import java.util.Random
import kotlin.math.log2

/** Character pools, each excluding look-alike glyphs so a typed password stays unambiguous. */
enum class SymbolSet(val label: String, val pool: String) {
    LOWER("a-z", "abcdefghijkmnopqrstuvwxyz"),
    UPPER("A-Z", "ABCDEFGHJKLMNPQRSTUVWXYZ"),
    DIGITS("0-9", "23456789"),
    SYMBOLS("!@#", "!@#$%^&*()-_=+[]{}:,.?/")
}

data class PasswordOptions(
    val length: Int = 20,
    val sets: Set<SymbolSet> = setOf(SymbolSet.LOWER, SymbolSet.UPPER, SymbolSet.DIGITS, SymbolSet.SYMBOLS)
)

object GeneratorEngine {

    const val MIN_LENGTH = 4
    const val MAX_LENGTH = 64

    fun pool(options: PasswordOptions): String =
        options.sets.sortedBy { it.ordinal }.joinToString("") { it.pool }

    /**
     * Picks uniformly from the combined pool with java.security.SecureRandom, guarantees at
     * least one character from every selected set, then shuffles so those characters are not
     * stuck at the front. Returns an empty string when no set is selected.
     */
    fun generate(options: PasswordOptions, random: Random = SecureRandom()): String {
        val sets = options.sets.sortedBy { it.ordinal }
        if (sets.isEmpty()) return ""
        val length = options.length.coerceIn(MIN_LENGTH, MAX_LENGTH)
        val pool = sets.joinToString("") { it.pool }
        val chars = CharArray(length)
        val ordered = sets.take(length)
        ordered.forEachIndexed { index, set ->
            chars[index] = set.pool[random.nextInt(set.pool.length)]
        }
        for (index in ordered.size until length) {
            chars[index] = pool[random.nextInt(pool.length)]
        }
        for (index in chars.lastIndex downTo 1) {
            val swap = random.nextInt(index + 1)
            val held = chars[index]
            chars[index] = chars[swap]
            chars[swap] = held
        }
        return String(chars)
    }

    /** Shannon entropy of a uniformly random password from the selected pools. */
    fun entropyBits(options: PasswordOptions): Double {
        val poolSize = pool(options).length
        if (poolSize <= 1) return 0.0
        return options.length * log2(poolSize.toDouble())
    }

    fun strengthLabel(bits: Double): String = when {
        bits <= 0.0 -> "Nothing selected"
        bits < 40.0 -> "Weak"
        bits < 70.0 -> "Reasonable"
        bits < 100.0 -> "Strong"
        else -> "Very strong"
    }

    fun rollDice(count: Int, sides: Int, random: Random = SecureRandom()): List<Int> {
        val rolls = count.coerceIn(1, 20)
        val faces = sides.coerceIn(2, 100)
        return List(rolls) { random.nextInt(faces) + 1 }
    }

    fun flipCoin(random: Random = SecureRandom()): Boolean = random.nextBoolean()

    fun diceTotal(rolls: List<Int>): Int = rolls.sum()
}
