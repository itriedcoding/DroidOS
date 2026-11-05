package dev.droidos.shell.shell.search

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.droidos.shell.apps.files.FilesRepository
import dev.droidos.shell.apps.notes.NotesRepository
import dev.droidos.shell.core.ServiceLocator
import dev.droidos.shell.shell.BuiltInApps
import dev.droidos.shell.shell.Screen
import dev.droidos.shell.ui.components.EmptyState
import dev.droidos.shell.ui.components.IconBadge
import dev.droidos.shell.ui.components.ScreenScaffold
import kotlinx.coroutines.delay

private enum class Kind { BUILTIN, PACKAGE, NOTE, FILE }

private data class SearchHit(
    val title: String,
    val subtitle: String,
    val kind: Kind,
    val payload: String
)

/**
 * Search over what actually exists on this device: built-in apps, installed packages, the notes
 * database and the one folder the user granted to Files. Each source is queried for real; when a
 * source is unavailable the screen says so instead of showing invented results.
 */
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenScreen: (Screen) -> Unit,
    onLaunchApp: (String) -> Unit
) {
    val context = LocalContext.current
    val notes = remember { NotesRepository(context) }
    val files = remember { FilesRepository(context) }
    val focusRequester = remember { FocusRequester() }

    var query by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<SearchHit>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var fileSourceNote by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
        fileSourceNote = if (files.savedTreeUri() == null) {
            "Files has no folder granted yet, so file names are not searchable"
        } else {
            null
        }
    }

    LaunchedEffect(query) {
        if (query.isBlank()) {
            hits = emptyList()
            searching = false
            return@LaunchedEffect
        }
        searching = true
        delay(200) // debounce typing

        val results = mutableListOf<SearchHit>()
        BuiltInApps.all
            .filter { it.label.contains(query, ignoreCase = true) }
            .forEach {
                results += SearchHit(it.label, "Built-in app", Kind.BUILTIN, it.screen.name)
            }

        val installed = runCatching { ServiceLocator.apps.apps() }.getOrDefault(emptyList())
        installed
            .filter { it.label.contains(query, ignoreCase = true) }
            .take(15)
            .forEach {
                results += SearchHit(it.label, it.packageName, Kind.PACKAGE, it.packageName)
            }

        runCatching { notes.all(query) }.getOrDefault(emptyList())
            .take(10)
            .forEach { note ->
                results += SearchHit(
                    title = note.title.ifBlank { "Untitled note" },
                    subtitle = "Note  \u00b7  " + note.body.replace('\n', ' ').take(60),
                    kind = Kind.NOTE,
                    payload = ""
                )
            }

        val tree = files.savedTreeUri()
        if (tree != null) {
            val listing = files.list(tree, files.rootDocumentId(tree))
            val items = when (listing) {
                is dev.droidos.shell.core.Outcome.Ok -> listing.value
                is dev.droidos.shell.core.Outcome.Err -> {
                    fileSourceNote = "File search unavailable: ${listing.message}"
                    emptyList()
                }
            }
            items
                .filter { it.name.contains(query, ignoreCase = true) }
                .take(10)
                .forEach { item ->
                    results += SearchHit(
                        title = item.name,
                        subtitle = if (item.isDirectory) "Folder  \u00b7  granted storage"
                        else "File  \u00b7  granted storage",
                        kind = Kind.FILE,
                        payload = ""
                    )
                }
        }

        hits = results
        searching = false
    }

    ScreenScaffold(
        title = "Search",
        subtitle = when {
            query.isBlank() -> "Apps, notes and files on this device"
            searching -> "Searching\u2026"
            else -> "${hits.size} result${if (hits.size == 1) "" else "s"}"
        },
        onBack = onBack
    ) {
        Column(Modifier.fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .focusRequester(focusRequester),
                placeholder = { Text("Search apps, notes and files") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )

            fileSourceNote?.let { note ->
                Text(
                    note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
            }

            Spacer(Modifier.height(6.dp))

            when {
                query.isBlank() -> EmptyState(
                    icon = Icons.Rounded.Search,
                    title = "Search everything",
                    message = "Installed apps, built-in tools, your notes and the folder granted to Files."
                )

                searching && hits.isEmpty() -> Column(
                    Modifier.fillMaxWidth().padding(top = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                }

                hits.isEmpty() -> EmptyState(
                    icon = Icons.Rounded.Search,
                    title = "No matches",
                    message = "Nothing on this device matches \"$query\"."
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(hits) { hit ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    when (hit.kind) {
                                        Kind.BUILTIN -> Screen.entries
                                            .firstOrNull { it.name == hit.payload }
                                            ?.let(onOpenScreen)

                                        Kind.PACKAGE -> onLaunchApp(hit.payload)
                                        Kind.NOTE -> onOpenScreen(Screen.NOTES)
                                        Kind.FILE -> onOpenScreen(Screen.FILES)
                                    }
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconBadge(iconFor(hit.kind), tintFor(hit.kind))
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    hit.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    hit.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(
                                Icons.Rounded.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun iconFor(kind: Kind): ImageVector = when (kind) {
    Kind.BUILTIN -> Icons.Rounded.Apps
    Kind.PACKAGE -> Icons.Rounded.Android
    Kind.NOTE -> Icons.AutoMirrored.Rounded.Notes
    Kind.FILE -> Icons.AutoMirrored.Rounded.InsertDriveFile
}

private fun tintFor(kind: Kind): Color = when (kind) {
    Kind.BUILTIN -> Color(0xFF5B8CFF)
    Kind.PACKAGE -> Color(0xFF9BA8C2)
    Kind.NOTE -> Color(0xFF3BD6A6)
    Kind.FILE -> Color(0xFFFFB454)
}
