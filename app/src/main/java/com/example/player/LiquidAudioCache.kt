package com.example.player

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.example.data.remote.innertube.InnerTubeXResolver
import com.example.data.remote.innertube.PlayerClient
import com.example.data.repository.UnifiedMusicRepository
import com.example.domain.model.AudioQuality
import com.example.domain.model.StreamResolution
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "LiquidAudioCache"
const val LIQUID_RESOLVE_SCHEME = "liquid"
const val LIQUID_RESOLVE_HOST = "resolve"

/**
 * Echo Music-grade Media3 Audio Cache & Stream Pipeline:
 * ResolvingDataSource (SimpleCache hit check -> InnerTubeX / Echo Addon / JioSaavn JIT + Per-URL headers)
 *   -> CacheDataSource (SimpleCache with source-isolated cache keys)
 *   -> DefaultDataSource (Local File / Content / Network)
 *   -> ChunkedDataSource (512 KB / 1 MB bounded googlevideo ranges, anti-throttle + 403 reporter)
 *   -> OkHttpDataSource (shared OkHttp connection pool).
 */
@OptIn(UnstableApi::class)
object LiquidAudioCache {

    @Volatile
    private var simpleCache: SimpleCache? = null

    @Volatile
    private var databaseProvider: StandaloneDatabaseProvider? = null

    @Volatile
    private var cacheDirRef: File? = null

    private val urlHeadersMap = ConcurrentHashMap<String, Map<String, String>>()
    private val pendingHlsRedirectTrackIds = ConcurrentHashMap.newKeySet<String>()

    private val _cacheSizeBytes = MutableStateFlow(0L)
    val cacheSizeBytes: StateFlow<Long> = _cacheSizeBytes.asStateFlow()

    private val _lastResolution = MutableStateFlow<Pair<String, StreamResolution>?>(null)
    val lastResolution: StateFlow<Pair<String, StreamResolution>?> = _lastResolution.asStateFlow()

    fun registerUrlHeaders(url: String, headers: Map<String, String>) {
        if (url.isBlank() || headers.isEmpty()) return
        if (urlHeadersMap.size > 128) urlHeadersMap.clear()
        urlHeadersMap[url] = headers
    }

    fun markHlsRedirectPending(trackId: String) {
        if (trackId.isNotBlank()) pendingHlsRedirectTrackIds.add(trackId)
    }

    fun consumeHlsRedirectPending(trackId: String?): Boolean {
        if (trackId.isNullOrBlank()) return false
        return pendingHlsRedirectTrackIds.remove(trackId)
    }

    @Synchronized
    fun getOrCreateCache(context: Context, maxCacheSizeMb: Int = 512): SimpleCache {
        simpleCache?.let {
            refreshCacheSize()
            return it
        }
        val dir = File(context.applicationContext.cacheDir, "liquid_audio_cache").apply {
            if (!exists()) mkdirs()
        }
        cacheDirRef = dir
        val dbProvider = databaseProvider ?: StandaloneDatabaseProvider(context.applicationContext).also {
            databaseProvider = it
        }
        val capBytes = maxCacheSizeMb.coerceIn(128, 4096).toLong() * 1024L * 1024L
        val evictor = LeastRecentlyUsedCacheEvictor(capBytes)
        return SimpleCache(dir, evictor, dbProvider).also {
            simpleCache = it
            _cacheSizeBytes.value = it.cacheSpace
        }
    }

    fun refreshCacheSize() {
        val bytes = runCatching { simpleCache?.cacheSpace }.getOrNull() ?: return
        _cacheSizeBytes.value = bytes
    }

    fun evictTrackCache(trackId: String) {
        val cache = simpleCache ?: return
        runCatching {
            cache.keys.toList()
                .filter { it == trackId || it.startsWith("${trackId}_") }
                .forEach { key -> cache.removeResource(key) }
        }.onFailure {
            Log.w(TAG, "Error evicting track cache for $trackId: ${it.message}")
        }
        refreshCacheSize()
    }

    fun onHttpRefusal(cacheKey: String?, url: String, statusCode: Int) {
        Log.w(TAG, "HTTP $statusCode refusal for key=$cacheKey")
        InnerTubeXResolver.onRefused(url)
        if (!cacheKey.isNullOrBlank()) {
            runCatching { simpleCache?.removeResource(cacheKey) }
        }
    }

    suspend fun clearCache(context: Context): Long = withContext(Dispatchers.IO) {
        val cache = simpleCache
        if (cache != null) {
            runCatching {
                cache.keys.toList().forEach { key ->
                    cache.removeResource(key)
                }
            }.onFailure {
                Log.w(TAG, "Error removing SimpleCache keys: ${it.message}")
            }
            refreshCacheSize()
            return@withContext _cacheSizeBytes.value
        }
        val dir = File(context.applicationContext.cacheDir, "liquid_audio_cache")
        runCatching { dir.deleteRecursively() }
        _cacheSizeBytes.value = 0L
        0L
    }

    fun buildResolvingUri(trackId: String): Uri =
        Uri.Builder()
            .scheme(LIQUID_RESOLVE_SCHEME)
            .authority(LIQUID_RESOLVE_HOST)
            .appendPath(trackId)
            .build()

    fun createDataSourceFactory(
        context: Context,
        okHttpClient: OkHttpClient,
        musicRepository: UnifiedMusicRepository,
        qualityProvider: () -> AudioQuality,
        maxCacheSizeMb: Int = 512
    ): DataSource.Factory {
        val appContext = context.applicationContext
        val cache = getOrCreateCache(appContext, maxCacheSizeMb)

        // Exact Echo Music transport stack: OkHttpDataSource -> ChunkedDataSource (512KB/1MB ranges)
        val okHttpFactory = OkHttpDataSource.Factory(okHttpClient)
        val chunkedHttpFactory = ChunkedDataSource.Factory(okHttpFactory, 1024L * 1024L)
        val upstreamFactory = DefaultDataSource.Factory(appContext, chunkedHttpFactory)

        val cacheFactory = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        return ResolvingDataSource.Factory(cacheFactory) { dataSpec: DataSpec ->
            val uri = dataSpec.uri
            if (uri.scheme == LIQUID_RESOLVE_SCHEME && uri.host == LIQUID_RESOLVE_HOST) {
                val trackId = uri.lastPathSegment.orEmpty()
                val quality = qualityProvider()

                // 1. Check SimpleCache first so cached/seeking/replayed songs serve directly from disk offline!
                val cachedKey = findCachedKeyForTrack(cache, trackId, quality, dataSpec.position)
                if (cachedKey != null) {
                    refreshCacheSize()
                    return@Factory dataSpec.buildUpon()
                        .setKey(cachedKey)
                        .setUri(Uri.parse("https://cached.liquid.local/$cachedKey"))
                        .build()
                }

                val resolution = runBlocking(Dispatchers.IO) {
                    val track = musicRepository.findRegisteredTrack(trackId)
                        ?: throw IOException("Track metadata unavailable for $trackId")
                    musicRepository.resolvePlaybackStream(track, quality)
                        ?: throw IOException("No playable stream found for \"${track.title}\".")
                }
                _lastResolution.value = trackId to resolution
                refreshCacheSize()

                val isHls = resolution.mimeType.contains("mpegurl", ignoreCase = true) ||
                    resolution.uri.contains(".m3u8", ignoreCase = true) ||
                    resolution.uri.contains("manifest.googlevideo.com", ignoreCase = true)
                if (isHls) {
                    if (resolution.headers.isNotEmpty()) {
                        registerUrlHeaders(resolution.uri, resolution.headers)
                    }
                    throw HlsRedirectIOException(
                        trackId = trackId,
                        hlsUri = resolution.uri,
                        headers = resolution.headers
                    )
                }

                val resolvedUri = Uri.parse(resolution.uri)
                val isYouTubeMediaUrl = resolvedUri.host?.let { host ->
                    host.contains("googlevideo.com", ignoreCase = true) ||
                        host.contains("youtube.com", ignoreCase = true)
                } == true
                val headers = buildMap {
                    putAll(dataSpec.httpRequestHeaders)
                    val matched = when {
                        resolution.headers.isNotEmpty() -> resolution.headers
                        isYouTubeMediaUrl -> InnerTubeXResolver.headersFor(resolution.uri)
                            ?: PlayerClient.forStreamUrl(resolution.uri).mediaHeaders()
                        else -> emptyMap()
                    }
                    putAll(matched)
                }
                if (headers.isNotEmpty()) {
                    registerUrlHeaders(resolution.uri, headers)
                }
                // Source-isolated cache key prevents stale/wrong cached audio across provider or quality switches!
                val isolatedCacheKey = "${trackId}_${resolution.resolvedBy.name}_${quality.name}"
                dataSpec.buildUpon()
                    .setUri(resolvedUri)
                    .setKey(isolatedCacheKey)
                    .setHttpRequestHeaders(headers)
                    .build()
            } else {
                refreshCacheSize()
                val rawUrl = uri.toString()
                val isYouTubeMediaUrl = uri.host?.let { host ->
                    host.contains("googlevideo.com", ignoreCase = true) ||
                        host.contains("youtube.com", ignoreCase = true)
                } == true
                val storedHeaders = urlHeadersMap[rawUrl]
                    ?: uri.host?.let { host ->
                        urlHeadersMap.entries.firstOrNull { (k, _) ->
                            runCatching { Uri.parse(k).host.equals(host, ignoreCase = true) }.getOrDefault(false)
                        }?.value
                    }
                if (!storedHeaders.isNullOrEmpty()) {
                    val merged = buildMap {
                        putAll(storedHeaders)
                        putAll(dataSpec.httpRequestHeaders)
                    }
                    dataSpec.buildUpon()
                        .setHttpRequestHeaders(merged)
                        .build()
                } else if (isYouTubeMediaUrl && dataSpec.httpRequestHeaders["User-Agent"].isNullOrBlank()) {
                    val headers = buildMap {
                        putAll(dataSpec.httpRequestHeaders)
                        putAll(
                            InnerTubeXResolver.headersFor(rawUrl)
                                ?: PlayerClient.forStreamUrl(rawUrl).mediaHeaders()
                        )
                    }
                    dataSpec.buildUpon()
                        .setHttpRequestHeaders(headers)
                        .build()
                } else {
                    dataSpec
                }
            }
        }
    }

    private fun findCachedKeyForTrack(
        cache: SimpleCache,
        trackId: String,
        quality: AudioQuality,
        position: Long
    ): String? {
        if (trackId.isBlank()) return null
        return runCatching {
            val prefix = "${trackId}_"
            val qualitySuffix = "_${quality.name}"
            val candidates = cache.keys.filter { it.startsWith(prefix) }
            val ordered = candidates.filter { it.endsWith(qualitySuffix) } +
                candidates.filterNot { it.endsWith(qualitySuffix) }
            ordered.firstOrNull { key ->
                val contentLength = cache.getContentMetadata(key).get("exo_len", -1L)
                val checkPos = position.coerceAtLeast(0L)
                if (contentLength > 0L && checkPos < contentLength) {
                    val remaining = (contentLength - checkPos).coerceAtMost(4096L)
                    cache.isCached(key, checkPos, remaining)
                } else {
                    cache.isCached(key, checkPos, 1L)
                }
            }
        }.getOrNull()
    }
}

/**
 * Thrown by [LiquidAudioCache] when a `liquid://resolve/{id}` item resolves to an HLS `.m3u8`
 * manifest so [LiquidPlaybackService] seamlessly switches that item to
 * `MimeTypes.APPLICATION_M3U8` for ExoPlayer's `HlsMediaSource`.
 */
class HlsRedirectIOException(
    val trackId: String,
    val hlsUri: String,
    val headers: Map<String, String> = emptyMap()
) : IOException("Redirecting $trackId to HLS manifest")
