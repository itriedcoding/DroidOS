package dev.droidos.shell.apps.cipher

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Real AES-256-GCM with PBKDF2-HMAC-SHA256 key derivation. The payload is
 * base64(salt | iv | ciphertext+tag); nothing is stored anywhere, so decrypting on another
 * device only needs the same passphrase.
 */
object CipherEngine {

    const val ITERATIONS = 120_000
    private const val KEY_BITS = 256
    private const val TAG_BITS = 128
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val PREFIX = "v1:"

    private val random = SecureRandom()

    fun encrypt(plaintext: String, passphrase: CharArray): String {
        require(passphrase.isNotEmpty()) { "Enter a passphrase first" }
        val salt = ByteArray(SALT_BYTES).also { random.nextBytes(it) }
        val iv = ByteArray(IV_BYTES).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(TAG_BITS, iv))
        val body = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val packed = ByteArray(salt.size + iv.size + body.size)
        salt.copyInto(packed, 0)
        iv.copyInto(packed, salt.size)
        body.copyInto(packed, salt.size + iv.size)
        return PREFIX + Base64.getEncoder().encodeToString(packed)
    }

    /**
     * Throws IllegalArgumentException with a readable reason when the payload is not one of
     * ours, is truncated, or the passphrase does not authenticate the ciphertext.
     */
    fun decrypt(payload: String, passphrase: CharArray): String {
        require(passphrase.isNotEmpty()) { "Enter the passphrase used to encrypt" }
        val trimmed = payload.trim()
        require(trimmed.startsWith(PREFIX)) { "Not a Cipher payload - expected the v1: prefix" }
        val packed = try {
            Base64.getDecoder().decode(trimmed.removePrefix(PREFIX))
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Payload is not valid base64")
        }
        // An empty plaintext still carries the full 16-byte GCM tag, so this is the exact floor.
        require(packed.size >= SALT_BYTES + IV_BYTES + TAG_BITS / 8) { "Payload is too short to be valid" }
        val salt = packed.copyOfRange(0, SALT_BYTES)
        val iv = packed.copyOfRange(SALT_BYTES, SALT_BYTES + IV_BYTES)
        val body = packed.copyOfRange(SALT_BYTES + IV_BYTES, packed.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(TAG_BITS, iv))
        val plain = try {
            cipher.doFinal(body)
        } catch (e: Exception) {
            throw IllegalArgumentException("Wrong passphrase, or the payload was altered")
        }
        return String(plain, Charsets.UTF_8)
    }

    fun looksLikePayload(text: String): Boolean = text.trim().startsWith(PREFIX)

    private fun deriveKey(passphrase: CharArray, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(passphrase, salt, ITERATIONS, KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val raw = factory.generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(raw, "AES")
    }
}
