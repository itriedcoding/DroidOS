package dev.droidos.shell.security

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * PIN storage: never the PIN itself, only a PBKDF2-HMAC-SHA256 derivation with a
 * 128-bit random salt. Verification is constant-time. Pure JCE so it is unit-testable
 * on the JVM and identical on device.
 */
object PinManager {

    const val DEFAULT_ITERATIONS = 120_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_BYTES = 16
    private const val MIN_PIN_LENGTH = 4
    private const val MAX_PIN_LENGTH = 12

    private val random = SecureRandom()

    /** Well-known PINs rejected before they can ever be stored. */
    private val COMMON_PINS = setOf(
        "0000", "1111", "2222", "3333", "4444", "5555", "6666", "7777", "8888", "9999",
        "1234", "12345", "123456", "1212", "1122", "4321", "654321", "000000"
    )

    fun newSalt(): ByteArray = ByteArray(SALT_BYTES).also { random.nextBytes(it) }

    fun hash(pin: CharArray, salt: ByteArray, iterations: Int = DEFAULT_ITERATIONS): ByteArray {
        val spec = PBEKeySpec(pin, salt, iterations, KEY_LENGTH_BITS)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    fun verify(
        pin: CharArray,
        salt: ByteArray,
        expected: ByteArray,
        iterations: Int = DEFAULT_ITERATIONS
    ): Boolean {
        val actual = hash(pin, salt, iterations)
        return MessageDigest.isEqual(actual, expected)
    }

    fun encode(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    fun decode(hex: String): ByteArray? {
        if (hex.length % 2 != 0) return null
        return runCatching {
            ByteArray(hex.length / 2) { index ->
                hex.substring(index * 2, index * 2 + 2).toInt(16).toByte()
            }
        }.getOrNull()
    }

    /** Rejects PINs that are trivially guessable before they are ever stored. */
    fun validate(pin: String): PinValidation {
        if (pin.length < MIN_PIN_LENGTH) return PinValidation.TooShort
        if (pin.length > MAX_PIN_LENGTH) return PinValidation.TooLong
        if (!pin.all { it.isDigit() }) return PinValidation.NotNumeric
        if (pin in COMMON_PINS) return PinValidation.TooCommon
        if (pin.toSet().size == 1) return PinValidation.TooRepetitive
        return PinValidation.Valid
    }

    /** Exponential lockout after repeated failures, capped at 5 minutes. */
    fun lockoutDurationMillis(failedAttempts: Int): Long = when {
        failedAttempts < 5 -> 0L
        else -> minOf(300_000L, 30_000L shl (failedAttempts - 5).coerceAtMost(4))
    }
}

sealed interface PinValidation {
    data object Valid : PinValidation
    data object TooShort : PinValidation
    data object TooLong : PinValidation
    data object NotNumeric : PinValidation
    data object TooRepetitive : PinValidation
    data object TooCommon : PinValidation

    val message: String
        get() = when (this) {
            Valid -> "PIN accepted"
            TooShort -> "Use at least 4 digits"
            TooLong -> "Use at most 12 digits"
            NotNumeric -> "Digits only"
            TooRepetitive -> "Avoid repeating one digit"
            TooCommon -> "That PIN is too easy to guess"
        }
}
