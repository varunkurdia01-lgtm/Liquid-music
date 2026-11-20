package com.example.core

import android.content.Context
import com.example.data.local.AuthRepository
import com.example.data.local.LiquidDao
import com.example.data.local.LiquidDatabase
import com.example.data.local.LocalAudioRepository
import com.example.data.local.OfflineDownloadManager
import com.example.data.local.PreferencesRepository
import com.example.data.remote.LyricsRepository
import com.example.data.remote.SaavnMusicRepository
import com.example.data.remote.VibeDjRepository
import com.example.player.PaletteExtractor
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class LiquidAppContainer(context: Context) {
    val appContext: Context = context.applicationContext

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    val database: LiquidDatabase = LiquidDatabase.getInstance(appContext)
    val dao: LiquidDao = database.liquidDao()

    val preferencesRepository: PreferencesRepository = PreferencesRepository(appContext)
    val authRepository: AuthRepository = preferencesRepository

    val localAudioRepository: LocalAudioRepository = LocalAudioRepository(appContext)
    val saavnRepository: SaavnMusicRepository = SaavnMusicRepository(okHttpClient)
    val lyricsRepository: LyricsRepository = LyricsRepository(okHttpClient)
    val downloadManager: OfflineDownloadManager = OfflineDownloadManager(appContext, okHttpClient, dao)
    val vibeDjRepository: VibeDjRepository = VibeDjRepository(appContext, saavnRepository)
    val paletteExtractor: PaletteExtractor = PaletteExtractor(appContext)

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
