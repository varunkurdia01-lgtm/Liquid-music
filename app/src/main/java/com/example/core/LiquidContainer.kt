package com.example.core

import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.example.data.local.AuthRepository
import com.example.data.local.BackupRestoreManager
import com.example.data.local.LiquidDao
import com.example.data.local.LiquidDatabase
import com.example.data.local.LocalAudioRepository
import com.example.data.local.OfflineDownloadManager
import com.example.data.local.PreferencesRepository
import com.example.data.remote.AudiusFallbackProvider
import com.example.data.remote.BilibiliFallbackProvider
import com.example.data.remote.EchoExtensionManager
import com.example.data.remote.SoundCloudFallbackProvider
import com.example.data.remote.LyricsRepository
import com.example.data.remote.MusicDnaRepository
import com.example.data.remote.SaavnMusicProvider
import com.example.data.remote.SaavnMusicRepository
import com.example.data.remote.VibeDjRepository
import com.example.data.remote.innertube.InnerTubeClient
import com.example.data.remote.innertube.InnerTubeMusicProvider
import com.example.data.remote.innertube.InnerTubeXResolver
import com.example.data.repository.UnifiedMusicRepository
import com.example.player.PaletteExtractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.util.concurrent.TimeUnit

class LiquidAppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var activeProxy: Proxy = Proxy.NO_PROXY

    private val dynamicProxySelector = object : ProxySelector() {
        override fun select(uri: URI?): MutableList<Proxy> = mutableListOf(activeProxy)
        override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {}
    }

    fun configureGlobalProxy(enabled: Boolean, proxyUrl: String) {
        if (!enabled || proxyUrl.isBlank()) {
            activeProxy = Proxy.NO_PROXY
            return
        }
        activeProxy = runCatching {
            val trimmed = proxyUrl.trim()
            val normalized = if (trimmed.contains("://")) trimmed else "http://$trimmed"
            val parsed = URI(normalized)
            val host = parsed.host ?: return@runCatching Proxy.NO_PROXY
            val type = if (parsed.scheme.equals("socks", true) || parsed.scheme.equals("socks5", true)) {
                Proxy.Type.SOCKS
            } else {
                Proxy.Type.HTTP
            }
            val port = if (parsed.port > 0) parsed.port else if (type == Proxy.Type.SOCKS) 1080 else 8080
            Proxy(type, InetSocketAddress(host, port))
        }.getOrDefault(Proxy.NO_PROXY)
    }

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .proxySelector(dynamicProxySelector)
            .connectionPool(ConnectionPool(15, 10, TimeUnit.MINUTES))
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(25, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .build()
    }

    val imageLoader: ImageLoader by lazy {
        ImageLoader.Builder(appContext)
            .okHttpClient { okHttpClient }
            .memoryCache {
                MemoryCache.Builder(appContext)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(File(appContext.cacheDir, "image_cache"))
                    .maxSizeBytes(150L * 1024 * 1024)
                    .build()
            }
            .respectCacheHeaders(false)
            .allowHardware(true)
            .build()
    }

    val database: LiquidDatabase by lazy { LiquidDatabase.getInstance(appContext) }
    val dao: LiquidDao by lazy { database.liquidDao() }

    val preferencesRepository: PreferencesRepository by lazy { PreferencesRepository(appContext) }
    val authRepository: AuthRepository get() = preferencesRepository

    val localAudioRepository: LocalAudioRepository by lazy { LocalAudioRepository(appContext) }

    // Modular Provider Stack (Echo Music Architecture + InnerTubeX + Echo Addons)
    private val innerTubeClient: InnerTubeClient by lazy { InnerTubeClient(okHttpClient) }
    private val innerTubeProvider: InnerTubeMusicProvider by lazy { InnerTubeMusicProvider(innerTubeClient) }
    private val saavnRawRepository: SaavnMusicRepository by lazy { SaavnMusicRepository(okHttpClient) }
    private val saavnProvider: SaavnMusicProvider by lazy { SaavnMusicProvider(okHttpClient, saavnRawRepository) }
    val extensionManager: EchoExtensionManager by lazy { EchoExtensionManager(okHttpClient) }

    val audiusFallback: AudiusFallbackProvider by lazy { AudiusFallbackProvider(okHttpClient) }
    val soundCloudFallback: SoundCloudFallbackProvider by lazy { SoundCloudFallbackProvider(okHttpClient) }
    val bilibiliFallback: BilibiliFallbackProvider by lazy { BilibiliFallbackProvider(okHttpClient) }

    // Unified Orchestrator exposed to ViewModels, Playback Service, Downloads & Vibe DJ
    val musicRepository: UnifiedMusicRepository by lazy {
        UnifiedMusicRepository(
            primaryCatalog = innerTubeProvider,
            primaryStream = innerTubeProvider,
            fallbackCatalog = saavnProvider,
            fallbackStream = saavnProvider,
            dao = dao,
            innerTubeClient = innerTubeClient,
            saavnRawRepository = saavnRawRepository,
            innerTubeProvider = innerTubeProvider,
            extensionManager = extensionManager,
            audiusFallback = audiusFallback,
            soundCloudFallback = soundCloudFallback,
            bilibiliFallback = bilibiliFallback,
            onProxyConfigured = ::configureGlobalProxy
        )
    }

    // Multi-Provider Synced Karaoke Lyrics + Gemini AI Translation
    val lyricsRepository: LyricsRepository by lazy {
        LyricsRepository(
            okHttpClient = okHttpClient,
            dao = dao,
            secondaryProviders = listOf(innerTubeProvider)
        )
    }

    val downloadManager: OfflineDownloadManager by lazy {
        OfflineDownloadManager(
            context = appContext,
            okHttpClient = okHttpClient,
            dao = dao,
            musicRepository = musicRepository
        )
    }

    val backupRestoreManager: BackupRestoreManager by lazy {
        BackupRestoreManager(
            context = appContext,
            dao = dao,
            musicRepository = musicRepository
        )
    }

    val vibeDjRepository: VibeDjRepository by lazy { VibeDjRepository(appContext, musicRepository) }
    val musicDnaRepository: MusicDnaRepository by lazy { MusicDnaRepository(okHttpClient) }
    val paletteExtractor: PaletteExtractor by lazy { PaletteExtractor(appContext, imageLoader) }

    init {
        // Prewarm Echo Music's InnerTubeX cipher & player_configs store asynchronously off the main thread
        InnerTubeXResolver.init(appContext)
    }

    companion object {
        @Volatile
        private var INSTANCE: LiquidAppContainer? = null

        fun getInstance(context: Context): LiquidAppContainer {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: LiquidAppContainer(context).also { INSTANCE = it }
            }
        }
    }
}
