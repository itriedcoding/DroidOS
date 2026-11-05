package dev.droidos.shell.apps.netcheck

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.droidos.shell.ui.components.GlassCard
import dev.droidos.shell.ui.components.MessageBanner
import dev.droidos.shell.ui.components.ScreenScaffold
import dev.droidos.shell.ui.components.SectionLabel
import kotlinx.coroutines.launch

@Composable
fun NetcheckScreen(onBack: () -> Unit) {
    val diagnostics = remember { NetDiagnostics() }
    val scope = rememberCoroutineScope()

    var host by remember { mutableStateOf("example.com") }
    var running by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<NetDiagnostics.Report?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun run() {
        val target = NetDiagnostics.normalize(host)
        if (target.isEmpty()) {
            error = "Enter a host to test"
            return
        }
        scope.launch {
            running = true
            error = null
            report = null
            report = runCatching { diagnostics.check(target) }
                .onFailure { error = it.message ?: "Probe failed" }
                .getOrNull()
            running = false
        }
    }

    ScreenScaffold(
        title = "Netcheck",
        subtitle = "DNS, TCP and HTTPS measured live",
        onBack = onBack
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            GlassCard {
                Column(Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = { Text("Host") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Go
                        ),
                        keyboardActions = KeyboardActions(onGo = { run() }),
                        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { run() },
                        enabled = !running,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (running) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(10.dp))
                            Text("Probing")
                        } else {
                            Text("Run checks")
                        }
                    }
                }
            }

            error?.let {
                Spacer(Modifier.height(10.dp))
                MessageBanner(it, isError = true)
            }

            report?.let { result ->
                SectionLabel("Results for ${result.host}")
                GlassCard {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ProbeRow("DNS lookup", result.dns)
                        ProbeRow("TCP 443", result.tcp)
                        ProbeRow("HTTPS", result.https)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                "Each row is a real measurement from this device: a resolver lookup, a socket " +
                    "connection with an 8 second timeout, and an HTTPS request. A failure shows the " +
                    "platform's own error text, which is usually the reason (no route, refused, " +
                    "timeout, unknown host).",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ProbeRow(label: String, probe: NetDiagnostics.Probe) {
    val failing = probe is NetDiagnostics.Probe.Failed
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(
                label,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                when (probe) {
                    is NetDiagnostics.Probe.Ok -> probe.detail
                    is NetDiagnostics.Probe.Failed -> probe.reason
                },
                fontSize = 12.sp,
                style = TextStyle(fontFamily = FontFamily.Monospace),
                color = if (failing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            when (probe) {
                is NetDiagnostics.Probe.Ok -> "${probe.millis} ms"
                is NetDiagnostics.Probe.Failed -> "${probe.millis} ms"
            },
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
