package dev.droidos.shell.apps.feeds

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.droidos.shell.ui.components.EmptyState
import dev.droidos.shell.ui.components.GlassCard
import dev.droidos.shell.ui.components.MessageBanner
import dev.droidos.shell.ui.components.ScreenScaffold
import dev.droidos.shell.ui.components.SectionLabel
import kotlinx.coroutines.launch

/** Feed subscriptions live in this app's private preferences; items are fetched on demand. */
private class FeedStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("droidos.feeds", Context.MODE_PRIVATE)

    fun urls(): List<String> = prefs.getString("urls", null)
        ?.split('\n')
        ?.filter { it.isNotBlank() }
        .orEmpty()

    fun add(url: String) {
        val current = urls().toMutableList()
        if (current.none { it.equals(url, ignoreCase = true) }) {
            current += url
            prefs.edit().putString("urls", current.joinToString("\n")).apply()
        }
    }

    fun remove(url: String) {
        val remaining = urls().filterNot { it.equals(url, ignoreCase = true) }
        prefs.edit().putString("urls", remaining.joinToString("\n")).apply()
    }
}

@Composable
fun FeedsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { FeedStore(context) }
    val client = remember { FeedClient() }
    val scope = rememberCoroutineScope()

    var feeds by remember { mutableStateOf(store.urls()) }
    var items by remember { mutableStateOf<List<FeedItem>>(emptyList()) }
    var draft by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun refresh() {
        if (feeds.isEmpty()) return
        scope.launch {
            busy = true
            error = null
            val collected = mutableListOf<FeedItem>()
            val failures = mutableListOf<String>()
            for (url in feeds) {
                client.fetch(url).fold(
                    onSuccess = { collected += it },
                    onFailure = { failures += "${FeedEngine.hostLabel(url)}: ${it.message}" }
                )
            }
            items = FeedEngine.dedupe(collected)
            if (failures.isNotEmpty()) error = failures.joinToString("\n")
            if (collected.isEmpty() && failures.isEmpty()) error = "Those feeds returned no entries"
            busy = false
        }
    }

    LaunchedEffect(feeds) {
        if (feeds.isNotEmpty() && items.isEmpty()) refresh()
    }

    ScreenScaffold(
        title = "Feeds",
        subtitle = when {
            feeds.isEmpty() -> "RSS and Atom reader"
            items.isEmpty() -> "${feeds.size} sources"
            else -> "${items.size} entries from ${feeds.size} sources"
        },
        onBack = onBack,
        actions = {
            IconButton(onClick = { refresh() }, enabled = feeds.isNotEmpty() && !busy) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Refresh feeds")
            }
        }
    ) {
        LazyColumn(
            Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        label = { Text("Feed URL") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            val normalized = FeedEngine.normaliseUrl(draft)
                            if (normalized == null) {
                                error = "Enter an http or https URL"
                                return@Button
                            }
                            store.add(normalized)
                            feeds = store.urls()
                            draft = ""
                            error = null
                        },
                        enabled = draft.isNotBlank()
                    ) { Text("Add") }
                }
            }

            if (feeds.isNotEmpty()) {
                item { SectionLabel("Sources") }
                items(feeds, key = { "feed-$it" }) { url ->
                    GlassCard {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                FeedEngine.hostLabel(url),
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            IconButton(onClick = {
                                store.remove(url)
                                feeds = store.urls()
                                items = items.filterNot { it.source == FeedEngine.hostLabel(url) }
                            }) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = "Remove feed",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (busy) {
                item {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    }
                }
            }

            when {
                feeds.isEmpty() -> item {
                    EmptyState(
                        icon = Icons.Rounded.Newspaper,
                        title = "Add a feed",
                        message = "Paste an RSS or Atom URL. Entries are fetched live over HTTP - nothing " +
                            "is bundled with the app.",
                        actionLabel = null,
                        onAction = null
                    )
                }

                items.isEmpty() && !busy -> item {
                    EmptyState(
                        icon = Icons.Rounded.Newspaper,
                        title = "No entries",
                        message = "Refresh, or check that the URL points at the feed itself rather than " +
                            "the site's home page.",
                        actionLabel = null,
                        onAction = null
                    )
                }

                else -> {
                    item { SectionLabel("${items.size} entries") }
                    items(items, key = { it.link }) { entry ->
                        GlassCard(onClick = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(entry.link)))
                            }.onFailure { error = "No app can open ${entry.link}" }
                        }) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(
                                    entry.title,
                                    fontSize = 13.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (entry.summary.isNotBlank()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        entry.summary,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "${entry.source}  -  ${FeedEngine.relativeTime(entry.publishedAt)}",
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            error?.let {
                item {
                    Spacer(Modifier.height(8.dp))
                    MessageBanner(it, isError = true)
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
