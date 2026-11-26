package com.example.data.remote.innertube

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
import com.example.domain.model.YouTubeAccountInfo
import org.json.JSONArray
import org.json.JSONObject

/**
 * Pure JSON parsers mapping raw YouTube Music InnerTube responses into unified provider-agnostic domain models.
 */
internal object InnerTubeParsers {

    fun parseStreamResolution(
        playerJson: JSONObject,
        quality: AudioQuality,
        forceOpus: Boolean = false
    ): StreamResolution? {
        val streamingData = playerJson.optJSONObject("streamingData") ?: return null
        val adaptive = streamingData.optJSONArray("adaptiveFormats")

        data class AudioCandidate(
            val url: String,
            val itag: Int,
            val bitrateBps: Int,
            val mimeType: String,
            val isMp4: Boolean,
            val isOpus: Boolean
        )

        val candidates = mutableListOf<AudioCandidate>()
        if (adaptive != null) {
            for (i in 0 until adaptive.length()) {
                val fmt = adaptive.optJSONObject(i) ?: continue
                val mime = fmt.optString("mimeType")
                val url = fmt.optString("url")
                if (!mime.startsWith("audio/") || !url.startsWith("http")) continue
                val itag = fmt.optInt("itag", 0)
                val bitrate = fmt.optInt("averageBitrate", fmt.optInt("bitrate", 128_000))
                candidates.add(
                    AudioCandidate(
                        url = url,
                        itag = itag,
                        bitrateBps = bitrate,
                        mimeType = mime.substringBefore(";"),
                        isMp4 = mime.contains("mp4") || mime.contains("m4a"),
                        isOpus = itag == 251 || mime.contains("opus", ignoreCase = true) || mime.contains("webm", ignoreCase = true)
                    )
                )
            }
        }
        // Fallback to muxed streamingData.formats (e.g. itag 18 MP4) when adaptive audio formats are ciphered/absent
        if (candidates.isEmpty()) {
            val formats = streamingData.optJSONArray("formats")
            if (formats != null) {
                for (i in 0 until formats.length()) {
                    val fmt = formats.optJSONObject(i) ?: continue
                    val mime = fmt.optString("mimeType")
                    val url = fmt.optString("url")
                    if (!url.startsWith("http")) continue
                    val itag = fmt.optInt("itag", 0)
                    val bitrate = fmt.optInt("averageBitrate", fmt.optInt("bitrate", 128_000))
                    candidates.add(
                        AudioCandidate(
                            url = url,
                            itag = itag,
                            bitrateBps = bitrate,
                            mimeType = mime.substringBefore(";").ifBlank { "video/mp4" },
                            isMp4 = mime.contains("mp4") || itag == 18,
                            isOpus = false
                        )
                    )
                }
            }
        }
        if (candidates.isEmpty()) {
            val hlsUrl = streamingData.optString("hlsManifestUrl")
            if (hlsUrl.startsWith("http")) {
                val audioConfig = playerJson.optJSONObject("playerConfig")?.optJSONObject("audioConfig")
                val loudnessDb = audioConfig?.takeIf { it.has("loudnessDb") }?.optDouble("loudnessDb")?.takeIf { !it.isNaN() }
                val perceptualLoudnessDb = audioConfig?.takeIf { it.has("perceptualLoudnessDb") }?.optDouble("perceptualLoudnessDb")?.takeIf { !it.isNaN() }
                val client = PlayerClient.forStreamUrl(hlsUrl)
                return StreamResolution(
                    uri = hlsUrl,
                    resolvedBy = MusicSource.YOUTUBE_MUSIC,
                    bitrateKbps = 128,
                    mimeType = "application/x-mpegURL",
                    usedFallback = false,
                    headers = client.mediaHeaders(),
                    clientName = "${client.clientName}_HLS",
                    loudnessDb = loudnessDb,
                    perceptualLoudnessDb = perceptualLoudnessDb
                )
            }
            return null
        }

        val best = when {
            forceOpus -> candidates.filter { it.isOpus }.maxByOrNull { it.bitrateBps }
                ?: candidates.maxByOrNull { it.bitrateBps }
            quality == AudioQuality.LOW_96 -> candidates.minByOrNull { it.bitrateBps }
            else -> candidates.maxWithOrNull(
                compareBy<AudioCandidate> { it.bitrateBps }
                    .thenBy { it.isOpus }
                    .thenBy { it.isMp4 }
            )
        } ?: candidates.first()

        val audioConfig = playerJson.optJSONObject("playerConfig")?.optJSONObject("audioConfig")
        val loudnessDb = audioConfig?.takeIf { it.has("loudnessDb") }?.optDouble("loudnessDb")?.takeIf { !it.isNaN() }
        val perceptualLoudnessDb = audioConfig?.takeIf { it.has("perceptualLoudnessDb") }?.optDouble("perceptualLoudnessDb")?.takeIf { !it.isNaN() }

        val client = PlayerClient.forStreamUrl(best.url)
        return StreamResolution(
            uri = best.url,
            resolvedBy = MusicSource.YOUTUBE_MUSIC,
            bitrateKbps = (best.bitrateBps / 1000).coerceAtLeast(64),
            mimeType = best.mimeType,
            usedFallback = false,
            headers = client.mediaHeaders(),
            clientName = client.clientName,
            loudnessDb = loudnessDb,
            perceptualLoudnessDb = perceptualLoudnessDb
        )
    }

    fun parseAccountInfo(root: JSONObject, dataSyncId: String = ""): YouTubeAccountInfo? {
        val header = findFirstObjectByKeys(root, setOf("activeAccountHeaderRenderer")) ?: return null
        val name = extractRunsText(header.optJSONObject("accountName")).trim()
        val handle = extractRunsText(header.optJSONObject("channelHandle")).trim()
        val email = extractRunsText(header.optJSONObject("email")).trim()
        val avatar = extractThumbnailUrl(header)
        if (name.isBlank() && handle.isBlank() && email.isBlank()) return null
        return YouTubeAccountInfo(
            name = name.ifBlank { handle.ifBlank { email } },
            handle = handle,
            email = email,
            avatarUrl = avatar,
            dataSyncId = dataSyncId
        )
    }

    fun parseLibraryPlaylists(root: JSONObject): List<PlaylistSummary> {
        val playlists = mutableListOf<PlaylistSummary>()
        val cards = mutableListOf<JSONObject>()
        collectObjectsByKeys(root, setOf("musicTwoRowItemRenderer", "musicResponsiveListItemRenderer"), cards)
        cards.forEach { node ->
            val dummyTracks = mutableListOf<Track>()
            val dummyAlbums = mutableListOf<AlbumSummary>()
            val dummyArtists = mutableListOf<ArtistSummary>()
            if (node.has("flexColumns")) {
                parseListItem(node, dummyTracks, dummyAlbums, dummyArtists, playlists)
            } else {
                parseTwoRowItem(node, dummyTracks, dummyAlbums, dummyArtists, playlists)
            }
        }
        return playlists.filter { it.browseId.isNotBlank() && it.title.isNotBlank() }.distinctBy { it.browseId }
    }

    fun extractContinuation(root: JSONObject): String? {
        return findFirstStringByKey(root, "continuation")
    }

    fun parseBrowseSections(root: JSONObject, defaultSectionTitle: String = "Featured"): List<BrowseSection> {
        val sections = mutableListOf<BrowseSection>()
        val shelfNodes = mutableListOf<JSONObject>()
        collectObjectsByKeys(
            root,
            setOf(
                "musicCarouselShelfRenderer",
                "musicShelfRenderer",
                "musicImmersiveCarouselShelfRenderer",
                "gridRenderer"
            ),
            shelfNodes
        )

        shelfNodes.forEachIndexed { idx, shelf ->
            val title = extractShelfTitle(shelf).ifBlank {
                if (idx == 0) defaultSectionTitle else "$defaultSectionTitle ${idx + 1}"
            }
            val subtitle = extractShelfSubtitle(shelf)
            val contents = shelf.optJSONArray("contents")
                ?: shelf.optJSONArray("items")
                ?: return@forEachIndexed

            val tracks = mutableListOf<Track>()
            val albums = mutableListOf<AlbumSummary>()
            val artists = mutableListOf<ArtistSummary>()

            for (i in 0 until contents.length()) {
                val item = contents.optJSONObject(i) ?: continue
                item.optJSONObject("musicResponsiveListItemRenderer")?.let { row ->
                    parseListItem(row, tracks, albums, artists, null)
                }
                item.optJSONObject("musicTwoRowItemRenderer")?.let { card ->
                    parseTwoRowItem(card, tracks, albums, artists, null)
                }
                item.optJSONObject("musicMultiRowListItemRenderer")?.let { multi ->
                    parseMultiRowItem(multi, tracks)
                }
            }

            if (tracks.isEmpty() && albums.isEmpty() && artists.isEmpty()) return@forEachIndexed

            val distinctTracks = tracks.distinctBy { it.id }
            val distinctAlbums = albums.distinctBy { it.id }
            val distinctArtists = artists.distinctBy { it.id }

            // If YouTube returns a mixed carousel with both songs AND albums (common on FEmusic_home),
            // emit both so songs are never hidden behind albums!
            if (distinctTracks.size >= 3 && distinctAlbums.size >= 2) {
                sections.add(
                    BrowseSection(
                        id = "ytm_sec_${idx}_${title.hashCode()}_tracks",
                        title = title,
                        subtitle = subtitle,
                        layoutType = if (idx == 0 || title.contains("Quick picks", true)) {
                            BrowseLayoutType.QUICK_PICKS_GRID
                        } else {
                            BrowseLayoutType.CAROUSEL_TRACKS
                        },
                        tracks = distinctTracks,
                        sourceProvider = MusicSource.YOUTUBE_MUSIC
                    )
                )
                sections.add(
                    BrowseSection(
                        id = "ytm_sec_${idx}_${title.hashCode()}_albums",
                        title = "$title · Albums",
                        subtitle = subtitle,
                        layoutType = BrowseLayoutType.CAROUSEL_ALBUMS,
                        albums = distinctAlbums,
                        sourceProvider = MusicSource.YOUTUBE_MUSIC
                    )
                )
                return@forEachIndexed
            }

            val layoutType = when {
                (idx == 0 && distinctTracks.size >= 4) || title.contains("Quick picks", ignoreCase = true) || title.contains("Speed dial", ignoreCase = true) ->
                    BrowseLayoutType.QUICK_PICKS_GRID
                title.contains("Chart", ignoreCase = true) || title.contains("Top", ignoreCase = true) || title.contains("Trending", ignoreCase = true) -> {
                    if (distinctTracks.isNotEmpty()) BrowseLayoutType.CHART_LIST else BrowseLayoutType.CAROUSEL_ALBUMS
                }
                distinctAlbums.size >= distinctTracks.size && distinctAlbums.isNotEmpty() -> BrowseLayoutType.CAROUSEL_ALBUMS
                distinctArtists.size >= distinctTracks.size && distinctArtists.isNotEmpty() -> BrowseLayoutType.CAROUSEL_ARTISTS
                else -> BrowseLayoutType.CAROUSEL_TRACKS
            }

            sections.add(
                BrowseSection(
                    id = "ytm_sec_${idx}_${title.hashCode()}",
                    title = title,
                    subtitle = subtitle,
                    layoutType = layoutType,
                    tracks = distinctTracks,
                    albums = distinctAlbums,
                    artists = distinctArtists,
                    sourceProvider = MusicSource.YOUTUBE_MUSIC
                )
            )
        }
        return sections
    }

    fun parseMoods(root: JSONObject): List<MoodItem> {
        val moods = mutableListOf<MoodItem>()
        val navButtons = mutableListOf<JSONObject>()
        collectObjectsByKeys(root, setOf("musicNavigationButtonRenderer"), navButtons)
        navButtons.forEach { btn ->
            val title = extractRunsText(btn.optJSONObject("buttonText")).trim()
            val endpoint = btn.optJSONObject("clickCommand")?.optJSONObject("browseEndpoint")
            val browseId = endpoint?.optString("browseId").orEmpty()
            val params = endpoint?.optString("params").orEmpty()
            val colorInt = btn.optJSONObject("solid")?.optLong("leftStripeColor", 0xFF7BE8FFL) ?: 0xFF7BE8FFL
            if (title.isNotBlank()) {
                moods.add(
                    MoodItem(
                        title = title,
                        query = "$title music hits",
                        browseId = browseId,
                        params = params,
                        accentHex = colorInt
                    )
                )
            }
        }
        return moods.distinctBy { it.title }.take(14)
    }

    fun parseSearchResults(root: JSONObject): UnifiedSearchResults {
        val tracks = mutableListOf<Track>()
        val albums = mutableListOf<AlbumSummary>()
        val artists = mutableListOf<ArtistSummary>()
        val playlists = mutableListOf<PlaylistSummary>()

        val rows = mutableListOf<JSONObject>()
        collectObjectsByKeys(root, setOf("musicResponsiveListItemRenderer", "musicCardShelfRenderer"), rows)
        rows.forEach { obj ->
            if (obj.has("flexColumns")) {
                parseListItem(obj, tracks, albums, artists, playlists)
            } else {
                // Top result card shelf
                parseCardShelfTopResult(obj, tracks, albums, artists, playlists)
            }
        }

        return UnifiedSearchResults(
            songs = tracks.distinctBy { it.id },
            albums = albums.distinctBy { it.id },
            artists = artists.distinctBy { it.id },
            playlists = playlists.distinctBy { it.id },
            activeSource = MusicSource.YOUTUBE_MUSIC,
            usedFallback = false
        )
    }

    fun parseSearchSuggestions(root: JSONObject): List<String> {
        val suggestions = mutableListOf<String>()
        val nodes = mutableListOf<JSONObject>()
        collectObjectsByKeys(root, setOf("searchSuggestionRenderer"), nodes)
        nodes.forEach { node ->
            val text = extractRunsText(node.optJSONObject("suggestion")).trim()
            if (text.isNotBlank()) suggestions.add(text)
        }
        return suggestions.distinct().take(8)
    }

    fun parseAlbumDetail(root: JSONObject, browseId: String): AlbumDetail? {
        val header = findFirstObjectByKeys(
            root,
            setOf("musicResponsiveHeaderRenderer", "musicDetailHeaderRenderer")
        )
        val title = header?.let { extractRunsText(it.optJSONObject("title")) }.orEmpty()
        val subtitle = header?.let { extractRunsText(it.optJSONObject("subtitle")) }.orEmpty()
        val strapline = header?.let { extractRunsText(it.optJSONObject("straplineTextOne")) }.orEmpty()
        val description = header?.let {
            extractRunsText(
                it.optJSONObject("description")
                    ?.optJSONObject("musicDescriptionShelfRenderer")
                    ?.optJSONObject("description")
                    ?: it.optJSONObject("description")
            )
        }.orEmpty()
        val artwork = header?.let { extractThumbnailUrl(it) }.orEmpty()

        val subtitleParts = subtitle.split("•").map { it.trim() }.filter { it.isNotBlank() }
        val artist = strapline.ifBlank {
            subtitleParts.firstOrNull { !it.equals("Album", true) && !it.equals("EP", true) && !it.equals("Single", true) && it.toIntOrNull() == null }
                ?: "Various Artists"
        }
        val year = subtitleParts.firstOrNull { it.length == 4 && it.toIntOrNull() != null }.orEmpty()

        val rows = mutableListOf<JSONObject>()
        collectObjectsByKeys(root, setOf("musicResponsiveListItemRenderer"), rows)

        val tracks = mutableListOf<Track>()
        rows.forEach { row ->
            val tempTracks = mutableListOf<Track>()
            parseListItem(row, tempTracks, mutableListOf(), mutableListOf(), null)
            tempTracks.firstOrNull()?.let { t ->
                tracks.add(
                    t.copy(
                        artist = if (t.artist == "Unknown Artist" || t.artist.isBlank()) artist else t.artist,
                        album = title.ifBlank { t.album },
                        albumId = browseId,
                        artworkUrl = t.artworkUrl.ifBlank { artwork }
                    )
                )
            }
        }

        if (title.isBlank() && tracks.isEmpty()) return null
        return AlbumDetail(
            id = "ytm_alb_$browseId",
            browseId = browseId,
            title = title.ifBlank { tracks.firstOrNull()?.album.orEmpty().ifBlank { "Album" } },
            artist = artist,
            year = year,
            description = description,
            artworkUrl = artwork.ifBlank { tracks.firstOrNull()?.artworkUrl.orEmpty() },
            tracks = tracks.distinctBy { it.id },
            sourceProvider = MusicSource.YOUTUBE_MUSIC
        )
    }

    fun parseArtistDetail(root: JSONObject, browseId: String): ArtistDetail? {
        val header = findFirstObjectByKeys(
            root,
            setOf("musicImmersiveHeaderRenderer", "musicVisualHeaderRenderer", "musicDetailHeaderRenderer")
        )
        val name = header?.let { extractRunsText(it.optJSONObject("title")) }.orEmpty()
        val description = header?.let { extractRunsText(it.optJSONObject("description")) }.orEmpty()
        val subscribers = header?.optJSONObject("subscriptionButton")
            ?.optJSONObject("subscribeButtonRenderer")
            ?.let { extractRunsText(it.optJSONObject("subscriberCountText")) }
            .orEmpty()
        val thumbnail = header?.let { extractThumbnailUrl(it) }.orEmpty()

        val radioPlaylistId = header?.optJSONObject("startRadioButton")
            ?.optJSONObject("buttonRenderer")
            ?.optJSONObject("navigationEndpoint")
            ?.optJSONObject("watchEndpoint")
            ?.optString("playlistId")
            .orEmpty()

        val sections = parseBrowseSections(root)
        val topSongs = sections.flatMap { it.tracks }.distinctBy { it.id }.take(15)
        val albums = sections
            .filter { !it.title.contains("Single", true) }
            .flatMap { it.albums }
            .distinctBy { it.id }
        val singles = sections
            .filter { it.title.contains("Single", true) }
            .flatMap { it.albums }
            .map { it.copy(isSingle = true) }
            .distinctBy { it.id }
        val relatedArtists = sections.flatMap { it.artists }.distinctBy { it.id }

        if (name.isBlank() && topSongs.isEmpty() && albums.isEmpty()) return null

        return ArtistDetail(
            id = "ytm_art_$browseId",
            browseId = browseId,
            name = name.ifBlank { "Artist" },
            description = description,
            subscribersText = subscribers,
            thumbnailUrl = thumbnail.ifBlank { topSongs.firstOrNull()?.artworkUrl.orEmpty() },
            topSongs = topSongs,
            albums = albums,
            singles = singles,
            relatedArtists = relatedArtists,
            radioPlaylistId = radioPlaylistId,
            sourceProvider = MusicSource.YOUTUBE_MUSIC
        )
    }

    fun parseRadioQueue(root: JSONObject): List<Track> {
        val items = mutableListOf<JSONObject>()
        collectObjectsByKeys(root, setOf("playlistPanelVideoRenderer"), items)
        val tracks = mutableListOf<Track>()

        items.forEach { item ->
            val videoId = item.optString("videoId")
            if (videoId.isBlank()) return@forEach
            val title = extractRunsText(item.optJSONObject("title"))
            if (title.isBlank()) return@forEach

            val byline = extractRunsText(item.optJSONObject("shortBylineText"))
                .ifBlank { extractRunsText(item.optJSONObject("longBylineText")) }
            val parts = byline.split("•").map { it.trim() }.filter { it.isNotBlank() }
            val artist = parts.firstOrNull().orEmpty().ifBlank { "Unknown Artist" }
            val album = parts.getOrNull(1).orEmpty()
            val durationMs = parseDurationMs(extractRunsText(item.optJSONObject("lengthText")))
            val artwork = extractThumbnailUrl(item)
            val artistId = findBrowseIdWithPrefix(item.optJSONObject("longBylineText"), "UC")
            val albumId = findBrowseIdWithPrefix(item.optJSONObject("longBylineText"), "MPRE")

            tracks.add(
                Track(
                    id = "ytm_$videoId",
                    title = title,
                    artist = artist,
                    album = album,
                    durationMs = durationMs,
                    artworkUrl = upgradeArtworkResolution(artwork),
                    sourceType = SourceType.ONLINE,
                    sourceProvider = MusicSource.YOUTUBE_MUSIC,
                    providerTrackId = videoId,
                    artistId = artistId,
                    albumId = albumId
                )
            )
        }
        return tracks.distinctBy { it.id }
    }

    fun extractLyricsBrowseId(nextJson: JSONObject): String? {
        val tabs = mutableListOf<JSONObject>()
        collectObjectsByKeys(nextJson, setOf("tabRenderer"), tabs)
        for (tab in tabs) {
            val title = tab.optString("title")
            val browseId = tab.optJSONObject("endpoint")
                ?.optJSONObject("browseEndpoint")
                ?.optString("browseId")
            if (!browseId.isNullOrBlank() && (title.equals("Lyrics", true) || browseId.startsWith("MPLY"))) {
                return browseId
            }
        }
        return null
    }

    fun extractPlainLyricsFromBrowse(browseJson: JSONObject): String? {
        val descShelves = mutableListOf<JSONObject>()
        collectObjectsByKeys(browseJson, setOf("musicDescriptionShelfRenderer"), descShelves)
        for (shelf in descShelves) {
            val text = extractRunsText(shelf.optJSONObject("description")).trim()
            if (text.length > 20) return text
        }
        return null
    }

    private fun parseCardShelfTopResult(
        card: JSONObject,
        tracks: MutableList<Track>,
        albums: MutableList<AlbumSummary>,
        artists: MutableList<ArtistSummary>,
        playlists: MutableList<PlaylistSummary>
    ) {
        val title = extractRunsText(card.optJSONObject("title")).trim()
        val subtitle = extractRunsText(card.optJSONObject("subtitle")).trim()
        val artwork = upgradeArtworkResolution(extractThumbnailUrl(card))
        val nav = card.optJSONObject("onTap") ?: return
        val videoId = nav.optJSONObject("watchEndpoint")?.optString("videoId").orEmpty()
        val browseId = nav.optJSONObject("browseEndpoint")?.optString("browseId").orEmpty()

        if (videoId.isNotBlank() && title.isNotBlank()) {
            val parts = subtitle.split("•").map { it.trim() }.filter { it.isNotBlank() }
            val artist = parts.firstOrNull { !it.equals("Song", true) && !it.equals("Video", true) } ?: "Unknown Artist"
            val durationMs = parts.lastOrNull()?.let { parseDurationMs(it) } ?: 0L
            tracks.add(
                Track(
                    id = "ytm_$videoId",
                    title = title,
                    artist = artist,
                    durationMs = durationMs,
                    artworkUrl = artwork,
                    sourceType = SourceType.ONLINE,
                    sourceProvider = MusicSource.YOUTUBE_MUSIC,
                    providerTrackId = videoId
                )
            )
        } else if (browseId.startsWith("UC") && title.isNotBlank()) {
            artists.add(
                ArtistSummary(
                    id = "ytm_art_$browseId",
                    browseId = browseId,
                    name = title,
                    subtitle = subtitle,
                    thumbnailUrl = artwork,
                    sourceProvider = MusicSource.YOUTUBE_MUSIC
                )
            )
        } else if ((browseId.startsWith("MPRE") || browseId.startsWith("OLAK")) && title.isNotBlank()) {
            val parts = subtitle.split("•").map { it.trim() }.filter { it.isNotBlank() }
            albums.add(
                AlbumSummary(
                    id = "ytm_alb_$browseId",
                    browseId = browseId,
                    title = title,
                    artist = parts.getOrNull(1) ?: parts.firstOrNull().orEmpty(),
                    artworkUrl = artwork,
                    sourceProvider = MusicSource.YOUTUBE_MUSIC
                )
            )
        } else if (browseId.startsWith("VL") && title.isNotBlank()) {
            playlists.add(
                PlaylistSummary(
                    id = "ytm_pl_$browseId",
                    browseId = browseId,
                    title = title,
                    author = subtitle,
                    artworkUrl = artwork,
                    sourceProvider = MusicSource.YOUTUBE_MUSIC
                )
            )
        }
    }

    private fun parseListItem(
        row: JSONObject,
        tracks: MutableList<Track>,
        albums: MutableList<AlbumSummary>,
        artists: MutableList<ArtistSummary>,
        playlists: MutableList<PlaylistSummary>?
    ) {
        val flexColumns = row.optJSONArray("flexColumns") ?: return
        val primaryText = extractFlexColumnText(flexColumns, 0).trim()
        if (primaryText.isBlank()) return
        val secondaryText = extractFlexColumnText(flexColumns, 1).trim()
        val artwork = upgradeArtworkResolution(extractThumbnailUrl(row))

        val videoId = extractDirectVideoId(row)
        val browseEndpoint = row.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")
        val browseId = browseEndpoint?.optString("browseId").orEmpty()
        val pageType = browseEndpoint
            ?.optJSONObject("browseEndpointContextSupportedConfigs")
            ?.optJSONObject("browseEndpointContextMusicConfig")
            ?.optString("pageType")
            .orEmpty()

        val secondaryParts = secondaryText.split("•").map { it.trim() }.filter { it.isNotBlank() }
        val durationText = extractFixedColumnText(row.optJSONArray("fixedColumns"))
            .ifBlank { secondaryParts.lastOrNull { it.contains(":") }.orEmpty() }
        val durationMs = parseDurationMs(durationText)

        when {
            pageType == "MUSIC_PAGE_TYPE_ARTIST" || (browseId.startsWith("UC") && videoId.isBlank()) -> {
                artists.add(
                    ArtistSummary(
                        id = "ytm_art_$browseId",
                        browseId = browseId,
                        name = primaryText,
                        subtitle = secondaryText.ifBlank { "Artist" },
                        thumbnailUrl = artwork,
                        sourceProvider = MusicSource.YOUTUBE_MUSIC
                    )
                )
            }

            pageType == "MUSIC_PAGE_TYPE_ALBUM" || ((browseId.startsWith("MPRE") || browseId.startsWith("OLAK")) && videoId.isBlank()) -> {
                val filtered = secondaryParts.filterNot {
                    it.equals("Album", true) || it.equals("EP", true) || it.equals("Single", true)
                }
                val year = filtered.firstOrNull { it.length == 4 && it.toIntOrNull() != null }.orEmpty()
                val artist = filtered.firstOrNull { it != year } ?: "Unknown Artist"
                albums.add(
                    AlbumSummary(
                        id = "ytm_alb_$browseId",
                        browseId = browseId,
                        title = primaryText,
                        artist = artist,
                        artistId = findBrowseIdWithPrefix(row, "UC"),
                        year = year,
                        artworkUrl = artwork,
                        sourceProvider = MusicSource.YOUTUBE_MUSIC,
                        isSingle = secondaryText.contains("Single", true)
                    )
                )
            }

            pageType == "MUSIC_PAGE_TYPE_PLAYLIST" || (browseId.startsWith("VL") && videoId.isBlank()) -> {
                playlists?.add(
                    PlaylistSummary(
                        id = "ytm_pl_$browseId",
                        browseId = browseId,
                        title = primaryText,
                        author = secondaryParts.firstOrNull { !it.equals("Playlist", true) }.orEmpty(),
                        trackCountText = secondaryParts.lastOrNull().orEmpty(),
                        artworkUrl = artwork,
                        sourceProvider = MusicSource.YOUTUBE_MUSIC
                    )
                )
            }

            videoId.isNotBlank() -> {
                val metaParts = secondaryParts.filterNot {
                    it.equals("Song", true) ||
                        it.equals("Video", true) ||
                        it.contains("plays", true) ||
                        it.contains("views", true) ||
                        it.matches(Regex("""^\d{1,2}:\d{2}(:\d{2})?$"""))
                }
                val artist = metaParts.firstOrNull().orEmpty().ifBlank { "Unknown Artist" }
                val album = metaParts.getOrNull(1).orEmpty()
                val explicit = row.toString().contains("MUSIC_EXPLICIT_BADGE")
                val isVideo = secondaryParts.any { it.equals("Video", true) } ||
                    row.toString().contains("MUSIC_VIDEO_TYPE_OMV") ||
                    row.toString().contains("MUSIC_VIDEO_TYPE_UGC")
                tracks.add(
                    Track(
                        id = "ytm_$videoId",
                        title = primaryText,
                        artist = artist,
                        album = album,
                        durationMs = durationMs,
                        artworkUrl = artwork,
                        sourceType = SourceType.ONLINE,
                        sourceProvider = MusicSource.YOUTUBE_MUSIC,
                        providerTrackId = videoId,
                        artistId = findBrowseIdWithPrefix(row, "UC"),
                        albumId = findBrowseIdWithPrefix(row, "MPRE"),
                        explicit = explicit,
                        isVideoSong = isVideo
                    )
                )
            }
        }
    }

    private fun parseTwoRowItem(
        card: JSONObject,
        tracks: MutableList<Track>,
        albums: MutableList<AlbumSummary>,
        artists: MutableList<ArtistSummary>,
        playlists: MutableList<PlaylistSummary>?
    ) {
        val title = extractRunsText(card.optJSONObject("title")).trim()
        if (title.isBlank()) return
        val subtitle = extractRunsText(card.optJSONObject("subtitle")).trim()
        val artwork = upgradeArtworkResolution(extractThumbnailUrl(card))

        val nav = card.optJSONObject("navigationEndpoint")
        val videoId = nav?.optJSONObject("watchEndpoint")?.optString("videoId").orEmpty()
            .ifBlank { extractOverlayVideoId(card) }
        val browseEndpoint = nav?.optJSONObject("browseEndpoint")
        val browseId = browseEndpoint?.optString("browseId").orEmpty()
        val pageType = browseEndpoint
            ?.optJSONObject("browseEndpointContextSupportedConfigs")
            ?.optJSONObject("browseEndpointContextMusicConfig")
            ?.optString("pageType")
            .orEmpty()

        val subtitleParts = subtitle.split("•").map { it.trim() }.filter { it.isNotBlank() }

        when {
            pageType == "MUSIC_PAGE_TYPE_ARTIST" || browseId.startsWith("UC") -> {
                artists.add(
                    ArtistSummary(
                        id = "ytm_art_$browseId",
                        browseId = browseId,
                        name = title,
                        subtitle = subtitle.ifBlank { "Artist" },
                        thumbnailUrl = artwork,
                        sourceProvider = MusicSource.YOUTUBE_MUSIC
                    )
                )
            }

            pageType == "MUSIC_PAGE_TYPE_ALBUM" || browseId.startsWith("MPRE") || browseId.startsWith("OLAK") -> {
                val filtered = subtitleParts.filterNot {
                    it.equals("Album", true) || it.equals("EP", true) || it.equals("Single", true)
                }
                val year = filtered.firstOrNull { it.length == 4 && it.toIntOrNull() != null }.orEmpty()
                val artist = filtered.firstOrNull { it != year } ?: "Unknown Artist"
                albums.add(
                    AlbumSummary(
                        id = "ytm_alb_$browseId",
                        browseId = browseId,
                        title = title,
                        artist = artist,
                        artistId = findBrowseIdWithPrefix(card, "UC"),
                        year = year,
                        artworkUrl = artwork,
                        sourceProvider = MusicSource.YOUTUBE_MUSIC,
                        isSingle = subtitle.contains("Single", true)
                    )
                )
            }

            videoId.isNotBlank() -> {
                val filteredParts = subtitleParts.filterNot {
                    it.equals("Song", true) ||
                        it.equals("Video", true) ||
                        it.equals("Single", true) ||
                        it.equals("Album", true) ||
                        it.equals("EP", true) ||
                        it.contains("views", true) ||
                        it.contains("plays", true) ||
                        it.matches(Regex("""^\d{4}$""")) ||
                        it.matches(Regex("""^\d{1,2}:\d{2}(:\d{2})?$"""))
                }
                val artist = filteredParts.firstOrNull().orEmpty().ifBlank { "Unknown Artist" }
                tracks.add(
                    Track(
                        id = "ytm_$videoId",
                        title = title,
                        artist = artist,
                        album = filteredParts.getOrNull(1).orEmpty(),
                        artworkUrl = artwork,
                        sourceType = SourceType.ONLINE,
                        sourceProvider = MusicSource.YOUTUBE_MUSIC,
                        providerTrackId = videoId,
                        artistId = findBrowseIdWithPrefix(card, "UC"),
                        albumId = findBrowseIdWithPrefix(card, "MPRE")
                    )
                )
            }

            browseId.startsWith("VL") -> {
                playlists?.add(
                    PlaylistSummary(
                        id = "ytm_pl_$browseId",
                        browseId = browseId,
                        title = title,
                        author = subtitle,
                        artworkUrl = artwork,
                        sourceProvider = MusicSource.YOUTUBE_MUSIC
                    )
                )
            }
        }
    }

    private fun parseMultiRowItem(item: JSONObject, tracks: MutableList<Track>) {
        val title = extractRunsText(item.optJSONObject("title")).trim()
        if (title.isBlank()) return
        val videoId = extractDirectVideoId(item)
        if (videoId.isBlank()) return
        val subtitle = extractRunsText(item.optJSONObject("subtitle")).trim()
        val parts = subtitle.split("•").map { it.trim() }.filter { it.isNotBlank() }
        val artist = parts.firstOrNull { !it.matches(Regex("""^\d{1,2}:\d{2}(:\d{2})?$""")) } ?: "Unknown Artist"
        val durationMs = parts.lastOrNull { it.contains(":") }?.let { parseDurationMs(it) } ?: 0L
        tracks.add(
            Track(
                id = "ytm_$videoId",
                title = title,
                artist = artist,
                durationMs = durationMs,
                artworkUrl = upgradeArtworkResolution(extractThumbnailUrl(item)),
                sourceType = SourceType.ONLINE,
                sourceProvider = MusicSource.YOUTUBE_MUSIC,
                providerTrackId = videoId,
                artistId = findBrowseIdWithPrefix(item, "UC")
            )
        )
    }

    private fun extractOverlayVideoId(node: JSONObject): String {
        return node.optJSONObject("thumbnailOverlay")
            ?.optJSONObject("musicItemThumbnailOverlayRenderer")
            ?.optJSONObject("content")
            ?.optJSONObject("musicPlayButtonRenderer")
            ?.optJSONObject("playNavigationEndpoint")
            ?.optJSONObject("watchEndpoint")
            ?.optString("videoId")
            .orEmpty()
            .ifBlank {
                node.optJSONObject("overlay")
                    ?.optJSONObject("musicItemThumbnailOverlayRenderer")
                    ?.optJSONObject("content")
                    ?.optJSONObject("musicPlayButtonRenderer")
                    ?.optJSONObject("playNavigationEndpoint")
                    ?.optJSONObject("watchEndpoint")
                    ?.optString("videoId")
                    .orEmpty()
            }
    }

    private fun extractDirectVideoId(row: JSONObject): String {
        val direct = row.optJSONObject("playlistItemData")?.optString("videoId").orEmpty()
        if (direct.isNotBlank()) return direct
        val overlay = extractOverlayVideoId(row)
        if (overlay.isNotBlank()) return overlay
        val nav = row.optJSONObject("navigationEndpoint")?.optJSONObject("watchEndpoint")?.optString("videoId").orEmpty()
        if (nav.isNotBlank()) return nav
        val onTap = row.optJSONObject("onTap")?.optJSONObject("watchEndpoint")?.optString("videoId").orEmpty()
        if (onTap.isNotBlank()) return onTap
        return findFirstStringByKey(row, "videoId").orEmpty()
    }

    private fun extractShelfTitle(shelf: JSONObject): String {
        val header = shelf.optJSONObject("header")
            ?.optJSONObject("musicCarouselShelfBasicHeaderRenderer")
            ?: shelf.optJSONObject("header")?.optJSONObject("gridHeaderRenderer")
            ?: shelf.optJSONObject("header")
            ?: shelf
        return extractRunsText(header.optJSONObject("title")).trim()
    }

    private fun extractShelfSubtitle(shelf: JSONObject): String {
        val header = shelf.optJSONObject("header")
            ?.optJSONObject("musicCarouselShelfBasicHeaderRenderer")
            ?: return ""
        return extractRunsText(header.optJSONObject("strapline")).trim()
    }

    private fun extractFlexColumnText(columns: JSONArray, index: Int): String {
        val col = columns.optJSONObject(index)
            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            ?: return ""
        return extractRunsText(col.optJSONObject("text"))
    }

    private fun extractFixedColumnText(columns: JSONArray?): String {
        if (columns == null || columns.length() == 0) return ""
        val col = columns.optJSONObject(0)
            ?.optJSONObject("musicResponsiveListItemFixedColumnRenderer")
            ?: return ""
        return extractRunsText(col.optJSONObject("text"))
    }

    private fun extractRunsText(obj: JSONObject?): String {
        if (obj == null) return ""
        val simple = obj.optString("simpleText")
        if (simple.isNotBlank()) return simple
        val runs = obj.optJSONArray("runs") ?: return ""
        val sb = StringBuilder()
        for (i in 0 until runs.length()) {
            sb.append(runs.optJSONObject(i)?.optString("text").orEmpty())
        }
        return sb.toString()
    }

    private fun extractThumbnailUrl(obj: JSONObject): String {
        val thumbArrays = mutableListOf<JSONArray>()
        collectThumbnailArrays(obj, thumbArrays)
        var bestUrl = ""
        var bestWidth = -1
        for (arr in thumbArrays) {
            for (i in 0 until arr.length()) {
                val t = arr.optJSONObject(i) ?: continue
                val url = t.optString("url")
                val w = t.optInt("width", 0)
                if (url.startsWith("http") && w >= bestWidth) {
                    bestWidth = w
                    bestUrl = url
                }
            }
            if (bestUrl.isNotBlank()) break
        }
        return bestUrl
    }

    private fun upgradeArtworkResolution(url: String): String {
        if (url.isBlank()) return ""
        return url.replace(Regex("""=w\d+-h\d+[^&]*"""), "=w600-h600-l90-rj")
    }

    private fun parseDurationMs(raw: String): Long {
        val clean = raw.trim()
        if (!clean.contains(":")) return 0L
        val parts = clean.split(":").mapNotNull { it.toLongOrNull() }
        return when (parts.size) {
            2 -> (parts[0] * 60L + parts[1]) * 1000L
            3 -> (parts[0] * 3600L + parts[1] * 60L + parts[2]) * 1000L
            else -> 0L
        }
    }

    private fun findBrowseIdWithPrefix(node: Any?, prefix: String): String {
        when (node) {
            is JSONObject -> {
                val bid = node.optString("browseId")
                if (bid.startsWith(prefix)) return bid
                val keys = node.keys()
                while (keys.hasNext()) {
                    val found = findBrowseIdWithPrefix(node.opt(keys.next()), prefix)
                    if (found.isNotBlank()) return found
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    val found = findBrowseIdWithPrefix(node.opt(i), prefix)
                    if (found.isNotBlank()) return found
                }
            }
        }
        return ""
    }

    private fun findFirstStringByKey(node: Any?, targetKey: String): String? {
        when (node) {
            is JSONObject -> {
                if (node.has(targetKey)) {
                    val v = node.optString(targetKey)
                    if (v.isNotBlank()) return v
                }
                val keys = node.keys()
                while (keys.hasNext()) {
                    val found = findFirstStringByKey(node.opt(keys.next()), targetKey)
                    if (!found.isNullOrBlank()) return found
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    val found = findFirstStringByKey(node.opt(i), targetKey)
                    if (!found.isNullOrBlank()) return found
                }
            }
        }
        return null
    }

    private fun findFirstObjectByKeys(node: Any?, targetKeys: Set<String>): JSONObject? {
        when (node) {
            is JSONObject -> {
                val keys = node.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val child = node.opt(key)
                    if (key in targetKeys && child is JSONObject) return child
                    val nested = findFirstObjectByKeys(child, targetKeys)
                    if (nested != null) return nested
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    val nested = findFirstObjectByKeys(node.opt(i), targetKeys)
                    if (nested != null) return nested
                }
            }
        }
        return null
    }

    private fun collectObjectsByKeys(node: Any?, targetKeys: Set<String>, out: MutableList<JSONObject>) {
        when (node) {
            is JSONObject -> {
                val keys = node.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val child = node.opt(key)
                    if (key in targetKeys && child is JSONObject) {
                        out.add(child)
                    } else {
                        collectObjectsByKeys(child, targetKeys, out)
                    }
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    collectObjectsByKeys(node.opt(i), targetKeys, out)
                }
            }
        }
    }

    private fun collectThumbnailArrays(node: Any?, out: MutableList<JSONArray>) {
        when (node) {
            is JSONObject -> {
                val thumbs = node.optJSONArray("thumbnails")
                if (thumbs != null && thumbs.length() > 0) {
                    out.add(thumbs)
                    return
                }
                val keys = node.keys()
                while (keys.hasNext()) {
                    collectThumbnailArrays(node.opt(keys.next()), out)
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    collectThumbnailArrays(node.opt(i), out)
                }
            }
        }
    }
}
