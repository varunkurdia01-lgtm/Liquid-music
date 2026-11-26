package com.example.data.remote.innertube

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.util.Log
import com.metrolist.innertubex.InnerTube
import com.metrolist.innertubex.InnerTubeLogLevel
import com.metrolist.innertubex.InnerTubeLogger
import com.metrolist.innertubex.cipher.PlayerConfigRepository
import com.metrolist.innertubex.cipher.RemotePlayerConfigStore
import com.metrolist.innertubex.cipher.YouTubeCipherService
import com.metrolist.innertubex.extraction.AudioQuality
import com.metrolist.innertubex.extraction.ContentHints
import com.metrolist.innertubex.extraction.InnerTubeExtractor
import com.metrolist.innertubex.extraction.PoTokenResult
import com.metrolist.innertubex.extraction.TokenProvider
import com.metrolist.innertubex.extraction.TokenProviderCapabilities
import com.metrolist.innertubex.extraction.YtConfigParserImpl
import com.metrolist.innertubex.extraction.generateClientPlaybackNonce
import com.metrolist.innertubex.models.YouTubeLocale
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

/**
 * Exact port of Echo Music's InnerTubeXResolver.kt backed by
 * `com.github.MetrolistGroup.innertubex:innertubex-android:v0.7.0`.
 *
 * Uses InnerTubeX's live-benchmarked client catalog, remote zemer-cipher player configs,
 * QuickJS EJS signature/n-challenge deobfuscation, clientPlaybackNonce (cpn), per-URL
 * matched media headers, and automatic 10-minute client profile exclusion on HTTP 403 refusals.
 */
object InnerTubeXResolver {
    private const val TAG = "InnerTubeXResolver"
    private const val PLAYER_CONFIG_URL =
        "https://raw.githubusercontent.com/ZemerTeam/zemer-cipher/master/library/src/main/assets/player_configs.json"
    private const val LOW_KBPS = 64
    private const val EXCLUDE_MS = 10L * 60L * 1000L
    private const val MAX_REMEMBERED = 64
    private const val WARM_DELAY_MS = 1_500L

    class Extracted(
        val videoId: String,
        val url: String,
        val kbps: Int,
        val mimeType: String,
        val loudnessDb: Double?,
        val perceptualLoudnessDb: Double? = null,
        val clientName: String,
        val profileId: String,
        val headers: Map<String, String>,
        val itag: Int = 0,
        val bitrate: Int? = null,
        val sampleRate: Int? = null,
        val contentLength: Long? = null
    )

    @Volatile
    private var prefs: SharedPreferences? = null

    @Volatile
    private var warmupJob: Job? = null

    @Volatile
    var sessionCookie: String? = null

    @Volatile
    var sessionVisitorData: String? = null

    @Volatile
    var sessionCountry: String = "US"

    @Volatile
    var sessionLanguage: String = "en"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Remembers headers and client profile for each minted stream URL. */
    private val minted = ConcurrentHashMap<String, Extracted>()

    /** Per-videoId excluded client profile IDs after a mid-playback 403/410 refusal. */
    private val excluded = ConcurrentHashMap<String, ConcurrentHashMap<String, Long>>()

    private val tokenProvider = object : TokenProvider {
        override val capabilities = TokenProviderCapabilities(
            providers = emptySet(),
            usesWebView = false
        )

        override suspend fun getPoToken(
            videoId: String,
            visitorData: String,
            cookie: String?
        ): PoTokenResult? = null

        override suspend fun close() {}
    }

    private val http: HttpClient by lazy {
        HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        explicitNulls = false
                        encodeDefaults = true
                    }
                )
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 8_000
                connectTimeoutMillis = 5_000
                socketTimeoutMillis = 6_000
            }
            expectSuccess = false
        }
    }

    private val logger = InnerTubeLogger { event ->
        val details = if (event.details.isEmpty()) ""
        else event.details.entries.joinToString(prefix = " [", postfix = "]") { "${it.key}=${it.value}" }
        val line = "ITX ${event.tag}: ${event.message}$details"
        when (event.level) {
            InnerTubeLogLevel.DEBUG -> Log.d(TAG, line)
            InnerTubeLogLevel.INFO -> Log.i(TAG, line)
            InnerTubeLogLevel.WARN -> Log.w(TAG, line)
            InnerTubeLogLevel.ERROR -> Log.e(TAG, line)
        }
    }

    private val repository = object : PlayerConfigRepository {
        override val enabled: Boolean
            get() = prefs != null
        override val sourceUrl: String = PLAYER_CONFIG_URL
        override val defaultSourceUrl: String = PLAYER_CONFIG_URL
        override var cachedJson: String
            get() = prefs?.getString("json", "").orEmpty()
            set(value) {
                prefs?.edit()?.putString("json", value)?.apply()
            }
        override var cachedAtMs: Long
            get() = prefs?.getLong("cached_at_ms", 0L) ?: 0L
            set(value) {
                prefs?.edit()?.putLong("cached_at_ms", value)?.apply()
            }
        override var cachedSourceUrl: String
            get() = prefs?.getString("source_url", "").orEmpty()
            set(value) {
                prefs?.edit()?.putString("source_url", value)?.apply()
            }
        override var cachedEtag: String
            get() = prefs?.getString("etag", "").orEmpty()
            set(value) {
                prefs?.edit()?.putString("etag", value)?.apply()
            }
    }

    private val innerTube: InnerTube by lazy { InnerTube(http, logger = logger) }
    private val remoteStore: RemotePlayerConfigStore by lazy { RemotePlayerConfigStore(http, repository, logger) }
    private val cipherService: YouTubeCipherService by lazy { YouTubeCipherService(http, remoteStore, logger) }
    private val extractor: InnerTubeExtractor by lazy {
        InnerTubeExtractor(
            configParser = YtConfigParserImpl(http, innerTube, remoteStore, logger),
            cipherService = cipherService,
            innerTube = innerTube,
            tokenProvider = tokenProvider,
            logger = logger
        )
    }

    fun init(context: Context) {
        if (prefs != null) return
        val app = context.applicationContext
        scope.launch {
            if (prefs == null) {
                prefs = app.getSharedPreferences("innertubex_player_config", Context.MODE_PRIVATE)
            }
            warm(WARM_DELAY_MS)
        }
    }

    fun syncSession(
        cookie: String?,
        visitorData: String?,
        country: String,
        language: String
    ) {
        val sessionChanged = sessionCookie != cookie || sessionVisitorData != visitorData
        sessionCookie = cookie?.takeIf { it.isNotBlank() }
        sessionVisitorData = visitorData?.takeIf { it.isNotBlank() }
        sessionCountry = country.ifBlank { "US" }
        sessionLanguage = language.ifBlank { "en" }
        if (sessionChanged) {
            onSessionChanged()
        }
    }

    private fun warm(delayMs: Long) {
        warmupJob?.cancel()
        warmupJob = scope.launch {
            if (delayMs > 0L) delay(delayMs)
            val start = SystemClock.elapsedRealtime()
            runCatching {
                innerTube.cookie = sessionCookie
                innerTube.visitorData = sessionVisitorData
                innerTube.locale = YouTubeLocale(gl = sessionCountry, hl = sessionLanguage)
                extractor.prewarm()
            }.onFailure {
                if (it is CancellationException) throw it
                Log.w(TAG, "InnerTubeX prewarm warning: ${it.message}")
            }.onSuccess {
                Log.i(TAG, "InnerTubeX prewarmed in ${SystemClock.elapsedRealtime() - start}ms")
            }
        }
    }

    suspend fun extract(
        videoId: String,
        maxKbps: Int,
        skipClients: Set<String> = emptySet(),
        requireM4a: Boolean = false
    ): Extracted? {
        innerTube.cookie = sessionCookie
        innerTube.visitorData = sessionVisitorData
        innerTube.locale = YouTubeLocale(gl = sessionCountry, hl = sessionLanguage)

        val qualityPref = when {
            requireM4a -> AudioQuality.MP4
            maxKbps <= LOW_KBPS -> AudioQuality.LOW
            else -> AudioQuality.HIGH
        }
        val excludedSet = excludedFor(videoId) + skipClients

        var stream = runCatching {
            extractor.extract(
                videoId = videoId,
                hints = ContentHints()
                    .withStreamCapabilities(allowHls = false, allowSabr = false, allowBoundedRange = true),
                excludedClients = excludedSet,
                audioQuality = qualityPref,
                clientPlaybackNonce = generateClientPlaybackNonce()
            )
        }.onFailure {
            if (it is CancellationException) throw it
            Log.w(TAG, "InnerTubeX direct pass failed for $videoId: ${it.message}")
            return null
        }.getOrNull()

        if (stream == null || stream.sabrBootstrap != null || !stream.audioUrl.startsWith("http")) {
            // Second pass: allow HLS manifests when direct returned SABR-only or empty
            stream = runCatching {
                extractor.extract(
                    videoId = videoId,
                    hints = ContentHints()
                        .withStreamCapabilities(allowHls = true, allowSabr = false, allowBoundedRange = true),
                    excludedClients = excludedSet,
                    audioQuality = qualityPref,
                    clientPlaybackNonce = generateClientPlaybackNonce()
                )
            }.onFailure {
                if (it is CancellationException) throw it
                Log.w(TAG, "InnerTubeX HLS pass failed for $videoId: ${it.message}")
            }.getOrNull()
        }
        if (stream == null) return null

        if (stream.sabrBootstrap != null || !stream.audioUrl.startsWith("http")) {
            Log.w(TAG, "Skipping SABR/invalid stream for $videoId")
            return null
        }

        val isHls = stream.audioUrl.contains(".m3u8", ignoreCase = true) ||
            stream.audioUrl.contains("manifest.googlevideo.com", ignoreCase = true) ||
            stream.mimeType.orEmpty().contains("mpegurl", ignoreCase = true)
        val mime = if (isHls) {
            "application/x-mpegURL"
        } else {
            stream.mimeType.orEmpty().ifBlank { "audio/mp4" }
        }
        val resolvedHeaders = stream.headers.ifEmpty { PlayerClient.forStreamUrl(stream.audioUrl).mediaHeaders() }
        val extracted = Extracted(
            videoId = videoId,
            url = stream.audioUrl,
            kbps = ((stream.bitrate ?: 160_000) / 1000).coerceAtLeast(64),
            mimeType = if (isHls || stream.codecs.isNullOrBlank()) mime else "$mime; codecs=\"${stream.codecs}\"",
            loudnessDb = stream.loudnessDb,
            perceptualLoudnessDb = stream.perceptualLoudnessDb,
            clientName = stream.clientName,
            profileId = stream.profileId,
            headers = resolvedHeaders,
            itag = stream.itag,
            bitrate = stream.bitrate,
            sampleRate = stream.sampleRate,
            contentLength = stream.contentLengthBytes
        )
        if (minted.size >= MAX_REMEMBERED) minted.clear()
        minted[extracted.url] = extracted
        return extracted
    }

    fun headersFor(url: String): Map<String, String>? = minted[url]?.headers

    /**
     * Records that googlevideo refused [url] (e.g. HTTP 403/410) so the next retry excludes
     * that client profile and refreshes cipher configs.
     */
    fun onRefused(url: String): String? {
        val refused = minted.remove(url) ?: return null
        exclude(refused.videoId, refused.profileId)
        scope.launch { runCatching { cipherService.refreshAfterStreamRejection() } }
        return refused.videoId
    }

    fun exclude(videoId: String, profileId: String) {
        if (videoId.isBlank() || profileId.isBlank()) return
        Log.w(TAG, "Excluding client profile $profileId for $videoId after refusal")
        excluded.getOrPut(videoId) { ConcurrentHashMap() }[profileId] =
            SystemClock.elapsedRealtime() + EXCLUDE_MS
    }

    fun onSessionChanged() {
        excluded.clear()
        minted.clear()
        if (prefs != null) warm(0L)
    }

    private fun excludedFor(videoId: String): Set<String> {
        val entries = excluded[videoId] ?: return emptySet()
        val now = SystemClock.elapsedRealtime()
        entries.entries.removeAll { it.value <= now }
        return entries.keys.toSet()
    }
}
