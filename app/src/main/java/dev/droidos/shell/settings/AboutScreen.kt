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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.droidos.shell.core.BuildInfo
import dev.droidos.shell.ui.components.GlassCard
import dev.droidos.shell.ui.components.ScreenScaffold
import dev.droidos.shell.ui.components.SectionLabel

@Composable
fun AboutScreen(onBack: () -> Unit) {
    ScreenScaffold(title = "About DroidOS", subtitle = "Build and device information", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            item { SectionLabel("Version") }
            item {
                GlassCard {
                    Column {
                        InfoPair("DroidOS shell", BuildInfo.VERSION_NAME)
                        InfoPair("Version code", BuildInfo.VERSION_CODE.toString())
                        InfoPair("Platform", BuildInfo.ANDROID_VERSION)
                        InfoPair("Security patch", BuildInfo.SECURITY_PATCH)
                    }
                }
            }

            item { SectionLabel("Device") }
            item {
                GlassCard {
                    Column {
                        InfoPair("Model", BuildInfo.DEVICE_SUMMARY)
                        InfoPair("Fingerprint", BuildInfo.BUILD_FINGERPRINT.trim())
                    }
                }
            }

            item { SectionLabel("What DroidOS is") }
            item {
                GlassCard {
                    Text(
                        "DroidOS is a complete launcher shell plus first-party applications running on " +
                            "the real Android platform. It replaces the home screen, notification shade, " +
                            "lock screen and app suite, and it uses only public Android APIs for every " +
                            "feature it offers. It is not a fork of the Android kernel or framework: " +
                            "verified boot, disk encryption and sandboxing are provided by the platform " +
                            "underneath, and DroidOS adds its own layer on top of them (Keystore-backed " +
                            "vault, per-app firewall, signing-identity pinning, privacy monitoring).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item { SectionLabel("Built with") }
            item {
                GlassCard {
                    Column {
                        listOf(
                            "Kotlin + Jetpack Compose (Material 3)",
                            "AndroidX DataStore for preferences",
                            "AndroidX Biometric for authentication",
                            "Android Keystore for AES-256-GCM",
                            "VpnService for the per-app firewall",
                            "NotificationListenerService for the shade",
                            "UsageStatsManager for recents"
                        ).forEach { entry ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                                Text("•", color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.height(0.dp))
                                Text(
                                    "  $entry",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun InfoPair(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 16.dp))
    }
}
