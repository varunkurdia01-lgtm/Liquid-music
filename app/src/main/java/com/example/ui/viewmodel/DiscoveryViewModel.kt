package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.LiquidAppContainer
import com.example.data.local.PlaylistEntity
import com.example.data.local.PlaylistTrackCrossRef
import com.example.data.local.SearchHistoryEntity
import com.example.data.local.TrackEntity
import com.example.domain.model.AlbumDetail
import com.example.domain.model.ArtistDetail
import com.example.domain.model.BrowseSection
import com.example.domain.model.MoodItem
import com.example.domain.model.MusicSource
import com.example.domain.model.PlaylistSummary
import com.example.domain.model.QuickPicksMode
import com.example.domain.model.Track
import com.example.domain.model.UnifiedSearchResults
import com.example.domain.model.VibeDjResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SearchFilter(val label: String) {
    ALL("All"),
    SONGS("Songs"),
    ALBUMS("Albums"),
    ARTISTS("Artists"),
    PLAYLISTS("Playlists"),
    LOCAL("Local")
}

sealed interface VibeDjUiState {
    data object Idle : VibeDjUiState
    data object Loading : VibeDjUiState
    data class Success(val result: VibeDjResult, val savedToLibrary: Boolean = false) : VibeDjUiState
    data class Error(val message: String, val lastPrompt: String) : VibeDjUiState
}

sealed interface DetailOverlayState {
    data object None : DetailOverlayState
    data class Album(
        val browseId: String,
        val source: MusicSource,
        val isLoading: Boolean = true,
        val detail: AlbumDetail? = null,
        val error: String? = null
    ) : DetailOverlayState

    data class Artist(
        val browseId: String,
        val source: MusicSource,
        val isLoading: Boolean = true,
        val detail: ArtistDetail? = null,
        val error: String? = null
    ) : DetailOverlayState
}

data class DiscoveryUiState(
    val selectedHomeMood: String = "All",
    val isLoadingHome: Boolean = true,
    val homeError: String? = null,
    val providerNotice: String? = null,
    val activeCatalogSource: MusicSource = MusicSource.YOUTUBE_MUSIC,
    val browseSections: List<BrowseSection> = emptyList(),
    val quickPicks: List<Track> = emptyList(),
    val keepListening: List<Track> = emptyList(),
    val forgottenFavorites: List<Track> = emptyList(),
    val similarRecommendationTitle: String = "",
    val similarRecommendationTracks: List<Track> = emptyList(),
    val moods: List<MoodItem> = emptyList(),
    val moodTracks: List<Track> = emptyList(),
    val isLoadingMood: Boolean = false,
    val jumpBackIn: List<Track> = emptyList(),
    val localTracks: List<Track> = emptyList(),
    val showHeroCarousel: Boolean = true,
    val showSpeedDial: Boolean = true,
    val showMoodAndGenres: Boolean = true,
    val searchQuery: String = "",
    val searchFilter: SearchFilter = SearchFilter.ALL,
    val isSearching: Boolean = false,
    val searchSuggestions: List<String> = emptyList(),
    val searchResults: UnifiedSearchResults = UnifiedSearchResults(),
    val localSearchResults: List<Track> = emptyList(),
    val detailOverlay: DetailOverlayState = DetailOverlayState.None,
    val vibePrompt: String = "",
    val vibeState: VibeDjUiState = VibeDjUiState.Idle
) {
    val displayedSongs: List<Track>
        get() = when (searchFilter) {
            SearchFilter.ALL -> (localSearchResults + searchResults.songs).distinctBy { it.id }
            SearchFilter.SONGS -> searchResults.songs
            SearchFilter.LOCAL -> localSearchResults
            else -> emptyList()
        }
}

class DiscoveryViewModel(
    private val container: LiquidAppContainer
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiscoveryUiState())
    val uiState: StateFlow<DiscoveryUiState> = _uiState.asStateFlow()

    val searchHistory: StateFlow<List<String>> = container.dao.observeSearchHistory()
        .map { list -> list.map { it.query } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var searchDebounceJob: Job? = null
    private var detailLoadJob: Job? = null

    init {
        observeEchoHomeSettings()
        observeJumpBackInAndEchoPersonalized()
    }

    private fun observeEchoHomeSettings() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            container.preferencesRepository.soundLabSettings
                .map { s ->
                    listOf(
                        s.contentCountry,
                        s.contentLanguage,
                        s.hideExplicit,
                        s.hideVideoSongs,
                        s.randomizeHomeOrder,
                        s.quickPicksMode,
                        s.showHomeHeroCarousel,
                        s.showSpeedDialQuickPicks,
                        s.showHomeMoodAndGenres,
                        s.blockedArtistsCsv
                    )
                }
                .distinctUntilChanged()
                .collect {
                    val s = container.preferencesRepository.soundLabSettings.first()
                    container.musicRepository.applyEchoProviderSettings(s)
                    _uiState.update {
                        it.copy(
                            showHeroCarousel = s.showHomeHeroCarousel,
                            showSpeedDial = s.showSpeedDialQuickPicks,
                            showMoodAndGenres = s.showHomeMoodAndGenres
                        )
                    }
                    loadHomeShelves()
                }
        }
    }

    fun loadHomeShelves() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _uiState.update { it.copy(isLoadingHome = true, homeError = null) }
            val settings = container.preferencesRepository.soundLabSettings.first()
            container.musicRepository.applyEchoProviderSettings(settings)

            val browseDef = async(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { container.musicRepository.loadHomeAndExplore() }
            }
            val localDef = async(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { container.localAudioRepository.scanDeviceAudio() }.getOrDefault(emptyList())
            }

            val browseResult = browseDef.await()
            val local = localDef.await()
            val blockedArtists = settings.blockedArtistsCsv
                .split(",")
                .map { it.trim().lowercase() }
                .filter { it.isNotBlank() }
                .toSet()

            fun filterEchoTracks(tracks: List<Track>): List<Track> {
                return tracks.filter { t ->
                    (!settings.hideExplicit || !t.explicit) &&
                        (!settings.hideVideoSongs || !t.isVideoSong) &&
                        blockedArtists.none { blocked -> t.artist.lowercase().contains(blocked) }
                }
            }

            browseResult.fold(
                onSuccess = { bundle ->
                    val filteredSections = bundle.sections.map { sec ->
                        sec.copy(tracks = filterEchoTracks(sec.tracks))
                    }.filter { it.tracks.isNotEmpty() || it.albums.isNotEmpty() || it.artists.isNotEmpty() }
                        .let { list -> if (settings.randomizeHomeOrder) list.shuffled() else list }

                    val feedTracks = filteredSections.flatMap { it.tracks }.distinctBy { it.id }
                    val recentSeed = _uiState.value.jumpBackIn.firstOrNull()
                    val relatedToRecent = if (recentSeed != null && settings.quickPicksMode == QuickPicksMode.LAST_LISTENED) {
                        runCatching { container.musicRepository.getEndlessRadio(recentSeed, limit = 16) }
                            .getOrDefault(emptyList())
                            .let(::filterEchoTracks)
                    } else emptyList()

                    val quickPicksList = (relatedToRecent + feedTracks).distinctBy { it.id }.take(20)

                    // Similar to [Your Recent Song] recommendation shelf (Echo HomeViewModel style)
                    val simSeed = recentSeed ?: feedTracks.firstOrNull()
                    val simTracks = if (simSeed != null) {
                        if (relatedToRecent.isNotEmpty()) relatedToRecent.take(12)
                        else runCatching { container.musicRepository.getEndlessRadio(simSeed, limit = 12) }
                            .getOrDefault(emptyList())
                            .let(::filterEchoTracks)
                    } else emptyList()

                    _uiState.update {
                        it.copy(
                            isLoadingHome = false,
                            homeError = null,
                            activeCatalogSource = bundle.activeSource,
                            providerNotice = bundle.noticeMessage,
                            browseSections = filteredSections,
                            quickPicks = quickPicksList,
                            similarRecommendationTitle = simSeed?.let { s -> "Similar to ${s.title}" }.orEmpty(),
                            similarRecommendationTracks = simTracks,
                            moods = bundle.moods,
                            localTracks = local
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoadingHome = false,
                            homeError = err.localizedMessage ?: "Unable to load music catalog.",
                            localTracks = local
                        )
                    }
                }
            )
        }
    }

    fun selectHomeMood(moodTitle: String) {
        _uiState.update { it.copy(selectedHomeMood = moodTitle) }
        if (moodTitle == "All" || moodTitle == "Local Files") {
            _uiState.update { it.copy(moodTracks = emptyList(), isLoadingMood = false) }
            return
        }
        val matchingMood = _uiState.value.moods.firstOrNull { it.title.equals(moodTitle, ignoreCase = true) }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMood = true) }
            // Try browsing mood browseId first if available, else unified search
            val browsedTracks = if (matchingMood != null && matchingMood.browseId.isNotBlank()) {
                runCatching {
                    container.musicRepository.getPlaylistTracks(matchingMood.browseId, MusicSource.YOUTUBE_MUSIC)
                }.getOrDefault(emptyList())
            } else emptyList()

            val tracks = if (browsedTracks.isNotEmpty()) {
                browsedTracks
            } else {
                val query = matchingMood?.query ?: "$moodTitle music hits"
                runCatching { container.musicRepository.searchUnified(query, limit = 20) }
                    .getOrDefault(UnifiedSearchResults())
                    .songs
            }
            _uiState.update { it.copy(isLoadingMood = false, moodTracks = tracks) }
        }
    }

    fun buildSurpriseMeQueue(): List<Track> {
        val state = _uiState.value
        val pool = (state.quickPicks + state.browseSections.flatMap { it.tracks } + state.jumpBackIn + state.localTracks)
            .distinctBy { it.id }
            .shuffled()
        return pool.take(25)
    }

    fun refreshLocalTracks() {
        viewModelScope.launch {
            val local = container.localAudioRepository.scanDeviceAudio()
            _uiState.update { it.copy(localTracks = local) }
        }
    }

    private fun observeJumpBackInAndEchoPersonalized() {
        viewModelScope.launch {
            container.dao.observeRecentTrackIds().collect { recentIds ->
                val storedTracks = container.dao.observeAllStoredTracks().first()
                val byId = storedTracks.associateBy { it.id }
                val recent = recentIds.mapNotNull { byId[it]?.toDomain() }
                container.musicRepository.registerTracks(recent)

                // Echo Music Keep Listening & Forgotten Favorites shelves from stored history
                val keepListening = recent.drop(1).take(12)
                val recentSet = recentIds.take(4).toSet()
                val forgotten = storedTracks
                    .map { it.toDomain() }
                    .filterNot { it.id in recentSet }
                    .shuffled()
                    .take(12)

                _uiState.update {
                    it.copy(
                        jumpBackIn = recent,
                        keepListening = keepListening,
                        forgottenFavorites = forgotten
                    )
                }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchDebounceJob?.cancel()
        if (query.isBlank()) {
            _uiState.update {
                it.copy(
                    isSearching = false,
                    searchSuggestions = emptyList(),
                    searchResults = UnifiedSearchResults(),
                    localSearchResults = emptyList()
                )
            }
            return
        }
        searchDebounceJob = viewModelScope.launch {
            delay(260L)
            val trimmed = query.trim()
            val suggestionsDef = async {
                runCatching { container.musicRepository.getSearchSuggestions(trimmed) }.getOrDefault(emptyList())
            }
            _uiState.update { it.copy(isSearching = true) }

            val qLower = trimmed.lowercase()
            val localMatches = _uiState.value.localTracks.filter {
                it.title.lowercase().contains(qLower) ||
                    it.artist.lowercase().contains(qLower) ||
                    it.album.lowercase().contains(qLower)
            }

            val unified = runCatching {
                container.musicRepository.searchUnified(trimmed, limit = 24)
            }.getOrElse {
                UnifiedSearchResults(errorMessage = it.localizedMessage ?: "Search failed.")
            }

            _uiState.update {
                it.copy(
                    isSearching = false,
                    searchSuggestions = suggestionsDef.await(),
                    localSearchResults = localMatches,
                    searchResults = unified
                )
            }
        }
    }

    fun submitSearch(query: String) {
        val clean = query.trim()
        if (clean.isBlank()) return
        viewModelScope.launch {
            val pauseSearchHistory = container.preferencesRepository.soundLabSettings.first().pauseSearchHistory
            if (!pauseSearchHistory) {
                runCatching { container.dao.upsertSearchHistory(SearchHistoryEntity(clean)) }
            }
        }
        updateSearchQuery(clean)
    }

    fun deleteSearchHistoryItem(query: String) {
        viewModelScope.launch { container.dao.deleteSearchHistory(query) }
    }

    fun clearSearchHistory() {
        viewModelScope.launch { container.dao.clearSearchHistory() }
    }

    fun selectSearchFilter(filter: SearchFilter) {
        _uiState.update { it.copy(searchFilter = filter) }
    }

    fun openAlbum(browseId: String, source: MusicSource) {
        if (browseId.isBlank()) return
        detailLoadJob?.cancel()
        _uiState.update {
            it.copy(detailOverlay = DetailOverlayState.Album(browseId = browseId, source = source, isLoading = true))
        }
        detailLoadJob = viewModelScope.launch {
            val detail = runCatching { container.musicRepository.getAlbumDetail(browseId, source) }.getOrNull()
            _uiState.update {
                it.copy(
                    detailOverlay = DetailOverlayState.Album(
                        browseId = browseId,
                        source = source,
                        isLoading = false,
                        detail = detail,
                        error = if (detail == null) "Could not load album tracklist. Tap Retry." else null
                    )
                )
            }
        }
    }

    fun openArtist(browseId: String, source: MusicSource) {
        if (browseId.isBlank()) return
        detailLoadJob?.cancel()
        _uiState.update {
            it.copy(detailOverlay = DetailOverlayState.Artist(browseId = browseId, source = source, isLoading = true))
        }
        detailLoadJob = viewModelScope.launch {
            val detail = runCatching { container.musicRepository.getArtistDetail(browseId, source) }.getOrNull()
            _uiState.update {
                it.copy(
                    detailOverlay = DetailOverlayState.Artist(
                        browseId = browseId,
                        source = source,
                        isLoading = false,
                        detail = detail,
                        error = if (detail == null) "Could not load artist profile. Tap Retry." else null
                    )
                )
            }
        }
    }

    fun closeDetailOverlay() {
        detailLoadJob?.cancel()
        _uiState.update { it.copy(detailOverlay = DetailOverlayState.None) }
    }

    fun loadPlaylistTracksAndPlay(playlist: PlaylistSummary, onReady: (List<Track>) -> Unit) {
        viewModelScope.launch {
            val tracks = runCatching {
                container.musicRepository.getPlaylistTracks(playlist.browseId, playlist.sourceProvider)
            }.getOrDefault(emptyList())
            if (tracks.isNotEmpty()) {
                onReady(tracks)
            } else {
                val fallback = container.musicRepository.searchUnified(playlist.title, limit = 15).songs
                if (fallback.isNotEmpty()) onReady(fallback)
            }
        }
    }

    fun loadArtistRadioAndPlay(artist: ArtistDetail, onReady: (List<Track>) -> Unit) {
        viewModelScope.launch {
            val tracks = runCatching { container.musicRepository.getArtistRadio(artist) }
                .getOrDefault(artist.topSongs)
            if (tracks.isNotEmpty()) onReady(tracks)
        }
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
