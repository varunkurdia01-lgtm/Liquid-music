package com.example.data.remote

import android.text.Html
import android.util.Log
import com.example.domain.model.AlbumDetail
import com.example.domain.model.AlbumSummary
import com.example.domain.model.ArtistDetail
import com.example.domain.model.ArtistSummary
import com.example.domain.model.AudioQuality
import com.example.domain.model.BrowseLayoutType
import com.example.domain.model.BrowseSection
import com.example.domain.model.MoodItem
import com.example.domain.model.MusicSource
import com.example.domain.model.PlaylistSummary
import com.example.domain.model.SourceType
import com.example.domain.model.StreamResolution
import com.example.domain.model.Track
import com.example.domain.model.UnifiedSearchResults
import com.example.domain.provider.MusicCatalogProvider
import com.example.domain.provider.StreamResolverProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "SaavnMusicProvider"

/**
 * Secondary fallback music provider backed by JioSaavn APIs, strictly conforming to the
 * unified MusicCatalogProvider and StreamResolverProvider interfaces.
 */
class SaavnMusicProvider(
    private val okHttpClient: OkHttpClient,
    private val saavnRepo: SaavnMusicRepository
) : MusicCatalogProvider, StreamResolverProvider {

    override val source: MusicSource = MusicSource.JIOSAAVN

    private val baseUrls = listOf(
        "https://saavn.sumit.co/api"
    )

    override suspend fun getHomeFeed(): List<BrowseSection> = coroutineScope {
        val quickDef = async { saavnRepo.searchSongs("Global Top Hits 2025 Pop", 12) }
        val hipHopDef = async { saavnRepo.searchSongs("Travis Scott Weeknd Drake Hits", 10) }
        val synthDef = async { saavnRepo.searchSongs("Electronic Synthwave Chill", 10) }
        val albumsDef = async { searchAlbums("Top Global Albums", 8) }

        val quick = quickDef.await().map { tagAsSaavn(it) }
        val hipHop = hipHopDef.await().map { tagAsSaavn(it) }
        val synth = synthDef.await().map { tagAsSaavn(it) }
        val albums = albumsDef.await()

        buildList {
            if (quick.isNotEmpty()) {
                add(
                    BrowseSection(
                        id = "saavn_quick_picks",
                        title = "Quick Picks",
                        subtitle = "Instant mix",
                        layoutType = BrowseLayoutType.QUICK_PICKS_GRID,
                        tracks = quick,
                        sourceProvider = source
                    )
                )
            }
            if (albums.isNotEmpty()) {
                add(
                    BrowseSection(
                        id = "saavn_albums",
                        title = "Featured Albums",
                        subtitle = "Curated records",
                        layoutType = BrowseLayoutType.CAROUSEL_ALBUMS,
                        albums = albums,
                        sourceProvider = source
                    )
                )
            }
            if (hipHop.isNotEmpty()) {
                add(
                    BrowseSection(
                        id = "saavn_hiphop",
                        title = "Trending Charts",
                        subtitle = "High energy rotations",
                        layoutType = BrowseLayoutType.CHART_LIST,
                        tracks = hipHop,
                        sourceProvider = source
                    )
                )
            }
            if (synth.isNotEmpty()) {
                add(
                    BrowseSection(
                        id = "saavn_synth",
                        title = "Midnight & Electronic",
                        subtitle = "Crystal atmosphere",
                        layoutType = BrowseLayoutType.CAROUSEL_TRACKS,
                        tracks = synth,
                        sourceProvider = source
                    )
                )
            }
        }
    }

    override suspend fun getExploreChartsAndMoods(): Pair<List<BrowseSection>, List<MoodItem>> {
        val chartTracks = saavnRepo.searchSongs("Top 50 Global Viral Hits", 12).map { tagAsSaavn(it) }
        val chartSection = if (chartTracks.isNotEmpty()) {
            listOf(
                BrowseSection(
                    id = "saavn_global_chart",
                    title = "Global Viral Chart",
                    subtitle = "Most replayed right now",
                    layoutType = BrowseLayoutType.CHART_LIST,
                    tracks = chartTracks,
                    sourceProvider = source
                )
            )
        } else emptyList()

        val moods = listOf(
            MoodItem("Energize", "workout phonk electronic energy", accentHex = 0xFF38D9F5),
            MoodItem("Chill", "chill lofi acoustic late night", accentHex = 0xFF7BE8FF),
            MoodItem("Focus", "deep focus ambient instrumental", accentHex = 0xFF818CF8),
            MoodItem("Night Drive", "synthwave night drive weeknd", accentHex = 0xFFF472B6),
            MoodItem("Hip-Hop", "hip hop rap hits", accentHex = 0xFFFBBF24),
            MoodItem("Pop", "global pop chart hits", accentHex = 0xFF34D399),
            MoodItem("Romance", "romantic acoustic ballads", accentHex = 0xFFFB7185),
            MoodItem("Party", "dance club edm hits", accentHex = 0xFFC084FC)
        )
        return chartSection to moods
    }

    override suspend fun search(query: String, limit: Int): UnifiedSearchResults = coroutineScope {
        val songsDef = async { saavnRepo.searchSongs(query, limit).map { tagAsSaavn(it) } }
        val albumsDef = async { searchAlbums(query, 8) }
        val artistsDef = async { searchArtists(query, 8) }
        val playlistsDef = async { searchPlaylists(query, 6) }

        UnifiedSearchResults(
            songs = songsDef.await(),
            albums = albumsDef.await(),
            artists = artistsDef.await(),
            playlists = playlistsDef.await(),
            activeSource = source,
            usedFallback = true
        )
    }

    override suspend fun getSearchSuggestions(query: String): List<String> {
        if (query.trim().length < 2) return emptyList()
        val songs = saavnRepo.searchSongs(query, 6)
        return songs.map { it.title }.distinct().take(6)
    }

    override suspend fun getAlbumDetail(browseId: String): AlbumDetail? = withContext(Dispatchers.IO) {
        val cleanId = browseId.removePrefix("saavn_alb_")
        for (base in baseUrls) {
            try {
                val url = "$base/albums".toHttpUrl().newBuilder()
                    .addQueryParameter("id", cleanId)
                    .build()
                val raw = executeGet(url.toString()) ?: continue
                val data = JSONObject(raw).optJSONObject("data") ?: continue
                val title = decodeHtml(data.optString("name"))
                val year = data.optString("year")
                val artwork = extractBestImage(data.optJSONArray("image"))
                val artist = extractPrimaryArtistNames(data)
                val songsArr = data.optJSONArray("songs") ?: JSONArray()
                val tracks = parseSaavnSongArray(songsArr, fallbackAlbum = title, fallbackArt = artwork)
                if (title.isNotBlank() || tracks.isNotEmpty()) {
                    return@withContext AlbumDetail(
                        id = "saavn_alb_$cleanId",
                        browseId = cleanId,
                        title = title.ifBlank { "Album" },
                        artist = artist.ifBlank { tracks.firstOrNull()?.artist ?: "Various Artists" },
                        year = year,
                        description = decodeHtml(data.optString("description")),
                        artworkUrl = artwork,
                        tracks = tracks,
                        sourceProvider = source
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Album detail failed on $base: ${e.message}")
            }
        }
        null
    }

    override suspend fun getArtistDetail(browseId: String): ArtistDetail? = withContext(Dispatchers.IO) {
        val cleanId = browseId.removePrefix("saavn_art_")
        for (base in baseUrls) {
            try {
                val url = "$base/artists/$cleanId".toHttpUrl().newBuilder()
                    .addQueryParameter("songCount", "12")
                    .addQueryParameter("albumCount", "8")
                    .build()
                val raw = executeGet(url.toString()) ?: continue
                val data = JSONObject(raw).optJSONObject("data") ?: continue
                val name = decodeHtml(data.optString("name"))
                val image = extractBestImage(data.optJSONArray("image"))
                val followers = data.optString("followerCount").takeIf { it.isNotBlank() && it != "null" }
                    ?.let { "$it listeners" }.orEmpty()
                val topSongs = parseSaavnSongArray(data.optJSONArray("topSongs") ?: JSONArray())
                val albums = parseSaavnAlbumArray(data.optJSONArray("topAlbums") ?: JSONArray())
                val singles = parseSaavnAlbumArray(data.optJSONArray("singles") ?: JSONArray())
                    .map { it.copy(isSingle = true) }

                if (name.isNotBlank() || topSongs.isNotEmpty()) {
                    return@withContext ArtistDetail(
                        id = "saavn_art_$cleanId",
                        browseId = cleanId,
                        name = name.ifBlank { "Artist" },
                        subscribersText = followers,
                        thumbnailUrl = image,
                        topSongs = topSongs,
                        albums = albums,
                        singles = singles,
                        sourceProvider = source
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Artist detail failed on $base: ${e.message}")
            }
        }
        null
    }

    override suspend fun getPlaylistTracks(browseId: String): List<Track> = withContext(Dispatchers.IO) {
        val cleanId = browseId.removePrefix("saavn_pl_")
        for (base in baseUrls) {
            try {
                val url = "$base/playlists".toHttpUrl().newBuilder()
                    .addQueryParameter("id", cleanId)
                    .addQueryParameter("limit", "30")
                    .build()
                val raw = executeGet(url.toString()) ?: continue
                val data = JSONObject(raw).optJSONObject("data") ?: continue
                val songs = parseSaavnSongArray(data.optJSONArray("songs") ?: JSONArray())
                if (songs.isNotEmpty()) return@withContext songs
            } catch (e: Exception) {
                Log.w(TAG, "Playlist tracks failed on $base: ${e.message}")
            }
        }
        emptyList()
    }

    override suspend fun getRadioQueue(seedTrack: Track, limit: Int): List<Track> {
        val query = "${seedTrack.artist.substringBefore(",")} ${seedTrack.title.split(" ").take(2).joinToString(" ")}".trim()
        return saavnRepo.searchSongs(query.ifBlank { seedTrack.title }, limit = limit + 3)
            .map { tagAsSaavn(it) }
            .filterNot { it.id == seedTrack.id || it.title.equals(seedTrack.title, ignoreCase = true) }
            .take(limit)
    }

    override suspend fun resolveStream(track: Track, quality: AudioQuality): StreamResolution? {
        // 1. Direct URL already attached on a JioSaavn track
        val direct = track.bestPlaybackUri(quality)
        if (track.sourceProvider == MusicSource.JIOSAAVN && direct.startsWith("http")) {
            return StreamResolution(
                uri = direct,
                resolvedBy = MusicSource.JIOSAAVN,
                bitrateKbps = quality.kbps,
                mimeType = "audio/mp4",
                usedFallback = false
            )
        }

        // 2. Strict cross-provider matching (Echo Music selectBestMatch rules):
        // Never blindly fall back to an unrelated #1 search result — only match when title tokens,
        // qualifiers (remix/live/acoustic), primary artist, and duration genuinely match!
        val identities = buildList {
            add(track.title to track.artist)
            TrackMatcher.uploadIdentity(track.title)?.let { if (it !in this) add(it) }
        }

        for ((wantTitle, wantArtist) in identities) {
            val cleanTitle = TrackMatcher.cleanTitleForSearch(wantTitle)
            val cleanedArtist = wantArtist
                .replace(Regex("""\s*-\s*Topic$""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""VEVO$""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""\bOfficial\b""", RegexOption.IGNORE_CASE), "")
                .trim()
            val primaryArtist = cleanedArtist.split(",", "&", "•", ";", "/").firstOrNull()?.trim().orEmpty()
                .takeUnless { it.equals("Unknown Artist", ignoreCase = true) }.orEmpty()

            val queriesToTry = buildList {
                val combined = "$cleanTitle $primaryArtist".trim()
                if (combined.isNotBlank()) add(combined)
                if (cleanTitle.length >= 3 && cleanTitle != combined) add(cleanTitle)
            }

            for (matchQuery in queriesToTry) {
                val candidates = saavnRepo.searchSongs(matchQuery, limit = 14)
                    .filter { it.bestPlaybackUri(quality).startsWith("http") }

                val best = TrackMatcher.selectBestMatch(
                    candidates = candidates,
                    title = wantTitle,
                    artist = cleanedArtist.ifBlank { wantArtist },
                    durationMs = track.durationMs.takeIf { it > 0L },
                    titleOf = { it.title },
                    artistOf = { it.artist },
                    durationMsOf = { it.durationMs.takeIf { d -> d > 0L } }
                )

                val uri = best?.bestPlaybackUri(quality)?.takeIf { it.startsWith("http") }
                if (uri != null) {
                    return StreamResolution(
                        uri = uri,
                        resolvedBy = MusicSource.JIOSAAVN,
                        bitrateKbps = quality.kbps,
                        mimeType = "audio/mp4",
                        usedFallback = track.sourceProvider != MusicSource.JIOSAAVN,
                        clientName = "JioSaavn320"
                    )
                }
            }
        }
        return null
    }

    private suspend fun searchAlbums(query: String, limit: Int): List<AlbumSummary> = withContext(Dispatchers.IO) {
        for (base in baseUrls) {
            try {
                val url = "$base/search/albums".toHttpUrl().newBuilder()
                    .addQueryParameter("query", query)
                    .addQueryParameter("limit", limit.toString())
                    .build()
                val raw = executeGet(url.toString()) ?: continue
                val results = JSONObject(raw).optJSONObject("data")?.optJSONArray("results") ?: continue
                val parsed = parseSaavnAlbumArray(results)
                if (parsed.isNotEmpty()) return@withContext parsed
            } catch (e: Exception) {
                Log.w(TAG, "Album search failed on $base: ${e.message}")
            }
        }
        emptyList()
    }

    private suspend fun searchArtists(query: String, limit: Int): List<ArtistSummary> = withContext(Dispatchers.IO) {
        for (base in baseUrls) {
            try {
                val url = "$base/search/artists".toHttpUrl().newBuilder()
                    .addQueryParameter("query", query)
                    .addQueryParameter("limit", limit.toString())
                    .build()
                val raw = executeGet(url.toString()) ?: continue
                val results = JSONObject(raw).optJSONObject("data")?.optJSONArray("results") ?: continue
                val artists = mutableListOf<ArtistSummary>()
                for (i in 0 until results.length()) {
                    val obj = results.optJSONObject(i) ?: continue
                    val id = obj.optString("id").ifBlank { continue }
                    val name = decodeHtml(obj.optString("name"))
                    if (name.isBlank()) continue
                    artists.add(
                        ArtistSummary(
                            id = "saavn_art_$id",
                            browseId = id,
                            name = name,
                            subtitle = obj.optString("role").ifBlank { "Artist" },
                            thumbnailUrl = extractBestImage(obj.optJSONArray("image")),
                            sourceProvider = source
                        )
                    )
                }
                if (artists.isNotEmpty()) return@withContext artists
            } catch (e: Exception) {
                Log.w(TAG, "Artist search failed on $base: ${e.message}")
            }
        }
        emptyList()
    }

    private suspend fun searchPlaylists(query: String, limit: Int): List<PlaylistSummary> = withContext(Dispatchers.IO) {
        for (base in baseUrls) {
            try {
                val url = "$base/search/playlists".toHttpUrl().newBuilder()
                    .addQueryParameter("query", query)
                    .addQueryParameter("limit", limit.toString())
                    .build()
                val raw = executeGet(url.toString()) ?: continue
                val results = JSONObject(raw).optJSONObject("data")?.optJSONArray("results") ?: continue
                val playlists = mutableListOf<PlaylistSummary>()
                for (i in 0 until results.length()) {
                    val obj = results.optJSONObject(i) ?: continue
                    val id = obj.optString("id").ifBlank { continue }
                    val title = decodeHtml(obj.optString("name"))
                    if (title.isBlank()) continue
                    playlists.add(
                        PlaylistSummary(
                            id = "saavn_pl_$id",
                            browseId = id,
                            title = title,
                            author = decodeHtml(obj.optString("language")).ifBlank { "Curated Playlist" },
                            trackCountText = obj.optString("songCount").takeIf { it.isNotBlank() }?.let { "$it songs" }.orEmpty(),
                            artworkUrl = extractBestImage(obj.optJSONArray("image")),
                            sourceProvider = source
                        )
                    )
                }
                if (playlists.isNotEmpty()) return@withContext playlists
            } catch (e: Exception) {
                Log.w(TAG, "Playlist search failed on $base: ${e.message}")
            }
        }
        emptyList()
    }

    private fun parseSaavnAlbumArray(array: JSONArray): List<AlbumSummary> {
        val albums = mutableListOf<AlbumSummary>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val id = obj.optString("id").ifBlank { continue }
            val title = decodeHtml(obj.optString("name"))
            if (title.isBlank()) continue
            albums.add(
                AlbumSummary(
                    id = "saavn_alb_$id",
                    browseId = id,
                    title = title,
                    artist = extractPrimaryArtistNames(obj).ifBlank { "Various Artists" },
                    year = obj.optString("year"),
                    artworkUrl = extractBestImage(obj.optJSONArray("image")),
                    sourceProvider = source
                )
            )
        }
        return albums
    }

    private fun parseSaavnSongArray(
        array: JSONArray,
        fallbackAlbum: String = "",
        fallbackArt: String = ""
    ): List<Track> {
        val out = mutableListOf<Track>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val id = obj.optString("id").ifBlank { continue }
            val title = decodeHtml(obj.optString("name"))
            if (title.isBlank()) continue
            val durationSec = obj.optLong("duration", 0L)
            val durationMs = if (durationSec in 1..36000) durationSec * 1000L else durationSec
            val urls = extractDownloadUrls(obj.optJSONArray("downloadUrl"))
            val url96 = urls["96kbps"].orEmpty()
            val url160 = urls["160kbps"].orEmpty()
            val url320 = urls["320kbps"].orEmpty()
            if (url96.isBlank() && url160.isBlank() && url320.isBlank()) continue

            out.add(
                Track(
                    id = "saavn_$id",
                    title = title,
                    artist = extractPrimaryArtistNames(obj).ifBlank { "Unknown Artist" },
                    album = decodeHtml(obj.optJSONObject("album")?.optString("name").orEmpty()).ifBlank { fallbackAlbum },
                    albumId = obj.optJSONObject("album")?.optString("id").orEmpty(),
                    durationMs = durationMs,
                    artworkUrl = extractBestImage(obj.optJSONArray("image")).ifBlank { fallbackArt },
                    streamUrl96 = url96,
                    streamUrl160 = url160,
                    streamUrl320 = url320,
                    sourceType = SourceType.ONLINE,
                    sourceProvider = source,
                    providerTrackId = id
                )
            )
        }
        return out
    }

    private fun tagAsSaavn(track: Track): Track = track.copy(
        sourceProvider = MusicSource.JIOSAAVN,
        providerTrackId = track.id.removePrefix("saavn_").removePrefix("seed_")
    )

    private fun executeGet(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "LiquidMusicAndroid/2.0")
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            return response.body?.string()
        }
    }

    private fun extractPrimaryArtistNames(obj: JSONObject): String {
        val primary = obj.optJSONObject("artists")?.optJSONArray("primary")
        if (primary != null && primary.length() > 0) {
            val names = (0 until primary.length()).mapNotNull {
                primary.optJSONObject(it)?.optString("name")?.takeIf { n -> n.isNotBlank() }
            }
            if (names.isNotEmpty()) return decodeHtml(names.joinToString(", "))
        }
        return decodeHtml(obj.optString("primaryArtists"))
    }

    private fun extractBestImage(images: JSONArray?): String {
        if (images == null || images.length() == 0) return ""
        var best = ""
        for (i in 0 until images.length()) {
            val img = images.optJSONObject(i) ?: continue
            val url = img.optString("url").ifBlank { img.optString("link") }
            if (img.optString("quality").contains("500") && url.isNotBlank()) return url
            if (url.isNotBlank()) best = url
        }
        return best
    }

    private fun extractDownloadUrls(downloads: JSONArray?): Map<String, String> {
        if (downloads == null) return emptyMap()
        val map = mutableMapOf<String, String>()
        for (i in 0 until downloads.length()) {
            val d = downloads.optJSONObject(i) ?: continue
            val q = d.optString("quality")
            val u = d.optString("url").ifBlank { d.optString("link") }
            if (q.isNotBlank() && u.isNotBlank()) map[q] = u
        }
        return map
    }

    private fun decodeHtml(input: String): String {
        if (input.isBlank()) return ""
        return Html.fromHtml(input, Html.FROM_HTML_MODE_LEGACY).toString().trim()
    }
}
