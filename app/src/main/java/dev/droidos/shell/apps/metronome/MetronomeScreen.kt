package dev.droidos.shell.apps.metronome

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.droidos.shell.ui.components.GlassCard
import dev.droidos.shell.ui.components.MessageBanner
import dev.droidos.shell.ui.components.ScreenScaffold
import dev.droidos.shell.ui.components.SectionLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

private const val CLICK_SAMPLE_RATE = 44_100

@Composable
fun MetronomeScreen(onBack: () -> Unit) {
    var bpm by remember { mutableStateOf(MetronomeEngine.DEFAULT_BPM) }
    var beatsPerBar by remember { mutableStateOf(4) }
    var running by remember { mutableStateOf(false) }
    var beat by remember { mutableStateOf(-1) }
    var taps by remember { mutableStateOf<List<Long>>(emptyList()) }
    var audioError by remember { mutableStateOf<String?>(null) }
    val clicker = remember { ClickPlayer() }

    DisposableEffect(Unit) {
        onDispose { clicker.release() }
    }

    LaunchedEffect(running, bpm, beatsPerBar) {
        if (!running) {
            beat = -1
            return@LaunchedEffect
        }
        if (!clicker.ensure()) {
            audioError = "The device refused an audio track, so the metronome cannot play"
            running = false
            return@LaunchedEffect
        }
        audioError = null
        withContext(Dispatchers.Default) {
            var beatIndex = 0
            var nextAt = System.nanoTime()
            while (isActive) {
                val wrote = clicker.click(MetronomeEngine.isAccent(beatIndex, beatsPerBar))
                if (!wrote) {
                    audioError = "Audio output stopped mid-bar"
                    break
                }
                beat = beatIndex
                beatIndex++
                nextAt += MetronomeEngine.intervalNanos(bpm)
                // Drift-corrected wait: if a write overran, the clock is re-based instead of
                // letting the error accumulate across bars.
                val waitMillis = (nextAt - System.nanoTime()) / 1_000_000L
                if (waitMillis > 0L) delay(waitMillis) else nextAt = System.nanoTime()
            }
        }
    }

    ScreenScaffold(
        title = "Metronome",
        subtitle = "AudioTrack clicks, drift corrected",
        onBack = onBack
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            GlassCard {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        bpm.toString(),
                        fontSize = 60.sp,
                        fontWeight = FontWeight.Light,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "BPM - ${MetronomeEngine.tempoName(bpm)}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    Slider(
                        value = bpm.toFloat(),
                        onValueChange = { bpm = it.roundToInt() },
                        valueRange = MetronomeEngine.MIN_BPM.toFloat()..MetronomeEngine.MAX_BPM.toFloat(),
                        steps = MetronomeEngine.MAX_BPM - MetronomeEngine.MIN_BPM - 1
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(-5, -1, 1, 5).forEach { delta ->
                            OutlinedButton(onClick = { bpm = MetronomeEngine.step(bpm, delta) }) {
                                Text(if (delta > 0) "+$delta" else "$delta", fontSize = 12.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        repeat(metronomeBeats(beatsPerBar)) { index ->
                            val active = running && Math.floorMod(beat, metronomeBeats(beatsPerBar)) == index
                            Box(
                                Modifier
                                    .padding(horizontal = 3.dp)
                                    .size(if (index == 0) 14.dp else 10.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            active && index == 0 -> MaterialTheme.colorScheme.primary
                                            active -> MaterialTheme.colorScheme.secondary
                                            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
                                        }
                                    )
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { running = !running },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                if (running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.size(6.dp))
                            Text(if (running) "Stop" else "Start", fontSize = 13.sp)
                        }
                        OutlinedButton(
                            onClick = {
                                val now = System.currentTimeMillis()
                                val updated = (taps + now).takeLast(6)
                                taps = updated
                                MetronomeEngine.tapToBpm(updated)?.let { bpm = it }
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Tap tempo", fontSize = 13.sp) }
                    }
                    if (taps.size > 1) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "${taps.size} taps - ${MetronomeEngine.tapToBpm(taps)?.let { "$it BPM" } ?: "keep tapping"}",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            audioError?.let {
                Spacer(Modifier.height(10.dp))
                MessageBanner(it, isError = true)
            }

            SectionLabel("Time signature")
            GlassCard {
                Column(Modifier.fillMaxWidth()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (MetronomeEngine.MIN_BEATS..8).forEach { count ->
                            FilterChip(
                                selected = beatsPerBar == count,
                                onClick = { beatsPerBar = count },
                                label = { Text("$count/4", fontSize = 11.sp) }
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Beat one of every bar is accented. Interval: ${MetronomeEngine.intervalMillis(bpm)} ms " +
                            "(${MetronomeEngine.intervalNanos(bpm)} ns).",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            SectionLabel("How it plays")
            GlassCard {
                Text(
                    "Two short PCM clicks are generated in memory - a 1.6 kHz accent and a 900 Hz beat - and " +
                        "written to a streaming AudioTrack. Each beat is scheduled against System.nanoTime() " +
                        "and the clock is re-based if a buffer write overruns, so the tempo does not creep. " +
                        "Timing is limited by the audio buffer, not by a timer tick.",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Start
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun metronomeBeats(beats: Int): Int = MetronomeEngine.clampBeats(beats)

/** Streams generated click buffers; everything is optional so a missing audio path is reportable. */
private class ClickPlayer {

    private var track: AudioTrack? = null
    private var accentPcm: ShortArray = ShortArray(0)
    private var beatPcm: ShortArray = ShortArray(0)

    fun ensure(): Boolean {
        if (track != null) return true
        accentPcm = click(1_600.0)
        beatPcm = click(900.0)
        val minimum = AudioTrack.getMinBufferSize(
            CLICK_SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minimum <= 0) return false
        val bufferSize = max(minimum, (beatPcm.size * 2 * 2))
        track = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(CLICK_SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(bufferSize)
                .build()
                .also { it.play() }
        }.getOrNull()
        return track != null
    }

    fun click(accent: Boolean): Boolean {
        val output = track ?: return false
        val pcm = if (accent) accentPcm else beatPcm
        return runCatching { output.write(pcm, 0, pcm.size) >= 0 }.getOrDefault(false)
    }

    fun release() {
        runCatching { track?.pause() }
        runCatching { track?.flush() }
        runCatching { track?.release() }
        track = null
    }

    private fun click(frequency: Double): ShortArray {
        val samples = CLICK_SAMPLE_RATE * 40 / 1000
        return ShortArray(samples) { index ->
            val seconds = index.toDouble() / CLICK_SAMPLE_RATE
            val envelope = exp(-seconds * 55.0)
            (sin(2.0 * PI * frequency * seconds) * envelope * Short.MAX_VALUE * 0.55)
                .roundToInt()
                .toShort()
        }
    }
}
