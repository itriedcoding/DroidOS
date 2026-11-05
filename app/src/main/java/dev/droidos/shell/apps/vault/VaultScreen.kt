package dev.droidos.shell.apps.vault

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Note
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import dev.droidos.shell.core.Outcome
import dev.droidos.shell.ui.BiometricRequest
import dev.droidos.shell.ui.biometricStatus
import dev.droidos.shell.ui.components.EmptyState
import dev.droidos.shell.ui.components.GlassCard
import dev.droidos.shell.ui.components.MessageBanner
import dev.droidos.shell.ui.components.ScreenScaffold
import kotlinx.coroutines.launch

@Composable
fun VaultScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { VaultRepository(context) }
    val scope = rememberCoroutineScope()

    var unlocked by remember { mutableStateOf(false) }
    var trigger by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    var items by remember { mutableStateOf<List<VaultItem>>(emptyList()) }
    var totalBytes by remember { mutableStateOf(0L) }
    var textDialog by remember { mutableStateOf(false) }
    var itemName by remember { mutableStateOf("") }
    var itemBody by remember { mutableStateOf("") }
    var viewing by remember { mutableStateOf<Pair<String, String>?>(null) }
    var exportTarget by remember { mutableStateOf<VaultItem?>(null) }
    var deleteTarget by remember { mutableStateOf<VaultItem?>(null) }

    val biometricAvailable = remember { biometricStatus(context) == BiometricManager.BIOMETRIC_SUCCESS }

    suspend fun reload() {
        items = repository.items()
        totalBytes = repository.totalBytes()
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                when (val result = repository.addFile(uri)) {
                    is Outcome.Ok -> {
                        message = "File encrypted into the vault"
                        isError = false
                        reload()
                    }
                    is Outcome.Err -> {
                        message = result.message
                        isError = true
                    }
                }
            }
        }
    }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri: Uri? ->
        val target = exportTarget
        exportTarget = null
        if (uri != null && target != null) {
            scope.launch {
                when (val result = repository.export(target, uri)) {
                    is Outcome.Ok -> {
                        message = "Decrypted copy written"
                        isError = false
                    }
                    is Outcome.Err -> {
                        message = result.message
                        isError = true
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (biometricAvailable) {
            trigger = 1
        } else {
            unlocked = false
        }
    }

    BiometricRequest(
        trigger = trigger,
        title = "Unlock vault",
        subtitle = "Confirm your identity to decrypt the vault"
    ) { success, error ->
        if (success) {
            unlocked = true
            message = null
        } else if (error != null) {
            message = error
            isError = true
        }
    }

    if (unlocked) {
        LaunchedEffect(Unit) { reload() }
    }

    ScreenScaffold(
        title = "Vault",
        subtitle = if (unlocked) "${items.size} items - ${Fmt.bytes(totalBytes)} encrypted" else "Locked",
        onBack = onBack
    ) {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            if (message != null) {
                MessageBanner(message.orEmpty(), isError = isError)
                Spacer(Modifier.height(8.dp))
            }

            if (!unlocked) {
                EmptyState(
                    icon = Icons.Rounded.Lock,
                    title = if (biometricAvailable) "Vault is locked" else "Biometrics unavailable",
                    message = if (biometricAvailable) {
                        "Contents are encrypted with a key held by the Android Keystore. Authenticate to decrypt them."
                    } else {
                        "No fingerprint or face is enrolled, so the vault cannot gate access with biometrics. " +
                            "Data at rest is still AES-256-GCM encrypted with a Keystore key."
                    },
                    actionLabel = if (biometricAvailable) "Authenticate" else "Continue unguarded",
                    onAction = {
                        if (biometricAvailable) {
                            trigger += 1
                        } else {
                            unlocked = true
                        }
                    }
                )
                return@Column
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(onClick = {
                    itemName = ""
                    itemBody = ""
                    textDialog = true
                }) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Secret note")
                }
                TextButton(onClick = { filePicker.launch(arrayOf("*/*")) }) {
                    Icon(Icons.Rounded.Fingerprint, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Encrypt file")
                }
            }

            if (items.isEmpty()) {
                EmptyState(
                    icon = Icons.Rounded.Lock,
                    title = "Vault is empty",
                    message = "Add a secret note or encrypt a file. Everything is sealed with a hardware-backed key."
                )
                return@Column
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(items, key = { it.id }) { item ->
                    GlassCard(
                        onClick = {
                            when (item.kind) {
                                VaultItem.Kind.TEXT -> scope.launch {
                                    when (val result = repository.readText(item)) {
                                        is Outcome.Ok -> viewing = item.name to result.value
                                        is Outcome.Err -> {
                                            message = result.message
                                            isError = true
                                        }
                                    }
                                }
                                VaultItem.Kind.FILE -> {
                                    exportTarget = item
                                    exporter.launch(item.name)
                                }
                            }
                        }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (item.kind == VaultItem.Kind.TEXT) Icons.Rounded.Note else Icons.Rounded.Download,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    item.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "${item.kind.name.lowercase()} - ${Fmt.bytes(item.sizeBytes)} - ${Fmt.date(item.createdAt)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = {
                                scope.launch {
                                    when (val result = repository.delete(item)) {
                                        is Outcome.Ok -> {
                                            message = "Deleted ${item.name}"
                                            isError = false
                                            reload()
                                        }
                                        is Outcome.Err -> {
                                            message = result.message
                                            isError = true
                                        }
                                    }
                                }
                            }) {
                                Icon(Icons.Rounded.Delete, contentDescription = "Delete ${item.name}")
                            }
                        }
                    }
                }
            }
        }
    }

    if (textDialog) {
        AlertDialog(
            onDismissRequest = { textDialog = false },
            title = { Text("Secret note") },
            text = {
                Column {
                    OutlinedTextField(
                        value = itemName,
                        onValueChange = { itemName = it },
                        label = { Text("Name") },
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = itemBody,
                        onValueChange = { itemBody = it },
                        label = { Text("Secret") },
                        minLines = 3
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = itemName.trim().ifBlank { "Secret" }
                    val body = itemBody
                    textDialog = false
                    scope.launch {
                        when (val result = repository.addText(name, body)) {
                            is Outcome.Ok -> {
                                message = "Secret encrypted"
                                isError = false
                                itemName = ""
                                itemBody = ""
                                reload()
                            }
                            is Outcome.Err -> {
                                message = result.message
                                isError = true
                            }
                        }
                    }
                }) { Text("Encrypt") }
            },
            dismissButton = { TextButton(onClick = { textDialog = false }) { Text("Cancel") } }
        )
    }

    viewing?.let { (name, body) ->
        AlertDialog(
            onDismissRequest = { viewing = null },
            title = { Text(name) },
            text = { Text(body, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = { TextButton(onClick = { viewing = null }) { Text("Close") } }
        )
    }
}
