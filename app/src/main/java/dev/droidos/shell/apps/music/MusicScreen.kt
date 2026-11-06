package dev.droidos.shell.apps.music

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import kotlin.math.roundToInt

@Composable
fun MusicScreen(onBack: () -> Unit, onOpenSpotify: () -> Unit) {
    val context = LocalContext.current
    val library = remember { MusicLibrary(context) }
    val player = remember { MusicPlayer(context, library) }
    val scope = rememberCoroutineScope()

    var granted by remember { mutableStateOf(library.hasPermission()) }
    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var index by remember { mutableStateOf(-1) }
    var queue by remember { mutableStateOf<List<Track>>(emptyList()) }
    var playing by remember { mutableStateOf(false) }
    var position by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var shuffle by remember { mutableStateOf(false) }
    var repeat by remember { mutableStateOf(RepeatMode.OFF) }
    var eqOpen by remember { mutableStateOf(false) }
    var presets by remember { mutableStateOf<List<String>>(emptyList()) }
    var bands by remember { mutableStateOf<List<EqBand>>(emptyList()) }
    var preset by remember { mutableStateOf(-1) }

    val filtered = remember(tracks, query) { MusicEngine.filter(tracks, query) }
    val current = queue.getOrNull(index)

    val reload: () -> Unit = {
        scope.launch {
            val result = withContext(Dispatchers.IO) { library.load() }
            result.onSuccess {
                tracks = MusicEngine.sortByTitle(it)
                message = null
            }.onFailure {
                message = "Could not read the media library: ${it.message}"
            }
        }
    }

    val permissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        granted = library.hasPermission()
        if (granted) reload() else message = "Audio access was not granted, so the library stays empty"
    }

    LaunchedEffect(granted) {
        if (granted) reload()
    }

    DisposableEffect(Unit) {
        onDispose { player.stop() }
    }

    LaunchedEffect(playing, index) {
        while (playing) {
            position = player.positionMs()
            val live = player.durationMs()
            if (live > 0L) duration = live
            delay(500)
        }
    }

    fun playAt(list: List<Track>, target: Int) {
        if (target !in list.indices) return
        queue = list
        index = target
        position = 0L
        duration = list[target].durationMs
        val started = player.play(list[target]) {
            // Completion arrives on the main thread; the delegates below read live state.
            val next = MusicEngine.advance(target, list.size, shuffle, repeat, automatic = true)
            if (next >= 0) {
                playAt(list, next)
            } else {
                playing = false
                index = -1
                position = 0L
            }
        }
        playing = started
        if (!started) message = player.lastError
        presets = player.presetNames()
        preset = player.currentPreset()
        bands = player.bands()
    }

    fun toggle() {
        when {
            index < 0 && filtered.isNotEmpty() -> playAt(filtered, 0)
            index < 0 -> Unit
            player.isPlaying() -> {
                player.pause()
                playing = false
            }
            else -> playing = player.resume()
        }
    }

    fun skip(forward: Boolean) {
        if (queue.isEmpty()) return
        val next = if (forward) {
            MusicEngine.advance(index, queue.size, shuffle, repeat, automatic = false)
        } else {
            MusicEngine.rewind(index, queue.size, shuffle, repeat)
        }
        if (next >= 0) playAt(queue, next)
    }

    ScreenScaffold(
        title = "Music",
        subtitle = if (tracks.isEmpty()) "Device library" else "${tracks.size} tracks on this device",
        onBack = onBack,
        actions = {
            IconButton(onClick = onOpenSpotify) {
                Icon(
                    Icons.Rounded.Headphones,
                    contentDescription = "Open Spotify",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = { eqOpen = !eqOpen },
                enabled = current != null
            ) {
                Icon(
                    Icons.Rounded.GraphicEq,
                    contentDescription = if (eqOpen) "Hide the equalizer" else "Show the equalizer",
                    tint = if (eqOpen) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
        ) {
            if (!granted) {
                EmptyState(
                    icon = Icons.Rounded.MusicNote,
                    title = "No access to your music",
                    message = "The player reads the device's own media index. Without audio access there " +
                        "is nothing to list, and nothing is invented to fill the space.",
                    actionLabel = "Grant audio access",
                    onAction = { permissions.launch(library.requiredPermissions().toTypedArray()) }
                )
                return@Column
            }

            Spacer(Modifier.height(8.dp))
            GlassCard {
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        current?.title ?: "Nothing playing",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (current != null) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        current?.let { "${it.artist} - ${it.album}" } ?: "Pick a track below",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(8.dp))
                    Slider(
                        value = if (duration > 0L) position.toFloat().coerceIn(0f, duration.toFloat()) else 0f,
                        onValueChange = { position = it.toLong() },
                        onValueChangeFinished = { player.seekTo(position) },
                        valueRange = 0f..(if (duration > 0L) duration.toFloat() else 1f)
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(MusicEngine.formatDuration(position), fontSize = 10.sp)
                        Text(MusicEngine.formatDuration(duration), fontSize = 10.sp)
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { shuffle = !shuffle }) {
                            Icon(
                                Icons.Rounded.Shuffle,
                                contentDescription = "Shuffle",
                                tint = if (shuffle) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { skip(false) }, enabled = queue.isNotEmpty()) {
                            Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous track")
                        }
                        IconButton(onClick = { toggle() }, enabled = current != null || filtered.isNotEmpty()) {
                            Icon(
                                if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (playing) "Pause" else "Play",
                                modifier = Modifier.size(34.dp)
                            )
                        }
                        IconButton(onClick = { skip(true) }, enabled = queue.isNotEmpty()) {
                            Icon(Icons.Rounded.SkipNext, contentDescription = "Next track")
                        }
                        IconButton(onClick = { repeat = MusicEngine.nextRepeatMode(repeat) }) {
                            Icon(
                                if (repeat == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                                contentDescription = repeat.label,
                                tint = if (repeat == RepeatMode.OFF) MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            message?.let {
                Spacer(Modifier.height(8.dp))
                MessageBanner(it, isError = true)
            }

            if (eqOpen) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    SectionLabel("Equalizer")
                    GlassCard {
                        Column(Modifier.fillMaxWidth()) {
                            if (!player.equalizerAttached()) {
                                Text(
                                    "The platform exposes no equalizer for this audio session, so the " +
                                        "controls stay away rather than pretend to work.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                if (presets.isEmpty()) {
                                    Text(
                                        "This build has no presets; the band controls below are still live.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        items(presets.size) { presetIndex ->
                                            FilterChip(
                                                selected = preset == presetIndex,
                                                onClick = {
                                                    if (player.usePreset(presetIndex)) {
                                                        preset = player.currentPreset()
                                                        bands = player.bands()
                                                    }
                                                },
                                                label = {
                                                    Text(presets[presetIndex], fontSize = 11.sp)
                                                }
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.height(10.dp))
                                bands.forEach { band ->
                                    if (band.min < band.max) {
                                        Column(Modifier.fillMaxWidth()) {
                                            Row(
                                                Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("${band.hz} Hz", fontSize = 10.sp)
                                                Text(
                                                    "${band.level / 100} dB",
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Slider(
                                                value = band.level.toFloat(),
                                                onValueChange = { level ->
                                                    val rounded = level.roundToInt().toShort()
                                                    if (player.setBandLevel(band.index, rounded)) {
                                                        bands = bands.map { existing ->
                                                            if (existing.index == band.index)
                                                                existing.copy(level = rounded)
                                                            else existing
                                                        }
                                                    }
                                                },
                                                valueRange = band.min.toFloat()..band.max.toFloat()
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            } else {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search title, artist or album", fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                SectionLabel("${filtered.size} of ${tracks.size} tracks")
                if (filtered.isEmpty()) {
                    EmptyState(
                        icon = Icons.Rounded.MusicNote,
                        title = if (tracks.isEmpty()) "No music yet" else "No matches",
                        message = MusicEngine.emptyMessage(query)
                    )
                } else {
                    LazyColumn(Modifier.weight(1f)) {
                        items(filtered, key = { it.id }) { track ->
                            val isCurrent = current?.id == track.id
                            GlassCard(
                                modifier = Modifier.padding(vertical = 2.dp),
                                onClick = { playAt(filtered, filtered.indexOf(track)) }
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            track.title,
                                            fontSize = 14.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = if (isCurrent) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            "${track.artist} - ${track.album}",
                                            fontSize = 10.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        MusicEngine.formatDuration(track.durationMs),
                                        fontSize = 11.sp,
                                        color = if (isCurrent) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        item { Spacer(Modifier.height(24.dp)) }
                    }
                }
            }
        }
    }
}
