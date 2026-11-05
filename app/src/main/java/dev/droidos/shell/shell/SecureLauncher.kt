package dev.droidos.shell.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.droidos.shell.core.Outcome
import dev.droidos.shell.core.ServiceLocator
import dev.droidos.shell.ui.BiometricRequest

/**
 * Launches an app, gating protected apps behind a real biometric check first. The gate is
 * enforced here - in the launcher's only launch path - so a locked app cannot be opened
 * from the drawer, the home grid or recents without passing authentication.
 */
@Composable
fun rememberSecureLauncher(
    lockedPackages: Set<String>,
    onMessage: (String) -> Unit
): (String) -> Unit {
    var pendingPackage by remember { mutableStateOf<String?>(null) }
    var trigger by remember { mutableIntStateOf(0) }

    BiometricRequest(
        trigger = trigger,
        title = "Unlock this app",
        subtitle = "Confirm your identity to open a protected app"
    ) { success, error ->
        val target = pendingPackage
        pendingPackage = null
        if (success && target != null) {
            val result = ServiceLocator.apps.launch(target)
            if (result is Outcome.Err) onMessage("Could not open app: ${result.message}")
        } else if (error != null) {
            onMessage(error)
        }
    }

    return { packageName ->
        if (packageName in lockedPackages) {
            pendingPackage = packageName
            trigger += 1
        } else {
            when (val result = ServiceLocator.apps.launch(packageName)) {
                is Outcome.Err -> onMessage("Could not open app: ${result.message}")
                is Outcome.Ok -> Unit
            }
        }
    }
}
