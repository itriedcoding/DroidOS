package dev.droidos.shell.security

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Chunked AES-256-GCM container used by the Vault.
 *
 * Layout:  magic[5] | baseNonce[8] | chunkSize[4] | chunks...
 * Each chunk is ciphertext||tag (16 bytes) sealed with a 12-byte nonce built from the
 * random base nonce plus a monotonically increasing 32-bit counter, so a large file is
 * streamed without ever holding it in memory and reordering/truncation is detected by
 * the GCM tags.
 *
 * Pure JCE: no Android dependency, therefore fully unit-tested on the JVM.
 */
object VaultFormat {

    private val MAGIC = byteArrayOf('D'.code.toByte(), 'O'.code.toByte(), 'S'.code.toByte(), 'V'.code.toByte(), 1)
    const val CHUNK_SIZE = 64 * 1024
    private const val TAG_BITS = 128
    private const val TAG_BYTES = TAG_BITS / 8
    private const val BASE_NONCE_BYTES = 8
    private const val MAX_CHUNK = 4 * 1024 * 1024

    private val random = SecureRandom()

    fun isEncrypted(bytes: ByteArray): Boolean =
        bytes.size >= MAGIC.size + BASE_NONCE_BYTES + 4 &&
            (0 until MAGIC.size).all { bytes[it] == MAGIC[it] }

    fun encrypt(source: InputStream, target: OutputStream, key: SecretKey, chunkSize: Int = CHUNK_SIZE) {
        require(chunkSize in 1..MAX_CHUNK) { "chunkSize out of range" }
        val baseNonce = ByteArray(BASE_NONCE_BYTES).also { random.nextBytes(it) }
        target.write(MAGIC)
        target.write(baseNonce)
        writeInt(target, chunkSize)

        val buffer = ByteArray(chunkSize)
        var counter = 0
        while (true) {
            val read = source.readFully(buffer)
            if (read <= 0) break
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonceFor(baseNonce, counter)))
            val sealed = cipher.doFinal(buffer, 0, read)
            target.write(sealed)
            counter++
        }
        target.flush()
    }

    fun decrypt(source: InputStream, target: OutputStream, key: SecretKey) {
        val header = ByteArray(MAGIC.size + BASE_NONCE_BYTES + 4)
        if (source.readFully(header) < header.size) throw IOException("Not a DroidOS vault file")
        for (index in MAGIC.indices) {
            if (header[index] != MAGIC[index]) throw IOException("Not a DroidOS vault file")
        }
        val baseNonce = header.copyOfRange(MAGIC.size, MAGIC.size + BASE_NONCE_BYTES)
        val chunkSize = readInt(header, MAGIC.size + BASE_NONCE_BYTES)
        if (chunkSize !in 1..MAX_CHUNK) throw IOException("Corrupt vault header")

        val sealedBuffer = ByteArray(chunkSize + TAG_BYTES)
        var counter = 0
        while (true) {
            val read = source.readFully(sealedBuffer)
            if (read <= 0) break
            if (read <= TAG_BYTES) throw IOException("Truncated vault chunk")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonceFor(baseNonce, counter)))
            target.write(cipher.doFinal(sealedBuffer, 0, read))
            counter++
        }
        target.flush()
    }

    fun encryptFile(source: File, target: File, key: SecretKey) {
        source.inputStream().use { input ->
            target.outputStream().buffered().use { output ->
                encrypt(input, output, key)
            }
        }
    }

    fun decryptFile(source: File, target: File, key: SecretKey) {
        source.inputStream().buffered().use { input ->
            target.outputStream().use { output ->
                decrypt(input, output, key)
            }
        }
    }

    /** Derives the 12-byte GCM nonce for a chunk from the base nonce and the counter. */
    fun nonceFor(baseNonce: ByteArray, counter: Int): ByteArray {
        require(baseNonce.size == BASE_NONCE_BYTES)
        val nonce = ByteArray(12)
        baseNonce.copyInto(nonce, 0, 0, BASE_NONCE_BYTES)
        nonce[8] = (counter ushr 24).toByte()
        nonce[9] = (counter ushr 16).toByte()
        nonce[10] = (counter ushr 8).toByte()
        nonce[11] = counter.toByte()
        return nonce
    }

    /** Helper for tests and for wrapping a raw key without Android Keystore. */
    fun rawKey(bytes: ByteArray): SecretKey {
        require(bytes.size == 32) { "AES-256 requires a 32-byte key" }
        return SecretKeySpec(bytes, "AES")
    }

    fun newRawKey(): ByteArray = ByteArray(32).also { random.nextBytes(it) }

    private fun InputStream.readFully(buffer: ByteArray): Int {
        var offset = 0
        while (offset < buffer.size) {
            val read = read(buffer, offset, buffer.size - offset)
            if (read < 0) break
            offset += read
        }
        return offset
    }

    private fun writeInt(target: OutputStream, value: Int) {
        target.write(byteArrayOf(
            (value ushr 24).toByte(),
            (value ushr 16).toByte(),
            (value ushr 8).toByte(),
            value.toByte()
        ))
    }

    private fun readInt(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)
}
