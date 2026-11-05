package dev.droidos.shell.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.droidos.shell.core.AppLog
import dev.droidos.shell.core.CrashGuard
import dev.droidos.shell.core.Fmt
import dev.droidos.shell.ui.components.EmptyState
import dev.droidos.shell.ui.components.GlassCard
import dev.droidos.shell.ui.components.MessageBanner
import dev.droidos.shell.ui.components.ScreenScaffold
import dev.droidos.shell.ui.components.SectionLabel

@Composable
fun DiagnosticsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val entries by AppLog.entries.collectAsStateWithLifecycle()
    var crashReports by remember { mutableStateOf(CrashGuard.listReports()) }
    var openReport by remember { mutableStateOf<Pair<String, String>?>(null) }
    var copied by remember { mutableStateOf(false) }

    ScreenScaffold(
        title = "Diagnostics",
        subtitle = "${entries.size} log entries - ${crashReports.size} crash reports",
        onBack = onBack,
        actions = {
            IconButton(onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("DroidOS diagnostics", AppLog.exportText()))
                copied = true
            }) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy log to clipboard")
            }
            IconButton(onClick = {
                AppLog.clear()
                CrashGuard.listReports().forEach { it.delete() }
                crashReports = emptyList()
            }) {
                Icon(Icons.Rounded.DeleteSweep, contentDescription = "Clear logs")
            }
        }
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            if (copied) {
                item {
                    MessageBanner("Log copied to clipboard")
                    Spacer(Modifier.height(10.dp))
                }
            }

            item { SectionLabel("Crash reports") }
            if (crashReports.isEmpty()) {
                item {
                    Text(
                        "No crashes recorded since installation.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(crashReports, key = { it.absolutePath }) { report ->
                    GlassCard(
                        onClick = {
                            openReport = report.name to runCatching { report.readText() }
                                .getOrElse { "Unreadable report: ${it.message}" }
                        }
                    ) {
                        Row {
                            Icon(
                                Icons.Rounded.BugReport,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(report.name, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "${Fmt.bytes(report.length())} - ${Fmt.date(report.lastModified())}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            item { SectionLabel("Event log") }
            if (entries.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.BugReport,
                        title = "No events yet",
                        message = "Warnings and errors raised by the shell are recorded here with their stack traces."
                    )
                }
            } else {
                items(entries, key = { "${it.timestamp}-${it.tag}-${it.message.hashCode()}" }) { entry ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Row {
                            Text(
                                entry.level.name.take(1),
                                style = MaterialTheme.typography.labelSmall,
                                color = when (entry.level) {
                                    AppLog.Level.ERROR -> MaterialTheme.colorScheme.error
                                    AppLog.Level.WARN -> Color(0xFFFFB454)
                                    AppLog.Level.INFO -> MaterialTheme.colorScheme.primary
                                    AppLog.Level.DEBUG -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                entry.tag,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                Fmt.relativeTime(entry.timestamp, System.currentTimeMillis()),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(entry.message, style = MaterialTheme.typography.bodySmall)
                        entry.throwable?.let { stack ->
                            Text(
                                stack.lineSequence().take(3).joinToString("\n"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    openReport?.let { (name, content) ->
        AlertDialog(
            onDismissRequest = { openReport = null },
            title = { Text(name) },
            text = {
                Text(
                    content.take(4000),
                    style = MaterialTheme.typography.bodySmall
                )
            },
            confirmButton = {
                TextButton(onClick = { openReport = null }) { Text("Close") }
            }
        )
    }
}
