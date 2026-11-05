package dev.droidos.shell.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class VaultFormatTest {

    private fun newKey(): SecretKey =
        KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    private fun seal(payload: ByteArray, key: SecretKey, chunkSize: Int = VaultFormat.CHUNK_SIZE): ByteArray {
        val out = ByteArrayOutputStream()
        VaultFormat.encrypt(ByteArrayInputStream(payload), out, key, chunkSize)
        return out.toByteArray()
    }

    private fun open(sealed: ByteArray, key: SecretKey): ByteArray {
        val out = ByteArrayOutputStream()
        VaultFormat.decrypt(ByteArrayInputStream(sealed), out, key)
        return out.toByteArray()
    }

    @Test
    fun `round trips a small payload`() {
        val key = newKey()
        val payload = "DroidOS vault secret".toByteArray()
        assertArrayEquals(payload, open(seal(payload, key), key))
    }

    @Test
    fun `round trips a payload spanning many chunks`() {
        val key = newKey()
        val payload = ByteArray(200_000) { (it % 251).toByte() }
        assertArrayEquals(payload, open(seal(payload, key, chunkSize = 4096), key))
    }

    @Test
    fun `round trips an empty payload`() {
        val key = newKey()
        assertArrayEquals(ByteArray(0), open(seal(ByteArray(0), key), key))
    }

    @Test
    fun `ciphertext does not contain the plaintext`() {
        val key = newKey()
        val payload = "SUPER_SECRET_MARKER".toByteArray()
        val sealed = seal(payload, key)
        assertFalse(String(sealed, Charsets.ISO_8859_1).contains("SUPER_SECRET_MARKER"))
    }

    @Test
    fun `detects the container magic`() {
        val key = newKey()
        val sealed = seal("hello".toByteArray(), key)
        assertTrue(VaultFormat.isEncrypted(sealed))
        assertFalse(VaultFormat.isEncrypted("hello".toByteArray()))
    }

    @Test
    fun `rejects tampered ciphertext`() {
        val key = newKey()
        val sealed = seal("payload that must not be silently altered".toByteArray(), key)
        sealed[sealed.size - 1] = (sealed[sealed.size - 1].toInt() xor 0xFF).toByte()

        var failed = false
        try {
            open(sealed, key)
        } catch (e: Exception) {
            failed = true
        }
        assertTrue("Tampered ciphertext must not decrypt", failed)
    }

    @Test
    fun `rejects a different key`() {
        val sealed = seal("secret".toByteArray(), newKey())
        var failed = false
        try {
            open(sealed, newKey())
        } catch (e: Exception) {
            failed = true
        }
        assertTrue("Wrong key must not decrypt", failed)
    }

    @Test
    fun `rejects truncated payload`() {
        val key = newKey()
        val sealed = seal(ByteArray(8192) { 7 }, key, chunkSize = 1024)
        val truncated = sealed.copyOf(sealed.size - 20)
        var failed = false
        try {
            open(truncated, key)
        } catch (e: Exception) {
            failed = true
        }
        assertTrue("Truncated container must not decrypt", failed)
    }

    @Test
    fun `nonce derivation is unique per chunk and deterministic`() {
        val base = ByteArray(8) { it.toByte() }
        val first = VaultFormat.nonceFor(base, 0)
        val second = VaultFormat.nonceFor(base, 1)
        assertEquals12(first)
        assertFalse(first.contentEquals(second))
        assertArrayEquals(first, VaultFormat.nonceFor(base, 0))
    }

    private fun assertEquals12(nonce: ByteArray) {
        assertTrue(nonce.size == 12)
    }
}
