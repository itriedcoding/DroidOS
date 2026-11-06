package dev.droidos.shell.apps.gallery

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import dev.droidos.shell.ui.components.EmptyState
import dev.droidos.shell.ui.components.MessageBanner
import dev.droidos.shell.ui.components.ScreenScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class Photo(
    val uri: Uri,
    val name: String,
    val takenSeconds: Long,
    val sizeBytes: Long
)

@Composable
fun GalleryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val widthDp = LocalConfiguration.current.screenWidthDp

    var photos by remember { mutableStateOf<List<Photo>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var granted by remember { mutableStateOf(hasMediaAccess(context)) }
    var viewerIndex by remember { mutableStateOf<Int?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { result ->
        granted = result
        if (!result) error = "Photo access was denied, so the gallery cannot list anything."
    }

    fun reload() {
        scope.launch {
            loading = true
            error = null
            val result = withContext(Dispatchers.IO) { queryPhotos(context) }
            photos = result
            if (result.isEmpty()) error = "No images were found in MediaStore on this device."
            loading = false
        }
    }

    LaunchedEffect(granted) {
        if (granted) reload()
    }

    ScreenScaffold(
        title = "Gallery",
        subtitle = if (photos.isEmpty()) "On-device images" else "${photos.size} images",
        onBack = onBack
    ) {
        Column(Modifier.fillMaxSize()) {
            when {
                !granted -> EmptyState(
                    icon = Icons.Rounded.PhotoLibrary,
                    title = "Photo access needed",
                    message = "The gallery reads images through MediaStore and needs the media " +
                        "permission to show them.",
                    actionLabel = "Grant access",
                    onAction = {
                        permissionLauncher.launch(
                            if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES
                            else Manifest.permission.READ_EXTERNAL_STORAGE
                        )
                    }
                )

                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.dp)
                }

                photos.isEmpty() -> EmptyState(
                    icon = Icons.Rounded.PhotoLibrary,
                    title = "Nothing here yet",
                    message = "Images saved in DCIM, Pictures, Downloads or Screenshots appear here.",
                    actionLabel = "Scan again",
                    onAction = { reload() }
                )

                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(GalleryEngine.columns(widthDp)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(photos, key = { _, photo -> photo.uri.toString() }) { index, photo ->
                        Box(
                            Modifier
                                .padding(3.dp)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.Black.copy(alpha = 0.4f))
                        ) {
                            PhotoThumbnail(
                                uri = photo.uri,
                                name = photo.name,
                                onClick = { viewerIndex = index },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }

            error?.let {
                Box(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    MessageBanner(it, isError = photos.isEmpty())
                }
            }
        }
    }

    viewerIndex?.let { index ->
        val photo = photos.getOrNull(index) ?: return@let
        PhotoViewer(
            photo = photo,
            position = GalleryEngine.positionLabel(index, photos.size),
            onClose = { viewerIndex = null },
            onPrevious = { if (index > 0) viewerIndex = index - 1 },
            onNext = { if (index < photos.lastIndex) viewerIndex = index + 1 }
        )
    }
}

@Composable
private fun PhotoThumbnail(
    uri: Uri,
    name: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, uri) {
        value = withContext(Dispatchers.IO) { decode(context, uri, 320)?.asImageBitmap() }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(10.dp))
                    .pointerInput(uri) { detectTapGestures { onClick() } }
            )
        } else {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 1.5.dp)
        }
    }
}

@Composable
private fun PhotoViewer(
    photo: Photo,
    position: String,
    onClose: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    val context = LocalContext.current
    var scale by remember(photo.uri) { mutableFloatStateOf(1f) }
    var offset by remember(photo.uri) { mutableStateOf(Offset.Zero) }
    val bitmap by produceState<ImageBitmap?>(initialValue = null, photo.uri) {
        value = withContext(Dispatchers.IO) { decode(context, photo.uri, 1_600)?.asImageBitmap() }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            val image = bitmap
            if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = photo.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        )
                        .pointerInput(photo.uri) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 6f)
                                offset = if (scale <= 1f) Offset.Zero else offset + pan
                            }
                        }
                )
            } else {
                CircularProgressIndicator(
                    Modifier.align(Alignment.Center).size(28.dp),
                    strokeWidth = 2.dp
                )
            }

            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp)) {
                Text(photo.name, color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "$position  -  ${GalleryEngine.takenLabelWithTime(photo.takenSeconds)}  -  " +
                        GalleryEngine.byteSize(photo.sizeBytes),
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 11.sp
                )
            }

            IconButton(
                onClick = onClose,
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Color.White)
            }

            Text(
                "<",
                color = Color.White,
                fontSize = 28.sp,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 18.dp)
                    .pointerInput(Unit) { detectTapGestures { onPrevious() } }
            )
            Text(
                ">",
                color = Color.White,
                fontSize = 28.sp,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 18.dp)
                    .pointerInput(Unit) { detectTapGestures { onNext() } }
            )
        }
    }
}

private fun hasMediaAccess(context: Context): Boolean {
    val permission =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES
        else Manifest.permission.READ_EXTERNAL_STORAGE
    return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

private fun queryPhotos(context: Context): List<Photo> {
    val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.DATE_TAKEN,
        MediaStore.Images.Media.DATE_ADDED,
        MediaStore.Images.Media.SIZE
    )
    val photos = mutableListOf<Photo>()
    runCatching {
        context.contentResolver.query(
            collection,
            projection,
            null,
            null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val takenColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val addedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            while (cursor.moveToNext() && photos.size < 5_000) {
                val id = cursor.getLong(idColumn)
                val taken = cursor.getLong(takenColumn)
                val addedSeconds = cursor.getLong(addedColumn)
                photos += Photo(
                    uri = ContentUris.withAppendedId(collection, id),
                    name = cursor.getString(nameColumn) ?: "image",
                    takenSeconds = if (taken > 0L) taken / 1000L else addedSeconds,
                    sizeBytes = cursor.getLong(sizeColumn)
                )
            }
        }
    }
    return photos
}

private fun decode(context: Context, uri: Uri, targetPx: Int): Bitmap? = runCatching {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    val options = BitmapFactory.Options().apply {
        inSampleSize = GalleryEngine.sampleSize(bounds.outWidth, bounds.outHeight, targetPx)
    }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
}.getOrNull()
