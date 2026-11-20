package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.LiquidAppContainer
import com.example.data.local.PlaylistEntity
import com.example.data.local.PlaylistTrackCrossRef
import com.example.data.local.TrackEntity
import com.example.domain.model.Playlist
import com.example.domain.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(
    private val container: LiquidAppContainer
) : ViewModel() {

    val playlists: StateFlow<List<Playlist>> = combine(
        container.dao.observePlaylists(),
        container.dao.observeAllPlaylistRefs(),
        container.dao.observeAllStoredTracks(),
        container.dao.observeAllDownloads()
    ) { playlistEntities, refs, storedTracks, downloads ->
        val downloadMap = downloads.associate { it.trackId to it.filePath }
        val trackMap = storedTracks.associate { it.id to it.toDomain(downloadMap[it.id]) }
        val refsByPlaylist = refs.groupBy { it.playlistId }

        playlistEntities.map { entity ->
            val tracks = refsByPlaylist[entity.id]
                .orEmpty()
                .sortedBy { it.position }
                .mapNotNull { trackMap[it.trackId] }
            Playlist(
                id = entity.id,
                name = entity.name,
                description = entity.description,
                coverArtUri = entity.coverArtUri,
                createdAt = entity.createdAt,
                tracks = tracks
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val likedTracks: StateFlow<List<Track>> = combine(
        container.dao.observeLikedTracks(),
        container.dao.observeAllDownloads()
    ) { liked, downloads ->
        val downloadMap = downloads.associate { it.trackId to it.filePath }
        liked.map { it.toDomain(downloadMap[it.id]) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val downloadedTracks: StateFlow<List<Track>> = combine(
        container.dao.observeDownloadedTracks(),
        container.dao.observeAllDownloads()
    ) { tracks, downloads ->
        val downloadMap = downloads.associate { it.trackId to it.filePath }
        tracks.map { it.toDomain(downloadMap[it.id]) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _localTracks = MutableStateFlow<List<Track>>(emptyList())
    val localTracks: StateFlow<List<Track>> = _localTracks.asStateFlow()

    private val _hasAudioPermission = MutableStateFlow(container.localAudioRepository.hasAudioPermission())
    val hasAudioPermission: StateFlow<Boolean> = _hasAudioPermission.asStateFlow()

    val requiredAudioPermission: String
        get() = container.localAudioRepository.requiredAudioPermission()

    init {
        refreshLocalAudio()
    }

    fun refreshLocalAudio() {
        val granted = container.localAudioRepository.hasAudioPermission()
        _hasAudioPermission.value = granted
        if (granted) {
            viewModelScope.launch {
                _localTracks.value = container.localAudioRepository.scanDeviceAudio()
            }
        }
    }

    fun createPlaylist(
        name: String,
        description: String,
        coverArtUri: String,
        initialTrack: Track? = null
    ) {
        val cleanName = name.trim().ifBlank { return }
        viewModelScope.launch {
            val id = container.dao.insertPlaylist(
                PlaylistEntity(
                    name = cleanName,
                    description = description.trim(),
                    coverArtUri = coverArtUri
                )
            )
            if (initialTrack != null) {
                container.dao.upsertTrack(TrackEntity.fromDomain(initialTrack))
                container.dao.insertPlaylistTrackRef(
                    PlaylistTrackCrossRef(
                        playlistId = id,
                        trackId = initialTrack.id,
                        position = 0
                    )
                )
            }
        }
    }

    fun updatePlaylist(playlist: Playlist, newName: String, newDescription: String, newCoverUri: String) {
        val cleanName = newName.trim().ifBlank { return }
        viewModelScope.launch {
            container.dao.updatePlaylist(
                PlaylistEntity(
                    id = playlist.id,
                    name = cleanName,
                    description = newDescription.trim(),
                    coverArtUri = newCoverUri,
                    createdAt = playlist.createdAt
                )
            )
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            container.dao.deletePlaylist(playlistId)
        }
    }

    fun addTrackToPlaylist(playlistId: Long, track: Track) {
        viewModelScope.launch {
            container.dao.upsertTrack(TrackEntity.fromDomain(track))
            val nextPos = container.dao.getMaxPlaylistPosition(playlistId) + 1
            container.dao.insertPlaylistTrackRef(
                PlaylistTrackCrossRef(
                    playlistId = playlistId,
                    trackId = track.id,
                    position = nextPos
                )
            )
        }
    }

    fun removeTrackFromPlaylist(playlistId: Long, trackId: String) {
        viewModelScope.launch {
            container.dao.removeTrackFromPlaylist(playlistId, trackId)
        }
    }

    fun deleteOfflineDownload(trackId: String) {
        viewModelScope.launch {
            container.downloadManager.deleteDownload(trackId)
        }
    }
}
