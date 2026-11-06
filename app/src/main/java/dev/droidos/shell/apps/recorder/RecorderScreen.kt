package dev.droidos.shell.apps.recorder

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.droidos.shell.ui.components.EmptyState
import dev.droidos.shell.ui.components.GlassCard
import dev.droidos.shell.ui.components.MessageBanner
import dev.droidos.shell.ui.components.ScreenScaffold
import dev.droidos.shell.ui.components.SectionLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private data class Recording(val file: File, val name: String, val size: Long, val durationMs: Long)

@Composable
fun RecorderScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val directory = remember { File(context.filesDir, "recordings").apply { mkdirs() } }
    val recorder = remember { AudioRecorder(context) }
    val playback = remember { ClipPlayback() }
    val scope = rememberCoroutineScope()

    var granted by remember { mutableStateOf(hasMicPermission(context)) }
    var recording by remember { mutableStateOf(false) }
    var elapsed by remember { mutableStateOf(0L) }
    var message by remember { mutableStateOf<String?>(null) }
    var recordings by remember { mutableStateOf<List<Recording>>(emptyList()) }
    var playingName by remember { mutableStateOf<String?>(null) }

    val permissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { allowed ->
        granted = allowed && hasMicPermission(context)
        if (!granted) message = "Microphone access was not granted, so nothing can be recorded"
    }

    val reload: () -> Unit = {
        scope.launch {
            recordings = withContext(Dispatchers.IO) {
                directory.listFiles()
                    ?.filter { it.isFile && it.length() > 0L }
                    ?.sortedByDescending { it.lastModified() }
                    ?.map { file -> Recording(file, file.name, file.length(), probeDuration(file)) }
                    .orEmpty()
            }
        }
    }

    LaunchedEffect(Unit) { reload() }

    DisposableEffect(Unit) {
        onDispose {
            playback.stop()
            recorder.stop()
        }
    }

    LaunchedEffect(recording) {
        if (recording) {
            val startedAt = System.currentTimeMillis()
            while (recording) {
                elapsed = System.currentTimeMillis() - startedAt
                delay(200)
            }
        }
    }

    fun startRecording() {
        val file = File(directory, RecorderEngine.fileName(System.currentTimeMillis()))
        recorder.start(file).onSuccess {
            recording = true
            elapsed = 0L
            message = null
        }.onFailure {
            message = "Could not start recording: ${it.message ?: "microphone unavailable"}"
        }
    }

    fun stopRecording() {
        val file = recorder.currentFile
        val result = recorder.stop()
        recording = false
        result.onSuccess {
            if (file != null && file.length() > 0L) {
                message = "Saved ${file.name}"
                reload()
            } else {
                file?.delete()
                message = "Nothing was captured, so no file was kept"
            }
        }.onFailure {
            file?.delete()
            message = "Recording failed: ${it.message ?: "the encoder stopped"} - no file was kept"
        }
    }

    val fill by animateColorAsState(
        targetValue = if (recording) MaterialTheme.colorScheme.error
        else MaterialTheme.colorScheme.primary,
        animationSpec = tween(200),
        label = "recordFill"
    )

    ScreenScaffold(
        title = "Recorder",
        subtitle = "Saved in the app's private storage",
        onBack = onBack
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
        ) {
            if (!granted) {
                EmptyState(
                    icon = Icons.Rounded.Mic,
                    title = "Microphone access needed",
                    message = "Recording uses MediaRecorder with the microphone source. Without the " +
                        "permission there is nothing to start, so no placeholder timer runs.",
                    actionLabel = "Grant microphone access",
                    onAction = { permissions.launch(Manifest.permission.RECORD_AUDIO) }
                )
                return@Column
            }

            Spacer(Modifier.height(12.dp))
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(132.dp)
                        .clip(CircleShape)
                        .background(fill.copy(alpha = 0.16f))
                        .border(2.dp, fill.copy(alpha = 0.55f), CircleShape)
                        .clickable { if (recording) stopRecording() else startRecording() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (recording) Icons.Rounded.Stop else Icons.Rounded.Mic,
                        contentDescription = if (recording) "Stop recording" else "Start recording",
                        tint = fill,
                        modifier = Modifier.size(52.dp)
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    RecorderEngine.formatElapsed(elapsed),
                    fontSize = 30.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (recording) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    if (recording) "Recording - tap to stop" else "Tap to record AAC audio",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            message?.let {
                Spacer(Modifier.height(10.dp))
                MessageBanner(it, isError = message?.startsWith("Could not") == true)
            }

            SectionLabel("${recordings.size} recordings")
            if (recordings.isEmpty()) {
                GlassCard {
                    Text(
                        "No recordings yet. Files are written as MPEG-4 AAC at 96 kbps into the app's " +
                            "private files directory.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(Modifier.weight(1f)) {
                    items(recordings, key = { it.name }) { item ->
                        GlassCard(Modifier.padding(vertical = 2.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = {
                                    if (playingName == item.name) {
                                        playback.stop()
                                        playingName = null
                                    } else {
                                        playback.stop()
                                        val started = playback.play(item.file) { playingName = null }
                                        playingName = if (started) item.name else null
                                        if (!started) message = "This recording could not be played"
                                    }
                                }) {
                                    Icon(
                                        if (playingName == item.name) Icons.Rounded.Stop
                                        else Icons.Rounded.PlayArrow,
                                        contentDescription = if (playingName == item.name) "Stop" else "Play",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        item.name,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "${RecorderEngine.formatElapsed(item.durationMs)} - " +
                                            RecorderEngine.humanSize(item.size),
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = {
                                    if (playingName == item.name) {
                                        playback.stop()
                                        playingName = null
                                    }
                                    if (item.file.delete()) {
                                        message = "Deleted ${item.name}"
                                        reload()
                                    } else {
                                        message = "The file could not be deleted"
                                    }
                                }) {
                                    Icon(
                                        Icons.Rounded.DeleteOutline,
                                        contentDescription = "Delete recording",
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

private fun hasMicPermission(context: Context): Boolean =
    context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

private fun probeDuration(file: File): Long = runCatching {
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(file.absolutePath)
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
    } finally {
        runCatching { retriever.release() }
    }
}.getOrDefault(0L)

/** MediaRecorder wrapper that always releases the encoder, even after a failed stop. */
private class AudioRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null

    var currentFile: File? = null
        private set

    fun start(file: File): Result<Unit> = runCatching {
        val media = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        media.setAudioSource(MediaRecorder.AudioSource.MIC)
        media.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        media.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        media.setAudioEncodingBitRate(RecorderEngine.BITRATE)
        media.setAudioSamplingRate(RecorderEngine.SAMPLE_RATE)
        media.setOutputFile(file.absolutePath)
        media.prepare()
        media.start()
        recorder = media
        currentFile = file
    }

    fun stop(): Result<Unit> = runCatching {
        recorder?.stop()
        Unit
    }.also {
        runCatching { recorder?.reset() }
        runCatching { recorder?.release() }
        recorder = null
    }
}

/** One MediaPlayer at a time, always released before the next clip starts. */
private class ClipPlayback {

    private var player: MediaPlayer? = null

    fun play(file: File, onFinished: () -> Unit): Boolean {
        stop()
        return runCatching {
            val media = MediaPlayer()
            media.setDataSource(file.absolutePath)
            media.setOnCompletionListener { onFinished() }
            media.prepare()
            media.start()
            player = media
            true
        }.getOrElse {
            stop()
            false
        }
    }

    fun stop() {
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
    }
}
