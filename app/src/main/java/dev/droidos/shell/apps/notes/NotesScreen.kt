package dev.droidos.shell.apps.notes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import dev.droidos.shell.core.Fmt
import dev.droidos.shell.ui.components.EmptyState
import dev.droidos.shell.ui.components.GlassCard
import dev.droidos.shell.ui.components.ScreenScaffold
import kotlinx.coroutines.launch

@Composable
fun NotesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { NotesRepository(context) }
    val scope = rememberCoroutineScope()

    var notes by remember { mutableStateOf<List<Note>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Note?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }

    suspend fun reload() {
        notes = repository.all(query)
        loading = false
    }

    LaunchedEffect(query) { reload() }

    if (showEditor) {
        val draft = editing ?: Note(title = "", body = "", updatedAt = System.currentTimeMillis(), pinned = false)
        NoteEditor(
            note = draft,
            isNew = editing == null,
            onCancel = {
                showEditor = false
                editing = null
            },
            onSave = { title, body ->
                scope.launch {
                    repository.save(draft.copy(title = title, body = body))
                    showEditor = false
                    editing = null
                    reload()
                }
            }
        )
        return
    }

    ScreenScaffold(
        title = "Notes",
        subtitle = "${notes.size} notes stored on this device",
        onBack = onBack
    ) {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                placeholder = { Text("Search notes") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )
            Spacer(Modifier.height(8.dp))

            Box(Modifier.fillMaxSize()) {
                when {
                    loading -> Unit

                    notes.isEmpty() -> EmptyState(
                        icon = Icons.Rounded.Notes,
                        title = if (query.isBlank()) "No notes yet" else "No matches",
                        message = if (query.isBlank()) {
                            "Notes are saved in a local SQLite database and never leave the device."
                        } else {
                            "No note contains \"$query\"."
                        },
                        actionLabel = if (query.isBlank()) "Write the first note" else null,
                        onAction = if (query.isBlank()) {
                            {
                                editing = null
                                showEditor = true
                            }
                        } else null
                    )

                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(notes, key = { it.id }) { note ->
                            GlassCard(
                                onClick = {
                                    editing = note
                                    showEditor = true
                                }
                            ) {
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (note.pinned) {
                                            Icon(
                                                Icons.Rounded.PushPin,
                                                contentDescription = "Pinned",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                        }
                                        Text(
                                            note.title.ifBlank { "Untitled" },
                                            style = MaterialTheme.typography.titleMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(onClick = { scope.launch { repository.delete(note.id); reload() } }) {
                                            Icon(Icons.Rounded.Delete, contentDescription = "Delete note")
                                        }
                                    }
                                    if (note.body.isNotBlank()) {
                                        Text(
                                            note.body,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        Fmt.relativeTime(note.updatedAt, System.currentTimeMillis()),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        item { Spacer(Modifier.height(80.dp)) }
                    }
                }

                FloatingActionButton(
                    onClick = {
                        editing = null
                        showEditor = true
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(20.dp)
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "New note")
                }
            }
        }
    }
}

@Composable
private fun NoteEditor(
    note: Note,
    isNew: Boolean,
    onCancel: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var title by remember { mutableStateOf(note.title) }
    var body by remember { mutableStateOf(note.body) }

    ScreenScaffold(
        title = if (isNew) "New note" else "Edit note",
        onBack = onCancel,
        actions = {
            TextButton(
                onClick = { onSave(title.trim(), body) },
                enabled = title.isNotBlank() || body.isNotBlank()
            ) { Text("Save") }
        }
    ) {
        Column(Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                label = { Text("Note") },
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}
