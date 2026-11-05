package dev.droidos.shell.settings

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.droidos.shell.core.BuildInfo
import dev.droidos.shell.core.Fmt
import dev.droidos.shell.core.ServiceLocator
import dev.droidos.shell.ui.components.GlassCard
import dev.droidos.shell.ui.components.MessageBanner
import dev.droidos.shell.ui.components.ScreenScaffold
import dev.droidos.shell.ui.components.SectionLabel
import dev.droidos.shell.ui.components.SwitchRow
import dev.droidos.shell.updates.UpdateRepository
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun UpdatesScreen(onBack: () -> Unit) {
    val repository = remember { ServiceLocator.updates }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var checking by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    var available by remember { mutableStateOf<UpdateRepository.CheckResult.Available?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var lastCheck by remember { mutableStateOf(0L) }
    var autoCheck by remember { mutableStateOf(true) }
    val settingsRepository = ServiceLocator.settings

    LaunchedEffect(Unit) {
        autoCheck = settingsRepository.current().autoUpdateCheck
        lastCheck = settingsRepository.current().lastUpdateCheck
    }

    fun runCheck() {
        scope.launch {
            checking = true
            error = null
            status = null
            available = null
            try {
                when (val result = repository.check()) {
                    is UpdateRepository.CheckResult.Available -> {
                        available = result
                        status = "DroidOS ${result.release.versionName} is available"
                    }
                    is UpdateRepository.CheckResult.UpToDate ->
                        status = "You are on the newest published release"
                    is UpdateRepository.CheckResult.Failed -> error = result.reason
                }
                lastCheck = System.currentTimeMillis()
            } catch (e: Exception) {
                // Keep the button usable and say what actually broke instead of spinning forever.
                error = "Update check failed: ${e.message ?: e::class.java.simpleName}"
            } finally {
                checking = false
            }
        }
    }

    ScreenScaffold(
        title = "System update",
        subtitle = "Release channel",
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

            item { SectionLabel("Release channel") }
            item {
                GlassCard {
                    Column {
                        Text(
                            "The latest release is read from the project's releases page, and its " +
                                "checksum from the signed update manifest. Both must describe the same " +
                                "file before anything is offered; the download is then verified against " +
                                "that checksum and this app's signing certificate.",
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
                                Text("Checking the release page")
                            } else {
                                Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(10.dp))
                                Text("Check for updates")
                            }
                        }
                        if (lastCheck > 0L) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Last check ${Fmt.relativeTime(lastCheck, System.currentTimeMillis())}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        TextButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(BuildInfo.RELEASES_PAGE_URL))
                                runCatching { context.startActivity(intent) }
                                    .onFailure { error = "Could not open the browser: ${it.message}" }
                            }
                        ) {
                            Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("View all releases")
                        }
                    }
                }
            }

            item { SectionLabel("Automation") }
            item {
                GlassCard {
                    SwitchRow(
                        icon = Icons.Rounded.Autorenew,
                        title = "Check for updates automatically",
                        subtitle = "At most once a day when the shell starts. A found release raises a " +
                            "notification; installing it always needs your confirmation in the system dialog.",
                        checked = autoCheck,
                        onCheckedChange = { enabled ->
                            autoCheck = enabled
                            scope.launch { settingsRepository.setAutoUpdateCheck(enabled) }
                        }
                    )
                }
            }

            if (error != null) {
                item {
                    Spacer(Modifier.height(10.dp))
                    MessageBanner(error.orEmpty(), isError = true)
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

            available?.let { offer ->
                val release = offer.release
                val manifest = offer.manifest

                item { SectionLabel("Published release") }
                item {
                    GlassCard {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "DroidOS ${release.versionName}",
                                    style = MaterialTheme.typography.headlineSmall
                                )
                                Spacer(Modifier.width(10.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                ) {
                                    Text(
                                        release.tag,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                buildString {
                                    append(if (release.publishedAt.isBlank()) "publish date unknown" else "published ${dateLabel(release.publishedAt)}")
                                    append(" · ")
                                    append(release.assetName)
                                    append(" · ")
                                    append(sizeLabel(release.assetBytes))
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (release.notes.isNotBlank()) {
                                Spacer(Modifier.height(10.dp))
                                Text(release.notes, style = MaterialTheme.typography.bodySmall)
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "SHA-256 ${manifest.sha256.take(16)}…",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(14.dp))
                            Button(
                                onClick = {
                                    scope.launch {
                                        downloading = true
                                        error = null
                                        try {
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
                                        } catch (e: Exception) {
                                            error = "Download failed: ${e.message ?: e::class.java.simpleName}"
                                        } finally {
                                            downloading = false
                                        }
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
                            TextButton(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(release.pageUrl))
                                    runCatching { context.startActivity(intent) }
                                        .onFailure { error = "Could not open the browser: ${it.message}" }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Open release page")
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
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            "The shell installs a new APK, not a firmware image. A full ROM update is " +
                                "built from the device tree in this repository and flashed separately.",
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

private fun dateLabel(iso: String): String = try {
    DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
        .withZone(ZoneId.systemDefault())
        .format(Instant.parse(iso))
} catch (e: Exception) {
    iso
}

private fun sizeLabel(bytes: Long): String =
    if (bytes <= 0L) "size unknown" else String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0))

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
