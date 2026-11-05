package dev.droidos.shell.apps.files

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.dp
import dev.droidos.shell.core.Fmt
import dev.droidos.shell.core.Outcome
import dev.droidos.shell.ui.components.EmptyState
import dev.droidos.shell.ui.components.MessageBanner
import dev.droidos.shell.ui.components.ScreenScaffold
import kotlinx.coroutines.launch

@Composable
fun FilesScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repository = remember { FilesRepository(context) }
    val scope = rememberCoroutineScope()

    var treeUri by remember { mutableStateOf(repository.savedTreeUri()) }
    var pathStack by remember { mutableStateOf(listOf<String>()) }
    var items by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    var folderDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<FileItem?>(null) }
    var deleteTarget by remember { mutableStateOf<FileItem?>(null) }
    var textInput by remember { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            repository.saveTreeUri(uri)
            treeUri = uri
            pathStack = listOf(repository.rootDocumentId(uri))
        }
    }

    fun load() {
        val uri = treeUri ?: return
        val current = pathStack.lastOrNull() ?: return
        scope.launch {
            loading = true
            when (val result = repository.list(uri, current)) {
                is Outcome.Ok -> {
                    items = result.value
                    message = null
                }
                is Outcome.Err -> {
                    message = result.message
                    isError = true
                }
            }
            loading = false
        }
    }

    LaunchedEffect(treeUri, pathStack) {
        if (treeUri == null) {
            treeUri = repository.savedTreeUri()
            treeUri?.let { pathStack = listOf(repository.rootDocumentId(it)) }
        } else {
            load()
        }
    }

    val canGoUp = pathStack.size > 1
    val goBack: () -> Unit = {
        if (canGoUp) pathStack = pathStack.dropLast(1) else onBack()
    }

    ScreenScaffold(
        title = "Files",
        subtitle = if (treeUri == null) "No folder granted" else "${items.size} items - level ${pathStack.size}",
        onBack = goBack,
        actions = {
            IconButton(onClick = { picker.launch(null) }) {
                Icon(Icons.Rounded.Search, contentDescription = "Change folder")
            }
        }
    ) {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            if (message != null) {
                MessageBanner(message.orEmpty(), isError = isError)
                Spacer(Modifier.height(8.dp))
            }

            if (treeUri == null) {
                EmptyState(
                    icon = Icons.Rounded.FolderOpen,
                    title = "Choose a folder",
                    message = "DroidOS browses storage through the system folder picker, so you decide exactly which directory it can read and write.",
                    actionLabel = "Pick a folder",
                    onAction = { picker.launch(null) }
                )
                return@Column
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(onClick = {
                    textInput = ""
                    folderDialog = true
                }) {
                    Icon(Icons.Rounded.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("New folder")
                }
                TextButton(onClick = {
                    repository.clearTreeUri()
                    treeUri = null
                    pathStack = emptyList()
                    items = emptyList()
                }) {
                    Text("Revoke access")
                }
            }

            if (loading) {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(40.dp))
                    CircularProgressIndicator()
                }
                return@Column
            }

            if (items.isEmpty()) {
                EmptyState(
                    icon = Icons.Rounded.Folder,
                    title = "This folder is empty",
                    message = "Create a folder here or pick a different directory."
                )
                return@Column
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                items(items, key = { it.documentId }) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (item.isDirectory) {
                                    pathStack = pathStack + item.documentId
                                } else {
                                    when (val result = repository.open(treeUri!!, item)) {
                                        is Outcome.Err -> {
                                            message = result.message
                                            isError = true
                                        }
                                        is Outcome.Ok -> Unit
                                    }
                                }
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (item.isDirectory) Icons.Rounded.Folder else Icons.Rounded.InsertDriveFile,
                            contentDescription = null,
                            tint = if (item.isDirectory) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                            Text(
                                if (item.isDirectory) "Folder" else "${Fmt.bytes(item.size)} - ${Fmt.date(item.lastModified)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = {
                            textInput = item.name
                            renameTarget = item
                        }) {
                            Icon(Icons.Rounded.DriveFileRenameOutline, contentDescription = "Rename ${item.name}")
                        }
                        IconButton(onClick = { deleteTarget = item }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Delete ${item.name}")
                        }
                    }
                }
            }
        }
    }

    if (folderDialog) {
        AlertDialog(
            onDismissRequest = { folderDialog = false },
            title = { Text("New folder") },
            text = {
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    label = { Text("Folder name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = textInput.trim()
                    folderDialog = false
                    if (name.isEmpty()) return@TextButton
                    val uri = treeUri ?: return@TextButton
                    scope.launch {
                        when (val result = repository.createFolder(uri, pathStack.last(), name)) {
                            is Outcome.Ok -> {
                                message = "Created $name"
                                isError = false
                                load()
                            }
                            is Outcome.Err -> {
                                message = result.message
                                isError = true
                            }
                        }
                    }
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { folderDialog = false }) { Text("Cancel") } }
        )
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename") },
            text = {
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    label = { Text("New name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = textInput.trim()
                    renameTarget = null
                    val uri = treeUri ?: return@TextButton
                    if (name.isEmpty()) return@TextButton
                    scope.launch {
                        when (val result = repository.rename(uri, target.documentId, name)) {
                            is Outcome.Ok -> {
                                message = "Renamed to $name"
                                isError = false
                                load()
                            }
                            is Outcome.Err -> {
                                message = result.message
                                isError = true
                            }
                        }
                    }
                }) { Text("Rename") }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancel") } }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete ${target.name}?") },
            text = { Text("This permanently removes the item from storage.") },
            confirmButton = {
                TextButton(onClick = {
                    deleteTarget = null
                    val uri = treeUri ?: return@TextButton
                    scope.launch {
                        when (val result = repository.delete(uri, target.documentId)) {
                            is Outcome.Ok -> {
                                message = "Deleted ${target.name}"
                                isError = false
                                load()
                            }
                            is Outcome.Err -> {
                                message = result.message
                                isError = true
                            }
                        }
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }
}
