package com.example.player

import android.app.PendingIntent
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.example.MainActivity
import com.example.core.LiquidAppContainer
import com.example.domain.model.SoundLabSettings
import com.example.domain.model.Track
import com.example.widget.LiquidMusicWidgetProvider
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sin

object PlaybackDspBridge {
    val soundLabController = SoundLabController()

    @Volatile
    var activePlayer: ExoPlayer? = null
        internal set

    @Volatile
    var currentSettings: SoundLabSettings = SoundLabSettings()
        private set

    @OptIn(UnstableApi::class)
    fun applySoundLab(settings: SoundLabSettings) {
        currentSettings = settings
        soundLabController.applySettings(settings)
        activePlayer?.let { player ->
            val safeSpeed = settings.playbackSpeed.coerceIn(0.5f, 2.0f)
            val safePitch = settings.playbackPitch.coerceIn(0.5f, 2.0f)
            player.playbackParameters = PlaybackParameters(safeSpeed, safePitch)
            runCatching {
                player.skipSilenceEnabled = settings.skipSilenceEnabled
            }
            player.pauseAtEndOfMediaItems = !settings.gaplessPlaybackEnabled

            // Enforce Audio Hardware Offload preference
            runCatching {
                val offloadMode = if (settings.audioOffloadEnabled && !settings.eightDEnabled) {
                    TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED
                } else {
                    TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED
                }
                val offloadPrefs = TrackSelectionParameters.AudioOffloadPreferences.Builder()
                    .setAudioOffloadMode(offloadMode)
                    .setIsGaplessSupportRequired(settings.gaplessPlaybackEnabled)
                    .build()
                player.trackSelectionParameters = player.trackSelectionParameters
                    .buildUpon()
                    .setAudioOffloadPreferences(offloadPrefs)
                    .build()
            }
        }
    }
}

/**
 * MediaLibraryService powering background playback, notification controls, Android Auto
 * browse tree & voice search, hardware volume/Bluetooth rules, and Home Screen Widget sync.
 */
class LiquidPlaybackService : MediaLibraryService() {

    private var mediaLibrarySession: MediaLibrarySession? = null
    private lateinit var player: ExoPlayer
    private lateinit var container: LiquidAppContainer
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var auraModulationJob: Job? = null
    private var lastAutoSearchResults: List<MediaItem> = emptyList()
    private var audioHardwareReceiverRegistered = false

    private val audioHardwareReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val settings = PlaybackDspBridge.currentSettings
            when (intent.action) {
                "android.media.VOLUME_CHANGED_ACTION" -> {
                    if (settings.pauseOnMute && ::player.isInitialized && player.isPlaying) {
                        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                        val mediaVol = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: -1
                        if (mediaVol == 0) {
                            player.pause()
                        }
                    }
                }
                BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)
                    if (state == BluetoothProfile.STATE_CONNECTED &&
                        settings.resumeOnBluetoothConnect &&
                        ::player.isInitialized &&
                        player.mediaItemCount > 0 &&
                        !player.isPlaying
                    ) {
                        player.play()
                    }
                }
                Intent.ACTION_HEADSET_PLUG -> {
                    val state = intent.getIntExtra("state", -1)
                    if (state == 1 &&
                        settings.resumeOnBluetoothConnect &&
                        ::player.isInitialized &&
                        player.mediaItemCount > 0 &&
                        !player.isPlaying
                    ) {
                        player.play()
                    }
                }
            }
        }
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        container = LiquidAppContainer.getInstance(applicationContext)
        val dataSourceFactory = LiquidAudioCache.createDataSourceFactory(
            context = applicationContext,
            okHttpClient = container.okHttpClient,
            musicRepository = container.musicRepository,
            qualityProvider = { PlaybackDspBridge.currentSettings.audioQuality },
            maxCacheSizeMb = PlaybackDspBridge.currentSettings.maxCacheSizeMb
        )
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        PlaybackDspBridge.activePlayer = player
        PlaybackDspBridge.applySoundLab(PlaybackDspBridge.currentSettings)

        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                PlaybackDspBridge.soundLabController.attachToAudioSession(audioSessionId)
                PlaybackDspBridge.applySoundLab(PlaybackDspBridge.currentSettings)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    val sessionId = player.audioSessionId
                    if (sessionId != C.AUDIO_SESSION_ID_UNSET && sessionId > 0) {
                        PlaybackDspBridge.soundLabController.attachToAudioSession(sessionId)
                    }
                    LiquidAudioCache.refreshCacheSize()
                }
                pushWidgetUpdate()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                pushWidgetUpdate()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                pushWidgetUpdate()
            }

            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                pushWidgetUpdate()
            }

            override fun onPlayerError(error: PlaybackException) {
                val hlsRedirect = generateSequence<Throwable>(error) { it.cause }
                    .filterIsInstance<HlsRedirectIOException>()
                    .firstOrNull() ?: return
                val currentItem = player.currentMediaItem ?: return
                if (currentItem.localConfiguration?.mimeType == MimeTypes.APPLICATION_M3U8) return
                val idx = player.currentMediaItemIndex
                val pos = player.currentPosition.coerceAtLeast(0L)
                val hlsItem = currentItem.buildUpon()
                    .setUri(Uri.parse(hlsRedirect.hlsUri))
                    .setMimeType(MimeTypes.APPLICATION_M3U8)
                    .build()
                player.replaceMediaItem(idx, hlsItem)
                player.seekTo(idx, pos)
                player.prepare()
                player.play()
            }
        })

        val sessionActivityIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        mediaLibrarySession = MediaLibrarySession.Builder(this, player, AutoLibraryCallback())
            .setSessionActivity(sessionActivityIntent)
            .build()

        registerAudioHardwareReceivers()
        startSpatialAuraLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            LiquidMusicWidgetProvider.ACTION_WIDGET_PLAY_PAUSE -> {
                if (::player.isInitialized) {
                    if (player.isPlaying) player.pause() else player.play()
                }
            }
            LiquidMusicWidgetProvider.ACTION_WIDGET_NEXT -> {
                if (::player.isInitialized && player.hasNextMediaItem()) {
                    player.seekToNextMediaItem()
                }
            }
            LiquidMusicWidgetProvider.ACTION_WIDGET_PREV -> {
                if (::player.isInitialized) {
                    if (player.currentPosition > 3_500L) player.seekTo(0L)
                    else if (player.hasPreviousMediaItem()) player.seekToPreviousMediaItem()
                }
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun registerAudioHardwareReceivers() {
        runCatching {
            val filter = IntentFilter().apply {
                addAction("android.media.VOLUME_CHANGED_ACTION")
                addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
                addAction(Intent.ACTION_HEADSET_PLUG)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(audioHardwareReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                registerReceiver(audioHardwareReceiver, filter)
            }
            audioHardwareReceiverRegistered = true
        }
    }

    private fun pushWidgetUpdate() {
        if (!::player.isInitialized) return
        val item = player.currentMediaItem
        val meta = item?.mediaMetadata
        val title = meta?.title?.toString().orEmpty().ifBlank { "Ready to Play" }
        val artist = meta?.artist?.toString().orEmpty().ifBlank { "Tap to open Liquid Music" }
        val artworkUrl = meta?.artworkUri?.toString().orEmpty()
        val badge = if (player.isPlaying) "NOW PLAYING" else "LIQUID MUSIC"
        LiquidMusicWidgetProvider.updateWidgetState(
            context = applicationContext,
            title = title,
            artist = artist,
            badge = badge,
            artworkUrl = artworkUrl,
            isPlaying = player.isPlaying
        )
    }

    private fun startSpatialAuraLoop() {
        auraModulationJob?.cancel()
        auraModulationJob = serviceScope.launch {
            var phase = 0.0
            while (isActive) {
                val settings = PlaybackDspBridge.currentSettings
                val p = PlaybackDspBridge.activePlayer
                if (p != null && p.isPlaying) {
                    val crossfadeMs = settings.crossfadeSeconds * 1000L
                    val duration = p.duration
                    val pos = p.currentPosition
                    var targetVolume = 1.0f

                    if (crossfadeMs > 0L && duration > crossfadeMs * 2L && pos > 0L) {
                        val remaining = duration - pos
                        if (remaining in 1..crossfadeMs) {
                            targetVolume = (remaining.toFloat() / crossfadeMs.toFloat()).coerceIn(0.18f, 1.0f)
                        } else if (pos in 1..(crossfadeMs / 2L)) {
                            targetVolume = (0.35f + 0.65f * (pos.toFloat() / (crossfadeMs / 2f))).coerceIn(0.35f, 1.0f)
                        }
                    }

                    if (settings.eightDEnabled) {
                        phase += 0.14
                        val auraWave = (0.88f + 0.12f * sin(phase).toFloat())
                        targetVolume *= auraWave
                    }

                    p.volume = targetVolume.coerceIn(0.1f, 1.0f)
                }
                delay(120L)
            }
        }
    }

    private inner class AutoLibraryCallback : MediaLibrarySession.Callback {

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val rootItem = buildFolderItem(ROOT_ID, "Liquid Music")
            return Futures.immediateFuture(LibraryResult.ofItem(rootItem, params))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
            serviceScope.launch(Dispatchers.IO) {
                val items: List<MediaItem> = runCatching {
                    when {
                        parentId == ROOT_ID -> listOf(
                            buildFolderItem(CAT_LIKED, "Liked Songs"),
                            buildFolderItem(CAT_DOWNLOADS, "Downloaded Offline"),
                            buildFolderItem(CAT_PLAYLISTS, "Playlists"),
                            buildFolderItem(CAT_QUICK_PICKS, "Quick Picks")
                        )
                        parentId == CAT_LIKED -> {
                            container.dao.getLikedTracksOnce().map { it.toDomain() }.map(::trackToPlayableItem)
                        }
                        parentId == CAT_DOWNLOADS -> {
                            container.dao.getDownloadedTracksOnce().map { it.toDomain() }.map(::trackToPlayableItem)
                        }
                        parentId == CAT_PLAYLISTS -> {
                            container.dao.observePlaylists().first().map { pl ->
                                buildFolderItem("pl_${pl.id}", pl.name, pl.coverArtUri)
                            }
                        }
                        parentId.startsWith("pl_") -> {
                            val plId = parentId.removePrefix("pl_").toLongOrNull() ?: -1L
                            container.dao.getTracksForPlaylistOnce(plId).map { it.toDomain() }.map(::trackToPlayableItem)
                        }
                        parentId == CAT_QUICK_PICKS -> {
                            val home = container.musicRepository.loadHomeAndExplore()
                            val tracks = home.sections.flatMap { it.tracks }.distinctBy { it.id }.take(20)
                            tracks.map(::trackToPlayableItem)
                        }
                        else -> emptyList()
                    }
                }.getOrDefault(emptyList())

                val paged = if (pageSize > 0) {
                    val start = (page * pageSize).coerceAtLeast(0)
                    if (start < items.size) items.subList(start, (start + pageSize).coerceAtMost(items.size)) else emptyList()
                } else items
                future.set(LibraryResult.ofItemList(ImmutableList.copyOf(paged), params))
            }
            return future
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val future = SettableFuture.create<LibraryResult<MediaItem>>()
            serviceScope.launch(Dispatchers.IO) {
                val track = container.musicRepository.findRegisteredTrack(mediaId)
                    ?: container.dao.getTrackById(mediaId)?.toDomain()
                if (track != null) {
                    future.set(LibraryResult.ofItem(trackToPlayableItem(track), null))
                } else {
                    future.set(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE))
                }
            }
            return future
        }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<Void>> {
            serviceScope.launch(Dispatchers.IO) {
                val searchRes = runCatching { container.musicRepository.searchUnified(query) }.getOrNull()
                val tracks = ((searchRes?.extensionSongs.orEmpty()) + (searchRes?.songs.orEmpty())).distinctBy { it.id }
                lastAutoSearchResults = tracks.map(::trackToPlayableItem)
                session.notifySearchResultChanged(browser, query, lastAutoSearchResults.size, params)
            }
            return Futures.immediateFuture(LibraryResult.ofVoid())
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            return Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.copyOf(lastAutoSearchResults), params))
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            val resolved = mediaItems.map { item ->
                if (item.localConfiguration?.uri != null) {
                    item
                } else {
                    item.buildUpon()
                        .setUri(Uri.parse("liquid://resolve/${item.mediaId}"))
                        .build()
                }
            }.toMutableList()
            return Futures.immediateFuture(resolved)
        }
    }

    private fun buildFolderItem(id: String, title: String, artUrl: String = ""): MediaItem {
        val metaBuilder = MediaMetadata.Builder()
            .setTitle(title)
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
        if (artUrl.isNotBlank()) metaBuilder.setArtworkUri(Uri.parse(artUrl))
        return MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(metaBuilder.build())
            .build()
    }

    private fun trackToPlayableItem(track: Track): MediaItem {
        container.musicRepository.registerTrack(track)
        val uri = when {
            track.downloadedFilePath.isNotBlank() -> Uri.parse(
                if (track.downloadedFilePath.startsWith("/")) "file://${track.downloadedFilePath}" else track.downloadedFilePath
            )
            track.localUri.isNotBlank() -> Uri.parse(track.localUri)
            else -> Uri.parse("liquid://resolve/${track.id}")
        }
        val meta = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.album)
            .setArtworkUri(if (track.artworkUrl.isNotBlank()) Uri.parse(track.artworkUrl) else null)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .build()
        return MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(uri)
            .setMediaMetadata(meta)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaLibrarySession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val stopOnClear = PlaybackDspBridge.currentSettings.stopMusicOnTaskClear
        if (stopOnClear || !player.playWhenReady || player.mediaItemCount == 0) {
            runCatching {
                player.pause()
                player.stop()
            }
            pushWidgetUpdate()
            stopSelf()
        }
    }

    override fun onDestroy() {
        if (audioHardwareReceiverRegistered) {
            runCatching { unregisterReceiver(audioHardwareReceiver) }
            audioHardwareReceiverRegistered = false
        }
        auraModulationJob?.cancel()
        serviceScope.cancel()
        PlaybackDspBridge.soundLabController.releaseEffects()
        PlaybackDspBridge.activePlayer = null
        mediaLibrarySession?.run {
            player.release()
            release()
            mediaLibrarySession = null
        }
        super.onDestroy()
    }

    companion object {
        private const val ROOT_ID = "liquid_auto_root"
        private const val CAT_LIKED = "cat_liked"
        private const val CAT_DOWNLOADS = "cat_downloads"
        private const val CAT_PLAYLISTS = "cat_playlists"
        private const val CAT_QUICK_PICKS = "cat_quick_picks"
    }
}
