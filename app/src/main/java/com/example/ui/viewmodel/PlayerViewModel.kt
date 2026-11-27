package com.example.ui.viewmodel

import android.content.ComponentName
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.datasource.HttpDataSource
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.core.LiquidAppContainer
import com.example.data.local.LikedTrackEntity
import com.example.data.local.ListeningEventEntity
import com.example.data.local.PlaybackQueueEntity
import com.example.data.local.PlaylistEntity
import com.example.data.local.PlaylistTrackCrossRef
import com.example.data.local.TrackEntity
import com.example.domain.model.AudioQuality
import com.example.domain.model.LyricsSearchCandidate
import com.example.domain.model.LyricsState
import com.example.domain.model.MusicSource
import com.example.domain.model.RepeatModeState
import com.example.domain.model.SavedQueueCursor
import com.example.domain.model.SleepTimerOption
import com.example.domain.model.SoundLabSettings
import com.example.domain.model.SourceType
import com.example.domain.model.StreamResolution
import com.example.domain.model.Track
import com.example.domain.model.TrackDnaInfo
import com.example.player.LIQUID_RESOLVE_SCHEME
import com.example.player.LiquidAudioCache
import com.example.player.LiquidPlaybackService
import com.example.player.PlaybackDspBridge
import com.example.ui.theme.LiquidDynamicPalette
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "PlayerViewModel"

@Immutable
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
    val autoPlayRadioEnabled: Boolean = true,
    val isLoadingRadio: Boolean = false,
    val sleepTimerOption: SleepTimerOption = SleepTimerOption.OFF,
    val sleepTimerRemainingSec: Int = 0,
    val lyricsState: LyricsState = LyricsState(),
    val lyricsSearchResults: List<LyricsSearchCandidate> = emptyList(),
    val isSearchingLyrics: Boolean = false,
    val trackDna: TrackDnaInfo? = null,
    val isLoadingDna: Boolean = false,
    val palette: LiquidDynamicPalette = LiquidDynamicPalette(),
    val activeStreamBadge: String = "",
    val playbackError: String? = null,
    val statusToast: String? = null
)

class PlayerViewModel(
    private val container: LiquidAppContainer
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    val likedTrackIds: StateFlow<Set<String>> = container.dao.observeLikedTrackIds()
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val downloadedPathsById: StateFlow<Map<String, String>> = container.dao.observeAllDownloads()
        .map { list -> list.associate { it.trackId to it.filePath } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val downloadProgress: StateFlow<Map<String, Float>> = container.downloadManager.activeProgress

    private var currentSettings: SoundLabSettings = SoundLabSettings()
    private var currentQuality: AudioQuality = AudioQuality.HIGH_320
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var sleepTimerJob: Job? = null
    private var lyricsJob: Job? = null
    private var paletteJob: Job? = null
    private var radioAppendJob: Job? = null
    private var dnaJob: Job? = null
    private var persistQueueJob: Job? = null
    private var preloadNextJob: Job? = null
    private var resolvePlayJob: Job? = null
    private var hasAttemptedQueueRestore: Boolean = false

    private var pendingQueue: Pair<List<Track>, Int>? = null
    private var lastLoggedTrackId: String? = null
    private var accumulatedListenMs: Long = 0L
    private var retryAttemptsForTrack: Pair<String, Int> = "" to 0

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _uiState.update { it.copy(isPlaying = isPlaying) }
            if (!isPlaying) {
                persistPlaybackQueueDebounced()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            _uiState.update {
                it.copy(
                    isBuffering = playbackState == Player.STATE_BUFFERING,
                    durationMs = mediaController?.duration?.takeIf { d -> d > 0 } ?: it.durationMs,
                    playbackError = if (playbackState == Player.STATE_READY) null else it.playbackError
                )
            }
            if (playbackState == Player.STATE_ENDED && _uiState.value.sleepTimerOption == SleepTimerOption.END_OF_TRACK) {
                mediaController?.pause()
                setSleepTimer(SleepTimerOption.OFF)
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val ctrl = mediaController ?: return
            val queue = _uiState.value.queue
            val mediaId = mediaItem?.mediaId
            // Prefer ExoPlayer's active index when its mediaId matches so duplicate tracks in queue highlight accurately
            val ctrlIdx = ctrl.currentMediaItemIndex
            val idx = if (ctrlIdx in queue.indices && (mediaId.isNullOrBlank() || queue[ctrlIdx].id == mediaId)) {
                ctrlIdx
            } else if (!mediaId.isNullOrBlank()) {
                queue.indexOfFirst { it.id == mediaId }.takeIf { it >= 0 } ?: ctrlIdx
            } else {
                ctrlIdx
            }
            if (idx in queue.indices) {
                val track = queue[idx]
                val alreadyActive = _uiState.value.currentTrack?.id == track.id && _uiState.value.currentIndex == idx
                if (!alreadyActive) {
                    commitListeningEventIfQualified()
                    onActiveTrackChanged(track, idx)
                }
                maybeAppendEndlessRadio(idx, queue)
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

        @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            val state = _uiState.value
            val activeTrack = state.currentTrack
            val ctrl = mediaController

            // Seamless HLS handoff when ResolvingDataSource returns a YouTube or SoundCloud HLS manifest
            val currentLocalConfig = ctrl?.currentMediaItem?.localConfiguration
            val currentUriStr = currentLocalConfig?.uri?.toString().orEmpty()
            val isAlreadyHls = currentLocalConfig?.mimeType == androidx.media3.common.MimeTypes.APPLICATION_M3U8 ||
                currentUriStr.contains(".m3u8", ignoreCase = true) ||
                currentUriStr.contains("manifest.googlevideo.com", ignoreCase = true) ||
                currentUriStr.contains("media-streaming.soundcloud.cloud", ignoreCase = true)
            val hlsRedirectCause = generateSequence<Throwable>(error) { it.cause }
                .filterIsInstance<com.example.player.HlsRedirectIOException>()
                .firstOrNull()
            if (hlsRedirectCause != null || LiquidAudioCache.consumeHlsRedirectPending(activeTrack?.id)) {
                // LiquidPlaybackService already handles the immediate HlsMediaSource switch on the player
                _uiState.update { it.copy(isBuffering = true, playbackError = null) }
                return
            }
            val hlsUri = if (!isAlreadyHls && activeTrack != null) {
                LiquidAudioCache.lastResolution.value
                    ?.takeIf { (id, res) ->
                        id == activeTrack.id && (
                            res.mimeType.contains("mpegurl", ignoreCase = true) ||
                                res.uri.contains(".m3u8", ignoreCase = true) ||
                                res.uri.contains("manifest.googlevideo.com", ignoreCase = true) ||
                                res.uri.contains("media-streaming.soundcloud.cloud", ignoreCase = true)
                            )
                    }?.second?.uri
            } else null

            if (!hlsUri.isNullOrBlank() && activeTrack != null && ctrl != null) {
                val safeIdx = state.currentIndex.coerceIn(0, (ctrl.mediaItemCount - 1).coerceAtLeast(0))
                val hlsItem = MediaItem.Builder()
                    .setMediaId(activeTrack.id)
                    .setUri(Uri.parse(hlsUri))
                    .setMimeType(androidx.media3.common.MimeTypes.APPLICATION_M3U8)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(activeTrack.title)
                            .setArtist(activeTrack.artist)
                            .setAlbumTitle(activeTrack.album)
                            .setArtworkUri(activeTrack.artworkUrl.takeIf { it.isNotBlank() }?.let(Uri::parse))
                            .build()
                    )
                    .build()
                _uiState.update { it.copy(isBuffering = true, playbackError = null) }
                ctrl.replaceMediaItem(safeIdx, hlsItem)
                ctrl.seekTo(safeIdx, _positionMs.value.coerceAtLeast(0L))
                ctrl.prepare()
                ctrl.play()
                return
            }

            val httpErr = generateSequence<Throwable>(error) { it.cause }
                .filterIsInstance<HttpDataSource.InvalidResponseCodeException>()
                .firstOrNull()
            val statusCode = httpErr?.responseCode ?: 0
            val failedUrl = httpErr?.dataSpec?.uri?.toString()
                ?: ctrl?.currentMediaItem?.localConfiguration?.uri?.toString()
            val lastResForTrack = LiquidAudioCache.lastResolution.value
                ?.takeIf { it.first == activeTrack?.id }
                ?.second

            // Automatic HTTP 567 / 403 / stream recovery: exclude failed provider/URL,
            // resolve a fresh verified stream, and replace the MediaItem on ExoPlayer!
            if (activeTrack != null && ctrl != null) {
                val prevAttempts = if (retryAttemptsForTrack.first == activeTrack.id) retryAttemptsForTrack.second else 0
                if (prevAttempts < MAX_AUTO_RECOVERY_ATTEMPTS) {
                    retryAttemptsForTrack = activeTrack.id to (prevAttempts + 1)
                    Log.w(
                        TAG,
                        "Recovering from ${error.errorCodeName} (HTTP $statusCode) for ${activeTrack.id} (attempt ${prevAttempts + 1}/$MAX_AUTO_RECOVERY_ATTEMPTS)"
                    )
                    container.musicRepository.markStreamFailed(
                        trackId = activeTrack.id,
                        failedUrl = failedUrl,
                        resolution = lastResForTrack,
                        statusCode = statusCode
                    )
                    LiquidAudioCache.evictTrackCache(activeTrack.id)
                    val resumePos = _positionMs.value.coerceAtLeast(0L)
                    _uiState.update {
                        it.copy(
                            isBuffering = true,
                            playbackError = null,
                            statusToast = "Switching audio stream source (attempt ${prevAttempts + 1}/$MAX_AUTO_RECOVERY_ATTEMPTS)…"
                        )
                    }
                    resolvePlayJob?.cancel()
                    resolvePlayJob = viewModelScope.launch {
                        delay(250L)
                        val freshRes = withContext(Dispatchers.IO) {
                            runCatching {
                                container.musicRepository.resolvePlaybackStream(activeTrack, currentQuality)
                            }.getOrNull()
                        }
                        if (!isActive || _uiState.value.currentTrack?.id != activeTrack.id) return@launch
                        val activeCtrl = mediaController ?: return@launch
                        if (freshRes != null && activeCtrl.mediaItemCount > 0) {
                            val safeIdx = _uiState.value.currentIndex
                                .coerceIn(0, (activeCtrl.mediaItemCount - 1).coerceAtLeast(0))
                            val freshItem = buildResolvedMediaItem(activeTrack, freshRes)
                            activeCtrl.replaceMediaItem(safeIdx, freshItem)
                            activeCtrl.seekTo(safeIdx, resumePos)
                            activeCtrl.prepare()
                            activeCtrl.play()
                        } else {
                            handleExhaustedPlaybackError(activeTrack, error, activeCtrl)
                        }
                    }
                    return
                }
            }

            Log.e(TAG, "ExoPlayer playback error: ${error.errorCodeName} (HTTP $statusCode)", error)
            handleExhaustedPlaybackError(activeTrack, error, ctrl)
        }
    }

    private fun handleExhaustedPlaybackError(
        activeTrack: Track?,
        error: androidx.media3.common.PlaybackException,
        ctrl: MediaController?
    ) {
        val rootMessage = error.cause?.message.orEmpty()
        val friendlyError = when {
            rootMessage.contains("No playable stream", ignoreCase = true) ->
                "No playable stream available for \"${activeTrack?.title.orEmpty()}\". Sign in to YouTube Music in Profile or tap Next."
            else -> "Stream error (${error.errorCodeName}). Tap Retry or Next."
        }
        _uiState.update {
            it.copy(
                isBuffering = false,
                playbackError = friendlyError
            )
        }

        // Respect Echo Music's Auto-Skip Next on Error setting after retries exhaust
        viewModelScope.launch {
            val autoSkip = runCatching {
                container.preferencesRepository.soundLabSettings.first().autoSkipNextOnError
            }.getOrDefault(false)
            if (autoSkip && ctrl != null && ctrl.hasNextMediaItem()) {
                delay(900L)
                skipNext()
            }
        }
    }

    init {
        viewModelScope.launch {
            container.preferencesRepository.soundLabSettings.collect { settings ->
                currentSettings = settings
                currentQuality = settings.audioQuality
                PlaybackDspBridge.applySoundLab(settings)
                _uiState.update { it.copy(autoPlayRadioEnabled = settings.autoPlayRadioEnabled) }
            }
        }
        viewModelScope.launch {
            LiquidAudioCache.lastResolution.collect { pair ->
                val activeId = _uiState.value.currentTrack?.id ?: return@collect
                if (pair != null && pair.first == activeId) {
                    val res = pair.second
                    PlaybackDspBridge.soundLabController.applyTrackLoudness(res.loudnessDb)
                    val badge = when {
                        res.resolvedBy == MusicSource.LOCAL -> "Offline Local"
                        res.resolvedBy == MusicSource.ECHO_ADDON -> "Extension · ${res.bitrateKbps}k"
                        res.usedFallback -> "${res.resolvedBy.displayName} Fallback · ${res.bitrateKbps}k"
                        else -> "${res.resolvedBy.displayName} · ${res.bitrateKbps}k"
                    }
                    _uiState.update { it.copy(activeStreamBadge = badge) }
                }
            }
        }
        // Defer MediaSessionService binding slightly so ExoPlayer & SimpleCache never block MainActivity's first SurfaceSyncGroup frames
        viewModelScope.launch {
            delay(350L)
            connectMediaController()
        }
        startProgressTicker()
    }

    private fun connectMediaController() {
        if (controllerFuture != null) return
        val context = container.appContext
        val sessionToken = SessionToken(context, ComponentName(context, LiquidPlaybackService::class.java))
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture = future
        future.addListener({
            runCatching {
                val controller = future.get()
                mediaController = controller
                controller.addListener(playerListener)
                val queued = pendingQueue
                if (queued != null) {
                    pendingQueue = null
                    playQueue(queued.first, queued.second)
                } else if (!hasAttemptedQueueRestore) {
                    hasAttemptedQueueRestore = true
                    restorePersistentQueueIfEnabled(controller)
                }
            }.onFailure {
                Log.e(TAG, "Failed connecting MediaController", it)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun restorePersistentQueueIfEnabled(controller: MediaController) {
        viewModelScope.launch {
            runCatching {
                val settings = container.preferencesRepository.soundLabSettings.first()
                if (!settings.persistentQueueEnabled || controller.mediaItemCount > 0 || _uiState.value.queue.isNotEmpty()) {
                    return@runCatching
                }
                val savedTracks = container.dao.getSavedQueueTracksOnce().map { it.toDomain() }
                if (savedTracks.isEmpty()) return@runCatching
                val cursor = container.preferencesRepository.getQueueCursorOnce()
                val safeIdx = cursor.activeIndex.coerceIn(savedTracks.indices)
                val safePos = cursor.positionMs.coerceAtLeast(0L)

                container.musicRepository.registerTracks(savedTracks)
                val items = savedTracks.map { buildMediaItem(it) }
                controller.setMediaItems(items, safeIdx, safePos)
                if (settings.rememberShuffleAndRepeat) {
                    controller.shuffleModeEnabled = cursor.shuffleEnabled
                    controller.repeatMode = when (cursor.repeatMode) {
                        RepeatModeState.ALL -> Player.REPEAT_MODE_ALL
                        RepeatModeState.ONE -> Player.REPEAT_MODE_ONE
                        RepeatModeState.OFF -> Player.REPEAT_MODE_OFF
                    }
                }
                val restoredTrack = savedTracks[safeIdx]
                if (isLocalTrack(restoredTrack)) {
                    controller.prepare()
                }
                controller.pause() // Ready in mini-player without triggering remote fetches or audio on startup

                val activeTrack = savedTracks[safeIdx]
                _positionMs.value = safePos
                _uiState.update {
                    it.copy(
                        queue = savedTracks,
                        currentIndex = safeIdx,
                        currentTrack = activeTrack,
                        positionMs = safePos,
                        durationMs = activeTrack.durationMs,
                        shuffleEnabled = if (settings.rememberShuffleAndRepeat) cursor.shuffleEnabled else false,
                        repeatMode = if (settings.rememberShuffleAndRepeat) cursor.repeatMode else RepeatModeState.OFF,
                        isPlaying = false,
                        isBuffering = false
                    )
                }
                onActiveTrackChanged(activeTrack, safeIdx, persist = false)
            }.onFailure {
                Log.w(TAG, "Persistent queue restore skipped: ${it.message}")
            }
        }
    }

    private fun persistPlaybackQueueDebounced() {
        if (!currentSettings.persistentQueueEnabled) return
        val snapshot = _uiState.value
        val queue = snapshot.queue
        if (queue.isEmpty()) return
        val currentPosMs = _positionMs.value.coerceAtLeast(0L)
        persistQueueJob?.cancel()
        persistQueueJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            delay(350L)
            runCatching {
                val limited = queue.take(150)
                container.dao.upsertTracks(limited.map { TrackEntity.fromDomain(it) })
                container.dao.clearSavedQueue()
                container.dao.insertQueueEntries(
                    limited.mapIndexed { idx, t -> PlaybackQueueEntity(position = idx, trackId = t.id) }
                )
                container.preferencesRepository.saveQueueCursor(
                    SavedQueueCursor(
                        activeIndex = snapshot.currentIndex.coerceIn(0, (limited.size - 1).coerceAtLeast(0)),
                        positionMs = currentPosMs,
                        shuffleEnabled = snapshot.shuffleEnabled,
                        repeatMode = snapshot.repeatMode
                    )
                )
            }
        }
    }

    private fun startProgressTicker() {
        viewModelScope.launch {
            while (isActive) {
                val ctrl = mediaController
                val playing = ctrl?.isPlaying == true
                if (ctrl != null) {
                    val pos = ctrl.currentPosition.coerceAtLeast(0L)
                    if (_positionMs.value != pos) {
                        _positionMs.value = pos
                    }
                    val dur = ctrl.duration.takeIf { it > 0L }
                        ?: _uiState.value.currentTrack?.durationMs
                        ?: 0L
                    if (_uiState.value.durationMs != dur) {
                        _uiState.update { state ->
                            if (state.durationMs != dur) state.copy(durationMs = dur) else state
                        }
                    }
                    if (playing) {
                        accumulatedListenMs += 100L
                        if (accumulatedListenMs >= 20_000L && lastLoggedTrackId != _uiState.value.currentTrack?.id) {
                            commitListeningEventIfQualified()
                        }
                    }
                }
                delay(if (playing) 100L else 500L)
            }
        }
    }

    fun playQueue(tracks: List<Track>, startIndex: Int = 0, shuffle: Boolean = false) {
        if (tracks.isEmpty()) return
        val effectiveTracks = if (shuffle) tracks.shuffled() else tracks
        val safeIdx = if (shuffle) 0 else startIndex.coerceIn(effectiveTracks.indices)
        val downloads = downloadedPathsById.value
        val enrichedTracks = effectiveTracks.map { track ->
            val localDownload = downloads[track.id]
            if (!localDownload.isNullOrBlank()) track.copy(downloadedFilePath = localDownload) else track
        }

        // Register all tracks in the unified orchestrator for JIT ResolvingDataSource
        container.musicRepository.registerTracks(enrichedTracks)

        val ctrl = mediaController
        if (ctrl == null) {
            pendingQueue = enrichedTracks to safeIdx
            connectMediaController()
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

        resolvePlayJob?.cancel()
        val activeTrack = enrichedTracks[safeIdx]
        commitListeningEventIfQualified()
        _positionMs.value = 0L
        _uiState.update {
            it.copy(
                queue = enrichedTracks,
                currentIndex = safeIdx,
                currentTrack = activeTrack,
                positionMs = 0L,
                durationMs = activeTrack.durationMs,
                playbackError = null,
                isBuffering = true
            )
        }
        onActiveTrackChanged(activeTrack, safeIdx)

        if (isLocalTrack(activeTrack)) {
            val mediaItems = enrichedTracks.map { buildMediaItem(it) }
            ctrl.setMediaItems(mediaItems, safeIdx, 0L)
            ctrl.prepare()
            ctrl.play()
        } else {
            // Pause previous audio immediately while pre-resolving the selected remote track off-thread.
            // Pre-resolving avoids throwing HlsRedirectIOException for HLS manifests and pre-validates HTTP status.
            ctrl.pause()
            resolvePlayJob = viewModelScope.launch {
                val resolved = withContext(Dispatchers.IO) {
                    runCatching {
                        container.musicRepository.resolvePlaybackStream(activeTrack, currentQuality)
                    }.getOrNull()
                }
                if (!isActive || _uiState.value.currentTrack?.id != activeTrack.id) return@launch
                val activeCtrl = mediaController ?: return@launch
                if (resolved == null) {
                    val fallbackItems = enrichedTracks.map { buildMediaItem(it) }
                    activeCtrl.setMediaItems(fallbackItems, safeIdx, 0L)
                    _uiState.update {
                        it.copy(
                            isBuffering = false,
                            playbackError = "No playable stream available for \"${activeTrack.title}\". Sign in to YouTube Music in Profile or tap Next."
                        )
                    }
                    return@launch
                }
                val mediaItems = enrichedTracks.mapIndexed { idx, track ->
                    if (idx == safeIdx) buildResolvedMediaItem(track, resolved) else buildMediaItem(track)
                }
                activeCtrl.setMediaItems(mediaItems, safeIdx, 0L)
                activeCtrl.prepare()
                activeCtrl.play()
            }
        }

        maybeAppendEndlessRadio(safeIdx, enrichedTracks)

        viewModelScope.launch {
            runCatching { container.dao.upsertTrack(TrackEntity.fromDomain(activeTrack)) }
        }
    }

    fun playTrack(track: Track) {
        playQueue(listOf(track), 0)
    }

    fun startRadio(seedTrack: Track) {
        playTrack(seedTrack)
        radioAppendJob?.cancel()
        radioAppendJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingRadio = true, statusToast = "Starting Endless Radio…") }
            val radioTracks = runCatching { container.musicRepository.getEndlessRadio(seedTrack, limit = 18) }
                .getOrDefault(emptyList())
            appendTracksToQueue(radioTracks)
            _uiState.update {
                it.copy(
                    isLoadingRadio = false,
                    statusToast = if (radioTracks.isNotEmpty()) "Added ${radioTracks.size} radio tracks" else "Radio unavailable offline"
                )
            }
        }
    }

    fun toggleAutoPlayRadio() {
        val next = !_uiState.value.autoPlayRadioEnabled
        _uiState.update { it.copy(autoPlayRadioEnabled = next) }
        viewModelScope.launch {
            container.preferencesRepository.updateSoundLabSettings { it.copy(autoPlayRadioEnabled = next) }
        }
    }

    private fun maybeAppendEndlessRadio(currentIndex: Int, queue: List<Track>) {
        if (!_uiState.value.autoPlayRadioEnabled) return
        if (_uiState.value.isLoadingRadio) return
        val remaining = queue.size - 1 - currentIndex
        if (remaining > 1) return
        val seed = queue.getOrNull(currentIndex) ?: return
        if (seed.sourceType == SourceType.LOCAL) return

        radioAppendJob?.cancel()
        radioAppendJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingRadio = true) }
            val more = runCatching { container.musicRepository.getEndlessRadio(seed, limit = 12) }
                .getOrDefault(emptyList())
            appendTracksToQueue(more)
            _uiState.update { it.copy(isLoadingRadio = false) }
        }
    }

    private fun appendTracksToQueue(newTracks: List<Track>) {
        if (newTracks.isEmpty()) return
        val existingIds = _uiState.value.queue.map { it.id }.toSet()
        val toAdd = if (currentSettings.preventDuplicateTracksInQueue) {
            newTracks.filterNot { it.id in existingIds }
        } else {
            newTracks
        }
        if (toAdd.isEmpty()) return
        container.musicRepository.registerTracks(toAdd)
        val mediaItems = toAdd.map { buildMediaItem(it) }
        mediaController?.addMediaItems(mediaItems)
        _uiState.update { it.copy(queue = it.queue + toAdd) }
        persistPlaybackQueueDebounced()
    }

    fun playNext(track: Track) {
        val state = _uiState.value
        if (state.queue.isEmpty()) {
            playTrack(track)
            return
        }
        val updated = state.queue.toMutableList()
        var currentIdx = state.currentIndex.coerceIn(0, (updated.size - 1).coerceAtLeast(0))
        if (currentSettings.preventDuplicateTracksInQueue) {
            val dupIdx = updated.indexOfFirst { it.id == track.id }
            if (dupIdx >= 0 && dupIdx != currentIdx) {
                updated.removeAt(dupIdx)
                mediaController?.removeMediaItem(dupIdx)
                if (dupIdx < currentIdx) {
                    currentIdx = (currentIdx - 1).coerceAtLeast(0)
                }
            }
        }
        val safeInsert = (currentIdx + 1).coerceIn(0, updated.size)
        updated.add(safeInsert, track)
        container.musicRepository.registerTrack(track)
        mediaController?.addMediaItem(safeInsert, buildMediaItem(track))
        _uiState.update {
            it.copy(
                queue = updated,
                currentIndex = currentIdx,
                statusToast = "Playing \"${track.title}\" next"
            )
        }
        persistPlaybackQueueDebounced()
    }

    fun addToQueue(track: Track) {
        if (_uiState.value.queue.isEmpty()) {
            playTrack(track)
            return
        }
        appendTracksToQueue(listOf(track))
        _uiState.update { it.copy(statusToast = "Added \"${track.title}\" to queue") }
    }

    fun searchLyricsCandidates(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSearchingLyrics = true) }
            val candidates = runCatching {
                container.lyricsRepository.searchLyricsCandidates(trimmed)
            }.getOrDefault(emptyList())
            _uiState.update { it.copy(isSearchingLyrics = false, lyricsSearchResults = candidates) }
        }
    }

    fun applyManualLyrics(candidate: LyricsSearchCandidate) {
        val track = _uiState.value.currentTrack ?: return
        viewModelScope.launch {
            val applied = container.lyricsRepository.applyManualLyrics(track, candidate)
            _uiState.update {
                it.copy(
                    lyricsState = applied,
                    statusToast = "Applied ${candidate.sourceName} lyrics"
                )
            }
        }
    }

    fun loadTrackDna(track: Track? = _uiState.value.currentTrack) {
        val target = track ?: return
        if (_uiState.value.trackDna?.trackId == target.id && !_uiState.value.isLoadingDna) return
        dnaJob?.cancel()
        dnaJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingDna = true) }
            val dna = runCatching { container.musicDnaRepository.fetchTrackDna(target) }.getOrNull()
            _uiState.update { it.copy(isLoadingDna = false, trackDna = dna) }
        }
    }

    fun retryCurrentTrack() {
        val state = _uiState.value
        val track = state.currentTrack ?: return
        retryAttemptsForTrack = track.id to 0
        container.musicRepository.clearTrackFailures(track.id)
        container.musicRepository.invalidateStream(track.id)
        LiquidAudioCache.evictTrackCache(track.id)
        _uiState.update { it.copy(playbackError = null, isBuffering = true) }
        container.musicRepository.registerTrack(track)
        val ctrl = mediaController ?: return
        val idx = state.currentIndex.coerceIn(0, (ctrl.mediaItemCount - 1).coerceAtLeast(0))
        val resumePos = _positionMs.value.coerceAtLeast(0L)
        resolveAndPlayQueueIndex(idx, resumePos, forceResolve = true)
    }

    fun saveQueueAsPlaylist(name: String) {
        val tracks = _uiState.value.queue
        val cleanName = name.trim().ifBlank { "Liquid Queue Mix" }
        if (tracks.isEmpty()) return
        viewModelScope.launch {
            val cover = tracks.firstOrNull { it.artworkUrl.isNotBlank() }?.artworkUrl.orEmpty()
            val id = container.dao.insertPlaylist(
                PlaylistEntity(name = cleanName, description = "Saved queue (${tracks.size} songs)", coverArtUri = cover)
            )
            tracks.forEachIndexed { idx, t ->
                container.dao.upsertTrack(TrackEntity.fromDomain(t))
                container.dao.insertPlaylistTrackRef(PlaylistTrackCrossRef(id, t.id, idx))
            }
            _uiState.update { it.copy(statusToast = "Saved \"$cleanName\" to Library") }
        }
    }

    fun toggleLyricsTranslation(targetLanguage: String = currentSettings.translateTargetLanguage.ifBlank { "English" }) {
        val state = _uiState.value
        val track = state.currentTrack ?: return
        val lyrics = state.lyricsState

        // Toggle back to original if already showing translation
        if (lyrics.showTranslation) {
            _uiState.update { it.copy(lyricsState = lyrics.copy(showTranslation = false)) }
            return
        }

        // If already cached translations exist on synced lines, show instantaneously!
        val hasCachedTranslation = lyrics.syncedLines.any { !it.translatedText.isNullOrBlank() }
        if (hasCachedTranslation && lyrics.translationLanguage.equals(targetLanguage, ignoreCase = true)) {
            _uiState.update { it.copy(lyricsState = lyrics.copy(showTranslation = true, translationError = null)) }
            return
        }

        if (lyrics.isTranslating) return
        _uiState.update {
            it.copy(lyricsState = lyrics.copy(isTranslating = true, translationError = null))
        }

        val translateMode = currentSettings.translateMode.ifBlank { "Natural / Poetic" }
        viewModelScope.launch {
            val result = container.lyricsRepository.translateLyrics(
                trackId = track.id,
                currentState = lyrics,
                targetLanguage = targetLanguage,
                translateMode = translateMode
            )
            result.fold(
                onSuccess = { translated ->
                    _uiState.update { it.copy(lyricsState = translated) }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            lyricsState = it.lyricsState.copy(
                                isTranslating = false,
                                translationError = err.localizedMessage ?: "Translation failed."
                            )
                        )
                    }
                }
            )
        }
    }

    fun togglePlayPause() {
        val ctrl = mediaController ?: return
        if (ctrl.isPlaying) {
            ctrl.pause()
        } else {
            val state = _uiState.value
            val currentIdx = state.currentIndex
            if (ctrl.playbackState == Player.STATE_IDLE && currentIdx in state.queue.indices) {
                resolveAndPlayQueueIndex(currentIdx, _positionMs.value.coerceAtLeast(0L))
                return
            }
            if (ctrl.playbackState == Player.STATE_ENDED) {
                ctrl.seekTo(0L)
            }
            ctrl.play()
        }
    }

    fun skipNext() {
        val ctrl = mediaController ?: return
        val queue = _uiState.value.queue
        if (queue.isEmpty()) return
        val nextIdx = if (ctrl.hasNextMediaItem()) {
            ctrl.nextMediaItemIndex
        } else {
            0
        }
        if (nextIdx in queue.indices) {
            resolveAndPlayQueueIndex(nextIdx, 0L)
        }
    }

    fun skipPrevious() {
        val ctrl = mediaController ?: return
        val queue = _uiState.value.queue
        if (ctrl.currentPosition > 3_500L) {
            ctrl.seekTo(0L)
            return
        }
        val prevIdx = if (ctrl.hasPreviousMediaItem()) {
            ctrl.previousMediaItemIndex
        } else {
            -1
        }
        if (prevIdx in queue.indices) {
            resolveAndPlayQueueIndex(prevIdx, 0L)
        } else {
            ctrl.seekTo(0L)
        }
    }

    fun seekTo(positionMs: Long) {
        val clamped = positionMs.coerceAtLeast(0L)
        mediaController?.seekTo(clamped)
        _positionMs.value = clamped
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
        resolveAndPlayQueueIndex(index, 0L)
    }

    private fun resolveAndPlayQueueIndex(
        index: Int,
        seekPosMs: Long,
        forceResolve: Boolean = false
    ) {
        val ctrl = mediaController ?: return
        val queue = _uiState.value.queue
        val track = queue.getOrNull(index) ?: return
        if (index >= ctrl.mediaItemCount) return

        val existingItem = ctrl.getMediaItemAt(index)
        val existingUri = existingItem.localConfiguration?.uri
        val needsRemoteResolve = !isLocalTrack(track) && (
            forceResolve ||
                (existingUri?.scheme == LIQUID_RESOLVE_SCHEME && !LiquidAudioCache.hasPreResolved(track.id))
            )

        commitListeningEventIfQualified()
        _positionMs.value = seekPosMs
        onActiveTrackChanged(track, index)

        if (!needsRemoteResolve) {
            _uiState.update { it.copy(playbackError = null, isBuffering = true) }
            ctrl.seekTo(index, seekPosMs)
            ctrl.prepare()
            ctrl.play()
            return
        }

        ctrl.pause()
        _uiState.update { it.copy(playbackError = null, isBuffering = true) }
        resolvePlayJob?.cancel()
        resolvePlayJob = viewModelScope.launch {
            val resolved = withContext(Dispatchers.IO) {
                runCatching {
                    container.musicRepository.resolvePlaybackStream(track, currentQuality)
                }.getOrNull()
            }
            if (!isActive || _uiState.value.currentTrack?.id != track.id) return@launch
            val activeCtrl = mediaController ?: return@launch
            if (resolved == null) {
                _uiState.update {
                    it.copy(
                        isBuffering = false,
                        playbackError = "No playable stream available for \"${track.title}\". Sign in to YouTube Music in Profile or tap Next."
                    )
                }
                return@launch
            }
            if (activeCtrl.mediaItemCount != queue.size) {
                val rebuilt = queue.mapIndexed { idx, t ->
                    if (idx == index) buildResolvedMediaItem(t, resolved) else buildMediaItem(t)
                }
                activeCtrl.setMediaItems(rebuilt, index, seekPosMs)
            } else if (index < activeCtrl.mediaItemCount) {
                activeCtrl.replaceMediaItem(index, buildResolvedMediaItem(track, resolved))
                activeCtrl.seekTo(index, seekPosMs)
            }
            activeCtrl.prepare()
            activeCtrl.play()
        }
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
        PlaybackDspBridge.setSleepFadeVolume(1.0f)
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
                if (remaining in 1..5) {
                    PlaybackDspBridge.setSleepFadeVolume((remaining / 5f).coerceIn(0.1f, 1f))
                }
            }
            mediaController?.pause()
            PlaybackDspBridge.setSleepFadeVolume(1.0f)
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
                val s = runCatching { container.preferencesRepository.soundLabSettings.first() }.getOrNull()
                if (s?.autoDownloadOnLike == true && !downloadedPathsById.value.containsKey(track.id)) {
                    container.downloadManager.downloadTrack(track, currentQuality)
                }
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

    fun downloadTracksBatch(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(statusToast = "Downloading ${tracks.size} tracks offline…") }
            val count = container.downloadManager.downloadBatch(tracks, currentQuality)
            _uiState.update { it.copy(statusToast = "Downloaded $count / ${tracks.size} tracks") }
        }
    }

    fun clearPlaybackError() {
        _uiState.update { it.copy(playbackError = null) }
    }

    fun clearStatusToast() {
        _uiState.update { it.copy(statusToast = null) }
    }

    private fun onActiveTrackChanged(track: Track, index: Int, persist: Boolean = true) {
        accumulatedListenMs = 0L
        lastLoggedTrackId = null
        if (retryAttemptsForTrack.first != track.id) {
            retryAttemptsForTrack = track.id to 0
        }
        val initialBadge = when {
            track.isDownloaded -> "Offline Download"
            track.sourceType == SourceType.LOCAL -> "Device Local"
            else -> track.sourceProvider.displayName
        }
        _uiState.update {
            it.copy(
                currentTrack = track,
                currentIndex = index,
                activeStreamBadge = initialBadge,
                lyricsState = LyricsState(isLoading = true),
                trackDna = if (it.trackDna?.trackId == track.id) it.trackDna else null
            )
        }

        if (persist) {
            persistPlaybackQueueDebounced()
        }

        paletteJob?.cancel()
        paletteJob = viewModelScope.launch {
            val extracted = container.paletteExtractor.extractPalette(track.artworkUrl)
            _uiState.update { it.copy(palette = extracted) }
        }

        lyricsJob?.cancel()
        lyricsJob = viewModelScope.launch {
            val s = runCatching { container.preferencesRepository.soundLabSettings.first() }.getOrDefault(currentSettings)
            val lyrics = container.lyricsRepository.fetchLyrics(track, s)
            _uiState.update { it.copy(lyricsState = lyrics) }
            if (s.autoTranslateLyrics && lyrics.syncedLines.isNotEmpty() && !lyrics.showTranslation) {
                toggleLyricsTranslation(s.translateTargetLanguage)
            }
            // Preload next track lyrics silently when enabled
            if (s.preloadLyricsEnabled) {
                val nextTrack = _uiState.value.queue.getOrNull(index + 1)
                if (nextTrack != null) {
                    runCatching { container.lyricsRepository.fetchLyrics(nextTrack, s) }
                }
            }
        }

        // Pre-resolve the next track's playback stream in the background so gapless/HLS transitions avoid HlsRedirectIOException
        preloadNextJob?.cancel()
        if (currentSettings.preloadNextSongEnabled) {
            val nextIdx = index + 1
            val nextTrack = _uiState.value.queue.getOrNull(nextIdx)
            if (nextTrack != null && !isLocalTrack(nextTrack)) {
                preloadNextJob = viewModelScope.launch(Dispatchers.IO) {
                    delay(1_200L)
                    val resolved = runCatching {
                        container.musicRepository.resolvePlaybackStream(nextTrack, currentQuality)
                    }.getOrNull() ?: return@launch
                    if (LiquidAudioCache.isHlsResolution(resolved)) {
                        if (resolved.headers.isNotEmpty()) {
                            LiquidAudioCache.registerUrlHeaders(resolved.uri, resolved.headers)
                        }
                        withContext(Dispatchers.Main.immediate) {
                            val ctrl = mediaController ?: return@withContext
                            if (nextIdx < ctrl.mediaItemCount && ctrl.getMediaItemAt(nextIdx).mediaId == nextTrack.id) {
                                ctrl.replaceMediaItem(nextIdx, buildResolvedMediaItem(nextTrack, resolved, updateLastResolution = false))
                            }
                        }
                    } else {
                        LiquidAudioCache.putPreResolved(nextTrack.id, resolved)
                    }
                }
            }
        }
    }

    private fun commitListeningEventIfQualified() {
        val track = _uiState.value.currentTrack ?: return
        if (accumulatedListenMs < 15_000L || lastLoggedTrackId == track.id) return
        val durationToLog = accumulatedListenMs.coerceAtLeast(15_000L)
        lastLoggedTrackId = track.id
        viewModelScope.launch {
            runCatching {
                val s = container.preferencesRepository.soundLabSettings.first()
                if (s.pauseListenHistory) return@runCatching
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

    private fun isLocalTrack(track: Track): Boolean {
        return track.downloadedFilePath.isNotBlank() ||
            (track.sourceType == SourceType.LOCAL && track.localUri.isNotBlank())
    }

    private fun buildTrackMetadata(track: Track): MediaMetadata {
        return MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.album)
            .apply {
                if (track.artworkUrl.isNotBlank()) {
                    setArtworkUri(Uri.parse(track.artworkUrl))
                }
            }
            .build()
    }

    private fun buildResolvedMediaItem(
        track: Track,
        resolution: StreamResolution?,
        updateLastResolution: Boolean = true
    ): MediaItem {
        if (resolution == null) return buildMediaItem(track)
        if (updateLastResolution) {
            LiquidAudioCache.recordLastResolution(track.id, resolution)
        }
        if (resolution.headers.isNotEmpty()) {
            LiquidAudioCache.registerUrlHeaders(resolution.uri, resolution.headers)
        }
        if (LiquidAudioCache.isHlsResolution(resolution)) {
            return MediaItem.Builder()
                .setMediaId(track.id)
                .setUri(Uri.parse(resolution.uri))
                .setMimeType(MimeTypes.APPLICATION_M3U8)
                .setMediaMetadata(buildTrackMetadata(track))
                .build()
        }
        if (resolution.uri.startsWith("file://") || resolution.uri.startsWith("content://")) {
            return MediaItem.Builder()
                .setMediaId(track.id)
                .setUri(Uri.parse(resolution.uri))
                .setMediaMetadata(buildTrackMetadata(track))
                .build()
        }
        LiquidAudioCache.putPreResolved(track.id, resolution)
        return buildMediaItem(track)
    }

    private fun buildMediaItem(track: Track): MediaItem {
        val uri = when {
            track.downloadedFilePath.isNotBlank() -> {
                val p = track.downloadedFilePath
                if (p.startsWith("/")) Uri.parse("file://$p") else Uri.parse(p)
            }
            track.sourceType == SourceType.LOCAL && track.localUri.isNotBlank() -> Uri.parse(track.localUri)
            else -> LiquidAudioCache.buildResolvingUri(track.id)
        }
        return MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(uri)
            .setMediaMetadata(buildTrackMetadata(track))
            .build()
    }

    private fun persistPlaybackQueueImmediate() {
        if (!currentSettings.persistentQueueEnabled) return
        val snapshot = _uiState.value
        val queue = snapshot.queue
        if (queue.isEmpty()) return
        val limited = queue.take(150)
        val activeIdx = snapshot.currentIndex.coerceIn(0, (limited.size - 1).coerceAtLeast(0))
        val posMs = _positionMs.value.coerceAtLeast(0L)
        val shuffle = snapshot.shuffleEnabled
        val repeat = snapshot.repeatMode
        container.applicationScope.launch {
            runCatching {
                container.dao.upsertTracks(limited.map { TrackEntity.fromDomain(it) })
                container.dao.clearSavedQueue()
                container.dao.insertQueueEntries(
                    limited.mapIndexed { idx, t -> PlaybackQueueEntity(position = idx, trackId = t.id) }
                )
                container.preferencesRepository.saveQueueCursor(
                    SavedQueueCursor(
                        activeIndex = activeIdx,
                        positionMs = posMs,
                        shuffleEnabled = shuffle,
                        repeatMode = repeat
                    )
                )
            }
        }
    }

    private fun commitListeningEventImmediate() {
        val track = _uiState.value.currentTrack ?: return
        if (accumulatedListenMs < 15_000L || lastLoggedTrackId == track.id) return
        val durationToLog = accumulatedListenMs.coerceAtLeast(15_000L)
        lastLoggedTrackId = track.id
        container.applicationScope.launch {
            runCatching {
                val s = container.preferencesRepository.soundLabSettings.first()
                if (s.pauseListenHistory) return@runCatching
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

    override fun onCleared() {
        commitListeningEventImmediate()
        persistPlaybackQueueImmediate()
        PlaybackDspBridge.setSleepFadeVolume(1.0f)
        mediaController?.removeListener(playerListener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        super.onCleared()
    }

    companion object {
        private const val MAX_AUTO_RECOVERY_ATTEMPTS = 3
    }
}
