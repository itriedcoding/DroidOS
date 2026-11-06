package dev.droidos.shell.apps.cleaner

import android.app.Activity
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.droidos.shell.ui.components.EmptyState
import dev.droidos.shell.ui.components.GlassCard
import dev.droidos.shell.ui.components.MessageBanner
import dev.droidos.shell.ui.components.RingStat
import dev.droidos.shell.ui.components.ScreenScaffold
import dev.droidos.shell.ui.components.SectionLabel
import dev.droidos.shell.ui.components.StatBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CleanerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scanner = remember { StorageScanner(context) }
    val scope = rememberCoroutineScope()

    var report by remember { mutableStateOf<ScanReport?>(null) }
    var largest by remember { mutableStateOf<List<MediaFile>>(emptyList()) }
    var scanning by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Set<Long>>(emptySet()) }

    val load: suspend () -> Unit = {
        scanning = true
        report = withContext(Dispatchers.IO) { scanner.scan() }
        largest = withContext(Dispatchers.IO) { scanner.largestMedia() }
        scanning = false
    }

    val permissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        scope.launch { load() }
    }

    val deleter = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            message = "The system deleted the ${selected.size} selected file(s)"
        } else {
            message = "Deletion was cancelled in the system dialog"
        }
        isError = false
        selected = emptySet()
        scope.launch { load() }
    }

    LaunchedEffect(Unit) { load() }

    fun purge() {
        scope.launch {
            scanning = true
            val (freed, removed) = withContext(Dispatchers.IO) { scanner.purge() }
            message = StorageMath.freedMessage(freed, removed)
            isError = false
            load()
        }
    }

    val drive = report?.drives?.firstOrNull()
    val media = report?.media.orEmpty()

    ScreenScaffold(
        title = "Cleaner",
        subtitle = "Real sizes, real deletions",
        onBack = onBack,
        actions = {
            IconButton(onClick = { scope.launch { load() } }, enabled = !scanning) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Rescan")
            }
        }
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            if (scanning && report == null) {
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 60.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Measuring the volume...", fontSize = 12.sp)
                    }
                }
            }

            drive?.let { info ->
                item {
                    GlassCard {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            RingStat(
                                fraction = info.usedFraction,
                                value = StorageMath.percentLabel(info.usedBytes, info.totalBytes),
                                caption = "used",
                                size = 132,
                                strokeWidth = 12
                            )
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    StorageMath.formatBytes(info.usedBytes),
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "used of ${StorageMath.formatBytes(info.totalBytes)}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    "${StorageMath.formatBytes(info.freeBytes)} free",
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }

            message?.let {
                item {
                    Spacer(Modifier.height(6.dp))
                    MessageBanner(it, isError = isError)
                }
            }

            item { SectionLabel("DroidOS caches") }
            item {
                val purgeable = report?.purgeable.orEmpty()
                GlassCard {
                    Column(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text(
                                    StorageMath.formatBytes(report?.purgeableBytes ?: 0L),
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    "${report?.purgeableFiles ?: 0} cache files that can be removed safely",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                Icons.Rounded.Storage,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { purge() },
                            enabled = !scanning && (report?.purgeableBytes ?: 0L) > 0L,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Clear caches") }
                        if (purgeable.isEmpty() && report != null) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "The cache directories are already empty.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (report?.folders.orEmpty().any { it.bytes > 0L }) {
                item { SectionLabel("Where it goes") }
                items(report?.folders.orEmpty().sortedByDescending { it.bytes }) { folder ->
                    if (folder.bytes <= 0L) return@items
                    GlassCard(Modifier.padding(vertical = 3.dp)) {
                        Column(Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(folder.label, fontSize = 13.sp)
                                Text(
                                    StorageMath.formatBytes(folder.bytes),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            StatBar(
                                fraction = StorageMath.shareOf(
                                    folder.bytes,
                                    report?.appBytes?.coerceAtLeast(1L) ?: 1L
                                ),
                                height = 4
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "${folder.files} files - ${if (folder.purgeable) "purgeable" else "kept, app data"}",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item { SectionLabel("Media library") }
            if (report?.mediaPermissionMissing == true) {
                item {
                    GlassCard {
                        Column(Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Rounded.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Media access not granted", fontSize = 13.sp)
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Without read access to the media store the shell cannot measure photos, " +
                                    "video or audio, so it shows nothing rather than a guess.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(10.dp))
                            OutlinedButton(onClick = {
                                permissions.launch(scanner.requiredMediaPermissions().toTypedArray())
                            }) { Text("Grant media access", fontSize = 12.sp) }
                        }
                    }
                }
            } else if (media.isEmpty() && report != null) {
                item {
                    GlassCard {
                        Text(
                            "The media store reported no files.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                item {
                    GlassCard {
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            media.forEach { category ->
                                Column(Modifier.fillMaxWidth()) {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(category.label, fontSize = 12.sp)
                                        Text(
                                            StorageMath.formatBytes(category.bytes) + " / ${category.files}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    StatBar(
                                        fraction = StorageMath.shareOf(
                                            category.bytes,
                                            media.maxOfOrNull { it.bytes }?.coerceAtLeast(1L) ?: 1L
                                        ),
                                        height = 4
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (largest.isNotEmpty()) {
                item { SectionLabel("Largest media files") }
                items(largest) { file ->
                    GlassCard(Modifier.padding(vertical = 2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = file.id in selected,
                                onCheckedChange = { checked ->
                                    selected = if (checked) selected + file.id else selected - file.id
                                }
                            )
                            Column(Modifier.weight(1f)) {
                                Text(file.name, fontSize = 12.sp, maxLines = 1)
                                Text(
                                    "${file.category} - ${StorageMath.formatBytes(file.bytes)}",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                item {
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val uris = largest.filter { it.id in selected }.map { it.uri }
                            if (uris.isEmpty()) {
                                message = "Select at least one file first"
                                isError = false
                                return@Button
                            }
                            runCatching {
                                MediaStore.createDeleteRequest(context.contentResolver, uris)
                            }.onSuccess { request ->
                                deleter.launch(IntentSenderRequest.Builder(request.intentSender).build())
                            }.onFailure {
                                message = "The system refused the delete request: ${it.message}"
                                isError = true
                            }
                        },
                        enabled = selected.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Delete ${selected.size} selected")
                    }
                }
            }

            item {
                Spacer(Modifier.height(10.dp))
                GlassCard {
                    Text(
                        "Caches are removed only from this app's own cache, code-cache and external-cache " +
                            "directories, and the figure you see is measured before and after the deletion. " +
                            "Media files are never touched directly: the request goes to the system, which " +
                            "shows its own confirmation and performs the delete.",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
