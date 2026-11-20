package com.example.data.local

import android.content.Context
import android.util.Log
import com.example.domain.model.AudioQuality
import com.example.domain.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

private const val TAG = "OfflineDownloadMgr"

class OfflineDownloadManager(
    private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val dao: LiquidDao
) {
    private val _activeProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val activeProgress: StateFlow<Map<String, Float>> = _activeProgress.asStateFlow()

    private val downloadsDir: File
        get() = File(context.filesDir, "downloads").apply { if (!exists()) mkdirs() }

    suspend fun downloadTrack(track: Track, quality: AudioQuality): Result<String> = withContext(Dispatchers.IO) {
        val url = track.bestPlaybackUri(quality)
        if (!url.startsWith("http")) {
            return@withContext Result.failure(IllegalArgumentException("Track is already local or has no downloadable stream URL."))
        }

        _activeProgress.update { it + (track.id to 0.05f) }
        try {
            val request = Request.Builder().url(url).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Download HTTP ${response.code}")
                }
                val body = response.body ?: throw IllegalStateException("Empty download response")
                val totalBytes = body.contentLength().coerceAtLeast(1L)
                val safeId = track.id.replace(Regex("[^a-zA-Z0-9_-]"), "_")
                val outFile = File(downloadsDir, "$safeId.m4a")

                body.byteStream().use { input ->
                    FileOutputStream(outFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesCopied = 0L
                        var read: Int
                        while (input.read(buffer).also { read = it } >= 0) {
                            output.write(buffer, 0, read)
                            bytesCopied += read
                            val fraction = (bytesCopied.toFloat() / totalBytes.toFloat()).coerceIn(0.05f, 0.99f)
                            _activeProgress.update { it + (track.id to fraction) }
                        }
                        output.flush()
                    }
                }

                val savedTrack = track.copy(downloadedFilePath = outFile.absolutePath)
                dao.upsertTrack(TrackEntity.fromDomain(savedTrack))
                dao.upsertDownload(
                    DownloadedTrackEntity(
                        trackId = track.id,
                        filePath = outFile.absolutePath,
                        bitrateKbps = quality.kbps,
                        fileSizeBytes = outFile.length()
                    )
                )
                _activeProgress.update { it - track.id }
                Result.success(outFile.absolutePath)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Download failed for ${track.title}", e)
            _activeProgress.update { it - track.id }
            Result.failure(e)
        }
    }

    suspend fun deleteDownload(trackId: String) = withContext(Dispatchers.IO) {
        val entry = dao.getDownloadById(trackId)
        if (entry != null) {
            runCatching { File(entry.filePath).delete() }
            dao.deleteDownload(trackId)
        }
        val existingTrack = dao.getTrackById(trackId)
        if (existingTrack != null) {
            dao.upsertTrack(existingTrack.copy(downloadedFilePath = ""))
        }
    }
}
