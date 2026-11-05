package dev.droidos.shell.apps.scanner

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PlayArrow
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
import dev.droidos.shell.core.Fmt
import dev.droidos.shell.core.ServiceLocator
import dev.droidos.shell.security.ScanEngine
import dev.droidos.shell.security.ScanReport
import dev.droidos.shell.security.ScanResult
import dev.droidos.shell.security.Severity
import dev.droidos.shell.ui.components.AppIcon
import dev.droidos.shell.ui.components.EmptyState
import dev.droidos.shell.ui.components.GlassCard
import dev.droidos.shell.ui.components.MessageBanner
import dev.droidos.shell.ui.components.ScreenScaffold
import dev.droidos.shell.ui.components.SectionLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ScannerScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var report by remember { mutableStateOf<ScanReport?>(null) }
    var scanning by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun scan() {
        scope.launch {
            scanning = true
            error = null
            try {
                val started = System.currentTimeMillis()
                val facts = withContext(Dispatchers.IO) { ServiceLocator.apps.allFacts() }
                val results = ScanEngine.scanAll(facts)
                report = ScanReport(
                    results = results,
                    scannedAt = System.currentTimeMillis(),
                    durationMs = System.currentTimeMillis() - started
                )
            } catch (e: Exception) {
                error = e.message ?: "The scan could not complete"
            } finally {
                scanning = false
            }
        }
    }

    ScreenScaffold(
        title = "Security scan",
        subtitle = "Heuristic risk review of installed packages",
        onBack = onBack
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            if (error != null) {
                item {
                    MessageBanner(error.orEmpty(), isError = true)
                    Spacer(Modifier.height(10.dp))
                }
            }

            item {
                GlassCard {
                    Column {
                        val current = report
                        if (current == null) {
                            Text(
                                "The scan inspects install source, requested permissions, exposed " +
                                    "control surfaces (accessibility, device admin) and debuggable flags. " +
                                    "Every finding cites the concrete fact behind it.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                                ScoreBlock("Flagged", current.flagged.size.toString(), MaterialTheme.colorScheme.primary)
                                ScoreBlock("High risk", current.highRisk.toString(), Color(0xFFFF6B6B))
                                ScoreBlock("Worth a look", current.mediumRisk.toString(), Color(0xFFFFB454))
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "${current.results.size} packages with findings - completed in ${Fmt.duration(current.durationMs)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        Button(
                            onClick = { scan() },
                            enabled = !scanning,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (scanning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(Modifier.width(10.dp))
                                Text("Scanning installed packages")
                            } else {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(10.dp))
                                Text("Run scan")
                            }
                        }
                    }
                }
            }

            val current = report
            if (current != null) {
                item { SectionLabel("Findings") }
                if (current.results.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Rounded.CheckCircle,
                            title = "No findings",
                            message = "No installed package matched any risk rule."
                        )
                    }
                } else {
                    items(current.results, key = { it.packageName }) { result ->
                        FindingCard(result)
                        Spacer(Modifier.height(10.dp))
                    }
                }
            } else if (!scanning) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.BugReport,
                        title = "Not scanned yet",
                        message = "Run a scan to review every installed package against the risk rules."
                    )
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun ScoreBlock(label: String, value: String, color: Color) {
    Column {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FindingCard(result: ScanResult) {
    val color = when (result.risk) {
        Severity.HIGH -> Color(0xFFFF6B6B)
        Severity.MEDIUM -> Color(0xFFFFB454)
        Severity.LOW -> Color(0xFF5B8CFF)
        Severity.SAFE -> MaterialTheme.colorScheme.primary
    }

    GlassCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(result.packageName, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(result.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                    Text(
                        "${result.packageName} - score ${result.score}/100",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Warning,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(result.risk.label, style = MaterialTheme.typography.labelMedium, color = color)
                }
            }
            Spacer(Modifier.height(8.dp))
            result.findings.forEach { finding ->
                Text(
                    "- ${finding.title} (+${finding.points})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    finding.detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 10.dp)
                )
            }
        }
    }
}
