package dev.droidos.shell.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.droidos.shell.core.Fmt
import dev.droidos.shell.core.ServiceLocator
import dev.droidos.shell.security.IntegrityVerifier
import dev.droidos.shell.shell.Screen
import dev.droidos.shell.shell.ShellViewModel
import dev.droidos.shell.ui.components.GlassCard
import dev.droidos.shell.ui.components.MessageBanner
import dev.droidos.shell.ui.components.ScreenScaffold
import dev.droidos.shell.ui.components.SectionLabel
import dev.droidos.shell.ui.components.SettingRow
import dev.droidos.shell.ui.components.SwitchRow
import kotlinx.coroutines.launch

@Composable
fun SecurityScreen(
    onBack: () -> Unit,
    onOpenScreen: (Screen) -> Unit,
    viewModel: ShellViewModel
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val firewallRunning by dev.droidos.shell.firewall.FirewallStatus.running.collectAsStateWithLifecycle()

    var integrityLine by remember { mutableStateOf("Verifying signing identity...") }
    var integrityDetail by remember { mutableStateOf("") }
    var showPinForm by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var useBiometric by remember { mutableStateOf(settings.biometricEnabled) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    var vaultKey by remember { mutableStateOf(ServiceLocator.crypto.hasVaultKey()) }

    LaunchedEffect(Unit) {
        val result = ServiceLocator.integrity.verify()
        integrityLine = when (result) {
            is IntegrityVerifier.Result.Trusted ->
                if (result.firstPin) "Signing identity pinned on first launch" else "Signing identity verified"
            is IntegrityVerifier.Result.Changed -> "Signing identity CHANGED"
            is IntegrityVerifier.Result.Unavailable -> "Integrity check unavailable"
        }
        integrityDetail = when (result) {
            is IntegrityVerifier.Result.Trusted -> "SHA-256 ${Fmt.truncateMiddle(result.digest, 30)}"
            is IntegrityVerifier.Result.Changed -> "Expected ${result.expected.take(16)}... got ${result.actual.take(16)}..."
            is IntegrityVerifier.Result.Unavailable -> result.reason
        }
    }

    LaunchedEffect(settings.biometricEnabled) { useBiometric = settings.biometricEnabled }

    ScreenScaffold(
        title = "Security centre",
        subtitle = "Locks, integrity and network control",
        onBack = onBack
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            if (message != null) {
                item {
                    MessageBanner(message.orEmpty(), isError = isError)
                    Spacer(Modifier.height(10.dp))
                }
            }

            item { SectionLabel("Application integrity") }
            item {
                GlassCard {
                    Column {
                        SettingRow(
                            icon = Icons.Rounded.VerifiedUser,
                            title = integrityLine,
                            subtitle = integrityDetail
                        )
                        Text(
                            "Kernel verified boot is provided by the device firmware. For the shell, " +
                                "DroidOS pins its own signing certificate on first launch and flags any " +
                                "later build signed by a different identity - the signature of a repackaged APK.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item { SectionLabel("Lockscreen") }
            item {
                GlassCard {
                    Column {
                        if (settings.lockEnabled) {
                            SettingRow(
                                icon = Icons.Rounded.Lock,
                                title = "Lockscreen PIN is set",
                                subtitle = "Failed attempts: ${settings.failedUnlocks}",
                                trailing = {
                                    TextButton(onClick = { showPinForm = !showPinForm }) {
                                        Text(if (showPinForm) "Cancel" else "Change")
                                    }
                                }
                            )
                            SwitchRow(
                                icon = Icons.Rounded.Fingerprint,
                                title = "Biometric unlock",
                                subtitle = "Uses the enrolled fingerprint or face",
                                checked = settings.biometricEnabled,
                                onCheckedChange = { enabled ->
                                    scope.launch {
                                        ServiceLocator.settings.setBiometric(enabled)
                                        message = if (enabled) "Biometric unlock enabled"
                                        else "Biometric unlock disabled"
                                        isError = false
                                    }
                                }
                            )
                        } else if (!showPinForm) {
                            SettingRow(
                                icon = Icons.Rounded.Lock,
                                title = "No lockscreen PIN",
                                subtitle = "Set a PIN to protect the shell",
                                trailing = {
                                    TextButton(onClick = { showPinForm = true }) { Text("Set up") }
                                }
                            )
                        }

                        if (showPinForm) {
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(
                                value = pin,
                                onValueChange = { pin = it.filter(Char::isDigit).take(12) },
                                label = { Text("New PIN (4-12 digits)") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = confirmPin,
                                onValueChange = { confirmPin = it.filter(Char::isDigit).take(12) },
                                label = { Text("Confirm PIN") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(6.dp))
                            SwitchRow(
                                icon = Icons.Rounded.Fingerprint,
                                title = "Enable biometric unlock",
                                checked = useBiometric,
                                onCheckedChange = { useBiometric = it }
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    if (pin != confirmPin) {
                                        message = "PINs do not match"
                                        isError = true
                                        return@Button
                                    }
                                    viewModel.configureLock(pin, useBiometric) { validation ->
                                        if (validation == null) {
                                            message = "Lockscreen PIN saved"
                                            isError = false
                                            showPinForm = false
                                            pin = ""
                                            confirmPin = ""
                                        } else {
                                            message = validation.message
                                            isError = true
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Save PIN") }

                            if (settings.lockEnabled) {
                                Spacer(Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = {
                                        viewModel.disableLock()
                                        showPinForm = false
                                        pin = ""
                                        confirmPin = ""
                                        message = "Lockscreen disabled"
                                        isError = false
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Remove lockscreen") }
                            }
                        }

                    }
                }
            }

            item { SectionLabel("App lock") }
            item {
                GlassCard {
                    SettingRow(
                        icon = Icons.Rounded.Shield,
                        title = "${settings.lockedApps.size} protected apps",
                        subtitle = "Protected apps require biometrics before launching",
                        onClick = { onOpenScreen(Screen.APPS) },
                        trailing = { Chevron() }
                    )
                }
            }

            item { SectionLabel("Network firewall") }
            item {
                GlassCard {
                    Column {
                        SettingRow(
                            icon = Icons.Rounded.Security,
                            title = if (firewallRunning) "Firewall active" else "Firewall inactive",
                            subtitle = "${settings.firewallBlocked.size} apps blocked",
                            onClick = { onOpenScreen(Screen.FIREWALL) },
                            trailing = { Chevron() }
                        )
                        Text(
                            "A local VPN tunnel that the system never forwards. Blocked apps lose all " +
                                "network access without root and without sending traffic anywhere.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item { SectionLabel("Encryption") }
            item {
                GlassCard {
                    Column {
                        SettingRow(
                            icon = Icons.Rounded.Lock,
                            title = if (vaultKey) "Vault key present in Keystore" else "Vault key not created",
                            subtitle = "AES-256-GCM, generated and held by the Android Keystore"
                        )
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    when (val result = ServiceLocator.crypto.destroyVaultKey()) {
                                        is dev.droidos.shell.core.Outcome.Err -> {
                                            message = "Could not destroy key: ${result.message}"
                                            isError = true
                                        }
                                        is dev.droidos.shell.core.Outcome.Ok -> {
                                            vaultKey = false
                                            message = "Vault key destroyed. Existing vault files are now unreadable."
                                            isError = true
                                        }
                                    }
                                }
                            },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Destroy vault key") }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Destroying the key permanently removes access to everything sealed in the " +
                                "Vault. This cannot be undone.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
