package dev.droidos.shell.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.droidos.shell.core.ServiceLocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Loads an app icon off the main thread and caches it through AppRepository's LruCache,
 * so long lists never decode the same drawable twice.
 */
@Composable
fun AppIcon(packageName: String, size: Dp = 44.dp) {
    val icon by produceState<ImageBitmap?>(initialValue = null, key1 = packageName) {
        value = withContext(Dispatchers.IO) { ServiceLocator.apps.icon(packageName) }
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size / 3.2f))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)),
        contentAlignment = Alignment.Center
    ) {
        val bitmap = icon
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.size(size * 0.86f)
            )
        } else {
            Icon(
                Icons.Rounded.Android,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size * 0.5f)
            )
        }
    }
}
