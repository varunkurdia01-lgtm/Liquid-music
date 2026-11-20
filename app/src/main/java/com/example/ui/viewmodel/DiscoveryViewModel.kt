package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.LiquidAppContainer
import com.example.data.local.PlaylistEntity
import com.example.data.local.PlaylistTrackCrossRef
import com.example.data.local.TrackEntity
import com.example.data.remote.FallbackMusicCatalog
import com.example.domain.model.Track
import com.example.domain.model.VibeDjResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SearchFilter(val label: String) {
    ALL("All"),
    ONLINE("Online"),
    LOCAL("Local")
}

sealed interface VibeDjUiState {
    data object Idle : VibeDjUiState
    data object Loading : VibeDjUiState
    data class Success(val result: VibeDjResult, val savedToLibrary: Boolean = false) : VibeDjUiState
    data class Error(val message: String, val lastPrompt: String) : VibeDjUiState
}

data class DiscoveryUiState(
    val selectedHomeMood: String = "All",
    val isLoadingHome: Boolean = true,
    val quickPicks: List<Track> = emptyList(),
    val jumpBackIn: List<Track> = emptyList(),
    val globalPop: List<Track> = emptyList(),
    val hipHopPulse: List<Track> = emptyList(),
    val electronicSynth: List<Track> = emptyList(),
    val chillLateNight: List<Track> = emptyList(),
    val localTracks: List<Track> = emptyList(),
    val searchQuery: String = "",
    val searchFilter: SearchFilter = SearchFilter.ALL,
    val isSearching: Boolean = false,
    val onlineSearchResults: List<Track> = emptyList(),
    val localSearchResults: List<Track> = emptyList(),
    val vibePrompt: String = "",
    val vibeState: VibeDjUiState = VibeDjUiState.Idle
) {
    val displayedSearchResults: List<Track>
        get() = when (searchFilter) {
            SearchFilter.ALL -> localSearchResults + onlineSearchResults
            SearchFilter.ONLINE -> onlineSearchResults
            SearchFilter.LOCAL -> localSearchResults
        }
}

class DiscoveryViewModel(
    private val container: LiquidAppContainer
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiscoveryUiState())
    val uiState: StateFlow<DiscoveryUiState> = _uiState.asStateFlow()

    private var searchDebounceJob: Job? = null

    init {
        loadHomeShelves()
        observeJumpBackIn()
    }

    fun loadHomeShelves() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingHome = true) }

            val popDeferred = async {
                runCatching { container.saavnRepository.searchSongs("The Weeknd Dua Lipa Pop Hits", 12) }
                    .getOrDefault(FallbackMusicCatalog.seedTracks)
            }
            val hipHopDeferred = async {
                runCatching { container.saavnRepository.searchSongs("Travis Scott Kendrick Lamar Hip Hop", 12) }
                    .getOrDefault(FallbackMusicCatalog.seedTracks.shuffled())
            }
            val electronicDeferred = async {
                runCatching { container.saavnRepository.searchSongs("Daft Punk Electronic Synthwave", 12) }
                    .getOrDefault(FallbackMusicCatalog.seedTracks.reversed())
            }
            val chillDeferred = async {
                runCatching { container.saavnRepository.searchSongs("Lofi Chill Late Night Ambient", 10) }
                    .getOrDefault(FallbackMusicCatalog.seedTracks)
            }
            val localDeferred = async {
                container.localAudioRepository.scanDeviceAudio()
            }

            val pop = popDeferred.await().ifEmpty { FallbackMusicCatalog.seedTracks }
            val hipHop = hipHopDeferred.await().ifEmpty { FallbackMusicCatalog.seedTracks }
            val electronic = electronicDeferred.await().ifEmpty { FallbackMusicCatalog.seedTracks }
            val chill = chillDeferred.await().ifEmpty { FallbackMusicCatalog.seedTracks }
            val local = localDeferred.await()

            val quickPicks = (pop.take(2) + hipHop.take(2) + electronic.take(2))
                .distinctBy { it.id }
                .take(6)

            _uiState.update {
                it.copy(
                    isLoadingHome = false,
                    quickPicks = quickPicks,
                    globalPop = pop,
                    hipHopPulse = hipHop,
                    electronicSynth = electronic,
                    chillLateNight = chill,
                    localTracks = local
                )
            }
        }
    }

    fun refreshLocalTracks() {
        viewModelScope.launch {
            val local = container.localAudioRepository.scanDeviceAudio()
            _uiState.update { it.copy(localTracks = local) }
        }
    }

    private fun observeJumpBackIn() {
        viewModelScope.launch {
            container.dao.observeRecentTrackIds().collect { recentIds ->
                val storedTracks = container.dao.observeAllStoredTracks().first()
                val byId = storedTracks.associateBy { it.id }
                val recent = recentIds.mapNotNull { byId[it]?.toDomain() }
                _uiState.update { it.copy(jumpBackIn = recent) }
            }
        }
    }

    fun selectHomeMood(mood: String) {
        _uiState.update { it.copy(selectedHomeMood = mood) }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchDebounceJob?.cancel()
        if (query.isBlank()) {
            _uiState.update {
                it.copy(
                    isSearching = false,
                    onlineSearchResults = emptyList(),
                    localSearchResults = emptyList()
                )
            }
            return
        }
        searchDebounceJob = viewModelScope.launch {
            delay(320L)
            _uiState.update { it.copy(isSearching = true) }

            val qLower = query.trim().lowercase()
            val localMatches = _uiState.value.localTracks.filter {
                it.title.lowercase().contains(qLower) ||
                    it.artist.lowercase().contains(qLower) ||
                    it.album.lowercase().contains(qLower)
            }
            val onlineMatches = runCatching {
                container.saavnRepository.searchSongs(query.trim(), limit = 20)
            }.getOrDefault(emptyList())

            _uiState.update {
                it.copy(
                    isSearching = false,
                    localSearchResults = localMatches,
                    onlineSearchResults = onlineMatches
                )
            }
        }
    }

    fun selectSearchFilter(filter: SearchFilter) {
        _uiState.update { it.copy(searchFilter = filter) }
    }

    fun updateVibePrompt(prompt: String) {
        _uiState.update { it.copy(vibePrompt = prompt) }
    }

    fun triggerVibeDj(promptOverride: String? = null) {
        val prompt = (promptOverride ?: _uiState.value.vibePrompt).trim()
        if (prompt.isBlank()) return
        _uiState.update { it.copy(vibePrompt = prompt, vibeState = VibeDjUiState.Loading) }

        viewModelScope.launch {
            val result = container.vibeDjRepository.curateVibePlaylist(
                prompt = prompt,
                localTracks = _uiState.value.localTracks
            )
            result.fold(
                onSuccess = { curated ->
                    _uiState.update { it.copy(vibeState = VibeDjUiState.Success(curated)) }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            vibeState = VibeDjUiState.Error(
                                message = error.localizedMessage ?: "Gemini Vibe DJ could not generate a mix.",
                                lastPrompt = prompt
                            )
                        )
                    }
                }
            )
        }
    }

    fun triggerOfflineVibeFallback(prompt: String) {
        _uiState.update { it.copy(vibeState = VibeDjUiState.Loading) }
        viewModelScope.launch {
            val result = container.vibeDjRepository.curateOfflineVibe(prompt, _uiState.value.localTracks)
            _uiState.update { it.copy(vibeState = VibeDjUiState.Success(result)) }
        }
    }

    fun saveVibePlaylistToLibrary(vibe: VibeDjResult) {
        viewModelScope.launch {
            val cover = vibe.tracks.firstOrNull { it.artworkUrl.isNotBlank() }?.artworkUrl.orEmpty()
            val playlistId = container.dao.insertPlaylist(
                PlaylistEntity(
                    name = vibe.title,
                    description = vibe.manifesto,
                    coverArtUri = cover
                )
            )
            vibe.tracks.forEachIndexed { idx, track ->
                container.dao.upsertTrack(TrackEntity.fromDomain(track))
                container.dao.insertPlaylistTrackRef(
                    PlaylistTrackCrossRef(
                        playlistId = playlistId,
                        trackId = track.id,
                        position = idx
                    )
                )
            }
            _uiState.update { state ->
                val current = state.vibeState
                if (current is VibeDjUiState.Success) {
                    state.copy(vibeState = current.copy(savedToLibrary = true))
                } else state
            }
        }
    }
}
