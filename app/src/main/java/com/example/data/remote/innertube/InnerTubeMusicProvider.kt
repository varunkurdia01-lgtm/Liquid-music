package com.example.data.remote.innertube

import android.util.Log
import com.example.data.remote.TrackMatcher
import com.example.domain.model.AlbumDetail
import com.example.domain.model.ArtistDetail
import com.example.domain.model.AudioQuality
import com.example.domain.model.BrowseLayoutType
import com.example.domain.model.BrowseSection
import com.example.domain.model.LyricsState
import com.example.domain.model.MoodItem
import com.example.domain.model.MusicSource
import com.example.domain.model.StreamResolution
import com.example.domain.model.Track
import com.example.domain.model.UnifiedSearchResults
import com.example.domain.provider.LyricsSourceProvider
import com.example.domain.provider.MusicCatalogProvider
import com.example.domain.provider.StreamResolverProvider
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "InnerTubeMusicProvider"

/**
 * Primary YouTube Music InnerTube provider matching Echo Music's YouTube.kt + YTPlayerUtils.kt + InnerTubeXResolver.kt.
 */
class InnerTubeMusicProvider(
    val client: InnerTubeClient
) : MusicCatalogProvider, StreamResolverProvider, LyricsSourceProvider {

    override val source: MusicSource = MusicSource.YOUTUBE_MUSIC
    override val providerName: String = "YouTube Music"

    private data class CachedStream(val resolution: StreamResolution, val expiresAtMs: Long)
    private val streamCache = ConcurrentHashMap<String, CachedStream>()

    fun invalidateStream(videoId: String) {
        if (videoId.isBlank()) return
        streamCache.keys.filter { it.startsWith("${videoId}_") }.forEach { streamCache.remove(it) }
    }

    override suspend fun getHomeFeed(): List<BrowseSection> = coroutineScope {
        val homePage1Def = async { client.browse("FEmusic_home") }
        val newReleasesDef = async { client.browse("FEmusic_new_releases_albums") }

        val sections = mutableListOf<BrowseSection>()
        val homeJson = homePage1Def.await()
        if (homeJson != null) {
            sections.addAll(InnerTubeParsers.parseBrowseSections(homeJson, defaultSectionTitle = "Quick Picks"))
            // Paginate up to 3 continuations like Echo Music HomeViewModel
            var nextContinuation = InnerTubeParsers.extractContinuation(homeJson)
            var page = 0
            while (!nextContinuation.isNullOrBlank() && page < 3) {
                val contJson = client.browse(continuation = nextContinuation) ?: break
                sections.addAll(InnerTubeParsers.parseBrowseSections(contJson, defaultSectionTitle = "Recommended"))
                val next = InnerTubeParsers.extractContinuation(contJson)
                if (next == nextContinuation) break
                nextContinuation = next
                page++
            }
        }

        val newReleasesJson = newReleasesDef.await()
        if (newReleasesJson != null) {
            val newReleaseSections = InnerTubeParsers.parseBrowseSections(
                newReleasesJson,
                defaultSectionTitle = "New Release Albums"
            ).map { sec ->
                if (sec.albums.isNotEmpty()) {
                    sec.copy(
                        id = "ytm_new_release_albums",
                        title = "New Release Albums",
                        subtitle = "Fresh albums & singles on YouTube Music",
                        layoutType = BrowseLayoutType.CAROUSEL_ALBUMS,
                        albums = sec.albums.take(24)
                    )
                } else sec
            }
            sections.addAll(newReleaseSections)
        }

        sections.distinctBy { it.title }
    }

    override suspend fun getExploreChartsAndMoods(): Pair<List<BrowseSection>, List<MoodItem>> = coroutineScope {
        val chartsDef = async { client.browse("FEmusic_charts", params = CHARTS_PARAMS) }
        val exploreDef = async { client.browse("FEmusic_explore") }
        val moodsDef = async { client.browse("FEmusic_moods_and_genres") }

        val chartsJson = chartsDef.await()
        val exploreJson = exploreDef.await()
        val moodsJson = moodsDef.await()

        val chartSections = chartsJson?.let {
            InnerTubeParsers.parseBrowseSections(it, defaultSectionTitle = "Top Charts")
        }.orEmpty()
        val exploreSections = exploreJson?.let {
            InnerTubeParsers.parseBrowseSections(it, defaultSectionTitle = "Explore")
        }.orEmpty()

        val moods = buildList {
            if (moodsJson != null) addAll(InnerTubeParsers.parseMoods(moodsJson))
            if (exploreJson != null) addAll(InnerTubeParsers.parseMoods(exploreJson))
        }.distinctBy { it.title }.take(24)

        val combined = (chartSections + exploreSections).distinctBy { it.title }
        combined to moods
    }

    override suspend fun search(query: String, limit: Int): UnifiedSearchResults = coroutineScope {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@coroutineScope UnifiedSearchResults(activeSource = source)
        val generalDef = async { client.search(trimmed) }
        val songsDef = async { client.search(trimmed, params = PARAM_SEARCH_SONGS) }
        val videosDef = async { client.search(trimmed, params = PARAM_SEARCH_VIDEOS) }

        val generalJson = generalDef.await()
        val songsJson = songsDef.await()
        val videosJson = videosDef.await()

        val generalParsed = generalJson?.let { InnerTubeParsers.parseSearchResults(it) }
            ?: UnifiedSearchResults(activeSource = source)
        val songsParsed = songsJson?.let { InnerTubeParsers.parseSearchResults(it) }
            ?: UnifiedSearchResults(activeSource = source)
        val videosParsed = videosJson?.let { InnerTubeParsers.parseSearchResults(it) }
            ?: UnifiedSearchResults(activeSource = source)

        val mergedSongs = (songsParsed.songs + generalParsed.songs + videosParsed.songs.take(8))
            .distinctBy { it.id }
            .take(limit)
        generalParsed.copy(
            songs = mergedSongs,
            albums = generalParsed.albums.take(14),
            artists = generalParsed.artists.take(12),
            playlists = generalParsed.playlists.take(12),
            activeSource = source,
            usedFallback = false
        )
    }

    override suspend fun getSearchSuggestions(query: String): List<String> {
        if (query.trim().length < 2) return emptyList()
        val json = client.searchSuggestions(query.trim()) ?: return emptyList()
        return InnerTubeParsers.parseSearchSuggestions(json)
    }

    override suspend fun getAlbumDetail(browseId: String): AlbumDetail? {
        if (browseId.isBlank()) return null
        val json = client.browse(browseId) ?: return null
        return InnerTubeParsers.parseAlbumDetail(json, browseId)
    }

    override suspend fun getArtistDetail(browseId: String): ArtistDetail? {
        if (browseId.isBlank()) return null
        val json = client.browse(browseId) ?: return null
        return InnerTubeParsers.parseArtistDetail(json, browseId)
    }

    override suspend fun getPlaylistTracks(browseId: String): List<Track> {
        if (browseId.isBlank()) return emptyList()
        val cleanId = if (browseId.startsWith("VL") || browseId.startsWith("MPRE") || browseId.startsWith("OLAK") || browseId.startsWith("FE")) {
            browseId
        } else {
            "VL$browseId"
        }
        val json = client.browse(cleanId) ?: client.browse(browseId) ?: return emptyList()
        return InnerTubeParsers.parseBrowseSections(json).flatMap { it.tracks }.distinctBy { it.id }
    }

    override suspend fun getRadioQueue(seedTrack: Track, limit: Int): List<Track> {
        val videoId = resolveVideoId(seedTrack) ?: return emptyList()
        val radioPlaylistId = "RDAMVM$videoId"
        val json = client.next(videoId = videoId, playlistId = radioPlaylistId)
            ?: client.next(videoId = videoId)
            ?: return emptyList()
        return InnerTubeParsers.parseRadioQueue(json)
            .filterNot { it.id == seedTrack.id || it.providerTrackId == videoId }
            .take(limit)
    }

    override suspend fun getArtistRadio(radioPlaylistId: String, limit: Int): List<Track> {
        if (radioPlaylistId.isBlank()) return emptyList()
        val json = client.next(playlistId = radioPlaylistId) ?: return emptyList()
        return InnerTubeParsers.parseRadioQueue(json).take(limit)
    }

    /**
     * Echo Music YTPlayerUtils + InnerTubeXResolver stream resolution pipeline:
     * 1. InnerTubeXResolver (MetrolistGroup innertubex-android v0.7.0 + QuickJS EJS cipher + zemer-cipher)
     * 2. Multi-client InnerTubeClient.player() fallback (VISIONOS -> ANDROID_VR 1.65.10 -> TVHTML5 -> IOS)
     * 3. Emergency Piped API fallback (pipedapi.kavin.rocks)
     */
    override suspend fun resolveStream(track: Track, quality: AudioQuality): StreamResolution? {
        val videoId = resolveVideoId(track) ?: return null
        val cacheKey = "${videoId}_${quality.name}_${client.forceOpusAudio}"
        val now = System.currentTimeMillis()
        streamCache[cacheKey]?.let { cached ->
            if (cached.expiresAtMs > now) return cached.resolution
        }

        // Ensure fresh visitorData before syncing InnerTubeX session state
        if (client.visitorData.isNullOrBlank()) {
            runCatching { client.ensureVisitorData() }
        }
        InnerTubeXResolver.syncSession(
            cookie = client.cookie,
            visitorData = client.visitorData,
            country = client.contentCountry,
            language = client.contentLanguage
        )

        // If YouTube recently returned anonymous IP bot-check ("Sign in to confirm you're not a bot"),
        // fast-fail immediately so UnifiedMusicRepository resolves via JioSaavn/SoundCloud/Audius in <1.5s!
        if (client.isAnonymousBotBlocked()) {
            return null
        }

        // 1. Try primary videoId through InnerTubeX -> direct player -> Piped/Invidious
        extractStreamForVideoId(videoId, quality)?.let { res ->
            streamCache[cacheKey] = CachedStream(res, now + STREAM_TTL_MS)
            return res
        }

        // Don't waste another round-trip searching alternate videoIds if the IP itself is bot-blocked
        if (client.isAnonymousBotBlocked()) {
            return null
        }

        // 2. If primary videoId is age-restricted/region-blocked, try an alternate matching YouTube upload of the SAME song
        val altVideoId = findAlternateVideoId(track, excludeVideoId = videoId)
        if (!altVideoId.isNullOrBlank()) {
            extractStreamForVideoId(altVideoId, quality)?.let { res ->
                val tagged = res.copy(usedFallback = true)
                streamCache[cacheKey] = CachedStream(tagged, now + STREAM_TTL_MS)
                return tagged
            }
        }

        return null
    }

    private suspend fun extractStreamForVideoId(videoId: String, quality: AudioQuality): StreamResolution? {
        // 1. Primary Echo Music InnerTubeXResolver engine (capped so restricted IPs fail over rapidly)
        val extractTimeoutMs = if (client.cookie.isNullOrBlank()) 5_500L else 9_000L
        val extracted = runCatching {
            withTimeoutOrNull(extractTimeoutMs) {
                InnerTubeXResolver.extract(
                    videoId = videoId,
                    maxKbps = quality.kbps,
                    requireM4a = false
                )
            }
        }.onFailure {
            Log.w(TAG, "InnerTubeX extraction warning for $videoId: ${it.message}")
        }.getOrNull()

        if (extracted != null && extracted.url.startsWith("http")) {
            return StreamResolution(
                uri = extracted.url,
                resolvedBy = MusicSource.YOUTUBE_MUSIC,
                bitrateKbps = extracted.kbps,
                mimeType = extracted.mimeType,
                usedFallback = false,
                headers = extracted.headers,
                clientName = extracted.clientName,
                profileId = extracted.profileId,
                contentLength = extracted.contentLength,
                loudnessDb = extracted.loudnessDb,
                perceptualLoudnessDb = extracted.perceptualLoudnessDb
            )
        }

        // 2. Direct multi-client InnerTubeClient.player() fallback (adaptive -> muxed -> HLS)
        val playerJson = client.player(videoId)
        if (playerJson != null) {
            val parsed = InnerTubeParsers.parseStreamResolution(
                playerJson = playerJson,
                quality = quality,
                forceOpus = client.forceOpusAudio
            )
            if (parsed != null) return parsed
        }
        if (client.isAnonymousBotBlocked()) {
            return null
        }

        // 3. Emergency Piped + Invidious multi-mirror fallback
        val piped = client.fetchPipedAudioStream(videoId)
        if (piped != null) {
            return StreamResolution(
                uri = piped.first,
                resolvedBy = MusicSource.YOUTUBE_MUSIC,
                bitrateKbps = piped.second,
                mimeType = "audio/mp4",
                usedFallback = true,
                headers = PlayerClient.forStreamUrl(piped.first).mediaHeaders(),
                clientName = "Piped/Invidious"
            )
        }
        return null
    }

    private suspend fun findAlternateVideoId(track: Track, excludeVideoId: String): String? {
        val cleanTitle = TrackMatcher.cleanTitleForSearch(track.title)
        val primaryArtist = track.artist.substringBefore(",").trim()
            .takeUnless { it.equals("Unknown Artist", ignoreCase = true) }.orEmpty()
        val query = "$cleanTitle $primaryArtist".trim()
        if (query.isBlank()) return null
        val candidates = runCatching { search(query, limit = 6).songs }.getOrDefault(emptyList())
            .filter { it.providerTrackId.length >= 8 && it.providerTrackId != excludeVideoId }
        val matched = TrackMatcher.selectBestMatch(
            candidates = candidates,
            title = track.title,
            artist = track.artist,
            durationMs = track.durationMs.takeIf { it > 0L },
            titleOf = { it.title },
            artistOf = { it.artist },
            durationMsOf = { it.durationMs.takeIf { d -> d > 0L } }
        )
        return matched?.providerTrackId
    }

    override suspend fun fetchLyrics(track: Track): LyricsState? {
        val videoId = resolveVideoId(track) ?: return null
        val nextJson = client.next(videoId = videoId) ?: return null
        val lyricsBrowseId = InnerTubeParsers.extractLyricsBrowseId(nextJson) ?: return null
        val lyricsJson = client.browse(lyricsBrowseId) ?: return null
        val plain = InnerTubeParsers.extractPlainLyricsFromBrowse(lyricsJson) ?: return null
        return LyricsState(
            isLoading = false,
            syncedLines = emptyList(),
            plainLyrics = plain,
            isInstrumental = false,
            sourceName = "YouTube Music"
        )
    }

    private suspend fun resolveVideoId(track: Track): String? {
        if (track.sourceProvider == MusicSource.YOUTUBE_MUSIC && track.providerTrackId.length >= 8) {
            return track.providerTrackId
        }
        val cleanTitle = TrackMatcher.cleanTitleForSearch(track.title)
        val primaryArtist = track.artist.substringBefore(",").trim()
        val query = "$cleanTitle $primaryArtist".trim()
        if (query.isBlank()) return null

        val candidates = search(query, limit = 8).songs
        val matched = TrackMatcher.selectBestMatch(
            candidates = candidates,
            title = track.title,
            artist = track.artist,
            durationMs = track.durationMs.takeIf { it > 0L },
            titleOf = { it.title },
            artistOf = { it.artist },
            durationMsOf = { it.durationMs.takeIf { d -> d > 0L } }
        )
        return matched?.providerTrackId?.takeIf { it.length >= 8 }
    }

    companion object {
        private const val PARAM_SEARCH_SONGS = "EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D"
        private const val PARAM_SEARCH_VIDEOS = "EgWKAQIQAWoKEAkQChAFEAMQBA%3D%3D"
        private const val CHARTS_PARAMS = "ggMGCgQIgAQ%3D"
        private const val STREAM_TTL_MS = 60L * 60L * 1000L
    }
}
