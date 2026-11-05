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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import dev.droidos.shell.core.Outcome
import dev.droidos.shell.core.ServiceLocator
import dev.droidos.shell.shell.Screen
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
fun AppsScreen(
    onBack: () -> Unit,
    onLaunchApp: (String) -> Unit,
    onOpenScreen: (Screen) -> Unit
) {
    val settings by ServiceLocator.settings.settings.collectAsStateWithLifecycle(
        initialValue = dev.droidos.shell.data.model.AppSettings()
    )
    val scope = rememberCoroutineScope()
    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    var showSystem by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        apps = ServiceLocator.apps.apps()
        loading = false
    }

    val visible = remember(apps, query, showSystem) {
        apps.asSequence()
            .filter { if (showSystem) true else !it.isSystem }
            .filter { it.label.contains(query, ignoreCase = true) }
            .toList()
    }

    ScreenScaffold(
        title = "Installed apps",
        subtitle = "${visible.size} shown - ${settings.lockedApps.size} protected",
        onBack = onBack
    ) {
        Column(Modifier.fillMaxSize()) {
            if (message != null) {
                MessageBanner(message.orEmpty())
                Spacer(Modifier.height(8.dp))
            }

            SectionLabel("App lock", Modifier.padding(horizontal = 16.dp))
            GlassCard(Modifier.padding(horizontal = 16.dp)) {
                Column {
                    Text(
                        "Protected apps require a biometric check before the launcher will open them.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    SwitchRow(
                        icon = Icons.Rounded.Lock,
                        title = "Show system apps",
                        subtitle = "Include firmware applications in the list",
                        checked = showSystem,
                        onCheckedChange = { showSystem = it }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                placeholder = { Text("Search apps") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )
            Spacer(Modifier.height(8.dp))

            if (loading) {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(48.dp))
                    CircularProgressIndicator()
                }
                return@Column
            }

            if (visible.isEmpty()) {
                EmptyState(
                    icon = Icons.Rounded.Search,
                    title = "No apps match",
                    message = if (showSystem) "Try another search term." else "No user apps match. Enable system apps to see more."
                )
                return@Column
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                items(visible, key = { it.packageName }) { app ->
                    val locked = app.packageName in settings.lockedApps
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppIcon(app.packageName, size = 44.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(app.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                            Text(
                                buildString {
                                    append(app.packageName)
                                    app.versionName?.let { append(" - v$it") }
                                    if (app.isSystem) append(" - system")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                        IconButton(onClick = { onLaunchApp(app.packageName) }) {
                            Icon(Icons.Rounded.OpenInNew, contentDescription = "Open ${app.label}")
                        }
                        IconButton(onClick = {
                            when (val result = ServiceLocator.apps.openAppInfo(app.packageName)) {
                                is Outcome.Err -> message = result.message
                                is Outcome.Ok -> Unit
                            }
                        }) {
                            Icon(Icons.Rounded.Info, contentDescription = "App info for ${app.label}")
                        }
                        IconButton(onClick = {
                            when (val result = ServiceLocator.apps.requestUninstall(app.packageName)) {
                                is Outcome.Err -> message = result.message
                                is Outcome.Ok -> Unit
                            }
                        }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Uninstall ${app.label}")
                        }
                        IconButton(onClick = {
                            val next = if (locked) settings.lockedApps - app.packageName
                            else settings.lockedApps + app.packageName
                            scope.launch {
                                ServiceLocator.settings.setLockedApps(next)
                                message = if (locked) "${app.label} no longer protected"
                                else "${app.label} will require biometrics"
                            }
                        }) {
                            Icon(
                                Icons.Rounded.Lock,
                                contentDescription = if (locked) "Remove protection" else "Protect app",
                                tint = if (locked) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                item {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Permission audit",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable { onOpenScreen(Screen.PERMISSIONS) }
                            .padding(vertical = 10.dp)
                    )
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}
