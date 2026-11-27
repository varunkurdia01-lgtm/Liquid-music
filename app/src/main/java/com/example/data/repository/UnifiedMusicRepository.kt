package com.example.data.repository

import android.util.Log
import com.example.data.local.LikedTrackEntity
import com.example.data.local.LiquidDao
import com.example.data.local.PlaylistEntity
import com.example.data.local.PlaylistTrackCrossRef
import com.example.data.local.SavedAlbumEntity
import com.example.data.local.TrackEntity
import com.example.data.remote.AudiusFallbackProvider
import com.example.data.remote.BilibiliFallbackProvider
import com.example.data.remote.EchoExtensionManager
import com.example.data.remote.SaavnMusicRepository
import com.example.data.remote.SoundCloudFallbackProvider
import com.example.data.remote.innertube.InnerTubeClient
import com.example.data.remote.innertube.InnerTubeMusicProvider
import com.example.data.remote.innertube.InnerTubeParsers
import com.example.data.remote.innertube.InnerTubeXResolver
import com.example.domain.model.AlbumDetail
import com.example.domain.model.ArtistDetail
import com.example.domain.model.AudioQuality
import com.example.domain.model.BrowseLayoutType
import com.example.domain.model.BrowseSection
import com.example.domain.model.EchoPlaybackEngine
import com.example.domain.model.MoodItem
import com.example.domain.model.MusicSource
import com.example.domain.model.SoundLabSettings
import com.example.domain.model.SourceType
import com.example.domain.model.StreamResolution
import com.example.domain.model.Track
import com.example.domain.model.UnifiedSearchResults
import com.example.domain.model.YouTubeAccountInfo
import com.example.data.remote.innertube.PlayerClient
import com.example.domain.provider.MusicCatalogProvider
import com.example.domain.provider.StreamResolverProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class YouTubeSyncSummary(
    val likedSongsSynced: Int = 0,
    val playlistsSynced: Int = 0,
    val albumsSynced: Int = 0,
    val errorMessage: String? = null
)

private const val TAG = "UnifiedMusicRepo"

data class BrowseFeedBundle(
    val sections: List<BrowseSection>,
    val moods: List<MoodItem>,
    val activeSource: MusicSource,
    val usedFallback: Boolean,
    val noticeMessage: String? = null
)

/**
 * Echo-style Unified Provider Orchestrator.
 * Strictly isolates all UI screens, ViewModels, and ExoPlayer from concrete music backends.
 * Coordinates Primary (YouTube Music InnerTube) -> Secondary Fallback / 320kbps Extension (JioSaavn) -> Local/Offline.
 * Contains ZERO hardcoded or fake songs.
 */
class UnifiedMusicRepository(
    private val primaryCatalog: MusicCatalogProvider,
    private val primaryStream: StreamResolverProvider,
    private val fallbackCatalog: MusicCatalogProvider,
    private val fallbackStream: StreamResolverProvider,
    private val dao: LiquidDao,
    private val innerTubeClient: InnerTubeClient? = null,
    private val saavnRawRepository: SaavnMusicRepository? = null,
    private val extensionManager: EchoExtensionManager? = null,
    private val innerTubeProvider: InnerTubeMusicProvider? = null,
    private val audiusFallback: AudiusFallbackProvider? = null,
    private val soundCloudFallback: SoundCloudFallbackProvider? = null,
    private val bilibiliFallback: BilibiliFallbackProvider? = null,
    private val okHttpClient: OkHttpClient? = null,
    private val onProxyConfigured: ((Boolean, String) -> Unit)? = null
) {
    private val trackRegistry = ConcurrentHashMap<String, Track>()
    private val failedProvidersByTrack = ConcurrentHashMap<String, ConcurrentHashMap<String, Long>>()
    private val failedUrlsByTrack = ConcurrentHashMap<String, ConcurrentHashMap<String, Long>>()
    private val ytFailureCountByTrack = ConcurrentHashMap<String, Int>()

    private val probeClient: OkHttpClient? by lazy {
        okHttpClient?.newBuilder()
            ?.connectTimeout(2500, TimeUnit.MILLISECONDS)
            ?.readTimeout(2500, TimeUnit.MILLISECONDS)
            ?.callTimeout(3500, TimeUnit.MILLISECONDS)
            ?.followRedirects(true)
            ?.build()
    }

    @Volatile
    var preferExtensionAudio: Boolean = false

    @Volatile
    var playbackEngine: EchoPlaybackEngine = EchoPlaybackEngine.AUTO

    fun applyEchoProviderSettings(settings: SoundLabSettings) {
        preferExtensionAudio = settings.preferExtensionAudio || settings.playbackEngine == EchoPlaybackEngine.EXTENSION_FIRST
        playbackEngine = settings.playbackEngine
        val country = settings.contentCountry.ifBlank { "US" }
        val lang = settings.contentLanguage.ifBlank { "en" }
        val cookieVal = settings.innerTubeCookie.takeIf { it.isNotBlank() }
        val visitorVal = settings.visitorData.takeIf { it.isNotBlank() }
        val syncIdVal = settings.dataSyncId.takeIf { it.isNotBlank() }

        onProxyConfigured?.invoke(settings.proxyEnabled, settings.proxyUrl)
        InnerTubeXResolver.configureProxy(settings.proxyEnabled, settings.proxyUrl)

        innerTubeClient?.let { client ->
            client.contentCountry = country
            client.contentLanguage = lang
            client.forceOpusAudio = settings.forceOpusAudio
            client.cookie = cookieVal
            client.dataSyncId = syncIdVal
            client.configureProxy(settings.proxyEnabled, settings.proxyUrl)
            if (visitorVal != null) {
                client.visitorData = visitorVal
            }
        }
        InnerTubeXResolver.syncSession(
            cookie = cookieVal,
            visitorData = visitorVal ?: innerTubeClient?.visitorData,
            country = country,
            language = lang
        )
        saavnRawRepository?.let { repo ->
            if (settings.customExtensionUrl.isNotBlank()) {
                repo.customBaseUrl = settings.customExtensionUrl
            }
        }
        extensionManager?.updateConfiguredAddons(
            customExtensionUrl = settings.customExtensionUrl,
            losslessExtensionUrl = settings.losslessExtensionUrl,
            addonsJson = settings.addonsJson
        )
    }

    suspend fun fetchYouTubeAccountInfo(
        cookie: String,
        visitorData: String = "",
        dataSyncId: String = ""
    ): YouTubeAccountInfo? {
        val client = innerTubeClient ?: return null
        client.cookie = cookie.takeIf { it.isNotBlank() }
        if (visitorData.isNotBlank()) client.visitorData = visitorData
        if (dataSyncId.isNotBlank()) client.dataSyncId = dataSyncId
        InnerTubeXResolver.syncSession(
            cookie = client.cookie,
            visitorData = client.visitorData,
            country = client.contentCountry,
            language = client.contentLanguage
        )
        val menuJson = client.getAccountMenu() ?: return null
        return InnerTubeParsers.parseAccountInfo(menuJson, dataSyncId)
    }

    /**
     * Real Echo Music YouTube Library Sync:
     * Fetches FEmusic_liked_videos, FEmusic_liked_playlists, and FEmusic_liked_albums via authenticated InnerTube
     * and merges them directly into the local Room library without duplicating existing rows.
     */
    suspend fun syncYouTubeLibrary(): YouTubeSyncSummary = coroutineScope {
        val client = innerTubeClient ?: return@coroutineScope YouTubeSyncSummary(errorMessage = "InnerTube client unavailable")
        if (client.cookie.isNullOrBlank()) {
            return@coroutineScope YouTubeSyncSummary(errorMessage = "Sign in to YouTube Music first")
        }

        val likedDef = async { runCatching { client.browse("FEmusic_liked_videos") }.getOrNull() }
        val playlistsDef = async { runCatching { client.browse("FEmusic_liked_playlists") }.getOrNull() }
        val albumsDef = async { runCatching { client.browse("FEmusic_liked_albums") }.getOrNull() }

        var likedCount = 0
        var playlistsCount = 0
        var albumsCount = 0

        // 1. Sync Liked Songs
        likedDef.await()?.let { likedJson ->
            val likedTracks = InnerTubeParsers.parseBrowseSections(likedJson, "Liked Songs")
                .flatMap { it.tracks }
                .distinctBy { it.id }
            if (likedTracks.isNotEmpty()) {
                registerTracks(likedTracks)
                dao.upsertTracks(likedTracks.map { TrackEntity.fromDomain(it) })
                likedTracks.forEach { t ->
                    runCatching { dao.insertLike(LikedTrackEntity(t.id)) }
                }
                likedCount = likedTracks.size
            }
        }

        // 2. Sync Saved Albums
        albumsDef.await()?.let { albumsJson ->
            val albums = InnerTubeParsers.parseBrowseSections(albumsJson, "Albums")
                .flatMap { it.albums }
                .distinctBy { it.browseId }
            albums.forEach { album ->
                runCatching { dao.upsertSavedAlbum(SavedAlbumEntity.fromDomain(album)) }
            }
            albumsCount = albums.size
        }

        // 3. Sync YouTube Music Playlists (top 12 user playlists)
        playlistsDef.await()?.let { plJson ->
            val summaries = InnerTubeParsers.parseLibraryPlaylists(plJson)
                .filterNot { it.browseId.equals("VLLM", ignoreCase = true) || it.title.equals("Liked Music", ignoreCase = true) }
                .take(12)
            for (summary in summaries) {
                val tracks = runCatching {
                    innerTubeProvider?.getPlaylistTracks(summary.browseId).orEmpty()
                }.getOrDefault(emptyList())
                if (tracks.isEmpty()) continue
                registerTracks(tracks)
                dao.upsertTracks(tracks.map { TrackEntity.fromDomain(it) })
                val existing = dao.findPlaylistByName(summary.title)
                val playlistId = existing?.id ?: dao.insertPlaylist(
                    PlaylistEntity(
                        name = summary.title,
                        description = "Synced from YouTube Music",
                        coverArtUri = summary.artworkUrl.ifBlank { tracks.firstOrNull()?.artworkUrl.orEmpty() }
                    )
                )
                tracks.forEachIndexed { idx, t ->
                    runCatching { dao.insertPlaylistTrackRef(PlaylistTrackCrossRef(playlistId, t.id, idx)) }
                }
                playlistsCount++
            }
        }

        YouTubeSyncSummary(
            likedSongsSynced = likedCount,
            playlistsSynced = playlistsCount,
            albumsSynced = albumsCount
        )
    }

    fun invalidateStream(trackId: String) {
        val track = trackRegistry[trackId]
        val videoId = track?.providerTrackId?.ifBlank { trackId.removePrefix("ytm_") }
            ?: trackId.removePrefix("ytm_")
        innerTubeProvider?.invalidateStream(videoId)
    }

    /**
     * Records a stream HTTP refusal/playback failure (e.g. HTTP 567 or HTTP 403) so the next
     * resolution immediately rotates away from the dead URL/provider instead of hammering it.
     */
    fun markStreamFailed(
        trackId: String,
        failedUrl: String? = null,
        resolution: StreamResolution? = null,
        statusCode: Int = 0
    ) {
        if (trackId.isBlank()) return
        invalidateStream(trackId)
        val expiresAt = System.currentTimeMillis() + FAILED_PROVIDER_TTL_MS

        if (!failedUrl.isNullOrBlank()) {
            failedUrlsByTrack.getOrPut(trackId) { ConcurrentHashMap() }[failedUrl] = expiresAt
            InnerTubeXResolver.onRefused(failedUrl, trackId)
        }

        val slot = inferProviderSlot(resolution, failedUrl) ?: return
        if (slot == PROVIDER_YOUTUBE_MUSIC) {
            val count = (ytFailureCountByTrack[trackId] ?: 0) + 1
            ytFailureCountByTrack[trackId] = count
            // Allow 1 YouTube profile-rotation retry before failing over to secondary providers
            if (count >= 2) {
                failedProvidersByTrack.getOrPut(trackId) { ConcurrentHashMap() }[slot] = expiresAt
            }
            Log.w(TAG, "YouTube Music stream failed for $trackId (HTTP $statusCode, strike $count)")
        } else {
            failedProvidersByTrack.getOrPut(trackId) { ConcurrentHashMap() }[slot] = expiresAt
            Log.w(TAG, "Marked provider $slot failed for $trackId (HTTP $statusCode)")
        }
    }

    fun clearTrackFailures(trackId: String) {
        if (trackId.isBlank()) return
        failedProvidersByTrack.remove(trackId)
        failedUrlsByTrack.remove(trackId)
        ytFailureCountByTrack.remove(trackId)
    }

    private fun inferProviderSlot(resolution: StreamResolution?, url: String?): String? {
        val client = resolution?.clientName.orEmpty()
        when {
            client.contains("SoundCloud", ignoreCase = true) -> return PROVIDER_SOUNDCLOUD
            client.contains("Bilibili", ignoreCase = true) -> return PROVIDER_BILIBILI
            client.contains("Audius", ignoreCase = true) -> return PROVIDER_AUDIUS
        }
        when (resolution?.resolvedBy) {
            MusicSource.YOUTUBE_MUSIC -> return PROVIDER_YOUTUBE_MUSIC
            MusicSource.JIOSAAVN -> return PROVIDER_JIOSAAVN
            MusicSource.ECHO_ADDON -> return PROVIDER_ECHO_ADDON
            else -> Unit
        }
        val lower = url.orEmpty().lowercase()
        return when {
            lower.contains("googlevideo.com") || lower.contains("youtube.com") -> PROVIDER_YOUTUBE_MUSIC
            lower.contains("sndcdn.com") || lower.contains("soundcloud") -> PROVIDER_SOUNDCLOUD
            lower.contains("bilivideo") || lower.contains("bilibili") -> PROVIDER_BILIBILI
            lower.contains("saavn") -> PROVIDER_JIOSAAVN
            lower.contains("audius") -> PROVIDER_AUDIUS
            else -> null
        }
    }

    private fun isProviderFailed(trackId: String, slot: String): Boolean {
        val map = failedProvidersByTrack[trackId] ?: return false
        val now = System.currentTimeMillis()
        map.entries.removeAll { it.value <= now }
        return map.containsKey(slot)
    }

    private fun isUrlFailed(trackId: String, url: String): Boolean {
        val map = failedUrlsByTrack[trackId] ?: return false
        val now = System.currentTimeMillis()
        map.entries.removeAll { it.value <= now }
        return map.containsKey(url)
    }

    fun getActiveVisitorData(): String = innerTubeClient?.visitorData.orEmpty()

    suspend fun refreshVisitorData(): String {
        innerTubeClient?.resetVisitorData()
        val refreshed = innerTubeClient?.ensureVisitorData().orEmpty()
        InnerTubeXResolver.syncSession(
            cookie = innerTubeClient?.cookie,
            visitorData = refreshed,
            country = innerTubeClient?.contentCountry ?: "US",
            language = innerTubeClient?.contentLanguage ?: "en"
        )
        return refreshed
    }

    fun registerTracks(tracks: Collection<Track>) {
        tracks.forEach { trackRegistry[it.id] = it }
    }

    fun registerTrack(track: Track) {
        trackRegistry[track.id] = track
    }

    suspend fun findRegisteredTrack(trackId: String): Track? {
        return trackRegistry[trackId] ?: dao.getTrackById(trackId)?.toDomain()?.also {
            trackRegistry[it.id] = it
        }
    }

    suspend fun loadHomeAndExplore(): BrowseFeedBundle = coroutineScope {
        val primaryHomeDef = async { runCatching { primaryCatalog.getHomeFeed() }.getOrDefault(emptyList()) }
        val primaryExploreDef = async { runCatching { primaryCatalog.getExploreChartsAndMoods() }.getOrNull() }

        val homeSections = primaryHomeDef.await()
        val explorePair = primaryExploreDef.await()
        val exploreSections = explorePair?.first.orEmpty()
        val moods = explorePair?.second.orEmpty()

        val combinedPrimary = (homeSections + exploreSections).distinctBy { it.title }
        if (combinedPrimary.isNotEmpty()) {
            combinedPrimary.forEach { registerTracks(it.tracks) }
            val resolvedMoods = moods.ifEmpty { fallbackCatalog.getExploreChartsAndMoods().second }
            return@coroutineScope BrowseFeedBundle(
                sections = combinedPrimary,
                moods = resolvedMoods,
                activeSource = primaryCatalog.source,
                usedFallback = false
            )
        }

        // Automatic fallback to JioSaavn / 320kbps Extension if YouTube Music InnerTube is unreachable
        Log.w(TAG, "Primary catalog (${primaryCatalog.source}) returned empty; failing over to ${fallbackCatalog.source}")
        val fallbackHome = runCatching { fallbackCatalog.getHomeFeed() }.getOrDefault(emptyList())
        val fallbackExplore = runCatching { fallbackCatalog.getExploreChartsAndMoods() }.getOrNull()
        val combinedFallback = (fallbackHome + fallbackExplore?.first.orEmpty()).distinctBy { it.title }

        if (combinedFallback.isNotEmpty()) {
            combinedFallback.forEach { registerTracks(it.tracks) }
            return@coroutineScope BrowseFeedBundle(
                sections = combinedFallback,
                moods = fallbackExplore?.second.orEmpty(),
                activeSource = fallbackCatalog.source,
                usedFallback = true,
                noticeMessage = "Connected to ${fallbackCatalog.source.displayName} 320kbps catalog"
            )
        }

        // True offline resilience: show user's stored library tracks from Room if offline, never fake songs
        val stored = runCatching { dao.getAllStoredTracksOnce().map { it.toDomain() } }.getOrDefault(emptyList())
        registerTracks(stored)
        val offlineSections = if (stored.isNotEmpty()) {
            listOf(
                BrowseSection(
                    id = "offline_library_tracks",
                    title = "From Your Library",
                    subtitle = "Available offline from your listening history",
                    layoutType = BrowseLayoutType.QUICK_PICKS_GRID,
                    tracks = stored.take(16),
                    sourceProvider = MusicSource.LOCAL
                )
            )
        } else {
            emptyList()
        }

        BrowseFeedBundle(
            sections = offlineSections,
            moods = fallbackExplore?.second.orEmpty(),
            activeSource = MusicSource.YOUTUBE_MUSIC,
            usedFallback = false,
            noticeMessage = if (offlineSections.isEmpty()) "No network connection — tap Refresh to reconnect to YouTube Music" else "Offline mode — showing your saved library tracks"
        )
    }

    suspend fun searchUnified(query: String, limit: Int = 20): UnifiedSearchResults = coroutineScope {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@coroutineScope UnifiedSearchResults()

        val primaryDef = async { runCatching { primaryCatalog.search(trimmed, limit) }.getOrNull() }
        val addonDef = async {
            if (extensionManager?.hasActiveEchoAddons() == true) {
                runCatching { extensionManager.searchInlineAddons(trimmed, limit = 10) }.getOrDefault(emptyList())
            } else emptyList()
        }

        val primaryResult = primaryDef.await()
        val addonSongs = addonDef.await()
        if (addonSongs.isNotEmpty()) registerTracks(addonSongs)

        if (primaryResult != null && (!primaryResult.isEmpty || addonSongs.isNotEmpty())) {
            registerTracks(primaryResult.songs)
            return@coroutineScope primaryResult.copy(
                extensionSongs = addonSongs,
                activeSource = primaryCatalog.source,
                usedFallback = false
            )
        }

        Log.w(TAG, "Primary search empty/failed for '$trimmed'; switching to ${fallbackCatalog.source}")
        val fallbackResult = runCatching { fallbackCatalog.search(trimmed, limit) }.getOrNull()
        if (fallbackResult != null && (!fallbackResult.isEmpty || addonSongs.isNotEmpty())) {
            registerTracks(fallbackResult.songs)
            return@coroutineScope fallbackResult.copy(
                extensionSongs = addonSongs,
                activeSource = fallbackCatalog.source,
                usedFallback = true
            )
        }

        if (addonSongs.isNotEmpty()) {
            return@coroutineScope UnifiedSearchResults(
                extensionSongs = addonSongs,
                activeSource = MusicSource.ECHO_ADDON,
                usedFallback = false
            )
        }

        UnifiedSearchResults(
            activeSource = fallbackCatalog.source,
            usedFallback = true,
            errorMessage = "No matching music found for \"$trimmed\". Check your connection or try another query."
        )
    }

    suspend fun getSearchSuggestions(query: String): List<String> {
        val trimmed = query.trim()
        if (trimmed.length < 2) return emptyList()
        val primary = runCatching { primaryCatalog.getSearchSuggestions(trimmed) }.getOrDefault(emptyList())
        if (primary.isNotEmpty()) return primary
        return runCatching { fallbackCatalog.getSearchSuggestions(trimmed) }.getOrDefault(emptyList())
    }

    suspend fun getAlbumDetail(browseId: String, source: MusicSource): AlbumDetail? {
        val first = if (source == fallbackCatalog.source) fallbackCatalog else primaryCatalog
        val second = if (first === primaryCatalog) fallbackCatalog else primaryCatalog
        val detail = runCatching { first.getAlbumDetail(browseId) }.getOrNull()
            ?: runCatching { second.getAlbumDetail(browseId) }.getOrNull()
        detail?.let { registerTracks(it.tracks) }
        return detail
    }

    suspend fun getArtistDetail(browseId: String, source: MusicSource): ArtistDetail? {
        val first = if (source == fallbackCatalog.source) fallbackCatalog else primaryCatalog
        val second = if (first === primaryCatalog) fallbackCatalog else primaryCatalog
        val detail = runCatching { first.getArtistDetail(browseId) }.getOrNull()
            ?: runCatching { second.getArtistDetail(browseId) }.getOrNull()
        detail?.let { registerTracks(it.topSongs) }
        return detail
    }

    suspend fun getPlaylistTracks(browseId: String, source: MusicSource): List<Track> {
        val first = if (source == fallbackCatalog.source) fallbackCatalog else primaryCatalog
        val second = if (first === primaryCatalog) fallbackCatalog else primaryCatalog
        val tracks = runCatching { first.getPlaylistTracks(browseId) }.getOrDefault(emptyList())
            .ifEmpty { runCatching { second.getPlaylistTracks(browseId) }.getOrDefault(emptyList()) }
        registerTracks(tracks)
        return tracks
    }

    suspend fun getEndlessRadio(seedTrack: Track, limit: Int = 18): List<Track> {
        registerTrack(seedTrack)
        val primaryTracks = runCatching { primaryCatalog.getRadioQueue(seedTrack, limit) }.getOrDefault(emptyList())
        if (primaryTracks.isNotEmpty()) {
            registerTracks(primaryTracks)
            return primaryTracks
        }
        val fallbackTracks = runCatching { fallbackCatalog.getRadioQueue(seedTrack, limit) }.getOrDefault(emptyList())
        registerTracks(fallbackTracks)
        return fallbackTracks
    }

    suspend fun getArtistRadio(artist: ArtistDetail): List<Track> {
        if (artist.radioPlaylistId.isNotBlank()) {
            val ytRadio = runCatching { primaryCatalog.getArtistRadio(artist.radioPlaylistId, 20) }
                .getOrDefault(emptyList())
            if (ytRadio.isNotEmpty()) {
                registerTracks(ytRadio)
                return ytRadio
            }
        }
        val seed = artist.topSongs.firstOrNull()
        return if (seed != null) {
            (artist.topSongs + getEndlessRadio(seed, 14)).distinctBy { it.id }
        } else {
            searchUnified(artist.name, limit = 16).songs
        }
    }

    /**
     * Unified Just-In-Time Stream Resolver with automatic failover & Echo 320kbps Extension priority:
     * 1. Offline Download file -> 2. Device Local URI ->
     * 3. If Prefer Extension Audio (320kbps) is ON or track is JioSaavn: try 320kbps Extension first ->
     * 4. Primary Stream Provider (YouTube Music InnerTube ANDROID_VR 1.65.10 / IOS) ->
     * 5. Secondary Fallback Stream Provider (JioSaavn 320kbps cross-match).
     */
    suspend fun resolvePlaybackStream(track: Track, quality: AudioQuality): StreamResolution? {
        registerTrack(track)

        // 1. Check downloaded file in Room or Track
        val downloadedPath = track.downloadedFilePath.ifBlank {
            dao.getDownloadById(track.id)?.filePath.orEmpty()
        }
        if (downloadedPath.isNotBlank() && File(downloadedPath).exists()) {
            return StreamResolution(
                uri = if (downloadedPath.startsWith("/")) "file://$downloadedPath" else downloadedPath,
                resolvedBy = MusicSource.LOCAL,
                bitrateKbps = quality.kbps,
                mimeType = "audio/mp4",
                usedFallback = false
            )
        }

        // 2. Device Local track
        if (track.sourceType == SourceType.LOCAL && track.localUri.isNotBlank()) {
            return StreamResolution(
                uri = track.localUri,
                resolvedBy = MusicSource.LOCAL,
                bitrateKbps = 320,
                mimeType = "audio/*",
                usedFallback = false
            )
        }

        // 3. Respect the user's selected audio quality tier (96k / 160k / 320k) while verifying reachability
        val requestedQuality = quality
        val forceExtensionFirst = preferExtensionAudio || playbackEngine == EchoPlaybackEngine.EXTENSION_FIRST

        resolveFromHealthyProviders(track, requestedQuality, forceExtensionFirst)?.let { return it }

        // If every provider had been previously excluded for this track (e.g., transient network drop),
        // reset exclusions once and attempt a clean resolution pass.
        if (failedProvidersByTrack[track.id]?.isNotEmpty() == true) {
            clearTrackFailures(track.id)
            resolveFromHealthyProviders(track, requestedQuality, forceExtensionFirst)?.let { return it }
        }
        return null
    }

    private suspend fun resolveFromHealthyProviders(
        track: Track,
        requestedQuality: AudioQuality,
        forceExtensionFirst: Boolean
    ): StreamResolution? {
        if (forceExtensionFirst) {
            if (!isProviderFailed(track.id, PROVIDER_ECHO_ADDON) && extensionManager?.hasActiveEchoAddons() == true) {
                val addonRes = runCatching { extensionManager?.resolveStream(track, requestedQuality) }.getOrNull()
                verifyCandidate(track.id, PROVIDER_ECHO_ADDON, addonRes)?.let { return it }
            }
            resolveVerifiedYouTube(track, requestedQuality)?.let { return it.copy(usedFallback = false) }
            if (!isProviderFailed(track.id, PROVIDER_JIOSAAVN)) {
                val direct = runCatching { fallbackStream.resolveStream(track, requestedQuality) }.getOrNull()
                verifyCandidate(track.id, PROVIDER_JIOSAAVN, direct)?.let { return it }
            }
        } else {
            // 4. Primary YouTube Music InnerTubeX stream resolution (with 1 automatic profile-switch retry on 403/567)
            resolveVerifiedYouTube(track, requestedQuality)?.let { return it }

            // 5. Try configured Echo Music Addon (strict TrackMatcher verified)
            if (!isProviderFailed(track.id, PROVIDER_ECHO_ADDON) &&
                (track.sourceProvider == MusicSource.ECHO_ADDON || extensionManager?.hasActiveEchoAddons() == true)
            ) {
                val addonRes = runCatching { extensionManager?.resolveStream(track, requestedQuality) }.getOrNull()
                verifyCandidate(track.id, PROVIDER_ECHO_ADDON, addonRes)?.let {
                    return it.copy(usedFallback = track.sourceProvider != MusicSource.ECHO_ADDON)
                }
            }

            // 6. Strict TrackMatcher-verified JioSaavn 320kbps failover (returns null rather than wrong song!)
            if (!isProviderFailed(track.id, PROVIDER_JIOSAAVN)) {
                Log.w(TAG, "YouTube Music stream unavailable for '${track.title}' — trying highest-quality fallback providers")
                val fallbackRes = runCatching { fallbackStream.resolveStream(track, requestedQuality) }.getOrNull()
                verifyCandidate(track.id, PROVIDER_JIOSAAVN, fallbackRes)?.let {
                    return it.copy(usedFallback = track.sourceProvider != MusicSource.JIOSAAVN)
                }
            }
        }

        // 7. Global zero-auth SoundCloud full-track failover (strictly TrackMatcher-verified, skips 30s previews)
        if (!isProviderFailed(track.id, PROVIDER_SOUNDCLOUD)) {
            val soundCloudRes = runCatching { soundCloudFallback?.resolveStream(track, requestedQuality) }.getOrNull()
            verifyCandidate(track.id, PROVIDER_SOUNDCLOUD, soundCloudRes)?.let { return it }
        }

        // 8. Global zero-auth Bilibili full-track DASH M4A audio failover
        if (!isProviderFailed(track.id, PROVIDER_BILIBILI)) {
            val bilibiliRes = runCatching { bilibiliFallback?.resolveStream(track, requestedQuality) }.getOrNull()
            verifyCandidate(track.id, PROVIDER_BILIBILI, bilibiliRes)?.let { return it }
        }

        // 9. Decentralized zero-auth Audius full-track failover (strictly TrackMatcher-verified)
        if (!isProviderFailed(track.id, PROVIDER_AUDIUS)) {
            val audiusRes = runCatching { audiusFallback?.resolveStream(track, requestedQuality) }.getOrNull()
            verifyCandidate(track.id, PROVIDER_AUDIUS, audiusRes)?.let { return it }
        }

        // 10. Last resort: any pre-attached HTTP URL on the track itself
        if (!isProviderFailed(track.id, PROVIDER_LAST_RESORT)) {
            val lastResort = track.bestPlaybackUri(requestedQuality)
            if (lastResort.startsWith("http")) {
                val res = StreamResolution(
                    uri = lastResort,
                    resolvedBy = track.sourceProvider,
                    bitrateKbps = requestedQuality.kbps,
                    usedFallback = true
                )
                verifyCandidate(track.id, PROVIDER_LAST_RESORT, res)?.let { return it }
            }
        }
        return null
    }

    private suspend fun resolveVerifiedYouTube(
        track: Track,
        requestedQuality: AudioQuality
    ): StreamResolution? {
        if (isProviderFailed(track.id, PROVIDER_YOUTUBE_MUSIC)) return null
        repeat(2) { attempt ->
            val primaryRes = runCatching { primaryStream.resolveStream(track, requestedQuality) }.getOrNull()
                ?: return null
            val verified = verifyCandidate(track.id, PROVIDER_YOUTUBE_MUSIC, primaryRes)
            if (verified != null) return verified
            // Candidate failed pre-flight probe (e.g. HTTP 403/567); invalidate & retry with next InnerTubeX profile
            invalidateStream(track.id)
            if (attempt == 1) {
                failedProvidersByTrack.getOrPut(track.id) { ConcurrentHashMap() }[PROVIDER_YOUTUBE_MUSIC] =
                    System.currentTimeMillis() + FAILED_PROVIDER_TTL_MS
            }
        }
        return null
    }

    /**
     * Fast pre-flight HTTP verification before handing a URL to ExoPlayer.
     * Rejects dead / HTTP 403 / 410 / 451 / 567 / 5xx URLs (including HLS manifests & their first
     * segment) so ExoPlayer receives a verified working stream on the first try.
     */
    private suspend fun verifyCandidate(
        trackId: String,
        providerSlot: String,
        candidate: StreamResolution?
    ): StreamResolution? {
        if (candidate == null) return null
        val uri = candidate.uri
        if (!uri.startsWith("http", ignoreCase = true)) return candidate
        if (isUrlFailed(trackId, uri)) return null

        val client = probeClient ?: return candidate
        val status = probeStreamReachable(client, candidate)
        if (status in 200..299 || status == 416) {
            return candidate
        }
        Log.w(TAG, "Pre-flight probe rejected $providerSlot for $trackId (HTTP $status)")
        failedUrlsByTrack.getOrPut(trackId) { ConcurrentHashMap() }[uri] =
            System.currentTimeMillis() + FAILED_PROVIDER_TTL_MS
        if (providerSlot == PROVIDER_YOUTUBE_MUSIC) {
            InnerTubeXResolver.onRefused(uri, trackId)
        } else {
            failedProvidersByTrack.getOrPut(trackId) { ConcurrentHashMap() }[providerSlot] =
                System.currentTimeMillis() + FAILED_PROVIDER_TTL_MS
        }
        return null
    }

    private suspend fun probeStreamReachable(
        client: OkHttpClient,
        res: StreamResolution
    ): Int = withContext(Dispatchers.IO) {
        val url = res.uri
        val isHls = res.mimeType.contains("mpegurl", ignoreCase = true) ||
            url.contains(".m3u8", ignoreCase = true) ||
            url.contains("manifest.googlevideo.com", ignoreCase = true) ||
            url.contains("media-streaming.soundcloud.cloud", ignoreCase = true)

        val effectiveHeaders = buildMap {
            if (res.headers.isNotEmpty()) {
                putAll(res.headers)
            } else if (url.contains("googlevideo.com", ignoreCase = true)) {
                putAll(InnerTubeXResolver.headersFor(url) ?: PlayerClient.forStreamUrl(url).mediaHeaders())
            }
        }

        runCatching {
            val reqBuilder = Request.Builder().url(url).get()
            effectiveHeaders.forEach { (k, v) ->
                if (!k.equals("Range", ignoreCase = true)) reqBuilder.header(k, v)
            }
            if (!isHls) {
                reqBuilder.header("Range", "bytes=0-0")
            }

            client.newCall(reqBuilder.build()).execute().use { resp ->
                val code = resp.code
                if (isRefusalStatus(code)) return@use code
                if (!isHls) return@use code

                // For HLS manifests, peek the first nested playlist/segment URI so segment-level 403/567 is caught early
                val nestedUrl = runCatching {
                    val reader = resp.body?.charStream()?.buffered() ?: return@runCatching null
                    var found: String? = null
                    repeat(80) {
                        val line = reader.readLine()?.trim() ?: return@repeat
                        if (line.isNotEmpty() && !line.startsWith("#")) {
                            found = if (line.startsWith("http", ignoreCase = true)) {
                                line
                            } else {
                                resp.request.url.resolve(line)?.toString()
                            }
                            if (found != null) return@repeat
                        }
                    }
                    found
                }.getOrNull()

                if (nestedUrl.isNullOrBlank()) return@use code

                val nestedIsPlaylist = nestedUrl.contains(".m3u8", ignoreCase = true) ||
                    nestedUrl.contains("manifest.googlevideo.com", ignoreCase = true)
                val nestedReq = Request.Builder().url(nestedUrl).get().apply {
                    effectiveHeaders.forEach { (k, v) ->
                        if (!k.equals("Range", ignoreCase = true)) header(k, v)
                    }
                    if (!nestedIsPlaylist) {
                        header("Range", "bytes=0-0")
                    }
                }.build()

                client.newCall(nestedReq).execute().use { nestedResp ->
                    nestedResp.code
                }
            }
        }.getOrElse {
            Log.w(TAG, "Stream probe IO failure for ${url.take(96)}: ${it.message}")
            -1
        }
    }

    private fun isRefusalStatus(code: Int): Boolean {
        return code == 401 || code == 403 || code == 404 || code == 410 || code == 451 || code >= 500
    }

    companion object {
        private const val FAILED_PROVIDER_TTL_MS = 5L * 60L * 1000L
        private const val PROVIDER_YOUTUBE_MUSIC = "YOUTUBE_MUSIC"
        private const val PROVIDER_ECHO_ADDON = "ECHO_ADDON"
        private const val PROVIDER_JIOSAAVN = "JIOSAAVN"
        private const val PROVIDER_SOUNDCLOUD = "SOUNDCLOUD"
        private const val PROVIDER_BILIBILI = "BILIBILI"
        private const val PROVIDER_AUDIUS = "AUDIUS"
        private const val PROVIDER_LAST_RESORT = "LAST_RESORT"
    }
}
