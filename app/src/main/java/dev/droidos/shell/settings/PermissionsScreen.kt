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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.droidos.shell.core.ServiceLocator
import dev.droidos.shell.security.AppPermissionStatus
import dev.droidos.shell.ui.components.AppIcon
import dev.droidos.shell.ui.components.EmptyState
import dev.droidos.shell.ui.components.GlassCard
import dev.droidos.shell.ui.components.ScreenScaffold
import dev.droidos.shell.ui.components.SectionLabel
import dev.droidos.shell.ui.components.SwitchRow

/** Real permission audit: every entry comes from PackageManager.checkPermission. */
@Composable
fun PermissionsScreen(onBack: () -> Unit) {
    var statuses by remember { mutableStateOf<List<AppPermissionStatus>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var includeSystem by remember { mutableStateOf(false) }

    LaunchedEffect(includeSystem) {
        loading = true
        statuses = ServiceLocator.permissionAudit.appsWithSensitivePermissions(includeSystem)
        loading = false
    }

    ScreenScaffold(
        title = "Permission audit",
        subtitle = if (loading) "Reading package database" else "${statuses.size} apps hold sensitive access",
        onBack = onBack
    ) {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            GlassCard(Modifier.padding(horizontal = 16.dp)) {
                SwitchRow(
                    icon = Icons.Rounded.VerifiedUser,
                    title = "Include system apps",
                    subtitle = "Firmware packages are usually broad by design",
                    checked = includeSystem,
                    onCheckedChange = { includeSystem = it }
                )
            }
            Spacer(Modifier.height(8.dp))

            when {
                loading -> Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.height(48.dp))
                    CircularProgressIndicator()
                }

                statuses.isEmpty() -> EmptyState(
                    icon = Icons.Rounded.VerifiedUser,
                    title = "Nothing to report",
                    message = if (includeSystem) {
                        "No installed app currently holds a sensitive permission."
                    } else {
                        "No user-installed app holds a sensitive permission right now."
                    }
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    items(statuses, key = { it.packageName }) { status ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            AppIcon(status.packageName, size = 40.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(status.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                                Text(
                                    status.packageName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                                Spacer(Modifier.height(6.dp))
                                status.granted.forEach { permission ->
                                    Text(
                                        "- ${permission.label} (${permission.group})",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                    item {
                        SectionLabel("Why this matters")
                        Text(
                            "These grants come straight from the OS package database, so revoking one " +
                                "here in the audit is not possible - Android requires the owning app's " +
                                "settings page. Use Installed apps > App info to change a grant.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}
