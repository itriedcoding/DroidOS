package dev.droidos.shell.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.droidos.shell.core.BuildInfo
import dev.droidos.shell.core.ServiceLocator
import dev.droidos.shell.ui.components.GlassCard
import dev.droidos.shell.ui.components.MessageBanner
import dev.droidos.shell.ui.components.ScreenScaffold
import dev.droidos.shell.ui.components.SectionLabel
import dev.droidos.shell.updates.UpdateManifest
import dev.droidos.shell.updates.UpdateRepository
import kotlinx.coroutines.launch

@Composable
fun UpdatesScreen(onBack: () -> Unit) {
    val repository = remember { ServiceLocator.updates }
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    var available by remember { mutableStateOf<UpdateManifest?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var lastCheck by remember { mutableStateOf(0L) }

    fun runCheck() {
        scope.launch {
            checking = true
            error = null
            status = null
            available = null
            when (val result = repository.check()) {
                is UpdateRepository.CheckResult.Available -> {
                    available = result.manifest
                    status = "Version ${result.manifest.versionName} is available"
                }
                is UpdateRepository.CheckResult.UpToDate -> status = "You are on the newest build"
                is UpdateRepository.CheckResult.Failed -> error = result.reason
            }
            lastCheck = System.currentTimeMillis()
            checking = false
        }
    }

    ScreenScaffold(
        title = "System update",
        subtitle = "Signed update channel",
        onBack = onBack
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            item {
                GlassCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.SystemUpdate,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(34.dp)
                        )
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text("DroidOS ${BuildInfo.VERSION_NAME}", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Build ${BuildInfo.VERSION_CODE}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    InfoPair("Android", BuildInfo.ANDROID_VERSION)
                    InfoPair("Security patch", BuildInfo.SECURITY_PATCH)
                    InfoPair("Device", BuildInfo.DEVICE_SUMMARY)
                }
            }

            item { SectionLabel("Update channel") }
            item {
                GlassCard {
                    Column {
                        Text(
                            "Manifests are fetched over HTTPS, the APK checksum must match, and the " +
                                "package must carry the same signing certificate as this installation. " +
                                "Anything else is discarded before you are asked to install it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(14.dp))
                        Button(
                            onClick = { runCheck() },
                            enabled = !checking && !downloading,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (checking) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(Modifier.width(10.dp))
                                Text("Checking")
                            } else {
                                Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(10.dp))
                                Text("Check for updates")
                            }
                        }
                        if (lastCheck > 0L) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Last check ${dev.droidos.shell.core.Fmt.relativeTime(lastCheck, System.currentTimeMillis())}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (error != null) {
                item {
                    Spacer(Modifier.height(10.dp))
                    MessageBanner("Update check failed: ${error.orEmpty()}", isError = true)
                }
            }

            if (status != null && available == null) {
                item {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(status.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            available?.let { manifest ->
                item { SectionLabel("Available") }
                item {
                    GlassCard {
                        Column {
                            Text(manifest.versionName, style = MaterialTheme.typography.headlineSmall)
                            if (manifest.notes.isNotBlank()) {
                                Spacer(Modifier.height(6.dp))
                                Text(manifest.notes, style = MaterialTheme.typography.bodyMedium)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "SHA-256 ${manifest.sha256.take(16)}...",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(14.dp))
                            Button(
                                onClick = {
                                    scope.launch {
                                        downloading = true
                                        error = null
                                        when (val result = repository.download(manifest)) {
                                            is UpdateRepository.DownloadResult.Ready -> {
                                                when (val install = repository.install(result.apk)) {
                                                    is dev.droidos.shell.core.Outcome.Err ->
                                                        error = install.message
                                                    is dev.droidos.shell.core.Outcome.Ok ->
                                                        status = "Verified - confirm the install in the system dialog"
                                                }
                                            }
                                            is UpdateRepository.DownloadResult.Failed -> error = result.reason
                                        }
                                        downloading = false
                                    }
                                },
                                enabled = !downloading,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (downloading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text("Downloading and verifying")
                                } else {
                                    Text("Download and verify")
                                }
                            }
                        }
                    }
                }
            }

            if (available == null && status != null) {
                item {
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            Icons.Rounded.Warning,
                            contentDescription = null,
                            tint = Color(0xFF9BA8C2),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            "This build is up to date. The shell does not flash firmware; a full ROM " +
                                "update is an OEM/AOSP responsibility documented in the project README.",
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

@Composable
private fun InfoPair(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}
