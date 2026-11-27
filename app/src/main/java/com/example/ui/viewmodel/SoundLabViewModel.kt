package com.example.ui.viewmodel

import android.webkit.CookieManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.imageLoader
import com.example.core.LiquidAppContainer
import com.example.data.remote.EchoExtensionManager
import com.example.domain.model.AudioQuality
import com.example.domain.model.EchoAddonConfig
import com.example.domain.model.EqPreset
import com.example.domain.model.GlassOpticalPreset
import com.example.domain.model.GlassQualityTier
import com.example.domain.model.LiquidGlassConfig
import com.example.domain.model.MagicSoundPreset
import com.example.domain.model.ReverbPreset
import com.example.domain.model.SoundLabSettings
import com.example.player.LiquidAudioCache
import com.example.player.PlaybackDspBridge
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class SoundLabViewModel(
    private val container: LiquidAppContainer
) : ViewModel() {

    private val _settings = MutableStateFlow(SoundLabSettings())
    val settings: StateFlow<SoundLabSettings> = _settings.asStateFlow()

    private var persistSettingsJob: Job? = null

    val bandLabels: StateFlow<List<String>> = PlaybackDspBridge.soundLabController.bandLabels
    val hardwareAttached: StateFlow<Boolean> = PlaybackDspBridge.soundLabController.hardwareAttached
    val cacheSizeBytes: StateFlow<Long> = LiquidAudioCache.cacheSizeBytes

    private val _activeVisitorData = MutableStateFlow("")
    val activeVisitorData: StateFlow<String> = _activeVisitorData.asStateFlow()

    private val _settingsActionNotice = MutableStateFlow<String?>(null)
    val settingsActionNotice: StateFlow<String?> = _settingsActionNotice.asStateFlow()

    private val _ytmSyncInProgress = MutableStateFlow(false)
    val ytmSyncInProgress: StateFlow<Boolean> = _ytmSyncInProgress.asStateFlow()

    val configuredAddons: StateFlow<List<EchoAddonConfig>> = container.preferencesRepository.soundLabSettings
        .map { s ->
            val parsed = EchoExtensionManager.parseAddonsJson(s.addonsJson).toMutableList()
            listOf(s.losslessExtensionUrl, s.customExtensionUrl)
                .map { container.extensionManager.sanitizeUrl(it) }
                .filter { url ->
                    url.isNotBlank() &&
                        !url.contains("saavn.sumit.co", ignoreCase = true) &&
                        !url.contains("saavn.dev", ignoreCase = true) &&
                        !url.contains("jiosaavn.com", ignoreCase = true)
                }
                .forEach { legacy ->
                    if (parsed.none { it.baseUrl.equals(legacy, ignoreCase = true) }) {
                        parsed.add(
                            EchoAddonConfig(
                                id = "addon_${legacy.hashCode()}",
                                baseUrl = legacy,
                                name = legacy.substringAfter("://").substringBefore("/"),
                                supportsLossless = legacy == container.extensionManager.sanitizeUrl(s.losslessExtensionUrl),
                                enabled = true,
                                priority = parsed.size
                            )
                        )
                    }
                }
            parsed.sortedBy { it.priority }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            container.preferencesRepository.soundLabSettings.collect { s ->
                if (persistSettingsJob?.isActive != true) {
                    _settings.value = s
                }
            }
        }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            LiquidAudioCache.refreshCacheSize()
            container.preferencesRepository.soundLabSettings
                .map { s ->
                    listOf(
                        s.preferExtensionAudio,
                        s.playbackEngine,
                        s.forceOpusAudio,
                        s.innerTubeCookie,
                        s.visitorData,
                        s.dataSyncId,
                        s.contentCountry,
                        s.contentLanguage,
                        s.proxyEnabled,
                        s.proxyUrl,
                        s.addonsJson,
                        s.losslessExtensionUrl,
                        s.customExtensionUrl
                    )
                }
                .distinctUntilChanged()
                .collect {
                    val s = container.preferencesRepository.soundLabSettings.first()
                    container.musicRepository.applyEchoProviderSettings(s)
                    _activeVisitorData.value = container.musicRepository.getActiveVisitorData()
                }
        }
    }

    fun clearActionNotice() {
        _settingsActionNotice.value = null
    }

    fun updateEchoSettings(
        notice: String? = null,
        transform: (SoundLabSettings) -> SoundLabSettings
    ) {
        updateSettings(transform)
        if (!notice.isNullOrBlank()) {
            _settingsActionNotice.value = notice
        }
    }

    fun refreshInnerTubeVisitorData(onCompleted: () -> Unit = {}) {
        viewModelScope.launch {
            val newVd = container.musicRepository.refreshVisitorData()
            _activeVisitorData.value = newVd
            if (newVd.isNotBlank()) {
                updateSettings { it.copy(visitorData = newVd) }
                _settingsActionNotice.value = "Refreshed YouTube Music VisitorData token"
            } else {
                _settingsActionNotice.value = "Could not fetch fresh VisitorData — check connection"
            }
            onCompleted()
        }
    }

    fun clearListenHistory() {
        viewModelScope.launch {
            container.dao.clearListeningHistory()
            _settingsActionNotice.value = "Listening history cleared"
        }
    }

    fun clearSearchHistory() {
        viewModelScope.launch {
            container.dao.clearSearchHistory()
            _settingsActionNotice.value = "Search history cleared"
        }
    }

    fun clearAllDownloads() {
        viewModelScope.launch {
            container.downloadManager.clearAllDownloads()
            _settingsActionNotice.value = "All offline downloaded songs removed"
        }
    }

    fun adjustLyricsOffset(deltaMs: Long) {
        updateSettings { it.copy(lyricsOffsetMs = (it.lyricsOffsetMs + deltaMs).coerceIn(-10_000L, 10_000L)) }
    }

    fun resetLyricsOffset() {
        updateSettings { it.copy(lyricsOffsetMs = 0L) }
        _settingsActionNotice.value = "Lyrics timing offset reset to 0.0s"
    }

    fun addAndTestAddon(rawUrl: String, onResult: (String) -> Unit = {}) {
        viewModelScope.launch {
            val result = container.extensionManager.inspectAddon(rawUrl)
            result.fold(
                onSuccess = { verified ->
                    val current = configuredAddons.value.toMutableList()
                    val existingIdx = current.indexOfFirst { it.baseUrl.equals(verified.baseUrl, ignoreCase = true) }
                    if (existingIdx >= 0) {
                        current[existingIdx] = verified.copy(priority = existingIdx)
                    } else {
                        current.add(verified.copy(priority = current.size))
                    }
                    val json = EchoExtensionManager.serializeAddonsJson(current)
                    updateSettings { it.copy(addonsJson = json) }
                    val msg = "Verified ${verified.name} (${verified.latencyMs ?: 0}ms)"
                    _settingsActionNotice.value = msg
                    onResult(msg)
                },
                onFailure = { err ->
                    val msg = "Addon check failed: ${err.message ?: "Unreachable"}"
                    _settingsActionNotice.value = msg
                    onResult(msg)
                }
            )
        }
    }

    fun toggleAddonEnabled(addonId: String) {
        val updated = configuredAddons.value.map {
            if (it.id == addonId) it.copy(enabled = !it.enabled) else it
        }
        updateSettings { it.copy(addonsJson = EchoExtensionManager.serializeAddonsJson(updated)) }
    }

    fun moveAddonPriority(addonId: String, moveUp: Boolean) {
        val list = configuredAddons.value.toMutableList()
        val idx = list.indexOfFirst { it.id == addonId }
        if (idx < 0) return
        val target = if (moveUp) idx - 1 else idx + 1
        if (target !in list.indices) return
        val item = list.removeAt(idx)
        list.add(target, item)
        val reindexed = list.mapIndexed { i, a -> a.copy(priority = i) }
        updateSettings { it.copy(addonsJson = EchoExtensionManager.serializeAddonsJson(reindexed)) }
    }

    fun removeAddon(addonId: String) {
        val remaining = configuredAddons.value.filterNot { it.id == addonId }
            .mapIndexed { i, a -> a.copy(priority = i) }
        updateSettings {
            it.copy(
                addonsJson = EchoExtensionManager.serializeAddonsJson(remaining),
                losslessExtensionUrl = if (remaining.none { a -> a.baseUrl == container.extensionManager.sanitizeUrl(it.losslessExtensionUrl) }) "" else it.losslessExtensionUrl
            )
        }
        _settingsActionNotice.value = "Extension removed"
    }

    fun completeYouTubeWebViewLogin(
        cookie: String,
        visitorData: String,
        dataSyncId: String,
        onFinished: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _ytmSyncInProgress.value = true
            val info = runCatching {
                container.musicRepository.fetchYouTubeAccountInfo(cookie, visitorData, dataSyncId)
            }.getOrNull()

            updateSettings { current ->
                current.copy(
                    innerTubeCookie = cookie,
                    visitorData = visitorData.ifBlank { current.visitorData },
                    dataSyncId = dataSyncId.ifBlank { current.dataSyncId },
                    ytmAccountName = info?.name?.ifBlank { "YouTube Music Account" } ?: "YouTube Music Account",
                    ytmAccountHandle = info?.handle.orEmpty(),
                    ytmAccountAvatarUrl = info?.avatarUrl.orEmpty()
                )
            }

            // Immediately sync Liked Songs, Playlists & Saved Albums into Room if sync is enabled
            val shouldSync = container.preferencesRepository.soundLabSettings.first().ytmSyncEnabled
            if (shouldSync) {
                val summary = container.musicRepository.syncYouTubeLibrary()
                if (summary.errorMessage == null) {
                    updateSettings { it.copy(ytmLastSyncedAt = System.currentTimeMillis()) }
                    _settingsActionNotice.value =
                        "Connected ${info?.name.orEmpty().ifBlank { "YouTube Music" }} · Synced ${summary.likedSongsSynced} likes, ${summary.playlistsSynced} playlists"
                } else {
                    _settingsActionNotice.value = "Connected YouTube Music account"
                }
            } else {
                _settingsActionNotice.value = "Connected YouTube Music account"
            }
            _ytmSyncInProgress.value = false
            onFinished()
        }
    }

    fun syncYouTubeLibraryNow(onFinished: () -> Unit = {}) {
        viewModelScope.launch {
            _ytmSyncInProgress.value = true
            val summary = container.musicRepository.syncYouTubeLibrary()
            if (summary.errorMessage != null) {
                _settingsActionNotice.value = summary.errorMessage
            } else {
                updateSettings { it.copy(ytmLastSyncedAt = System.currentTimeMillis()) }
                _settingsActionNotice.value =
                    "Synced ${summary.likedSongsSynced} likes, ${summary.playlistsSynced} playlists, ${summary.albumsSynced} albums"
            }
            _ytmSyncInProgress.value = false
            onFinished()
        }
    }

    fun disconnectYouTubeAccount() {
        runCatching {
            val cookieManager = CookieManager.getInstance()
            cookieManager.removeAllCookies(null)
            cookieManager.flush()
        }
        updateSettings {
            it.copy(
                innerTubeCookie = "",
                dataSyncId = "",
                ytmAccountName = "",
                ytmAccountHandle = "",
                ytmAccountAvatarUrl = "",
                ytmLastSyncedAt = 0L
            )
        }
        _settingsActionNotice.value = "Disconnected YouTube Music account"
    }

    fun clearImageCache() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val loader = container.appContext.imageLoader
                loader.memoryCache?.clear()
                loader.diskCache?.clear()
            }
            val imageCacheDir = File(container.appContext.cacheDir, "image_cache")
            if (imageCacheDir.exists()) {
                imageCacheDir.deleteRecursively()
            }
            _settingsActionNotice.value = "Image artwork cache cleared"
        }
    }

    fun toggleSkipSilence(enabled: Boolean) {
        updateSettings { it.copy(skipSilenceEnabled = enabled) }
    }

    fun toggleVolumeNormalization(enabled: Boolean) {
        updateSettings { it.copy(volumeNormalizationEnabled = enabled) }
    }

    fun toggleGaplessPlayback(enabled: Boolean) {
        updateSettings { it.copy(gaplessPlaybackEnabled = enabled) }
    }

    fun toggleAutoPlayRadio(enabled: Boolean) {
        updateSettings { it.copy(autoPlayRadioEnabled = enabled) }
    }

    fun updateMaxCacheSizeMb(sizeMb: Int) {
        updateSettingsDebounced(applyDsp = false) { it.copy(maxCacheSizeMb = sizeMb.coerceIn(128, 4096)) }
    }

    fun clearAudioCache() {
        viewModelScope.launch {
            LiquidAudioCache.clearCache(container.appContext)
            _settingsActionNotice.value = "Audio stream cache cleared"
        }
    }

    fun selectEqPreset(preset: EqPreset) {
        updateSettings { current ->
            current.copy(
                eqPreset = preset,
                eqBandLevelsDb = if (preset == EqPreset.CUSTOM) current.eqBandLevelsDb else preset.bandsDb
            )
        }
    }

    fun updateBandLevel(bandIndex: Int, levelDb: Float) {
        updateSettingsDebounced { current ->
            val updatedBands = current.eqBandLevelsDb.toMutableList()
            if (bandIndex in updatedBands.indices) {
                updatedBands[bandIndex] = levelDb.coerceIn(-12f, 12f)
            }
            current.copy(
                eqPreset = EqPreset.CUSTOM,
                eqBandLevelsDb = updatedBands
            )
        }
    }

    fun updateBassBoost(strength: Int) {
        updateSettingsDebounced { it.copy(bassBoostStrength = strength.coerceIn(0, 1000)) }
    }

    fun selectReverb(reverb: ReverbPreset) {
        updateSettings { it.copy(reverbPreset = reverb) }
    }

    fun updateSpeed(speed: Float) {
        updateSettingsDebounced {
            it.copy(
                playbackSpeed = speed.coerceIn(0.5f, 2.0f),
                activeMagicPreset = MagicSoundPreset.NORMAL
            )
        }
    }

    fun updatePitch(pitch: Float) {
        updateSettingsDebounced {
            it.copy(
                playbackPitch = pitch.coerceIn(0.5f, 2.0f),
                activeMagicPreset = MagicSoundPreset.NORMAL
            )
        }
    }

    fun applyMagicPreset(preset: MagicSoundPreset) {
        updateSettings { current ->
            when (preset) {
                MagicSoundPreset.NORMAL -> current.copy(
                    playbackSpeed = 1.0f,
                    playbackPitch = 1.0f,
                    reverbPreset = ReverbPreset.NONE,
                    eightDEnabled = false,
                    activeMagicPreset = MagicSoundPreset.NORMAL
                )
                MagicSoundPreset.SLOWED_REVERB -> current.copy(
                    playbackSpeed = 0.84f,
                    playbackPitch = 0.86f,
                    reverbPreset = ReverbPreset.LARGE_HALL,
                    bassBoostStrength = 520,
                    eightDEnabled = false,
                    activeMagicPreset = MagicSoundPreset.SLOWED_REVERB
                )
                MagicSoundPreset.NIGHTCORE -> current.copy(
                    playbackSpeed = 1.24f,
                    playbackPitch = 1.22f,
                    reverbPreset = ReverbPreset.SMALL_ROOM,
                    eqPreset = EqPreset.STUDIO,
                    eqBandLevelsDb = EqPreset.STUDIO.bandsDb,
                    eightDEnabled = false,
                    activeMagicPreset = MagicSoundPreset.NIGHTCORE
                )
                MagicSoundPreset.EIGHT_D -> current.copy(
                    playbackSpeed = 1.0f,
                    playbackPitch = 1.0f,
                    reverbPreset = ReverbPreset.PLATE,
                    eightDEnabled = true,
                    activeMagicPreset = MagicSoundPreset.EIGHT_D
                )
            }
        }
    }

    fun updateCrossfade(seconds: Int) {
        updateSettingsDebounced { it.copy(crossfadeSeconds = seconds.coerceIn(0, 12)) }
    }

    fun updateAudioQuality(quality: AudioQuality) {
        updateSettings { it.copy(audioQuality = quality) }
    }

    fun updateGlassQuality(tier: GlassQualityTier) {
        updateSettings { it.copy(glassQualityTier = tier) }
    }

    fun toggleEightD(enabled: Boolean) {
        updateSettings {
            it.copy(
                eightDEnabled = enabled,
                activeMagicPreset = if (enabled) MagicSoundPreset.EIGHT_D else MagicSoundPreset.NORMAL
            )
        }
    }

    fun applyGlassOpticalPreset(preset: GlassOpticalPreset) {
        updateSettings { current ->
            current.copy(
                glassConfig = LiquidGlassConfig(
                    opticalPreset = preset,
                    pillScale = preset.pillScale,
                    springStiffness = preset.springStiffness,
                    springDamping = preset.springDamping,
                    refractionHeightDp = preset.refractionHeightDp,
                    refractionAmountDp = preset.refractionAmountDp,
                    blurRadiusDp = preset.blurRadiusDp,
                    chromaticAberration = preset.chromaticAberration
                )
            )
        }
    }

    fun updateGlassConfig(transform: (LiquidGlassConfig) -> LiquidGlassConfig) {
        updateSettingsDebounced(applyDsp = false) { current ->
            val updated = transform(current.glassConfig).copy(
                opticalPreset = GlassOpticalPreset.CUSTOM
            )
            current.copy(glassConfig = updated)
        }
    }

    private fun updateSettingsDebounced(
        delayMs: Long = 220L,
        applyDsp: Boolean = true,
        transform: (SoundLabSettings) -> SoundLabSettings
    ) {
        val next = transform(_settings.value)
        _settings.value = next
        if (applyDsp) {
            PlaybackDspBridge.applySoundLab(next)
        }
        persistSettingsJob?.cancel()
        persistSettingsJob = viewModelScope.launch {
            delay(delayMs)
            val snapshot = _settings.value
            container.preferencesRepository.updateSoundLabSettings { snapshot }
        }
    }

    private fun updateSettings(transform: (SoundLabSettings) -> SoundLabSettings) {
        persistSettingsJob?.cancel()
        val optimistic = transform(_settings.value)
        _settings.value = optimistic
        PlaybackDspBridge.applySoundLab(optimistic)
        viewModelScope.launch {
            var updatedSettings: SoundLabSettings? = null
            container.preferencesRepository.updateSoundLabSettings { current ->
                val next = transform(current)
                updatedSettings = next
                next
            }
            updatedSettings?.let { next ->
                _settings.value = next
                PlaybackDspBridge.applySoundLab(next)
            }
        }
    }
}
