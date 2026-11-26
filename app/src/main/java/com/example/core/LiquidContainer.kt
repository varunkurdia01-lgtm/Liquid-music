package com.example.core

import android.content.Context
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
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class LiquidAppContainer(context: Context) {
    val appContext: Context = context.applicationContext

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectionPool(ConnectionPool(15, 10, TimeUnit.MINUTES))
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(25, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
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
            bilibiliFallback = bilibiliFallback
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
    val paletteExtractor: PaletteExtractor by lazy { PaletteExtractor(appContext) }

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
