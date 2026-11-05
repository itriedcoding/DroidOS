package dev.droidos.shell.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import dev.droidos.shell.core.AppLog
import dev.droidos.shell.core.Outcome
import dev.droidos.shell.core.attempt
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Hardware-backed cryptography. The AES-256 key is generated inside the Android Keystore
 * and never leaves it; the app only ever holds a handle. Files are sealed with the
 * chunked AES-GCM container in [VaultFormat].
 */
class CryptoManager(private val context: Context) {

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "droidos_vault_key"
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }

    /**
     * Returns the vault key, creating it on first use. Randomized encryption is disabled so
     * the container can supply its own unique per-chunk nonces (base nonce + counter).
     */
    fun vaultKey(): SecretKey {
        val store = keyStore()
        (store.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        AppLog.i("Crypto", "Generating AES-256 vault key in Android Keystore")
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(false)
                .setUserAuthenticationRequired(false)
                .build()
        )
        return generator.generateKey()
    }

    fun hasVaultKey(): Boolean = runCatching { keyStore().containsAlias(ALIAS) }.getOrDefault(false)

    fun encryptFile(source: File, target: File): Outcome<File> = attempt("Encrypt ${source.name}") {
        target.parentFile?.mkdirs()
        VaultFormat.encryptFile(source, target, vaultKey())
        target
    }

    fun decryptFile(source: File, target: File): Outcome<File> = attempt("Decrypt ${source.name}") {
        target.parentFile?.mkdirs()
        VaultFormat.decryptFile(source, target, vaultKey())
        target
    }

    fun encryptText(plain: String): Outcome<String> = attempt("Encrypt text") {
        val tempIn = File.createTempFile("plain", ".tmp", context.cacheDir)
        val tempOut = File.createTempFile("sealed", ".tmp", context.cacheDir)
        try {
            tempIn.writeText(plain)
            VaultFormat.encryptFile(tempIn, tempOut, vaultKey())
            android.util.Base64.encodeToString(tempOut.readBytes(), android.util.Base64.NO_WRAP)
        } finally {
            tempIn.delete()
            tempOut.delete()
        }
    }

    fun decryptText(encoded: String): Outcome<String> = attempt("Decrypt text") {
        val tempIn = File.createTempFile("sealed", ".tmp", context.cacheDir)
        val tempOut = File.createTempFile("plain", ".tmp", context.cacheDir)
        try {
            tempIn.writeBytes(android.util.Base64.decode(encoded, android.util.Base64.NO_WRAP))
            VaultFormat.decryptFile(tempIn, tempOut, vaultKey())
            tempOut.readText()
        } finally {
            tempIn.delete()
            tempOut.delete()
        }
    }

    fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** Deletes the vault key, making every sealed file permanently unrecoverable. */
    fun destroyVaultKey(): Outcome<Unit> = attempt("Destroy vault key") {
        keyStore().deleteEntry(ALIAS)
        Unit
    }
}
