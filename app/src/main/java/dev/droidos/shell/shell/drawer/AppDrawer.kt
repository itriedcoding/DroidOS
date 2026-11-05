package dev.droidos.shell.shell.drawer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.droidos.shell.core.ServiceLocator
import dev.droidos.shell.data.model.AppSettings
import dev.droidos.shell.shell.BuiltInApp
import dev.droidos.shell.shell.BuiltInApps
import dev.droidos.shell.shell.Screen
import dev.droidos.shell.shell.apps.AppEntry
import dev.droidos.shell.ui.components.EmptyState
import dev.droidos.shell.ui.components.ScreenScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AppDrawer(
    onBack: () -> Unit,
    onLaunchApp: (String) -> Unit,
    onOpenScreen: (Screen) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            apps = ServiceLocator.apps.apps()
        } catch (e: Exception) {
            error = e.message ?: "Could not read installed apps"
        } finally {
            loading = false
        }
    }

    val filteredBuiltIns = remember(query) {
        if (query.isBlank()) BuiltInApps.all
        else BuiltInApps.all.filter { it.label.contains(query, ignoreCase = true) }
    }
    val filteredApps = remember(query, apps) {
        if (query.isBlank()) apps
        else apps.filter { it.label.contains(query, ignoreCase = true) }
    }

    ScreenScaffold(title = "All apps", subtitle = "${filteredApps.size + filteredBuiltIns.size} apps", onBack = onBack) {
        Column(Modifier.fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                placeholder = { Text("Search apps") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )

            Spacer(Modifier.height(8.dp))

            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                error != null -> EmptyState(
                    icon = Icons.Rounded.Android,
                    title = "App list unavailable",
                    message = error ?: "Unknown error"
                )

                filteredBuiltIns.isEmpty() && filteredApps.isEmpty() -> EmptyState(
                    icon = Icons.Rounded.Search,
                    title = "No matches",
                    message = "Nothing installed matches \"$query\"."
                )

                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 82.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(filteredBuiltIns, key = { "builtin-${it.screen.name}" }) { app ->
                        BuiltInTile(app) { onOpenScreen(app.screen) }
                    }
                    items(filteredApps, key = { it.packageName }) { app ->
                        AppTile(app, onLaunchApp)
                    }
                }
            }
        }
    }
}

@Composable
private fun BuiltInTile(app: BuiltInApp, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(app.tint.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(app.icon, contentDescription = app.label, tint = app.tint, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            app.label,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun AppTile(app: AppEntry, onLaunch: (String) -> Unit) {
    val icon by androidx.compose.runtime.produceState<ImageBitmap?>(
        initialValue = null,
        key1 = app.packageName
    ) {
        value = withContext(Dispatchers.IO) { ServiceLocator.apps.icon(app.packageName) }
    }

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable { onLaunch(app.packageName) }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)),
            contentAlignment = Alignment.Center
        ) {
            val bitmap = icon
            if (bitmap != null) {
                androidx.compose.foundation.Image(
                    bitmap = bitmap,
                    contentDescription = app.label,
                    modifier = Modifier.size(46.dp)
                )
            } else {
                Icon(
                    Icons.Rounded.Android,
                    contentDescription = app.label,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            app.label,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}
