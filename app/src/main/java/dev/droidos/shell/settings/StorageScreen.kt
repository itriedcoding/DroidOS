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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.droidos.shell.core.Fmt
import dev.droidos.shell.core.ServiceLocator
import dev.droidos.shell.ui.components.GlassCard
import dev.droidos.shell.ui.components.RingStat
import dev.droidos.shell.ui.components.ScreenScaffold
import dev.droidos.shell.ui.components.SectionLabel
import dev.droidos.shell.ui.components.SettingRow
import dev.droidos.shell.ui.components.StatBar

@Composable
fun StorageScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val stats = remember { ServiceLocator.deviceStats }
    var storage by remember { mutableStateOf(stats.storage()) }
    val memory = remember { mutableStateOf(stats.memory()) }

    ScreenScaffold(
        title = "Storage",
        subtitle = storage.summary,
        onBack = onBack,
        actions = {
            IconButton(onClick = {
                storage = stats.storage()
                memory.value = stats.memory()
            }) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Refresh")
            }
        }
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            item {
                GlassCard {
                    Row(Modifier.fillMaxWidth()) {
                        RingStat(
                            fraction = storage.usedFraction,
                            value = Fmt.percent(storage.usedFraction),
                            caption = "used",
                            size = 140,
                            strokeWidth = 13
                        )
                    }
                }
            }

            item { SectionLabel("Internal storage") }
            item {
                GlassCard {
                    Column {
                        Text(Fmt.bytes(storage.usedBytes), style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "used of ${Fmt.bytes(storage.totalBytes)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        StatBar(storage.usedFraction)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "${Fmt.bytes(storage.freeBytes)} free",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            item { SectionLabel("Memory") }
            item {
                GlassCard {
                    Column {
                        Text(
                            "${Fmt.bytes(memory.value.usedBytes)} of ${Fmt.bytes(memory.value.totalBytes)}",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(Modifier.height(8.dp))
                        StatBar(memory.value.usedFraction, MaterialTheme.colorScheme.tertiary)
                        if (memory.value.lowMemory) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "The system reports low memory. Close unused apps to recover headroom.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            item { SectionLabel("Manage") }
            item {
                GlassCard {
                    Column {
                        SettingRow(
                            icon = Icons.Rounded.OpenInNew,
                            title = "System storage settings",
                            subtitle = "Per-category breakdown and cleanup tools",
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        android.content.Intent(android.provider.Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
                                    )
                                }
                            },
                            trailing = { Chevron() }
                        )
                        SettingRow(
                            icon = Icons.Rounded.Storage,
                            title = "Browse files",
                            subtitle = "Open the DroidOS file manager",
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        android.content.Intent(
                                            android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                                        )
                                    )
                                }
                            },
                            trailing = { Chevron() }
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
