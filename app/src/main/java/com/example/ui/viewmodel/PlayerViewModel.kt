package com.example.ui.viewmodel

import android.content.ComponentName
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.core.LiquidAppContainer
import com.example.data.local.LikedTrackEntity
import com.example.data.local.ListeningEventEntity
import com.example.data.local.TrackEntity
import com.example.domain.model.AudioQuality
import com.example.domain.model.LyricsState
import com.example.domain.model.RepeatModeState
import com.example.domain.model.SleepTimerOption
import com.example.domain.model.Track
import com.example.player.LiquidPlaybackService
import com.example.player.PlaybackDspBridge
import com.example.ui.theme.LiquidDynamicPalette
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "PlayerViewModel"

data class PlayerUiState(
    val currentTrack: Track? = null,
    val queue: List<Track> = emptyList(),
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatModeState = RepeatModeState.OFF,
    val sleepTimerOption: SleepTimerOption = SleepTimerOption.OFF,
    val sleepTimerRemainingSec: Int = 0,
    val lyricsState: LyricsState = LyricsState(),
    val palette: LiquidDynamicPalette = LiquidDynamicPalette(),
    val playbackError: String? = null
)

class PlayerViewModel(
    private val container: LiquidAppContainer
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    val likedTrackIds: StateFlow<Set<String>> = container.dao.observeLikedTrackIds()
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val downloadedPathsById: StateFlow<Map<String, String>> = container.dao.observeAllDownloads()
        .map { list -> list.associate { it.trackId to it.filePath } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val downloadProgress: StateFlow<Map<String, Float>> = container.downloadManager.activeProgress

    private var currentQuality: AudioQuality = AudioQuality.HIGH_320
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var sleepTimerJob: Job? = null
    private var lyricsJob: Job? = null
    private var paletteJob: Job? = null

    private var pendingQueue: Pair<List<Track>, Int>? = null
    private var lastLoggedTrackId: String? = null
    private var accumulatedListenMs: Long = 0L

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _uiState.update { it.copy(isPlaying = isPlaying) }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            _uiState.update {
                it.copy(
                    isBuffering = playbackState == Player.STATE_BUFFERING,
                    durationMs = mediaController?.duration?.takeIf { d -> d > 0 } ?: it.durationMs
                )
            }
            if (playbackState == Player.STATE_ENDED && _uiState.value.sleepTimerOption == SleepTimerOption.END_OF_TRACK) {
                mediaController?.pause()
                setSleepTimer(SleepTimerOption.OFF)
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val ctrl = mediaController ?: return
            val idx = ctrl.currentMediaItemIndex
            val queue = _uiState.value.queue
            if (idx in queue.indices) {
                val track = queue[idx]
                commitListeningEventIfQualified()
                onActiveTrackChanged(track, idx)
            }
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO &&
                _uiState.value.sleepTimerOption == SleepTimerOption.END_OF_TRACK
            ) {
                ctrl.pause()
                setSleepTimer(SleepTimerOption.OFF)
            }
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            _uiState.update { it.copy(shuffleEnabled = shuffleModeEnabled) }
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            val mapped = when (repeatMode) {
                Player.REPEAT_MODE_ALL -> RepeatModeState.ALL
                Player.REPEAT_MODE_ONE -> RepeatModeState.ONE
                else -> RepeatModeState.OFF
            }
            _uiState.update { it.copy(repeatMode = mapped) }
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            Log.e(TAG, "ExoPlayer playback error: ${error.errorCodeName}", error)
            _uiState.update {
                it.copy(
                    isBuffering = false,
                    playbackError = "Playback issue: ${error.localizedMessage ?: error.errorCodeName}"
                )
            }
        }
    }

    init {
        viewModelScope.launch {
            container.preferencesRepository.soundLabSettings.collect { settings ->
                currentQuality = settings.audioQuality
                PlaybackDspBridge.applySoundLab(settings)
            }
        }
        connectMediaController()
        startProgressTicker()
    }

    private fun connectMediaController() {
        val context = container.appContext
        val sessionToken = SessionToken(context, ComponentName(context, LiquidPlaybackService::class.java))
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture = future
        future.addListener({
            runCatching {
                val controller = future.get()
                mediaController = controller
                controller.addListener(playerListener)
                pendingQueue?.let { (tracks, startIdx) ->
                    pendingQueue = null
                    playQueue(tracks, startIdx)
                }
            }.onFailure {
                Log.e(TAG, "Failed connecting MediaController", it)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun startProgressTicker() {
        viewModelScope.launch {
            while (isActive) {
                val ctrl = mediaController
                if (ctrl != null) {
                    val pos = ctrl.currentPosition.coerceAtLeast(0L)
                    val dur = ctrl.duration.takeIf { it > 0L }
                        ?: _uiState.value.currentTrack?.durationMs
                        ?: 0L
                    _uiState.update { state ->
                        if (state.positionMs != pos || state.durationMs != dur) {
                            state.copy(positionMs = pos, durationMs = dur)
                        } else state
                    }
                    if (ctrl.isPlaying) {
                        accumulatedListenMs += 250L
                        if (accumulatedListenMs >= 20_000L && lastLoggedTrackId != _uiState.value.currentTrack?.id) {
                            commitListeningEventIfQualified()
                        }
                    }
                }
                delay(250L)
            }
        }
    }

    fun playQueue(tracks: List<Track>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return
        val safeIdx = startIndex.coerceIn(tracks.indices)
        val downloads = downloadedPathsById.value
        val enrichedTracks = tracks.map { track ->
            val localDownload = downloads[track.id]
            if (!localDownload.isNullOrBlank()) track.copy(downloadedFilePath = localDownload) else track
        }

        val ctrl = mediaController
        if (ctrl == null) {
            pendingQueue = enrichedTracks to safeIdx
            _uiState.update {
                it.copy(
                    queue = enrichedTracks,
                    currentIndex = safeIdx,
                    currentTrack = enrichedTracks[safeIdx],
                    isBuffering = true,
                    playbackError = null
                )
            }
            onActiveTrackChanged(enrichedTracks[safeIdx], safeIdx)
            return
        }

        val mediaItems = enrichedTracks.map { buildMediaItem(it) }
        commitListeningEventIfQualified()
        _uiState.update {
            it.copy(
                queue = enrichedTracks,
                currentIndex = safeIdx,
                currentTrack = enrichedTracks[safeIdx],
                positionMs = 0L,
                durationMs = enrichedTracks[safeIdx].durationMs,
                playbackError = null
            )
        }
        onActiveTrackChanged(enrichedTracks[safeIdx], safeIdx)

        ctrl.setMediaItems(mediaItems, safeIdx, 0L)
        ctrl.prepare()
        ctrl.play()

        // Cache active track metadata in Room for recent history
        viewModelScope.launch {
            runCatching { container.dao.upsertTrack(TrackEntity.fromDomain(enrichedTracks[safeIdx])) }
        }
    }

    fun playTrack(track: Track) {
        playQueue(listOf(track), 0)
    }

    fun togglePlayPause() {
        val ctrl = mediaController ?: return
        if (ctrl.isPlaying) {
            ctrl.pause()
        } else {
            if (ctrl.playbackState == Player.STATE_ENDED) {
                ctrl.seekTo(0L)
            }
            ctrl.play()
        }
    }

    fun skipNext() {
        val ctrl = mediaController ?: return
        if (ctrl.hasNextMediaItem()) {
            ctrl.seekToNextMediaItem()
        } else if (ctrl.mediaItemCount > 0) {
            ctrl.seekTo(0, 0L)
        }
    }

    fun skipPrevious() {
        val ctrl = mediaController ?: return
        if (ctrl.currentPosition > 3_500L) {
            ctrl.seekTo(0L)
        } else if (ctrl.hasPreviousMediaItem()) {
            ctrl.seekToPreviousMediaItem()
        } else {
            ctrl.seekTo(0L)
        }
    }

    fun seekTo(positionMs: Long) {
        val clamped = positionMs.coerceAtLeast(0L)
        mediaController?.seekTo(clamped)
        _uiState.update { it.copy(positionMs = clamped) }
    }

    fun toggleShuffle() {
        val ctrl = mediaController ?: return
        ctrl.shuffleModeEnabled = !ctrl.shuffleModeEnabled
    }

    fun cycleRepeatMode() {
        val ctrl = mediaController ?: return
        val next = when (_uiState.value.repeatMode) {
            RepeatModeState.OFF -> Player.REPEAT_MODE_ALL
            RepeatModeState.ALL -> Player.REPEAT_MODE_ONE
            RepeatModeState.ONE -> Player.REPEAT_MODE_OFF
        }
        ctrl.repeatMode = next
    }

    fun jumpToQueueIndex(index: Int) {
        val queue = _uiState.value.queue
        if (index !in queue.indices) return
        mediaController?.seekTo(index, 0L)
        mediaController?.play()
        onActiveTrackChanged(queue[index], index)
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val currentQueue = _uiState.value.queue.toMutableList()
        if (fromIndex !in currentQueue.indices || toIndex !in currentQueue.indices || fromIndex == toIndex) return
        val moved = currentQueue.removeAt(fromIndex)
        currentQueue.add(toIndex, moved)
        mediaController?.moveMediaItem(fromIndex, toIndex)
        val newActiveIdx = mediaController?.currentMediaItemIndex?.coerceIn(currentQueue.indices)
            ?: _uiState.value.currentIndex
        _uiState.update {
            it.copy(
                queue = currentQueue,
                currentIndex = newActiveIdx,
                currentTrack = currentQueue.getOrNull(newActiveIdx)
            )
        }
    }

    fun removeQueueItem(index: Int) {
        val currentQueue = _uiState.value.queue.toMutableList()
        if (index !in currentQueue.indices) return
        currentQueue.removeAt(index)
        mediaController?.removeMediaItem(index)
        val newIdx = mediaController?.currentMediaItemIndex ?: -1
        _uiState.update {
            it.copy(
                queue = currentQueue,
                currentIndex = newIdx,
                currentTrack = currentQueue.getOrNull(newIdx)
            )
        }
    }

    fun setSleepTimer(option: SleepTimerOption) {
        sleepTimerJob?.cancel()
        val minutes = option.minutes
        if (option == SleepTimerOption.OFF || minutes == null) {
            _uiState.update { it.copy(sleepTimerOption = SleepTimerOption.OFF, sleepTimerRemainingSec = 0) }
            return
        }
        if (option == SleepTimerOption.END_OF_TRACK) {
            _uiState.update { it.copy(sleepTimerOption = SleepTimerOption.END_OF_TRACK, sleepTimerRemainingSec = -1) }
            return
        }
        val totalSec = minutes * 60
        _uiState.update { it.copy(sleepTimerOption = option, sleepTimerRemainingSec = totalSec) }
        sleepTimerJob = viewModelScope.launch {
            var remaining = totalSec
            while (remaining > 0) {
                delay(1000L)
                remaining--
                _uiState.update { it.copy(sleepTimerRemainingSec = remaining) }
                // Smooth 5-second fade-out before pausing
                if (remaining in 1..5) {
                    PlaybackDspBridge.activePlayer?.volume = (remaining / 5f).coerceIn(0.1f, 1f)
                }
            }
            mediaController?.pause()
            PlaybackDspBridge.activePlayer?.volume = 1f
            _uiState.update { it.copy(sleepTimerOption = SleepTimerOption.OFF, sleepTimerRemainingSec = 0) }
        }
    }

    fun toggleLike(track: Track) {
        viewModelScope.launch {
            val isLiked = likedTrackIds.value.contains(track.id)
            if (isLiked) {
                container.dao.deleteLike(track.id)
            } else {
                container.dao.upsertTrack(TrackEntity.fromDomain(track))
                container.dao.insertLike(LikedTrackEntity(track.id))
            }
        }
    }

    fun toggleDownload(track: Track) {
        viewModelScope.launch {
            val isAlreadyDownloaded = downloadedPathsById.value.containsKey(track.id)
            if (isAlreadyDownloaded) {
                container.downloadManager.deleteDownload(track.id)
            } else {
                container.downloadManager.downloadTrack(track, currentQuality)
            }
        }
    }

    fun clearPlaybackError() {
        _uiState.update { it.copy(playbackError = null) }
    }

    private fun onActiveTrackChanged(track: Track, index: Int) {
        accumulatedListenMs = 0L
        lastLoggedTrackId = null
        _uiState.update {
            it.copy(
                currentTrack = track,
                currentIndex = index,
                lyricsState = LyricsState(isLoading = true)
            )
        }

        // Extract dynamic album-art palette (700ms animated crossfade in Theme)
        paletteJob?.cancel()
        paletteJob = viewModelScope.launch {
            val extracted = container.paletteExtractor.extractPalette(track.artworkUrl)
            _uiState.update { it.copy(palette = extracted) }
        }

        // Fetch synced lyrics from LRCLIB
        lyricsJob?.cancel()
        lyricsJob = viewModelScope.launch {
            val lyrics = container.lyricsRepository.fetchLyrics(track)
            _uiState.update { it.copy(lyricsState = lyrics) }
        }
    }

    private fun commitListeningEventIfQualified() {
        val track = _uiState.value.currentTrack ?: return
        if (accumulatedListenMs < 15_000L || lastLoggedTrackId == track.id) return
        val durationToLog = accumulatedListenMs.coerceAtLeast(15_000L)
        lastLoggedTrackId = track.id
        viewModelScope.launch {
            runCatching {
                container.dao.upsertTrack(TrackEntity.fromDomain(track))
                container.dao.insertListeningEvent(
                    ListeningEventEntity(
                        trackId = track.id,
                        title = track.title,
                        artist = track.artist,
                        artworkUrl = track.artworkUrl,
                        durationListenedMs = durationToLog
                    )
                )
            }
        }
    }

    private fun buildMediaItem(track: Track): MediaItem {
        val playbackUri = track.bestPlaybackUri(currentQuality)
        val uri = if (playbackUri.startsWith("/")) Uri.parse("file://$playbackUri") else Uri.parse(playbackUri)
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.album)
            .apply {
                if (track.artworkUrl.isNotBlank()) {
                    setArtworkUri(Uri.parse(track.artworkUrl))
                }
            }
            .build()
        return MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(uri)
            .setMediaMetadata(metadata)
            .build()
    }

    override fun onCleared() {
        commitListeningEventIfQualified()
        mediaController?.removeListener(playerListener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        super.onCleared()
    }
}
