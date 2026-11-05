package dev.droidos.shell.security

import android.content.Context
import android.content.pm.PackageManager
import dev.droidos.shell.core.AppLog
import dev.droidos.shell.data.SettingsRepository

/**
 * Runtime tamper detection. An app cannot implement kernel verified boot, but it can pin
 * the certificate that signed it on first launch and refuse to trust a later build signed
 * with a different identity - which is what re-packaging / malware injection looks like.
 */
class IntegrityVerifier(
    private val context: Context,
    private val settingsRepository: SettingsRepository
) {

    sealed interface Result {
        data class Trusted(val digest: String, val firstPin: Boolean) : Result
        data class Changed(val expected: String, val actual: String) : Result
        data class Unavailable(val reason: String) : Result
    }

    fun currentSignerDigest(): String? = try {
        val info = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_SIGNING_CERTIFICATES
        )
        val signers = info.signingInfo?.apkContentsSigners
        if (signers.isNullOrEmpty()) {
            null
        } else {
            java.security.MessageDigest.getInstance("SHA-256")
                .digest(signers[0].toByteArray())
                .joinToString("") { "%02x".format(it) }
        }
    } catch (e: Exception) {
        AppLog.e("Integrity", "Could not read signing certificate", e)
        null
    }

    suspend fun verify(): Result {
        val digest = currentSignerDigest()
            ?: return Result.Unavailable("Signing certificate could not be read")

        val pinned = settingsRepository.current().pinnedSignerDigest
        return when {
            pinned == null -> {
                settingsRepository.setPinnedSigner(digest)
                AppLog.i("Integrity", "Pinned application signing identity $digest")
                Result.Trusted(digest, firstPin = true)
            }
            pinned == digest -> Result.Trusted(digest, firstPin = false)
            else -> {
                AppLog.e("Integrity", "Signing identity changed! expected=$pinned actual=$digest")
                Result.Changed(expected = pinned, actual = digest)
            }
        }
    }
}
