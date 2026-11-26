package com.example.domain.provider

import com.example.domain.model.AlbumDetail
import com.example.domain.model.ArtistDetail
import com.example.domain.model.AudioQuality
import com.example.domain.model.BrowseSection
import com.example.domain.model.LyricsState
import com.example.domain.model.MoodItem
import com.example.domain.model.MusicSource
import com.example.domain.model.StreamResolution
import com.example.domain.model.Track
import com.example.domain.model.UnifiedSearchResults

/**
 * Provider-agnostic catalog contract modeled on Echo Music's modular provider isolation.
 * UI screens, ViewModels, and ExoPlayer never depend on concrete provider implementations.
 */
interface MusicCatalogProvider {
    val source: MusicSource

    suspend fun getHomeFeed(): List<BrowseSection>

    suspend fun getExploreChartsAndMoods(): Pair<List<BrowseSection>, List<MoodItem>>

    suspend fun search(query: String, limit: Int = 20): UnifiedSearchResults

    suspend fun getSearchSuggestions(query: String): List<String>

    suspend fun getAlbumDetail(browseId: String): AlbumDetail?

    suspend fun getArtistDetail(browseId: String): ArtistDetail?

    suspend fun getPlaylistTracks(browseId: String): List<Track>

    suspend fun getRadioQueue(seedTrack: Track, limit: Int = 18): List<Track>

    suspend fun getArtistRadio(radioPlaylistId: String, limit: Int = 20): List<Track> = emptyList()
}

/**
 * Provider-agnostic stream resolution contract for just-in-time playable audio URI resolution.
 */
interface StreamResolverProvider {
    val source: MusicSource

    suspend fun resolveStream(track: Track, quality: AudioQuality): StreamResolution?
}

/**
 * Provider-agnostic synced/plain lyrics contract.
 */
interface LyricsSourceProvider {
    val providerName: String

    suspend fun fetchLyrics(track: Track): LyricsState?
}
