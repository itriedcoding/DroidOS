package dev.droidos.shell.firewall

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.droidos.shell.core.Fmt
import dev.droidos.shell.core.Outcome
import dev.droidos.shell.core.ServiceLocator
import dev.droidos.shell.data.model.AppSettings
import dev.droidos.shell.shell.apps.AppEntry
import dev.droidos.shell.ui.components.AppIcon
import dev.droidos.shell.ui.components.EmptyState
import dev.droidos.shell.ui.components.GlassCard
import dev.droidos.shell.ui.components.MessageBanner
import dev.droidos.shell.ui.components.ScreenScaffold
import dev.droidos.shell.ui.components.SectionLabel
import dev.droidos.shell.ui.components.SwitchRow
import kotlinx.coroutines.launch

@Composable
fun FirewallScreen(onBack: () -> Unit) {
    val controller = remember { ServiceLocator.firewall }
    val scope = rememberCoroutineScope()
    val settings by ServiceLocator.settings.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val running by FirewallStatus.running.collectAsStateWithLifecycle()
    val tunnelError by FirewallStatus.error.collectAsStateWithLifecycle()

    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    fun enabled(blocked: Set<String>) {
        scope.launch {
            when (val result = controller.enable(blocked)) {
                is Outcome.Ok -> {
                    message = "Firewall enabled"
                    isError = false
                }
                is Outcome.Err -> {
                    message = result.message
                    isError = true
                }
            }
        }
    }

    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            enabled(settings.firewallBlocked)
        } else {
            message = "VPN consent was declined, so the firewall cannot start"
            isError = true
        }
    }

    LaunchedEffect(Unit) {
        apps = ServiceLocator.apps.apps()
        loading = false
    }

    val visible = remember(apps, query) {
        if (query.isBlank()) apps else apps.filter { it.label.contains(query, ignoreCase = true) }
    }

    ScreenScaffold(
        title = "Firewall",
        subtitle = FirewallRules.summary(apps.size, settings.firewallBlocked.size),
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
            if (tunnelError != null) {
                item {
                    MessageBanner("Tunnel error: ${tunnelError.orEmpty()}", isError = true)
                    Spacer(Modifier.height(10.dp))
                }
            }

            item {
                GlassCard {
                    Column {
                        SwitchRow(
                            icon = Icons.Rounded.Shield,
                            title = if (running) "Firewall is protecting this device" else "Firewall is off",
                            subtitle = if (running) {
                                "${settings.firewallBlocked.size} apps have no network access"
                            } else {
                                "Block network access per app with a local VPN tunnel"
                            },
                            checked = running,
                            onCheckedChange = { wanted ->
                                if (wanted) {
                                    val consent = controller.prepareIntent()
                                    if (consent != null) {
                                        consentLauncher.launch(consent)
                                    } else {
                                        enabled(settings.firewallBlocked)
                                    }
                                } else {
                                    scope.launch {
                                        when (val result = controller.disable()) {
                                            is Outcome.Ok -> {
                                                message = "Firewall disabled"
                                                isError = false
                                            }
                                            is Outcome.Err -> {
                                                message = result.message
                                                isError = true
                                            }
                                        }
                                    }
                                }
                            }
                        )
                        Text(
                            "Traffic from blocked apps is routed into a tunnel that is never read, so the " +
                                "kernel drops it. Nothing is forwarded to a server and no VPN provider is " +
                                "involved. Android requires one-time VPN consent, which is why the system " +
                                "dialog appears the first time you enable this.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item { SectionLabel("Bulk actions") }
            item {
                Row {
                    TextButton(onClick = {
                        val all = apps.map { it.packageName }.toSet()
                        scope.launch {
                            when (val result = controller.setBlocked(all)) {
                                is Outcome.Ok -> {
                                    message = "All apps blocked"
                                    isError = false
                                }
                                is Outcome.Err -> {
                                    message = result.message
                                    isError = true
                                }
                            }
                        }
                    }) { Text("Block all") }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = {
                        scope.launch {
                            when (val result = controller.setBlocked(emptySet())) {
                                is Outcome.Ok -> {
                                    message = "All apps allowed"
                                    isError = false
                                }
                                is Outcome.Err -> {
                                    message = result.message
                                    isError = true
                                }
                            }
                        }
                    }) { Text("Allow all") }
                }
            }

            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search apps") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp)
                )
                Spacer(Modifier.height(8.dp))
            }

            item { SectionLabel("Per-app network access") }

            if (loading) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                }
            } else if (visible.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.Search,
                        title = "No apps match",
                        message = "Try a different search term."
                    )
                }
            } else {
                items(visible, key = { it.packageName }) { app ->
                    val blocked = app.packageName in settings.firewallBlocked
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppIcon(app.packageName, size = 38.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(app.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                            Text(
                                if (blocked) "No network access" else "Allowed",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (blocked) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = blocked,
                            onCheckedChange = { wantBlocked ->
                                val next = if (wantBlocked) settings.firewallBlocked + app.packageName
                                else settings.firewallBlocked - app.packageName
                                scope.launch {
                                    when (val result = controller.setBlocked(next)) {
                                        is Outcome.Ok -> Unit
                                        is Outcome.Err -> {
                                            message = result.message
                                            isError = true
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
