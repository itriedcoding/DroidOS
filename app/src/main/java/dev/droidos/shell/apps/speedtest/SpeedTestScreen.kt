package dev.droidos.shell.apps.speedtest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.droidos.shell.apps.cleaner.StorageMath
import dev.droidos.shell.ui.components.GlassCard
import dev.droidos.shell.ui.components.MessageBanner
import dev.droidos.shell.ui.components.ScreenScaffold
import dev.droidos.shell.ui.components.SectionLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val DEFAULT_URL = "https://speed.cloudflare.com/__down?bytes=25000000"
private const val LIMIT_BYTES = 25L * 1024L * 1024L

@Composable
fun SpeedTestScreen(onBack: () -> Unit) {
    var url by remember { mutableStateOf(DEFAULT_URL) }
    var running by remember { mutableStateOf(false) }
    var bytes by remember { mutableStateOf(0L) }
    var elapsed by remember { mutableStateOf(0L) }
    var measured by remember { mutableStateOf<ProbeResult.Measured?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val probe = remember { DownloadProbe() }
    val scope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        onDispose { probe.cancel() }
    }

    fun start() {
        if (running) return
        running = true
        bytes = 0L
        elapsed = 0L
        measured = null
        error = null
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                probe.run(url, LIMIT_BYTES) { received, millis ->
                    bytes = received
                    elapsed = millis
                }
            }
            when (outcome) {
                is ProbeResult.Measured -> measured = outcome
                is ProbeResult.Failed -> error = outcome.reason
            }
            running = false
        }
    }

    val liveRate = ThroughputMeter.bitsPerSecond(bytes, elapsed)
    val fraction = ThroughputMeter.progress(bytes, LIMIT_BYTES)

    ScreenScaffold(
        title = "Speed test",
        subtitle = "Measures a real download over your connection",
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
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("Payload URL") },
                        singleLine = true,
                        enabled = !running,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { start() },
                            enabled = !running && url.isNotBlank(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon_speed()
                            Text("Start", fontSize = 13.sp)
                        }
                        OutlinedButton(
                            onClick = {
                                probe.cancel()
                                running = false
                            },
                            enabled = running,
                            modifier = Modifier.weight(1f)
                        ) { Text("Stop", fontSize = 13.sp) }
                    }
                }
            }

            error?.let {
                Spacer(Modifier.height(10.dp))
                MessageBanner(it, isError = true)
            }

            SectionLabel("Throughput")
            GlassCard {
                Column(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(
                            ThroughputMeter.formatRate(if (running) liveRate else measured?.let {
                                ThroughputMeter.bitsPerSecond(it.bytes, it.millis)
                            } ?: 0.0),
                            fontSize = 30.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        if (running) {
                            CircularProgressIndicator(
                                modifier = Modifier.padding(4.dp).height(18.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    }
                    Text(
                        if (elapsed > 0) ThroughputMeter.formatByteRate(
                            ThroughputMeter.bytesPerSecond(bytes, elapsed)
                        ) else "No measurement yet",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${StorageMath.formatBytes(bytes)} received", fontSize = 11.sp)
                        Text(
                            if (elapsed > 0) ThroughputMeter.formatSeconds(elapsed) else "--",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            measured?.let { done ->
                SectionLabel("Result")
                GlassCard {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Line("Average", ThroughputMeter.formatRate(ThroughputMeter.bitsPerSecond(done.bytes, done.millis)))
                        Line("Transferred", StorageMath.formatBytes(done.bytes))
                        Line("Elapsed", ThroughputMeter.formatSeconds(done.millis))
                        Line("HTTP status", done.status.toString())
                        Text(
                            ThroughputMeter.summariseComplete(done.complete),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            SectionLabel("What this measures")
            GlassCard {
                Text(
                    "A plain HTTPS GET is streamed and counted byte by byte, capped at 25 MB so it " +
                        "cannot burn through a data plan. The rate is bytes x 8 / elapsed time - nothing " +
                        "is estimated or cached. Point the URL at your own server if you prefer. Mobile " +
                        "radios ramp up, so a single run understates short bursts.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Icon_speed() {
    androidx.compose.material3.Icon(
        Icons.Rounded.Speed,
        contentDescription = null,
        modifier = Modifier.padding(end = 6.dp).height(16.dp)
    )
}

@Composable
private fun Line(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
    }
}
