package dev.droidos.shell.apps.digest

import java.security.MessageDigest
import java.util.Base64

/**
 * Text hashing and Base64 through the platform's own JCA providers - nothing is hand rolled and
 * nothing is stubbed. Pure, so the known-answer vectors are unit-tested on the JVM.
 */
object DigestEngine {

    enum class Algorithm(val label: String) {
        SHA256("SHA-256"), SHA1("SHA-1"), MD5("MD5")
    }

    fun digest(text: String, algorithm: Algorithm): String =
        toHex(MessageDigest.getInstance(algorithm.label).digest(utf8(text)))

    fun toHex(bytes: ByteArray): String {
        val out = StringBuilder(bytes.size * 2)
        for (byte in bytes) {
            val value = byte.toInt() and 0xFF
            out.append(HEX[value ushr 4]).append(HEX[value and 0x0F])
        }
        return out.toString()
    }

    fun utf8(text: String): ByteArray = text.toByteArray(Charsets.UTF_8)

    fun byteCount(text: String): Int = utf8(text).size

    fun base64(text: String): String = Base64.getEncoder().encodeToString(utf8(text))

    sealed interface Decoded {
        data class Text(val value: String) : Decoded
        data class Failed(val reason: String) : Decoded
    }

    /** Decodes Base64 and reports failure instead of guessing at replacement characters. */
    fun decodeBase64(value: String): Decoded {
        val trimmed = value.filterNot { it.isWhitespace() }
        if (trimmed.isEmpty()) return Decoded.Failed("nothing to decode")
        return try {
            val bytes = Base64.getDecoder().decode(trimmed)
            Decoded.Text(String(bytes, Charsets.UTF_8))
        } catch (e: IllegalArgumentException) {
            Decoded.Failed("not valid Base64")
        }
    }

    fun uuid(): String = java.util.UUID.randomUUID().toString()

    private const val HEX = "0123456789abcdef"
}
