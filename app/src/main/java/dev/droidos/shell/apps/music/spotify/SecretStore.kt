package dev.droidos.shell.apps.music.spotify

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts secrets with an AES-256-GCM key that never leaves the Android Keystore, then stores
 * the ciphertext in private preferences. A token is never written to disk in the clear.
 */
class SecretStore(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun put(name: String, value: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        val body = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val packed = ByteArray(iv.size + body.size)
        iv.copyInto(packed, 0)
        body.copyInto(packed, iv.size)
        prefs.edit().putString(name, Base64.encodeToString(packed, Base64.NO_WRAP)).apply()
    }

    fun get(name: String): String? {
        val stored = prefs.getString(name, null) ?: return null
        return runCatching {
            val packed = Base64.decode(stored, Base64.NO_WRAP)
            if (packed.size <= IV_BYTES) return@runCatching null
            val iv = packed.copyOfRange(0, IV_BYTES)
            val body = packed.copyOfRange(IV_BYTES, packed.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv))
            String(cipher.doFinal(body), Charsets.UTF_8)
        }.getOrNull()
    }

    fun remove(name: String) {
        prefs.edit().remove(name).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val PREFS = "droidos.secrets"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "droidos.secrets.key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
