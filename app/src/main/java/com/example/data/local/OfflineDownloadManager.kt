package com.example.data.local

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.util.Log
import com.example.data.remote.innertube.InnerTubeXResolver
import com.example.data.remote.innertube.PlayerClient
import com.example.data.repository.UnifiedMusicRepository
import com.example.domain.model.AudioQuality
import com.example.domain.model.Track
import com.example.player.PlaybackDspBridge
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

/**
 * Echo Music-grade Offline Download Manager:
 * - Resolves fresh stream URLs with matched PlayerClient headers (eliminating HTTP 403)
 * - Uses bounded 512 KB / 1 MB HTTP Range chunks on googlevideo.com URLs carrying `clen` (bypassing 15 KB/s throttling)
 * - Supports `.flac`, `.mp3`, `.webm`, and `.m4a` lossless/high-bitrate streams
 * - Enforces Wi-Fi-only download preference and unified disk cleanup with zero orphan files.
 */
class OfflineDownloadManager(
    private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val dao: LiquidDao,
    private val musicRepository: UnifiedMusicRepository
) {
    private val _activeProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val activeProgress: StateFlow<Map<String, Float>> = _activeProgress.asStateFlow()

    val downloadsDir: File
        get() = File(context.filesDir, "downloads").apply { if (!exists()) mkdirs() }

    suspend fun downloadTrack(track: Track, quality: AudioQuality): Result<String> = withContext(Dispatchers.IO) {
        if (PlaybackDspBridge.currentSettings.downloadOnWifiOnly && !isOnWifi()) {
            return@withContext Result.failure(IllegalStateException("Wi-Fi only downloads enabled in Settings."))
        }

        _activeProgress.update { it + (track.id to 0.04f) }
        try {
            val resolved = musicRepository.resolvePlaybackStream(track, quality)
            val url = resolved?.uri ?: track.bestPlaybackUri(quality)
            if (!url.startsWith("http")) {
                _activeProgress.update { it - track.id }
                return@withContext Result.failure(
                    IllegalArgumentException("Track is already local or has no downloadable stream URL.")
                )
            }

            val parsedUri = Uri.parse(url)
            val isYouTubeMedia = parsedUri.host?.let { host ->
                host.contains("googlevideo.com", ignoreCase = true) ||
                    host.contains("youtube.com", ignoreCase = true)
            } == true

            val headers = when {
                resolved != null && resolved.headers.isNotEmpty() -> resolved.headers
                isYouTubeMedia -> InnerTubeXResolver.headersFor(url)
                    ?: PlayerClient.forStreamUrl(url).mediaHeaders()
                else -> emptyMap()
            }

            val safeId = track.id.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val mime = resolved?.mimeType.orEmpty().lowercase()
            val isHls = mime.contains("mpegurl") ||
                url.contains(".m3u8", ignoreCase = true) ||
                url.contains("manifest.googlevideo.com", ignoreCase = true)
            val ext = when {
                isHls -> "aac"
                mime.contains("flac") -> "flac"
                mime.contains("mpeg") || mime.contains("mp3") -> "mp3"
                mime.contains("webm") || mime.contains("opus") -> "webm"
                else -> "m4a"
            }
            val tempFile = File(downloadsDir, "$safeId.$ext.part")
            val outFile = File(downloadsDir, "$safeId.$ext")

            val clen = resolved?.contentLength
                ?: parsedUri.getQueryParameter("clen")?.toLongOrNull()

            if (isHls) {
                downloadHlsSegments(
                    trackId = track.id,
                    manifestUrl = url,
                    headers = headers,
                    outFile = tempFile
                )
            } else if (isYouTubeMedia && clen != null && clen > 0L) {
                downloadChunked(
                    trackId = track.id,
                    url = url,
                    totalBytes = clen,
                    chunkSize = PlayerClient.rangeBytesFor(url),
                    headers = headers,
                    outFile = tempFile
                )
            } else {
                downloadSinglePass(
                    trackId = track.id,
                    url = url,
                    headers = headers,
                    outFile = tempFile
                )
            }

            if (outFile.exists()) outFile.delete()
            if (!tempFile.renameTo(outFile)) {
                tempFile.copyTo(outFile, overwrite = true)
                tempFile.delete()
            }

            val savedTrack = track.copy(downloadedFilePath = outFile.absolutePath)
            dao.upsertTrack(TrackEntity.fromDomain(savedTrack))
            dao.upsertDownload(
                DownloadedTrackEntity(
                    trackId = track.id,
                    filePath = outFile.absolutePath,
                    bitrateKbps = resolved?.bitrateKbps ?: quality.kbps,
                    fileSizeBytes = outFile.length()
                )
            )
            _activeProgress.update { it - track.id }
            Result.success(outFile.absolutePath)
        } catch (e: Exception) {
            Log.e(TAG, "Download failed for ${track.title}", e)
            _activeProgress.update { it - track.id }
            Result.failure(e)
        }
    }

    private fun downloadChunked(
        trackId: String,
        url: String,
        totalBytes: Long,
        chunkSize: Long,
        headers: Map<String, String>,
        outFile: File
    ) {
        FileOutputStream(outFile).use { output ->
            var offset = 0L
            val buffer = ByteArray(16 * 1024)
            while (offset < totalBytes) {
                val end = (offset + chunkSize - 1L).coerceAtMost(totalBytes - 1L)
                val reqBuilder = Request.Builder()
                    .url(url)
                    .header("Range", "bytes=$offset-$end")
                headers.forEach { (k, v) -> reqBuilder.header(k, v) }

                okHttpClient.newCall(reqBuilder.build()).execute().use { resp ->
                    if (resp.code == 403 || resp.code == 410) {
                        InnerTubeXResolver.onRefused(url)
                    }
                    if (!resp.isSuccessful && resp.code != 206) {
                        throw IllegalStateException("Download HTTP ${resp.code}")
                    }
                    val body = resp.body ?: throw IllegalStateException("Empty chunk response")
                    body.byteStream().use { input ->
                        var read: Int
                        while (input.read(buffer).also { read = it } >= 0) {
                            output.write(buffer, 0, read)
                            offset += read
                            val fraction = (offset.toFloat() / totalBytes.toFloat()).coerceIn(0.05f, 0.99f)
                            _activeProgress.update { it + (trackId to fraction) }
                        }
                    }
                }
            }
            output.flush()
        }
    }

    private fun downloadSinglePass(
        trackId: String,
        url: String,
        headers: Map<String, String>,
        outFile: File
    ) {
        val reqBuilder = Request.Builder().url(url)
        headers.forEach { (k, v) -> reqBuilder.header(k, v) }

        okHttpClient.newCall(reqBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                if (response.code == 403 || response.code == 410) {
                    InnerTubeXResolver.onRefused(url)
                }
                throw IllegalStateException("Download HTTP ${response.code}")
            }
            val body = response.body ?: throw IllegalStateException("Empty download response")
            val totalBytes = body.contentLength().coerceAtLeast(1L)

            body.byteStream().use { input ->
                FileOutputStream(outFile).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var bytesCopied = 0L
                    var read: Int
                    while (input.read(buffer).also { read = it } >= 0) {
                        output.write(buffer, 0, read)
                        bytesCopied += read
                        val fraction = (bytesCopied.toFloat() / totalBytes.toFloat()).coerceIn(0.05f, 0.99f)
                        _activeProgress.update { it + (trackId to fraction) }
                    }
                    output.flush()
                }
            }
        }
    }

    private fun downloadHlsSegments(
        trackId: String,
        manifestUrl: String,
        headers: Map<String, String>,
        outFile: File
    ) {
        val manifestReq = Request.Builder().url(manifestUrl).apply {
            headers.forEach { (k, v) -> header(k, v) }
        }.build()
        val manifestBody = okHttpClient.newCall(manifestReq).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("HLS manifest HTTP ${resp.code}")
            resp.body?.string().orEmpty()
        }
        val baseUri = java.net.URI(manifestUrl)
        val segmentUrls = manifestBody.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { line ->
                if (line.startsWith("http://") || line.startsWith("https://")) line
                else baseUri.resolve(line).toString()
            }
        if (segmentUrls.isEmpty()) throw IllegalStateException("No audio segments found in HLS playlist")

        FileOutputStream(outFile).use { output ->
            val buffer = ByteArray(16 * 1024)
            segmentUrls.forEachIndexed { idx, segUrl ->
                val segReq = Request.Builder().url(segUrl).apply {
                    headers.forEach { (k, v) -> header(k, v) }
                }.build()
                okHttpClient.newCall(segReq).execute().use { resp ->
                    if (!resp.isSuccessful) throw IllegalStateException("HLS segment HTTP ${resp.code}")
                    resp.body?.byteStream()?.use { input ->
                        var read: Int
                        while (input.read(buffer).also { read = it } >= 0) {
                            output.write(buffer, 0, read)
                        }
                    }
                }
                val fraction = ((idx + 1).toFloat() / segmentUrls.size.toFloat()).coerceIn(0.05f, 0.99f)
                _activeProgress.update { it + (trackId to fraction) }
            }
            output.flush()
        }
    }

    suspend fun downloadBatch(tracks: List<Track>, quality: AudioQuality): Int = withContext(Dispatchers.IO) {
        var succeeded = 0
        for (track in tracks) {
            if (dao.getDownloadById(track.id) != null) {
                succeeded++
                continue
            }
            if (downloadTrack(track, quality).isSuccess) {
                succeeded++
            }
        }
        succeeded
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

    suspend fun clearAllDownloads() = withContext(Dispatchers.IO) {
        runCatching {
            downloadsDir.listFiles()?.forEach { it.delete() }
            // Also clean any legacy folder if present
            val legacy = File(context.filesDir, "offline_audio")
            if (legacy.exists()) legacy.deleteRecursively()
        }
        val stored = dao.getAllStoredTracksOnce()
        stored.filter { it.downloadedFilePath.isNotBlank() }.forEach {
            dao.upsertTrack(it.copy(downloadedFilePath = ""))
        }
        dao.clearAllDownloads()
    }

    private fun isOnWifi(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }
}
