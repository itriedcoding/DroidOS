package dev.droidos.shell.apps.music.spotify

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.droidos.shell.apps.music.MusicEngine
import dev.droidos.shell.ui.components.GlassCard
import dev.droidos.shell.ui.components.MessageBanner
import dev.droidos.shell.ui.components.ScreenScaffold
import dev.droidos.shell.ui.components.SectionLabel
import kotlinx.coroutines.launch

@Composable
fun SpotifyScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val secrets = remember { SecretStore(context) }
    val auth = remember { SpotifyAuth(context, secrets) }
    val api = remember { SpotifyApi(auth) }
    val scope = rememberCoroutineScope()

    var clientId by remember { mutableStateOf(auth.clientId()) }
    var redirect by remember { mutableStateOf(auth.redirectUri()) }
    var connected by remember { mutableStateOf(auth.isConnected()) }
    var query by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<SpotifyApi.Item>>(emptyList()) }
    var playback by remember { mutableStateOf<SpotifyApi.Playback?>(null) }
    var devices by remember { mutableStateOf<List<String>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun refreshPlayback() {
        scope.launch {
            api.playback()
                .onSuccess { playback = it }
                .onFailure { message = it.message }
            api.devices().onSuccess { list ->
                devices = list.map { device ->
                    device.name + if (device.active) " (active)" else "" +
                        if (device.restricted) " - restricted" else ""
                }
            }
        }
    }

    fun connect() {
        auth.saveClientId(clientId)
        auth.saveRedirectUri(redirect)
        if (auth.clientId().isBlank()) {
            message = "Paste the Client ID from your Spotify app first"
            return
        }
        message = null
        runCatching { context.startActivity(auth.beginAuthorization()) }
            .onFailure { message = "No browser is available to show the Spotify consent page" }
    }

    DisposableEffect(Unit) {
        SpotifyAuthBridge.register { uri ->
            scope.launch {
                busy = true
                auth.completeAuthorization(uri).fold(
                    onSuccess = {
                        connected = true
                        message = "Connected to Spotify"
                        refreshPlayback()
                    },
                    onFailure = { message = it.message ?: "Authorization failed" }
                )
                busy = false
            }
        }
        onDispose { SpotifyAuthBridge.unregister() }
    }

    LaunchedEffect(connected) {
        if (connected) refreshPlayback()
    }

    ScreenScaffold(
        title = "Spotify",
        subtitle = if (connected) "Remote control for your own account"
        else "Connect your own Spotify app",
        onBack = onBack,
        actions = {
            if (connected) {
                IconButton(onClick = {
                    auth.signOut()
                    connected = false
                    playback = null
                    items = emptyList()
                    devices = emptyList()
                    message = "Disconnected. Tokens were deleted from this device."
                }) {
                    Icon(Icons.Rounded.Logout, contentDescription = "Disconnect Spotify")
                }
            }
        }
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            if (!connected) {
                GlassCard {
                    Column(Modifier.fillMaxWidth()) {
                        Text(
                            "Add your Spotify developer app",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "This shell is its own Spotify client, so it needs a Client ID you create at " +
                                "developer.spotify.com/dashboard. That is how Spotify grants access - there is " +
                                "no shared key to borrow.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = clientId,
                            onValueChange = { clientId = it },
                            label = { Text("Client ID") },
                            singleLine = true,
                            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = redirect,
                            onValueChange = { redirect = it },
                            label = { Text("Redirect URI (register this exactly)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = { connect() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(6.dp))
                            Text("Connect Spotify", fontSize = 13.sp)
                        }
                        if (busy) {
                            Spacer(Modifier.height(8.dp))
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        }
                    }
                }

                SectionLabel("What is requested")
                GlassCard {
                    Text(
                        "Scopes: ${SpotifyEngine.SCOPES.joinToString(", ")}. The authorization code flow " +
                            "uses PKCE with S256, so no client secret lives on the device. Tokens are " +
                            "encrypted with a key held by the Android Keystore.\n\n" +
                            "Create the app, add the redirect URI above, then paste the Client ID here and " +
                            "press Connect. Playback control needs Premium and an active Spotify device.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                GlassCard {
                    Column(Modifier.fillMaxWidth()) {
                        Text(
                            playback?.title?.takeIf { it.isNotBlank() } ?: "Nothing playing",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            playback?.artists?.takeIf { it.isNotBlank() } ?: "Start playback in the Spotify app",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (playback != null && playback!!.durationMs > 0L) {
                                "${MusicEngine.formatDuration(playback!!.progressMs)} / " +
                                    MusicEngine.formatDuration(playback!!.durationMs)
                            } else {
                                "--:--"
                            },
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            IconButton(onClick = {
                                scope.launch {
                                    api.skipPrevious().onFailure { message = it.message }
                                    refreshPlayback()
                                }
                            }) {
                                Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous")
                            }
                            IconButton(onClick = {
                                scope.launch {
                                    val playing = playback?.playing == true
                                    (if (playing) api.pause() else api.play("")).onFailure {
                                        message = it.message
                                    }
                                    refreshPlayback()
                                }
                            }) {
                                Icon(
                                    if (playback?.playing == true) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = "Play or pause",
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                            IconButton(onClick = {
                                scope.launch {
                                    api.skipNext().onFailure { message = it.message }
                                    refreshPlayback()
                                }
                            }) {
                                Icon(Icons.Rounded.SkipNext, contentDescription = "Next")
                            }
                        }
                        if (devices.isNotEmpty()) {
                            Text(
                                "Devices: ${devices.joinToString(" | ")}",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Search Spotify") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                busy = true
                                api.search(query).fold(
                                    onSuccess = {
                                        items = it
                                        if (it.isEmpty()) message = "No tracks matched that search"
                                    },
                                    onFailure = { message = it.message }
                                )
                                busy = false
                            }
                        },
                        enabled = !busy && query.isNotBlank()
                    ) { Text("Go", fontSize = 13.sp) }
                }

                if (items.isNotEmpty()) {
                    SectionLabel("${items.size} results")
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items.forEach { item ->
                            GlassCard(onClick = {
                                scope.launch {
                                    api.play(item.uri).fold(
                                        onSuccess = {
                                            message = "Asked Spotify to play ${item.name}"
                                            refreshPlayback()
                                        },
                                        onFailure = { message = it.message }
                                    )
                                }
                            }) {
                                Row(Modifier.fillMaxWidth()) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            item.name,
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            item.artists,
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        MusicEngine.formatDuration(item.durationMs),
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            message?.let {
                Spacer(Modifier.height(10.dp))
                MessageBanner(it, isError = it.contains("error", ignoreCase = true) || it.contains("HTTP"))
            }

            Spacer(Modifier.height(10.dp))
            GlassCard {
                Text(
                    "This is an independent client of the Spotify Web API. It plays nothing itself: it asks " +
                        "Spotify to play on your active device, which is why playback control needs Premium. " +
                        "Nothing is cached from Spotify beyond the access and refresh tokens on this device.",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
