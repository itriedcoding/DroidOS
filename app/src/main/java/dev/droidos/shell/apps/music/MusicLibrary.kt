package dev.droidos.shell.apps.music

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore

/**
 * Reads the device's own audio index. Nothing is bundled or invented: every track here is a row
 * from MediaStore, and an unreadable store is returned as a failure the UI can explain.
 */
class MusicLibrary(private val context: Context) {

    fun requiredPermissions(): List<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

    fun hasPermission(): Boolean = requiredPermissions().all { permission ->
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }

    fun uriFor(track: Track): Uri =
        ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, track.id)

    fun load(): Result<List<Track>> {
        if (!hasPermission()) {
            return Result.failure(SecurityException("Audio access has not been granted"))
        }
        return runCatching {
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION
            )
            val tracks = mutableListOf<Track>()
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                "${MediaStore.Audio.Media.IS_MUSIC} != 0",
                null,
                "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                while (cursor.moveToNext()) {
                    tracks += Track(
                        id = cursor.getLong(idColumn),
                        title = cursor.getString(titleColumn)?.takeIf { it.isNotBlank() } ?: "Untitled",
                        artist = cursor.getString(artistColumn)?.takeIf { it.isNotBlank() }
                            ?: MediaStore.UNKNOWN_STRING,
                        album = cursor.getString(albumColumn)?.takeIf { it.isNotBlank() }
                            ?: MediaStore.UNKNOWN_STRING,
                        durationMs = if (cursor.isNull(durationColumn)) 0L
                        else cursor.getLong(durationColumn)
                    )
                }
            }
            tracks
        }
    }
}
