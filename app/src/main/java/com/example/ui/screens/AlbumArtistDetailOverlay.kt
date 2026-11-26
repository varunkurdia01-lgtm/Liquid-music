package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.domain.model.AlbumDetail
import com.example.domain.model.AlbumSummary
import com.example.domain.model.ArtistDetail
import com.example.domain.model.ArtistSummary
import com.example.domain.model.MusicSource
import com.example.domain.model.Track
import com.example.ui.glass.LiquidAuroraCanvas
import com.example.ui.glass.LiquidGlassButton
import com.example.ui.glass.LiquidGlassSurface
import com.example.ui.glass.TrackArtwork
import com.example.ui.theme.FrostWhite
import com.example.ui.theme.LocalLiquidPalette
import com.example.ui.theme.LuminousRose
import com.example.ui.theme.MistSecondary
import com.example.ui.theme.ObsidianBlack
import com.example.ui.viewmodel.DetailOverlayState

@Composable
fun AlbumArtistDetailOverlay(
    overlayState: DetailOverlayState,
    activeTrackId: String?,
    isPlaying: Boolean,
    likedTrackIds: Set<String>,
    downloadedTrackIds: Set<String>,
    savedAlbumIds: Set<String>,
    bottomPadding: Dp,
    onClose: () -> Unit,
    onRetry: () -> Unit,
    onPlayQueue: (List<Track>, Int, Boolean) -> Unit,
    onStartTrackRadio: (Track) -> Unit,
    onStartArtistRadio: (ArtistDetail) -> Unit,
    onToggleSaveAlbum: (AlbumSummary) -> Unit,
    onDownloadBatch: (List<Track>) -> Unit,
    onToggleLike: (Track) -> Unit,
    onToggleDownload: (Track) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onOpenAlbum: (String, MusicSource) -> Unit,
    onOpenArtist: (String, MusicSource) -> Unit
) {
    if (overlayState is DetailOverlayState.None) return

    BackHandler(onBack = onClose)

    val palette = LocalLiquidPalette.current
    val heroArtwork = when (overlayState) {
        is DetailOverlayState.Album -> overlayState.detail?.artworkUrl.orEmpty()
        is DetailOverlayState.Artist -> overlayState.detail?.thumbnailUrl.orEmpty()
        DetailOverlayState.None -> ""
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack)
            .testTag("detail_overlay_container")
    ) {
        // Atmospheric hero backdrop
        if (heroArtwork.isNotBlank()) {
            AsyncImage(
                model = heroArtwork,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp)
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to ObsidianBlack.copy(alpha = 0.35f),
                                0.28f to ObsidianBlack.copy(alpha = 0.72f),
                                0.52f to ObsidianBlack.copy(alpha = 0.96f),
                                1.0f to ObsidianBlack
                            )
                        )
                    )
            )
        } else {
            LiquidAuroraCanvas(intensity = 0.85f)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                LiquidGlassButton(
                    onClick = onClose,
                    cornerRadius = 18.dp,
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("detail_overlay_back")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = FrostWhite,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                val badgeText = when (overlayState) {
                    is DetailOverlayState.Album -> overlayState.source.displayName
                    is DetailOverlayState.Artist -> overlayState.source.displayName
                    DetailOverlayState.None -> ""
                }
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color(0x6612182B))
                        .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.secondaryGlow
                    )
                }
            }

            when (overlayState) {
                is DetailOverlayState.Album -> {
                    when {
                        overlayState.isLoading -> DetailLoadingBox("Loading album tracklist…")
                        overlayState.error != null || overlayState.detail == null -> {
                            DetailErrorCard(
                                message = overlayState.error ?: "Album details unavailable.",
                                onRetry = onRetry
                            )
                        }
                        else -> AlbumDetailContent(
                            album = overlayState.detail,
                            isSaved = savedAlbumIds.contains(overlayState.detail.id),
                            activeTrackId = activeTrackId,
                            isPlaying = isPlaying,
                            likedTrackIds = likedTrackIds,
                            downloadedTrackIds = downloadedTrackIds,
                            bottomPadding = bottomPadding,
                            onPlayQueue = onPlayQueue,
                            onStartTrackRadio = onStartTrackRadio,
                            onToggleSave = { onToggleSaveAlbum(overlayState.detail.toSummary()) },
                            onDownloadAlbum = { onDownloadBatch(overlayState.detail.tracks) },
                            onToggleLike = onToggleLike,
                            onToggleDownload = onToggleDownload,
                            onAddToPlaylist = onAddToPlaylist,
                            onOpenArtist = {
                                if (overlayState.detail.artistId.isNotBlank()) {
                                    onOpenArtist(overlayState.detail.artistId, overlayState.detail.sourceProvider)
                                }
                            }
                        )
                    }
                }

                is DetailOverlayState.Artist -> {
                    when {
                        overlayState.isLoading -> DetailLoadingBox("Loading artist catalog…")
                        overlayState.error != null || overlayState.detail == null -> {
                            DetailErrorCard(
                                message = overlayState.error ?: "Artist profile unavailable.",
                                onRetry = onRetry
                            )
                        }
                        else -> ArtistDetailContent(
                            artist = overlayState.detail,
                            activeTrackId = activeTrackId,
                            isPlaying = isPlaying,
                            likedTrackIds = likedTrackIds,
                            downloadedTrackIds = downloadedTrackIds,
                            bottomPadding = bottomPadding,
                            onPlayQueue = onPlayQueue,
                            onStartTrackRadio = onStartTrackRadio,
                            onStartArtistRadio = { onStartArtistRadio(overlayState.detail) },
                            onToggleLike = onToggleLike,
                            onToggleDownload = onToggleDownload,
                            onAddToPlaylist = onAddToPlaylist,
                            onOpenAlbum = onOpenAlbum,
                            onOpenArtist = onOpenArtist
                        )
                    }
                }

                DetailOverlayState.None -> Unit
            }
        }
    }
}

@Composable
private fun AlbumDetailContent(
    album: AlbumDetail,
    isSaved: Boolean,
    activeTrackId: String?,
    isPlaying: Boolean,
    likedTrackIds: Set<String>,
    downloadedTrackIds: Set<String>,
    bottomPadding: Dp,
    onPlayQueue: (List<Track>, Int, Boolean) -> Unit,
    onStartTrackRadio: (Track) -> Unit,
    onToggleSave: () -> Unit,
    onDownloadAlbum: () -> Unit,
    onToggleLike: (Track) -> Unit,
    onToggleDownload: (Track) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onOpenArtist: () -> Unit
) {
    val palette = LocalLiquidPalette.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("album_detail_list"),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "album_hero") {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TrackArtwork(
                    artworkUrl = album.artworkUrl,
                    contentDescription = album.title,
                    cornerRadius = 24.dp,
                    modifier = Modifier.size(196.dp)
                )
                Text(
                    text = album.title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = FrostWhite,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = buildString {
                        append(album.artist)
                        if (album.year.isNotBlank()) append(" · ${album.year}")
                        append(" · ${album.tracks.size} songs")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (album.artistId.isNotBlank()) palette.secondaryGlow else MistSecondary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(enabled = album.artistId.isNotBlank(), onClick = onOpenArtist)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )

                // Primary Actions Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LiquidGlassButton(
                        onClick = { if (album.tracks.isNotEmpty()) onPlayQueue(album.tracks, 0, false) },
                        accentGlow = true,
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .testTag("album_play_all")
                    ) {
                        Row(
                            modifier = Modifier.align(Alignment.Center),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = FrostWhite, modifier = Modifier.size(20.dp))
                            Text("Play", style = MaterialTheme.typography.labelLarge, color = FrostWhite)
                        }
                    }

                    LiquidGlassButton(
                        onClick = { if (album.tracks.isNotEmpty()) onPlayQueue(album.tracks, 0, true) },
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .testTag("album_shuffle")
                    ) {
                        Row(
                            modifier = Modifier.align(Alignment.Center),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Rounded.Shuffle, contentDescription = null, tint = FrostWhite, modifier = Modifier.size(18.dp))
                            Text("Shuffle", style = MaterialTheme.typography.labelLarge, color = FrostWhite)
                        }
                    }

                    LiquidGlassButton(
                        onClick = onToggleSave,
                        accentGlow = isSaved,
                        modifier = Modifier
                            .size(46.dp)
                            .testTag("album_save_button")
                    ) {
                        Icon(
                            imageVector = if (isSaved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                            contentDescription = if (isSaved) "Remove saved album" else "Save album",
                            tint = if (isSaved) palette.primaryGlow else FrostWhite,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }

                    LiquidGlassButton(
                        onClick = onDownloadAlbum,
                        modifier = Modifier
                            .size(46.dp)
                            .testTag("album_download_all")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = "Download album offline",
                            tint = FrostWhite,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
            }
        }

        if (album.tracks.isEmpty()) {
            item(key = "album_empty") {
                LiquidGlassSurface(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp, useRefractionShell = false) {
                    Text(
                        text = "No playable tracks returned for this album.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MistSecondary,
                        modifier = Modifier.padding(18.dp)
                    )
                }
            }
        } else {
            itemsIndexed(album.tracks, key = { idx, t -> "alb_t_${idx}_${t.id}" }) { idx, track ->
                TrackRowItem(
                    track = track,
                    isActive = track.id == activeTrackId,
                    isPlaying = isPlaying && track.id == activeTrackId,
                    isLiked = likedTrackIds.contains(track.id),
                    isDownloaded = downloadedTrackIds.contains(track.id),
                    onClick = { onPlayQueue(album.tracks, idx, false) },
                    onToggleLike = { onToggleLike(track) },
                    onToggleDownload = { onToggleDownload(track) },
                    onAddToPlaylist = { onAddToPlaylist(track) },
                    trailingExtra = {
                        IconButton(onClick = { onStartTrackRadio(track) }, modifier = Modifier.size(38.dp)) {
                            Icon(
                                imageVector = Icons.Rounded.Radio,
                                contentDescription = "Start song radio",
                                tint = palette.secondaryGlow,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ArtistDetailContent(
    artist: ArtistDetail,
    activeTrackId: String?,
    isPlaying: Boolean,
    likedTrackIds: Set<String>,
    downloadedTrackIds: Set<String>,
    bottomPadding: Dp,
    onPlayQueue: (List<Track>, Int, Boolean) -> Unit,
    onStartTrackRadio: (Track) -> Unit,
    onStartArtistRadio: () -> Unit,
    onToggleLike: (Track) -> Unit,
    onToggleDownload: (Track) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onOpenAlbum: (String, MusicSource) -> Unit,
    onOpenArtist: (String, MusicSource) -> Unit
) {
    val palette = LocalLiquidPalette.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("artist_detail_list"),
        contentPadding = PaddingValues(top = 8.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "artist_hero") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TrackArtwork(
                    artworkUrl = artist.thumbnailUrl,
                    contentDescription = artist.name,
                    cornerRadius = 72.dp,
                    modifier = Modifier
                        .size(144.dp)
                        .border(1.5.dp, palette.primaryGlow.copy(alpha = 0.55f), CircleShape)
                )
                Text(
                    text = artist.name,
                    style = MaterialTheme.typography.headlineLarge,
                    color = FrostWhite,
                    textAlign = TextAlign.Center
                )
                if (artist.subscribersText.isNotBlank()) {
                    Text(
                        text = artist.subscribersText,
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.secondaryGlow
                    )
                }
                if (artist.description.isNotBlank()) {
                    Text(
                        text = artist.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MistSecondary,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LiquidGlassButton(
                        onClick = { if (artist.topSongs.isNotEmpty()) onPlayQueue(artist.topSongs, 0, false) },
                        accentGlow = true,
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .testTag("artist_play_top")
                    ) {
                        Row(
                            modifier = Modifier.align(Alignment.Center),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = FrostWhite, modifier = Modifier.size(20.dp))
                            Text("Top Songs", style = MaterialTheme.typography.labelLarge, color = FrostWhite)
                        }
                    }

                    LiquidGlassButton(
                        onClick = onStartArtistRadio,
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .testTag("artist_start_radio")
                    ) {
                        Row(
                            modifier = Modifier.align(Alignment.Center),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Rounded.Radio, contentDescription = null, tint = palette.secondaryGlow, modifier = Modifier.size(18.dp))
                            Text("Artist Radio", style = MaterialTheme.typography.labelLarge, color = FrostWhite)
                        }
                    }
                }
            }
        }

        if (artist.topSongs.isNotEmpty()) {
            item(key = "artist_top_songs_header") {
                Text(
                    text = "Popular Tracks",
                    style = MaterialTheme.typography.headlineMedium,
                    color = FrostWhite,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }

            itemsIndexed(artist.topSongs.take(10), key = { idx, t -> "art_s_${idx}_${t.id}" }) { idx, track ->
                Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                    TrackRowItem(
                        track = track,
                        isActive = track.id == activeTrackId,
                        isPlaying = isPlaying && track.id == activeTrackId,
                        isLiked = likedTrackIds.contains(track.id),
                        isDownloaded = downloadedTrackIds.contains(track.id),
                        onClick = { onPlayQueue(artist.topSongs, idx, false) },
                        onToggleLike = { onToggleLike(track) },
                        onToggleDownload = { onToggleDownload(track) },
                        onAddToPlaylist = { onAddToPlaylist(track) },
                        trailingExtra = {
                            IconButton(onClick = { onStartTrackRadio(track) }, modifier = Modifier.size(38.dp)) {
                                Icon(
                                    imageVector = Icons.Rounded.Radio,
                                    contentDescription = "Start radio",
                                    tint = palette.secondaryGlow,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                    )
                }
            }
        }

        if (artist.albums.isNotEmpty()) {
            item(key = "artist_albums") {
                AlbumCarouselRow(
                    title = "Albums",
                    albums = artist.albums,
                    onOpenAlbum = onOpenAlbum
                )
            }
        }

        if (artist.singles.isNotEmpty()) {
            item(key = "artist_singles") {
                AlbumCarouselRow(
                    title = "Singles & EPs",
                    albums = artist.singles,
                    onOpenAlbum = onOpenAlbum
                )
            }
        }

        if (artist.relatedArtists.isNotEmpty()) {
            item(key = "artist_related") {
                ArtistCarouselRow(
                    title = "Fans Also Like",
                    artists = artist.relatedArtists,
                    onOpenArtist = onOpenArtist
                )
            }
        }
    }
}

@Composable
fun AlbumCarouselRow(
    title: String,
    albums: List<AlbumSummary>,
    onOpenAlbum: (String, MusicSource) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = FrostWhite,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(albums, key = { "alb_${title}_${it.id}" }) { album ->
                LiquidGlassSurface(
                    modifier = Modifier
                        .width(154.dp)
                        .clickable { onOpenAlbum(album.browseId, album.sourceProvider) }
                        .testTag("album_card_${album.id}"),
                    cornerRadius = 20.dp,
                    useRefractionShell = false
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        TrackArtwork(
                            artworkUrl = album.artworkUrl,
                            contentDescription = album.title,
                            cornerRadius = 15.dp,
                            modifier = Modifier.size(134.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = album.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = FrostWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = buildString {
                                append(album.artist.ifBlank { "Album" })
                                if (album.year.isNotBlank()) append(" · ${album.year}")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MistSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ArtistCarouselRow(
    title: String,
    artists: List<ArtistSummary>,
    onOpenArtist: (String, MusicSource) -> Unit
) {
    val palette = LocalLiquidPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = FrostWhite,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(artists, key = { "art_${title}_${it.id}" }) { artist ->
                Column(
                    modifier = Modifier
                        .width(114.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { onOpenArtist(artist.browseId, artist.sourceProvider) }
                        .padding(6.dp)
                        .testTag("artist_card_${artist.id}"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TrackArtwork(
                        artworkUrl = artist.thumbnailUrl,
                        contentDescription = artist.name,
                        cornerRadius = 52.dp,
                        modifier = Modifier
                            .size(96.dp)
                            .border(1.dp, palette.secondaryGlow.copy(alpha = 0.4f), CircleShape)
                    )
                    Text(
                        text = artist.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = FrostWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                    if (artist.subtitle.isNotBlank()) {
                        Text(
                            text = artist.subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MistSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailLoadingBox(message: String) {
    val palette = LocalLiquidPalette.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = palette.secondaryGlow, strokeWidth = 2.5.dp)
            Spacer(modifier = Modifier.height(14.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MistSecondary)
        }
    }
}

@Composable
private fun DetailErrorCard(
    message: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        LiquidGlassSurface(modifier = Modifier.fillMaxWidth(), cornerRadius = 24.dp, useRefractionShell = false) {
            Column(
                modifier = Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(message, style = MaterialTheme.typography.bodyMedium, color = LuminousRose, textAlign = TextAlign.Center)
                LiquidGlassButton(
                    onClick = onRetry,
                    accentGlow = true,
                    modifier = Modifier.height(44.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, tint = FrostWhite, modifier = Modifier.size(18.dp))
                        Text("Retry", style = MaterialTheme.typography.labelLarge, color = FrostWhite)
                    }
                }
            }
        }
    }
}
