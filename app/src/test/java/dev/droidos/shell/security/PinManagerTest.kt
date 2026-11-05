package dev.droidos.shell.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinManagerTest {

    private val iterations = 1000

    @Test
    fun `hash verifies with the correct pin`() {
        val salt = PinManager.newSalt()
        val hash = PinManager.hash("482913".toCharArray(), salt, iterations)
        assertTrue(PinManager.verify("482913".toCharArray(), salt, hash, iterations))
    }

    @Test
    fun `wrong pin does not verify`() {
        val salt = PinManager.newSalt()
        val hash = PinManager.hash("482913".toCharArray(), salt, iterations)
        assertFalse(PinManager.verify("482914".toCharArray(), salt, hash, iterations))
    }

    @Test
    fun `same pin with different salt yields a different hash`() {
        val first = PinManager.hash("123456".toCharArray(), PinManager.newSalt(), iterations)
        val second = PinManager.hash("123456".toCharArray(), PinManager.newSalt(), iterations)
        assertFalse(first.contentEquals(second))
    }

    @Test
    fun `hash is deterministic for a fixed salt`() {
        val salt = PinManager.newSalt()
        val first = PinManager.hash("998877".toCharArray(), salt, iterations)
        val second = PinManager.hash("998877".toCharArray(), salt, iterations)
        assertArrayEquals(first, second)
    }

    @Test
    fun `hex encoding round trips`() {
        val bytes = byteArrayOf(0, 15, 127, -1, -128, 42)
        val encoded = PinManager.encode(bytes)
        val decoded = PinManager.decode(encoded)
        assertArrayEquals(bytes, decoded)
        assertEquals(12, encoded.length)
    }

    @Test
    fun `invalid hex is rejected`() {
        assertEquals(null, PinManager.decode("abc"))
        assertEquals(null, PinManager.decode("zz"))
    }

    @Test
    fun `pin validation enforces policy`() {
        assertEquals(PinValidation.TooShort, PinManager.validate("123"))
        assertEquals(PinValidation.NotNumeric, PinManager.validate("12a5"))
        assertEquals(PinValidation.TooRepetitive, PinManager.validate("66666"))
        assertEquals(PinValidation.TooCommon, PinManager.validate("1234"))
        assertEquals(PinValidation.TooCommon, PinManager.validate("0000"))
        assertEquals(PinValidation.Valid, PinManager.validate("482913"))
    }

    @Test
    fun `lockout grows and is capped`() {
        assertEquals(0L, PinManager.lockoutDurationMillis(0))
        assertEquals(0L, PinManager.lockoutDurationMillis(4))
        assertEquals(30_000L, PinManager.lockoutDurationMillis(5))
        assertEquals(60_000L, PinManager.lockoutDurationMillis(6))
        assertTrue(PinManager.lockoutDurationMillis(20) <= 300_000L)
    }

    @Test
    fun `salts are random`() {
        assertNotEquals(PinManager.encode(PinManager.newSalt()), PinManager.encode(PinManager.newSalt()))
        assertEquals(16, PinManager.newSalt().size)
    }
}
