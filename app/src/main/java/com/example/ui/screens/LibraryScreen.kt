package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.AlbumSummary
import com.example.domain.model.MusicSource
import com.example.domain.model.Playlist
import com.example.domain.model.Track
import com.example.ui.glass.LiquidGlassButton
import com.example.ui.glass.LiquidGlassChip
import com.example.ui.glass.LiquidGlassSurface
import com.example.ui.glass.TrackArtwork
import com.example.ui.theme.DeepVoid
import com.example.ui.theme.FrostWhite
import com.example.ui.theme.LocalLiquidPalette
import com.example.ui.theme.LuminousRose
import com.example.ui.theme.MistSecondary

private enum class LibrarySegment(val label: String) {
    PLAYLISTS("Playlists"),
    LIKED("Liked Songs"),
    ALBUMS("Saved Albums"),
    DOWNLOADED("Downloads"),
    LOCAL("Local Files")
}

@Composable
fun LibraryScreen(
    playlists: List<Playlist>,
    likedTracks: List<Track>,
    savedAlbums: List<AlbumSummary>,
    downloadedTracks: List<Track>,
    localTracks: List<Track>,
    hasAudioPermission: Boolean,
    requiredAudioPermission: String,
    transferStatus: String?,
    activeTrackId: String?,
    isPlaying: Boolean,
    likedTrackIds: Set<String>,
    downloadedTrackIds: Set<String>,
    bottomPadding: Dp,
    onAudioPermissionGranted: () -> Unit,
    onCreatePlaylist: (name: String, description: String, coverUri: String) -> Unit,
    onUpdatePlaylist: (Playlist, String, String, String) -> Unit,
    onDeletePlaylist: (Long) -> Unit,
    onRemoveTrackFromPlaylist: (Long, String) -> Unit,
    onExportPlaylistM3u8: (Playlist, Uri) -> Unit,
    onImportPlaylistUri: (Uri) -> Unit,
    onClearTransferStatus: () -> Unit,
    onOpenAlbum: (String, MusicSource) -> Unit,
    onPlayQueue: (List<Track>, Int) -> Unit,
    onToggleLike: (Track) -> Unit,
    onToggleDownload: (Track) -> Unit,
    onAddToPlaylist: (Track) -> Unit
) {
    val palette = LocalLiquidPalette.current
    var selectedSegment by rememberSaveable { mutableStateOf(LibrarySegment.PLAYLISTS) }
    var openedPlaylistId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showPlaylistEditor by remember { mutableStateOf(false) }
    var editingPlaylist by remember { mutableStateOf<Playlist?>(null) }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) onAudioPermissionGranted()
    }

    // Zero-permission SAF Playlist Import (.m3u8 / .json)
    val importPlaylistLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onImportPlaylistUri(uri)
    }

    val openedPlaylist = playlists.firstOrNull { it.id == openedPlaylistId }

    if (openedPlaylist != null) {
        BackHandler { openedPlaylistId = null }
        val exportM3u8Launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("audio/x-mpegurl")
        ) { uri ->
            if (uri != null) onExportPlaylistM3u8(openedPlaylist, uri)
        }
        PlaylistDetailView(
            playlist = openedPlaylist,
            activeTrackId = activeTrackId,
            isPlaying = isPlaying,
            likedTrackIds = likedTrackIds,
            downloadedTrackIds = downloadedTrackIds,
            bottomPadding = bottomPadding,
            onBack = { openedPlaylistId = null },
            onEdit = {
                editingPlaylist = openedPlaylist
                showPlaylistEditor = true
            },
            onExportM3u8 = {
                val safeName = openedPlaylist.name.replace(Regex("[^a-zA-Z0-9_\\- ]"), "").ifBlank { "playlist" }
                exportM3u8Launcher.launch("$safeName.m3u8")
            },
            onDelete = {
                onDeletePlaylist(openedPlaylist.id)
                openedPlaylistId = null
            },
            onPlayQueue = onPlayQueue,
            onToggleLike = onToggleLike,
            onToggleDownload = onToggleDownload,
            onAddToPlaylist = onAddToPlaylist,
            onRemoveTrack = { trackId -> onRemoveTrackFromPlaylist(openedPlaylist.id, trackId) }
        )
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .testTag("library_screen_list"),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = bottomPadding),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "library_header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Your Library", style = MaterialTheme.typography.headlineLarge, color = FrostWhite)
                        Text(
                            "${playlists.size} playlists · ${likedTracks.size} liked · ${savedAlbums.size} albums",
                            style = MaterialTheme.typography.bodySmall,
                            color = MistSecondary
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LiquidGlassButton(
                            onClick = { importPlaylistLauncher.launch(arrayOf("*/*")) },
                            cornerRadius = 18.dp,
                            modifier = Modifier
                                .size(44.dp)
                                .testTag("import_playlist_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.FileUpload,
                                contentDescription = "Import M3U8 or JSON playlist",
                                tint = palette.secondaryGlow,
                                modifier = Modifier.align(Alignment.Center).size(20.dp)
                            )
                        }
                        LiquidGlassButton(
                            onClick = {
                                editingPlaylist = null
                                showPlaylistEditor = true
                            },
                            accentGlow = true,
                            cornerRadius = 18.dp,
                            modifier = Modifier
                                .height(44.dp)
                                .testTag("create_playlist_button")
                        ) {
                            Row(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null, tint = FrostWhite, modifier = Modifier.size(18.dp))
                                Text("New", style = MaterialTheme.typography.labelLarge, color = FrostWhite)
                            }
                        }
                    }
                }
            }

            if (!transferStatus.isNullOrBlank()) {
                item(key = "transfer_status_banner") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0x55141E36))
                            .border(1.dp, palette.primaryGlow.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                            .clickable(onClick = onClearTransferStatus)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(transferStatus, style = MaterialTheme.typography.bodySmall, color = FrostWhite, modifier = Modifier.weight(1f))
                        Text("OK", style = MaterialTheme.typography.labelSmall, color = palette.primaryGlow)
                    }
                }
            }

            item(key = "library_segments") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(LibrarySegment.entries, key = { it.name }) { segment ->
                        LiquidGlassChip(
                            label = segment.label,
                            selected = selectedSegment == segment,
                            onClick = { selectedSegment = segment },
                            modifier = Modifier.testTag("library_segment_${segment.name.lowercase()}")
                        )
                    }
                }
            }

            when (selectedSegment) {
                LibrarySegment.PLAYLISTS -> {
                    if (playlists.isEmpty()) {
                        item(key = "empty_playlists") {
                            EmptyLibraryCard(
                                title = "Create or import a playlist",
                                subtitle = "Mix YouTube Music, JioSaavn & local FLAC/MP3 tracks, or import an M3U8/JSON playlist file.",
                                actionLabel = "Create Playlist",
                                onAction = {
                                    editingPlaylist = null
                                    showPlaylistEditor = true
                                }
                            )
                        }
                    } else {
                        items(playlists, key = { "pl_${it.id}" }) { playlist ->
                            LiquidGlassSurface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { openedPlaylistId = playlist.id }
                                    .testTag("playlist_card_${playlist.id}"),
                                cornerRadius = 20.dp,
                                useRefractionShell = false
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TrackArtwork(
                                        artworkUrl = playlist.coverArtUri.ifBlank {
                                            playlist.tracks.firstOrNull()?.artworkUrl.orEmpty()
                                        },
                                        contentDescription = playlist.name,
                                        cornerRadius = 14.dp,
                                        modifier = Modifier.size(62.dp)
                                    )
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = playlist.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = FrostWhite,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = playlist.description.ifBlank { "${playlist.tracks.size} tracks" },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MistSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${playlist.tracks.size} tracks",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = palette.secondaryGlow
                                        )
                                    }
                                    if (playlist.tracks.isNotEmpty()) {
                                        IconButton(onClick = { onPlayQueue(playlist.tracks, 0) }) {
                                            Icon(Icons.Rounded.PlayArrow, contentDescription = "Play playlist", tint = palette.primaryGlow)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                LibrarySegment.LIKED -> {
                    if (likedTracks.isEmpty()) {
                        item(key = "empty_liked") {
                            EmptyLibraryCard(
                                title = "No Liked Songs yet",
                                subtitle = "Tap the heart on any streamed or local track to build your personal favorites collection."
                            )
                        }
                    } else {
                        item(key = "play_all_liked") {
                            PlayAllHeader(count = likedTracks.size, onPlay = { onPlayQueue(likedTracks, 0) }, onShuffle = { onPlayQueue(likedTracks.shuffled(), 0) })
                        }
                        itemsIndexed(likedTracks, key = { idx, t -> "liked_${idx}_${t.id}" }) { idx, track ->
                            TrackRowItem(
                                track = track,
                                isActive = track.id == activeTrackId,
                                isPlaying = isPlaying && track.id == activeTrackId,
                                isLiked = true,
                                isDownloaded = downloadedTrackIds.contains(track.id),
                                onClick = { onPlayQueue(likedTracks, idx) },
                                onToggleLike = { onToggleLike(track) },
                                onToggleDownload = { onToggleDownload(track) },
                                onAddToPlaylist = { onAddToPlaylist(track) }
                            )
                        }
                    }
                }

                LibrarySegment.ALBUMS -> {
                    if (savedAlbums.isEmpty()) {
                        item(key = "empty_saved_albums") {
                            EmptyLibraryCard(
                                title = "No Saved Albums yet",
                                subtitle = "Open any album from Home or Search and tap the bookmark icon to keep it in your Library."
                            )
                        }
                    } else {
                        items(savedAlbums, key = { "saved_alb_${it.id}" }) { album ->
                            LiquidGlassSurface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenAlbum(album.browseId, album.sourceProvider) }
                                    .testTag("saved_album_${album.id}"),
                                cornerRadius = 20.dp,
                                useRefractionShell = false
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TrackArtwork(
                                        artworkUrl = album.artworkUrl,
                                        contentDescription = album.title,
                                        cornerRadius = 14.dp,
                                        modifier = Modifier.size(60.dp)
                                    )
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(album.title, style = MaterialTheme.typography.titleMedium, color = FrostWhite, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            text = listOf(album.artist, album.year).filter { it.isNotBlank() }.joinToString(" · "),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MistSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(album.sourceProvider.displayName, style = MaterialTheme.typography.labelSmall, color = palette.secondaryGlow)
                                    }
                                }
                            }
                        }
                    }
                }

                LibrarySegment.DOWNLOADED -> {
                    if (downloadedTracks.isEmpty()) {
                        item(key = "empty_downloads") {
                            EmptyLibraryCard(
                                title = "No Offline Downloads",
                                subtitle = "Tap the download button on any track, album, or in Now Playing to save it locally for offline listening."
                            )
                        }
                    } else {
                        item(key = "play_all_downloads") {
                            PlayAllHeader(count = downloadedTracks.size, onPlay = { onPlayQueue(downloadedTracks, 0) }, onShuffle = { onPlayQueue(downloadedTracks.shuffled(), 0) })
                        }
                        itemsIndexed(downloadedTracks, key = { idx, t -> "dl_${idx}_${t.id}" }) { idx, track ->
                            TrackRowItem(
                                track = track,
                                isActive = track.id == activeTrackId,
                                isPlaying = isPlaying && track.id == activeTrackId,
                                isLiked = likedTrackIds.contains(track.id),
                                isDownloaded = true,
                                onClick = { onPlayQueue(downloadedTracks, idx) },
                                onToggleLike = { onToggleLike(track) },
                                onToggleDownload = { onToggleDownload(track) },
                                onAddToPlaylist = { onAddToPlaylist(track) }
                            )
                        }
                    }
                }

                LibrarySegment.LOCAL -> {
                    if (!hasAudioPermission) {
                        item(key = "local_permission_card") {
                            LiquidGlassSurface(modifier = Modifier.fillMaxWidth(), cornerRadius = 24.dp, useRefractionShell = false) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.AudioFile,
                                        contentDescription = null,
                                        tint = palette.secondaryGlow,
                                        modifier = Modifier.size(34.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.local_permission_title),
                                        style = MaterialTheme.typography.titleLarge,
                                        color = FrostWhite
                                    )
                                    Text(
                                        text = stringResource(R.string.local_permission_body),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MistSecondary
                                    )
                                    LiquidGlassButton(
                                        onClick = { audioPermissionLauncher.launch(requiredAudioPermission) },
                                        accentGlow = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("grant_local_audio_permission_button")
                                    ) {
                                        Text(
                                            text = stringResource(R.string.local_permission_grant),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = FrostWhite,
                                            modifier = Modifier.align(Alignment.Center)
                                        )
                                    }
                                }
                            }
                        }
                    } else if (localTracks.isEmpty()) {
                        item(key = "empty_local") {
                            EmptyLibraryCard(
                                title = "No Audio Files Detected",
                                subtitle = "Add MP3, FLAC, WAV, M4A, or OGG files to your device Music folder and tap rescan.",
                                actionLabel = "Rescan Device",
                                onAction = onAudioPermissionGranted
                            )
                        }
                    } else {
                        item(key = "play_all_local") {
                            PlayAllHeader(count = localTracks.size, onPlay = { onPlayQueue(localTracks, 0) }, onShuffle = { onPlayQueue(localTracks.shuffled(), 0) })
                        }
                        itemsIndexed(localTracks, key = { idx, t -> "local_${idx}_${t.id}" }) { idx, track ->
                            TrackRowItem(
                                track = track,
                                isActive = track.id == activeTrackId,
                                isPlaying = isPlaying && track.id == activeTrackId,
                                isLiked = likedTrackIds.contains(track.id),
                                isDownloaded = false,
                                onClick = { onPlayQueue(localTracks, idx) },
                                onToggleLike = { onToggleLike(track) },
                                onToggleDownload = {},
                                onAddToPlaylist = { onAddToPlaylist(track) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showPlaylistEditor) {
        PlaylistEditorDialog(
            existing = editingPlaylist,
            onDismiss = { showPlaylistEditor = false },
            onSave = { name, desc, coverUri ->
                val target = editingPlaylist
                if (target == null) {
                    onCreatePlaylist(name, desc, coverUri)
                } else {
                    onUpdatePlaylist(target, name, desc, coverUri)
                }
                showPlaylistEditor = false
            }
        )
    }
}

@Composable
private fun PlaylistDetailView(
    playlist: Playlist,
    activeTrackId: String?,
    isPlaying: Boolean,
    likedTrackIds: Set<String>,
    downloadedTrackIds: Set<String>,
    bottomPadding: Dp,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onExportM3u8: () -> Unit,
    onDelete: () -> Unit,
    onPlayQueue: (List<Track>, Int) -> Unit,
    onToggleLike: (Track) -> Unit,
    onToggleDownload: (Track) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onRemoveTrack: (String) -> Unit
) {
    val palette = LocalLiquidPalette.current
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .testTag("playlist_detail_screen"),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "detail_topbar") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("playlist_detail_back")) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = FrostWhite)
                }
                Row {
                    IconButton(onClick = onExportM3u8, modifier = Modifier.testTag("playlist_detail_export")) {
                        Icon(Icons.Rounded.FileDownload, contentDescription = "Export M3U8 playlist", tint = palette.secondaryGlow)
                    }
                    IconButton(onClick = onEdit, modifier = Modifier.testTag("playlist_detail_edit")) {
                        Icon(Icons.Rounded.Edit, contentDescription = "Edit playlist", tint = FrostWhite)
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.testTag("playlist_detail_delete")) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Delete playlist", tint = LuminousRose)
                    }
                }
            }
        }

        item(key = "detail_hero") {
            LiquidGlassSurface(modifier = Modifier.fillMaxWidth(), cornerRadius = 26.dp, useRefractionShell = false) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TrackArtwork(
                        artworkUrl = playlist.coverArtUri.ifBlank {
                            playlist.tracks.firstOrNull()?.artworkUrl.orEmpty()
                        },
                        contentDescription = playlist.name,
                        cornerRadius = 20.dp,
                        modifier = Modifier.size(150.dp)
                    )
                    Text(playlist.name, style = MaterialTheme.typography.headlineMedium, color = FrostWhite)
                    if (playlist.description.isNotBlank()) {
                        Text(playlist.description, style = MaterialTheme.typography.bodyMedium, color = MistSecondary)
                    }
                    if (playlist.tracks.isNotEmpty()) {
                        PlayAllHeader(
                            count = playlist.tracks.size,
                            onPlay = { onPlayQueue(playlist.tracks, 0) },
                            onShuffle = { onPlayQueue(playlist.tracks.shuffled(), 0) }
                        )
                    }
                }
            }
        }

        if (playlist.tracks.isEmpty()) {
            item(key = "detail_empty") {
                EmptyLibraryCard(
                    title = "Playlist is empty",
                    subtitle = "Tap + on any track in Home, Search, or Local Files to add songs here."
                )
            }
        } else {
            itemsIndexed(playlist.tracks, key = { idx, t -> "pltrack_${idx}_${t.id}" }) { idx, track ->
                TrackRowItem(
                    track = track,
                    isActive = track.id == activeTrackId,
                    isPlaying = isPlaying && track.id == activeTrackId,
                    isLiked = likedTrackIds.contains(track.id),
                    isDownloaded = downloadedTrackIds.contains(track.id),
                    onClick = { onPlayQueue(playlist.tracks, idx) },
                    onToggleLike = { onToggleLike(track) },
                    onToggleDownload = { onToggleDownload(track) },
                    onAddToPlaylist = { onAddToPlaylist(track) },
                    trailingExtra = {
                        IconButton(onClick = { onRemoveTrack(track.id) }, modifier = Modifier.size(38.dp)) {
                            Icon(
                                imageVector = Icons.Rounded.RemoveCircleOutline,
                                contentDescription = "Remove from playlist",
                                tint = LuminousRose.copy(alpha = 0.85f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun PlayAllHeader(
    count: Int,
    onPlay: () -> Unit,
    onShuffle: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LiquidGlassButton(
            onClick = onPlay,
            accentGlow = true,
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
        ) {
            Row(
                modifier = Modifier.align(Alignment.Center),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = FrostWhite, modifier = Modifier.size(18.dp))
                Text("Play ($count)", style = MaterialTheme.typography.labelLarge, color = FrostWhite)
            }
        }
        LiquidGlassButton(
            onClick = onShuffle,
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
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
    }
}

@Composable
private fun EmptyLibraryCard(
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    LiquidGlassSurface(modifier = Modifier.fillMaxWidth(), cornerRadius = 24.dp, useRefractionShell = false) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = FrostWhite)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MistSecondary)
            if (actionLabel != null && onAction != null) {
                Spacer(modifier = Modifier.height(4.dp))
                LiquidGlassButton(
                    onClick = onAction,
                    accentGlow = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                ) {
                    Text(actionLabel, style = MaterialTheme.typography.labelLarge, color = FrostWhite, modifier = Modifier.align(Alignment.Center))
                }
            }
        }
    }
}

@Composable
private fun PlaylistEditorDialog(
    existing: Playlist?,
    onDismiss: () -> Unit,
    onSave: (name: String, description: String, coverUri: String) -> Unit
) {
    val palette = LocalLiquidPalette.current
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var description by remember { mutableStateOf(existing?.description.orEmpty()) }
    var coverUri by remember { mutableStateOf(existing?.coverArtUri.orEmpty()) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            coverUri = uri.toString()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DeepVoid,
        titleContentColor = FrostWhite,
        textContentColor = FrostWhite,
        title = { Text(if (existing == null) "New Playlist" else "Edit Playlist") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TrackArtwork(
                        artworkUrl = coverUri,
                        contentDescription = "Playlist cover",
                        cornerRadius = 16.dp,
                        modifier = Modifier.size(68.dp)
                    )
                    LiquidGlassButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("playlist_pick_cover_button")
                    ) {
                        Row(
                            modifier = Modifier.align(Alignment.Center),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Rounded.Image, contentDescription = null, tint = palette.secondaryGlow, modifier = Modifier.size(18.dp))
                            Text("Choose Cover Art", style = MaterialTheme.typography.labelLarge, color = FrostWhite)
                        }
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Playlist Name", color = MistSecondary) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = FrostWhite,
                        unfocusedTextColor = FrostWhite,
                        focusedBorderColor = palette.primaryGlow,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("playlist_name_input")
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional)", color = MistSecondary) },
                    maxLines = 2,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = FrostWhite,
                        unfocusedTextColor = FrostWhite,
                        focusedBorderColor = palette.primaryGlow,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, description, coverUri) },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("playlist_save_confirm")
            ) {
                Text("Save", color = palette.primaryGlow)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MistSecondary)
            }
        }
    )
}
