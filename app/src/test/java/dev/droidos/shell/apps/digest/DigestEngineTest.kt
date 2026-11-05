package dev.droidos.shell.apps.digest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DigestEngineTest {

    @Test
    fun knownVectorsMatchThePublishedDigests() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            DigestEngine.digest("abc", DigestEngine.Algorithm.SHA256)
        )
        assertEquals(
            "a9993e364706816aba3e25717850c26c9cd0d89d",
            DigestEngine.digest("abc", DigestEngine.Algorithm.SHA1)
        )
        assertEquals(
            "900150983cd24fb0d6963f7d28e17f72",
            DigestEngine.digest("abc", DigestEngine.Algorithm.MD5)
        )
    }

    @Test
    fun emptyInputStillHashes() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            DigestEngine.digest("", DigestEngine.Algorithm.SHA256)
        )
    }

    @Test
    fun hexIsAlwaysTwoCharactersPerByte() {
        assertEquals("00ff10", DigestEngine.toHex(byteArrayOf(0x00, 0xFF.toByte(), 0x10)))
        assertEquals(64, DigestEngine.digest("anything", DigestEngine.Algorithm.SHA256).length)
    }

    @Test
    fun base64RoundTripsThroughUtf8() {
        assertEquals("aGVsbG8=", DigestEngine.base64("hello"))
        assertEquals(2, DigestEngine.byteCount("é"))

        val decoded = DigestEngine.decodeBase64("aGVsbG8=")
        assertTrue(decoded is DigestEngine.Decoded.Text)
        assertEquals("hello", (decoded as DigestEngine.Decoded.Text).value)
    }

    @Test
    fun invalidBase64IsReportedNotGuessed() {
        val result = DigestEngine.decodeBase64("not base64 !!!")
        assertTrue(result is DigestEngine.Decoded.Failed)
        assertTrue(DigestEngine.decodeBase64("") is DigestEngine.Decoded.Failed)
    }

    @Test
    fun uuidHasTheExpectedShape() {
        val uuid = DigestEngine.uuid()
        assertEquals(36, uuid.length)
        assertEquals(5, uuid.split("-").size)
    }
}
