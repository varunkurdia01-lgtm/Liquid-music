package com.example.data.local

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.example.domain.model.SourceType
import com.example.domain.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalAudioRepository(private val context: Context) {

    fun requiredAudioPermission(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }

    fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            requiredAudioPermission()
        ) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun scanDeviceAudio(): List<Track> = withContext(Dispatchers.IO) {
        if (!hasAudioPermission()) return@withContext emptyList()

        val tracks = mutableListOf<Track>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DATE_ADDED
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 10000"
        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

        runCatching {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val addedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)

                val albumArtBase = Uri.parse("content://media/external/audio/albumart")

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val title = cursor.getString(titleCol)?.takeIf { it.isNotBlank() } ?: "Unknown Track"
                    val artist = cursor.getString(artistCol)?.takeIf { it != "<unknown>" && it.isNotBlank() }
                        ?: "Local Artist"
                    val album = cursor.getString(albumCol)?.takeIf { it != "<unknown>" && it.isNotBlank() }
                        ?: "On Device"
                    val duration = cursor.getLong(durationCol).coerceAtLeast(0L)
                    val albumId = cursor.getLong(albumIdCol)
                    val dateAdded = cursor.getLong(addedCol) * 1000L

                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id
                    )
                    val artUri = ContentUris.withAppendedId(albumArtBase, albumId)

                    tracks.add(
                        Track(
                            id = "local_$id",
                            title = title,
                            artist = artist,
                            album = album,
                            durationMs = duration,
                            artworkUrl = artUri.toString(),
                            localUri = contentUri.toString(),
                            sourceType = SourceType.LOCAL,
                            addedAt = dateAdded
                        )
                    )
                }
            }
        }
        tracks
    }
}
