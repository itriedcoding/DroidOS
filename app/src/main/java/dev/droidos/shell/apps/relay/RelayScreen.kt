package dev.droidos.shell.apps.relay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.droidos.shell.apps.music.spotify.SecretStore
import dev.droidos.shell.ui.components.EmptyState
import dev.droidos.shell.ui.components.GlassCard
import dev.droidos.shell.ui.components.MessageBanner
import dev.droidos.shell.ui.components.ScreenScaffold
import dev.droidos.shell.ui.components.SectionLabel
import kotlinx.coroutines.launch

private const val TOKEN_KEY = "relay.botToken"

/**
 * Relay is an unaffiliated client of the official Discord HTTP API. It signs in with a bot token
 * the user creates in their own Discord application. User-account tokens are not accepted: driving
 * a personal account through the API breaks Discord's terms.
 */
@Composable
fun RelayScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val secrets = remember { SecretStore(context) }
    val client = remember { RelayClient() }
    val scope = rememberCoroutineScope()

    var token by remember { mutableStateOf(secrets.get(TOKEN_KEY).orEmpty()) }
    var draftToken by remember { mutableStateOf("") }
    var botName by remember { mutableStateOf<String?>(null) }
    var guilds by remember { mutableStateOf<List<RelayClient.Guild>>(emptyList()) }
    var guild by remember { mutableStateOf<RelayClient.Guild?>(null) }
    var channels by remember { mutableStateOf<List<RelayClient.Channel>>(emptyList()) }
    var channel by remember { mutableStateOf<RelayClient.Channel?>(null) }
    var messages by remember { mutableStateOf<List<RelayClient.Message>>(emptyList()) }
    var composer by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun loadGuilds() {
        scope.launch {
            busy = true
            client.verifyToken(token)
                .onSuccess { botName = it }
                .onFailure { error = it.message }
            client.guilds(token).fold(
                onSuccess = {
                    guilds = it
                    if (it.isEmpty()) error = "That bot has not been invited to a server yet"
                },
                onFailure = { error = it.message }
            )
            busy = false
        }
    }

    fun loadChannels(target: RelayClient.Guild) {
        scope.launch {
            busy = true
            client.channels(token, target.id).fold(
                onSuccess = {
                    channels = it
                    if (it.isEmpty()) error = "This bot cannot see any text channels in that server"
                },
                onFailure = { error = it.message }
            )
            busy = false
        }
    }

    fun loadMessages(target: RelayClient.Channel) {
        scope.launch {
            busy = true
            client.messages(token, target.id).fold(
                onSuccess = { messages = it.asReversed() },
                onFailure = { error = it.message }
            )
            busy = false
        }
    }

    ScreenScaffold(
        title = when {
            channel != null -> "#${channel!!.name}"
            guild != null -> guild!!.name
            else -> "Relay"
        },
        subtitle = when {
            channel != null -> "Live channel history"
            guild != null -> "Pick a text channel"
            botName != null -> "Signed in as $botName"
            else -> "Discord bot bridge"
        },
        onBack = {
            when {
                channel != null -> {
                    channel = null
                    messages = emptyList()
                    error = null
                }
                guild != null -> {
                    guild = null
                    channels = emptyList()
                    error = null
                }
                else -> onBack()
            }
        },
        actions = {
            when {
                channel != null -> IconButton(onClick = { loadMessages(channel!!) }) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Reload messages")
                }
                guild != null -> IconButton(onClick = { loadChannels(guild!!) }) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Reload channels")
                }
                token.isNotBlank() -> IconButton(onClick = { loadGuilds() }) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Reload servers")
                }
                else -> Unit
            }
            if (token.isNotBlank()) {
                IconButton(onClick = {
                    secrets.remove(TOKEN_KEY)
                    token = ""
                    botName = null
                    guilds = emptyList()
                    channels = emptyList()
                    messages = emptyList()
                    guild = null
                    channel = null
                    error = null
                }) {
                    Icon(Icons.Rounded.Logout, contentDescription = "Forget the bot token")
                }
            }
        }
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .imePadding()
                .navigationBarsPadding()
        ) {
            if (busy) {
                CircularProgressIndicator(
                    Modifier
                        .padding(16.dp)
                        .size(20.dp),
                    strokeWidth = 2.dp
                )
            }

            when {
                token.isBlank() -> Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    GlassCard {
                        Column(Modifier.fillMaxWidth()) {
                            Text(
                                "Connect a bot you own",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Relay is an independent client of Discord's official API - not Discord, " +
                                    "and not affiliated with it. Create an application at " +
                                    "discord.com/developers/applications, open Bot, reset the token, and " +
                                    "invite the bot to a server you share.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(
                                value = draftToken,
                                onValueChange = { draftToken = it },
                                label = { Text("Bot token") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    val trimmed = draftToken.trim()
                                    if (trimmed.isEmpty()) {
                                        error = "Paste a bot token first"
                                        return@Button
                                    }
                                    token = trimmed
                                    secrets.put(TOKEN_KEY, trimmed)
                                    error = null
                                    loadGuilds()
                                },
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Connect bot", fontSize = 13.sp) }
                        }
                    }

                    SectionLabel("Requirements")
                    GlassCard {
                        Text(
                            "The token is encrypted with an Android Keystore key. Enable the " +
                                "Message Content Intent on the Bot page so message text is returned, and " +
                                "give the bot Read Message History and Send Messages in the channels you " +
                                "care about.\n\nUser-account tokens are deliberately refused: automating a " +
                                "personal account through the API violates Discord's terms.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                }

                channel != null -> {
                    val active = channel!!
                    LazyColumn(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(messages, key = { it.id }) { item ->
                            GlassCard {
                                Column(Modifier.fillMaxWidth()) {
                                    Row(Modifier.fillMaxWidth()) {
                                        Text(
                                            RelayEngine.authorLabel(item.author, item.bot),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            RelayEngine.relativeTime(item.timestamp),
                                            fontSize = 9.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        item.content.ifBlank { "(no text content)" },
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                        item { Spacer(Modifier.height(4.dp)) }
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = composer,
                            onValueChange = { composer = it },
                            placeholder = { Text("Message #${active.name}", fontSize = 12.sp) },
                            modifier = Modifier.weight(1f),
                            maxLines = 3
                        )
                        IconButton(
                            onClick = {
                                val body = composer
                                scope.launch {
                                    busy = true
                                    client.send(token, active.id, body).fold(
                                        onSuccess = {
                                            composer = ""
                                            loadMessages(active)
                                        },
                                        onFailure = { error = it.message }
                                    )
                                    busy = false
                                }
                            },
                            enabled = composer.isNotBlank() && !busy
                        ) {
                            Icon(Icons.Rounded.Send, contentDescription = "Send message")
                        }
                    }
                }

                guild != null -> LazyColumn(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (channels.isEmpty()) {
                        item {
                            EmptyState(
                                icon = Icons.Rounded.Forum,
                                title = "No channels",
                                message = "Refresh, or grant the bot View Channel in this server."
                            )
                        }
                    }
                    items(channels, key = { it.id }) { item ->
                        GlassCard(onClick = {
                            channel = item
                            error = null
                            loadMessages(item)
                        }) {
                            Row(Modifier.fillMaxWidth()) {
                                Text("#", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.size(6.dp))
                                Text(item.name, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.weight(1f))
                                if (item.type == 5) {
                                    Text(
                                        "announcement",
                                        fontSize = 9.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                else -> LazyColumn(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (guilds.isEmpty()) {
                        item {
                            EmptyState(
                                icon = Icons.Rounded.Groups,
                                title = "No servers yet",
                                message = "Invite the bot with the bot scope, then refresh."
                            )
                        }
                    }
                    items(guilds, key = { it.id }) { item ->
                        GlassCard(onClick = {
                            guild = item
                            channels = emptyList()
                            error = null
                            loadChannels(item)
                        }) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(item.name, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "server ${item.id}",
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            error?.let {
                Box(Modifier.padding(horizontal = 16.dp)) { MessageBanner(it, isError = true) }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}
