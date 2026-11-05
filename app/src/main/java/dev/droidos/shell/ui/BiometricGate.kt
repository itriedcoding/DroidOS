package dev.droidos.shell.ui

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import dev.droidos.shell.core.AppLog

/** Walks the context chain to the hosting activity BiometricPrompt requires. */
fun Context.findFragmentActivity(): FragmentActivity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is FragmentActivity) return current
        current = current.baseContext
    }
    return null
}

fun biometricStatus(context: Context): Int =
    BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)

/**
 * Shows the system biometric prompt whenever [trigger] changes to a non-zero value.
 * Results are delivered exactly once per trigger.
 */
@Composable
fun BiometricRequest(
    trigger: Int,
    title: String,
    subtitle: String,
    negativeLabel: String = "Use PIN",
    onResult: (success: Boolean, error: String?) -> Unit
) {
    val context = LocalContext.current
    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect

        val activity = context.findFragmentActivity()
        if (activity == null) {
            onResult(false, "Biometric prompt needs an activity host")
            return@LaunchedEffect
        }
        val status = biometricStatus(context)
        if (status != BiometricManager.BIOMETRIC_SUCCESS) {
            val reason = when (status) {
                BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> "No biometric hardware on this device"
                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> "Enrol a fingerprint or face in system settings first"
                BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> "Biometric sensor is unavailable right now"
                else -> "Biometrics are not available"
            }
            AppLog.w("Biometric", "Prompt skipped: $reason")
            onResult(false, reason)
            return@LaunchedEffect
        }

        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onResult(true, null)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onResult(false, errString.toString())
                }

                override fun onAuthenticationFailed() {
                    // A single mismatch: the prompt stays open for another try.
                    AppLog.w("Biometric", "Authentication attempt failed")
                }
            }
        )

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .setNegativeButtonText(negativeLabel)
            .setConfirmationRequired(false)
            .build()

        try {
            prompt.authenticate(info)
        } catch (e: Exception) {
            AppLog.e("Biometric", "Could not show biometric prompt", e)
            onResult(false, e.message ?: "Biometric prompt failed")
        }
    }
}
