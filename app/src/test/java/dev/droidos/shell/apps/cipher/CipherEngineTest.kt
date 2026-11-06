package dev.droidos.shell.apps.cipher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class CipherEngineTest {

    private val passphrase = "correct horse battery".toCharArray()

    @Test
    fun roundTripsTextIncludingUnicode() {
        val plain = "DroidOS cipher - 100% local. \u00E4\u00F6\u00FC \u4F60\u597D"
        val payload = CipherEngine.encrypt(plain, passphrase)
        assertTrue(payload.startsWith("v1:"))
        assertEquals(plain, CipherEngine.decrypt(payload, passphrase))
    }

    @Test
    fun everyEncryptionUsesAFreshIv() {
        val first = CipherEngine.encrypt("same input", passphrase)
        val second = CipherEngine.encrypt("same input", passphrase)
        assertNotEquals(first, second)
        assertEquals("same input", CipherEngine.decrypt(first, passphrase))
        assertEquals("same input", CipherEngine.decrypt(second, passphrase))
    }

    @Test(expected = IllegalArgumentException::class)
    fun wrongPassphraseIsRejected() {
        val payload = CipherEngine.encrypt("secret", passphrase)
        CipherEngine.decrypt(payload, "wrong horse battery".toCharArray())
    }

    @Test(expected = IllegalArgumentException::class)
    fun tamperedCiphertextIsRejectedByTheTag() {
        val payload = CipherEngine.encrypt("secret", passphrase)
        val raw = Base64.getDecoder().decode(payload.removePrefix("v1:"))
        raw[raw.lastIndex] = (raw[raw.lastIndex].toInt() xor 0x01).toByte()
        val tampered = "v1:" + Base64.getEncoder().encodeToString(raw)
        CipherEngine.decrypt(tampered, passphrase)
    }

    @Test(expected = IllegalArgumentException::class)
    fun emptyPassphraseIsRefused() {
        CipherEngine.encrypt("secret", CharArray(0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun payloadWithoutPrefixIsRefused() {
        CipherEngine.decrypt(Base64.getEncoder().encodeToString(ByteArray(64)), passphrase)
    }

    @Test(expected = IllegalArgumentException::class)
    fun truncatedPayloadIsRefused() {
        CipherEngine.decrypt("v1:AAAA", passphrase)
    }

    @Test
    fun payloadDetectionMatchesOutputShape() {
        assertTrue(CipherEngine.looksLikePayload(CipherEngine.encrypt("x", passphrase)))
        assertFalse(CipherEngine.looksLikePayload("just some text"))
    }

    @Test
    fun emptyPlaintextStillProducesAVerifiablePayload() {
        val payload = CipherEngine.encrypt("", passphrase)
        assertEquals("", CipherEngine.decrypt(payload, passphrase))
    }
}
