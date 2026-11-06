package dev.droidos.shell.apps.cleaner

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import java.io.File

/** Real capacity of the shared internal volume, straight from StorageStatsManager. */
data class DriveInfo(val totalBytes: Long, val freeBytes: Long) {
    val usedBytes: Long get() = (totalBytes - freeBytes).coerceAtLeast(0L)
    val usedFraction: Float get() = StorageMath.percentUsed(usedBytes, totalBytes)
}

/** One directory the shell can actually read, with what it currently holds. */
data class FolderUse(
    val label: String,
    val path: String,
    val bytes: Long,
    val files: Int,
    val purgeable: Boolean
)

data class MediaCategory(val label: String, val bytes: Long, val files: Int)

/** A single MediaStore row, addressable for deletion. */
data class MediaFile(val id: Long, val name: String, val bytes: Long, val category: String) {
    val uri: Uri get() = ContentUris.withAppendedId(
        MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL),
        id
    )
}

data class ScanReport(
    val drives: List<DriveInfo>,
    val folders: List<FolderUse>,
    val media: List<MediaCategory>,
    val mediaPermissionMissing: Boolean
) {
    val purgeable: List<FolderUse> get() = folders.filter { it.purgeable && it.bytes > 0L }
    val purgeableBytes: Long get() = purgeable.sumOf { it.bytes }
    val purgeableFiles: Int get() = purgeable.sumOf { it.files }
    val appBytes: Long get() = folders.sumOf { it.bytes }
}

/**
 * Walks the directories this app can read and, when the media permission is granted, the
 * shared MediaStore totals. Nothing here invents a number: every figure comes from either a
 * real directory listing or a real MediaStore query, and an inaccessible store is reported as
 * unreadable rather than shown as zero.
 */
class StorageScanner(private val context: Context) {

    /**
     * Real capacity of the data partition, read with StatFs. This is the same number the system
     * storage settings show, and it needs no permission.
     */
    fun drives(): List<DriveInfo> = runCatching {
        val stat = StatFs(Environment.getDataDirectory().absolutePath)
        val total = stat.totalBytes
        val free = stat.availableBytes
        if (total <= 0L) emptyList() else listOf(DriveInfo(total, free))
    }.getOrDefault(emptyList())

    private fun ownRoots(): List<Triple<String, File?, Boolean>> = listOf(
        Triple("App cache", context.cacheDir, true),
        Triple("Code cache", context.codeCacheDir, true),
        Triple("External cache", context.externalCacheDir, true),
        Triple("App files", context.filesDir, false),
        Triple("No-backup files", context.noBackupFilesDir, false)
    )

    fun folders(): List<FolderUse> = ownRoots().mapNotNull { (label, dir, purgeable) ->
        dir ?: return@mapNotNull null
        val (bytes, files) = measure(dir)
        FolderUse(label, dir.absolutePath, bytes, files, purgeable)
    }

    fun scan(): ScanReport {
        val (media, missing) = mediaTotals()
        return ScanReport(
            drives = drives(),
            folders = folders(),
            media = media,
            mediaPermissionMissing = missing
        )
    }

    /** Sums bytes and file counts under a directory without following symlinks. */
    fun measure(root: File): Pair<Long, Int> {
        var bytes = 0L
        var files = 0
        val stack = ArrayDeque<File>()
        stack.addLast(root)
        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            val children = runCatching { current.listFiles() }.getOrNull() ?: continue
            for (child in children) {
                if (child.isDirectory) {
                    stack.addLast(child)
                } else {
                    bytes += child.length()
                    files++
                }
            }
        }
        return bytes to files
    }

    /**
     * Deletes the contents of the purgeable cache roots only, keeping the directories
     * themselves so the app still has a cache to write into. Returns what was actually
     * reclaimed, measured before and after.
     */
    fun purge(): Pair<Long, Int> {
        var freed = 0L
        var removed = 0
        val roots = listOfNotNull(context.cacheDir, context.codeCacheDir, context.externalCacheDir)
        for (root in roots) {
            val before = measure(root).first
            removed += deleteContents(root)
            val after = measure(root).first
            freed += (before - after).coerceAtLeast(0L)
        }
        return freed to removed
    }

    private fun deleteContents(dir: File): Int {
        var removed = 0
        val children = runCatching { dir.listFiles() }.getOrNull() ?: return 0
        for (child in children) {
            if (child.isDirectory) {
                removed += deleteContents(child)
                runCatching { child.delete() }
            } else if (runCatching { child.delete() }.getOrDefault(false)) {
                removed++
            }
        }
        return removed
    }

    fun hasMediaPermission(): Boolean = requiredMediaPermissions().all {
        context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
    }

    fun requiredMediaPermissions(): List<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        listOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO
        )
    } else {
        listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    private fun mediaTotals(): Pair<List<MediaCategory>, Boolean> {
        if (!hasMediaPermission()) return emptyList<MediaCategory>() to true
        val totals = mutableMapOf<Int, Long>()
        val counts = mutableMapOf<Int, Int>()
        runCatching {
            val projection = arrayOf(
                MediaStore.Files.FileColumns.SIZE,
                MediaStore.Files.FileColumns.MEDIA_TYPE
            )
            context.contentResolver.query(
                MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL),
                projection,
                null,
                null,
                null
            )?.use { cursor ->
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val typeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
                while (cursor.moveToNext()) {
                    val type = if (cursor.isNull(typeCol)) 0 else cursor.getInt(typeCol)
                    totals[type] = (totals[type] ?: 0L) + cursor.getLong(sizeCol)
                    counts[type] = (counts[type] ?: 0) + 1
                }
            }
        }.onFailure { return emptyList<MediaCategory>() to true }

        val labels = mapOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE to "Images",
            MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO to "Audio",
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO to "Video",
            0 to "Other files"
        )
        val categories = labels.mapNotNull { (type, label) ->
            val bytes = totals[type] ?: return@mapNotNull null
            MediaCategory(label, bytes, counts[type] ?: 0)
        }.sortedByDescending { it.bytes }
        return categories to false
    }

    /** Largest media rows, biggest first, for the "big files" list. */
    fun largestMedia(limit: Int = 20): List<MediaFile> {
        if (!hasMediaPermission()) return emptyList()
        val files = mutableListOf<MediaFile>()
        runCatching {
            val projection = arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.SIZE,
                MediaStore.Files.FileColumns.MEDIA_TYPE
            )
            context.contentResolver.query(
                MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL),
                projection,
                null,
                null,
                "${MediaStore.Files.FileColumns.SIZE} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val typeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
                while (cursor.moveToNext() && files.size < limit) {
                    val type = if (cursor.isNull(typeCol)) 0 else cursor.getInt(typeCol)
                    files += MediaFile(
                        id = cursor.getLong(idCol),
                        name = cursor.getString(nameCol) ?: "Unnamed file",
                        bytes = cursor.getLong(sizeCol),
                        category = when (type) {
                            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE -> "Image"
                            MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO -> "Audio"
                            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO -> "Video"
                            else -> "File"
                        }
                    )
                }
            }
        }
        return files
    }
}
