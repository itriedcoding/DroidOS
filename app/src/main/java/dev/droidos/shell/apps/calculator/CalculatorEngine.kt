package dev.droidos.shell.apps.calculator

import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow

/**
 * Recursive-descent expression evaluator: + - * / % ^, parentheses, unary sign and
 * decimals. Pure Kotlin, fully unit-tested, no evaluation library and no scripting engine.
 */
object CalculatorEngine {

    sealed interface EvalResult {
        data class Value(val value: Double) : EvalResult
        data class Error(val message: String) : EvalResult
    }

    fun evaluate(expression: String): EvalResult {
        val cleaned = expression.trim().replace('×', '*').replace('÷', '/').replace('−', '-')
        if (cleaned.isBlank()) return EvalResult.Error("Empty expression")
        return try {
            val parser = Parser(cleaned)
            val value = parser.parseExpression()
            parser.expectEnd()
            if (value.isNaN() || value.isInfinite()) EvalResult.Error("Result is not a number")
            else EvalResult.Value(value)
        } catch (e: IllegalArgumentException) {
            EvalResult.Error(e.message ?: "Invalid expression")
        } catch (e: ArithmeticException) {
            EvalResult.Error("Division by zero")
        }
    }

    fun format(value: Double): String {
        if (value == 0.0) return "0"
        val magnitude = abs(value)
        if (magnitude >= 1e12 || magnitude < 1e-6) {
            return String.format(Locale.US, "%.6e", value)
        }
        val rounded = Math.round(value * 1e10) / 1e10
        return if (rounded == rounded.toLong().toDouble()) {
            rounded.toLong().toString()
        } else {
            rounded.toString().trimEnd('0').trimEnd('.')
        }
    }

    private class Parser(private val text: String) {
        private var index = 0

        fun parseExpression(): Double {
            var value = parseTerm()
            while (true) {
                skipSpaces()
                when (peek()) {
                    '+' -> { index++; value += parseTerm() }
                    '-' -> { index++; value -= parseTerm() }
                    else -> return value
                }
            }
        }

        private fun parseTerm(): Double {
            var value = parseFactor()
            while (true) {
                skipSpaces()
                when (peek()) {
                    '*' -> { index++; value *= parseFactor() }
                    '/' -> {
                        index++
                        val divisor = parseFactor()
                        if (divisor == 0.0) throw ArithmeticException("Division by zero")
                        value /= divisor
                    }
                    '%' -> {
                        index++
                        val divisor = parseFactor()
                        if (divisor == 0.0) throw ArithmeticException("Division by zero")
                        value %= divisor
                    }
                    else -> return value
                }
            }
        }

        private fun parseFactor(): Double {
            val base = parseUnary()
            skipSpaces()
            if (peek() == '^') {
                index++
                return base.pow(parseFactor())
            }
            return base
        }

        private fun parseUnary(): Double {
            skipSpaces()
            return when (peek()) {
                '-' -> { index++; -parseUnary() }
                '+' -> { index++; parseUnary() }
                else -> parsePrimary()
            }
        }

        private fun parsePrimary(): Double {
            skipSpaces()
            if (peek() == '(') {
                index++
                val value = parseExpression()
                skipSpaces()
                if (peek() != ')') throw IllegalArgumentException("Missing closing parenthesis")
                index++
                return value
            }
            return parseNumber()
        }

        private fun parseNumber(): Double {
            skipSpaces()
            val start = index
            var seenDot = false
            while (index < text.length) {
                val c = text[index]
                if (c.isDigit()) {
                    index++
                } else if (c == '.' && !seenDot) {
                    seenDot = true
                    index++
                } else {
                    break
                }
            }
            if (start == index) throw IllegalArgumentException("Expected a number at position ${index + 1}")
            val raw = text.substring(start, index)
            return raw.toDoubleOrNull() ?: throw IllegalArgumentException("Invalid number '$raw'")
        }

        fun expectEnd() {
            skipSpaces()
            if (index < text.length) {
                throw IllegalArgumentException("Unexpected '${text[index]}' at position ${index + 1}")
            }
        }

        private fun peek(): Char? = if (index < text.length) text[index] else null

        private fun skipSpaces() {
            while (index < text.length && text[index].isWhitespace()) index++
        }
    }
}
