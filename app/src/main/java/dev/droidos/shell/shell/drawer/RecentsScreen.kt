package dev.droidos.shell.shell.drawer

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import dev.droidos.shell.core.Fmt
import dev.droidos.shell.core.ServiceLocator
import dev.droidos.shell.shell.recents.RecentEntry
import dev.droidos.shell.ui.components.EmptyState
import dev.droidos.shell.ui.components.GlassCard
import dev.droidos.shell.ui.components.ScreenScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Multitasking view. Android does not expose other apps' live task stacks to third-party
 * launchers, so this is the real usage-stats history, ordered by most recent foreground use.
 */
@Composable
fun RecentsScreen(onBack: () -> Unit, onLaunchApp: (String) -> Unit) {
    var entries by remember { mutableStateOf<List<RecentEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var hasAccess by remember { mutableStateOf(ServiceLocator.recents.hasUsageAccess()) }
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(Unit) {
        hasAccess = ServiceLocator.recents.hasUsageAccess()
        entries = ServiceLocator.recents.recent(limit = 20)
        loading = false
    }

    ScreenScaffold(
        title = "Recents",
        subtitle = if (hasAccess) "Apps used in the last 24 hours" else "Usage access required",
        onBack = onBack
    ) {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            if (!hasAccess) {
                EmptyState(
                    icon = Icons.Rounded.History,
                    title = "Usage access needed",
                    message = "DroidOS reads the system usage history to build this list. Android requires you to grant it explicitly.",
                    actionLabel = "Grant access",
                    onAction = {
                        runCatching { context.startActivity(ServiceLocator.recents.usageAccessIntent()) }
                    }
                )
                return@Column
            }

            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Column
            }

            if (entries.isEmpty()) {
                EmptyState(
                    icon = Icons.Rounded.History,
                    title = "Nothing recent",
                    message = "Open a few apps and they will appear here with the time you last used them."
                )
                return@Column
            }

            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(entries, key = { it.packageName }) { entry ->
                    GlassCard(onClick = { onLaunchApp(entry.packageName) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AppAvatar(entry.packageName)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(entry.label, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "Last used ${Fmt.relativeTime(entry.lastUsed, System.currentTimeMillis())}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (entry.totalTimeInForegroundMs > 0) {
                                Text(
                                    Fmt.duration(entry.totalTimeInForegroundMs),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun AppAvatar(packageName: String) {
    val icon by androidx.compose.runtime.produceState<ImageBitmap?>(
        initialValue = null,
        key1 = packageName
    ) {
        value = withContext(Dispatchers.IO) { ServiceLocator.apps.icon(packageName) }
    }
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)),
        contentAlignment = Alignment.Center
    ) {
        val bitmap = icon
        if (bitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.size(38.dp)
            )
        } else {
            Icon(
                Icons.Rounded.Android,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
