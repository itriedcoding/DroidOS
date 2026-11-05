package dev.droidos.shell

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.droidos.shell.core.AppLog
import dev.droidos.shell.shell.Screen
import dev.droidos.shell.shell.ShellRoot
import dev.droidos.shell.shell.ShellViewModel
import dev.droidos.shell.ui.theme.DroidOsTheme

/**
 * The shell lives in one activity so navigation, the shade and the lock screen stay in a
 * single composition. FragmentActivity (rather than ComponentActivity) is required by
 * BiometricPrompt for the real authentication dialog.
 */
class MainActivity : FragmentActivity() {

    private val viewModel: ShellViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val initial = resolveDeepLink(intent)
        AppLog.i("MainActivity", "onCreate deepLink=${initial ?: "none"}")

        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            DroidOsTheme(settings) {
                ShellRoot(viewModel = viewModel, initialScreen = initial)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    /** Supports the launcher shortcuts declared in res/xml/shortcuts.xml. */
    private fun resolveDeepLink(intent: Intent?): Screen? = when (intent?.data?.host) {
        "vault" -> Screen.VAULT
        "firewall" -> Screen.FIREWALL
        "settings" -> Screen.SETTINGS
        else -> null
    }
}
