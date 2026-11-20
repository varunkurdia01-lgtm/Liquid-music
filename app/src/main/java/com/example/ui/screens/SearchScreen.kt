package com.example.ui.screens

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.SourceType
import com.example.domain.model.Track
import com.example.domain.model.VibeDjResult
import com.example.ui.glass.AnimatedWaveformBars
import com.example.ui.glass.LiquidGlassButton
import com.example.ui.glass.LiquidGlassChip
import com.example.ui.glass.LiquidGlassSurface
import com.example.ui.glass.TrackArtwork
import com.example.ui.theme.ArcticCyan
import com.example.ui.theme.DeepIndigoAurora
import com.example.ui.theme.EmeraldPulse
import com.example.ui.theme.FrostWhite
import com.example.ui.theme.LocalLiquidPalette
import com.example.ui.theme.LuminousRose
import com.example.ui.theme.MistSecondary
import com.example.ui.viewmodel.DiscoveryUiState
import com.example.ui.viewmodel.SearchFilter
import com.example.ui.viewmodel.VibeDjUiState

@Composable
fun SearchScreen(
    uiState: DiscoveryUiState,
    activeTrackId: String?,
    isPlaying: Boolean,
    likedTrackIds: Set<String>,
    downloadedTrackIds: Set<String>,
    bottomPadding: Dp,
    onSearchQueryChange: (String) -> Unit,
    onSearchFilterSelected: (SearchFilter) -> Unit,
    onVibePromptChange: (String) -> Unit,
    onGenerateVibe: (String?) -> Unit,
    onUseOfflineVibe: (String) -> Unit,
    onSaveVibePlaylist: (VibeDjResult) -> Unit,
    onPlayQueue: (List<Track>, Int) -> Unit,
    onToggleLike: (Track) -> Unit,
    onToggleDownload: (Track) -> Unit,
    onAddToPlaylist: (Track) -> Unit
) {
    val palette = LocalLiquidPalette.current
    val vibeIdeas = listOf(
        "Midnight highway synth drive",
        "Heavy gym phonk",
        "Rainy lo-fi coding",
        "Euphoric sunset pop"
    )
    val genres = listOf(
        Triple("Global Pop", "The Weeknd Dua Lipa Pop", Color(0xFF4F46E5)),
        Triple("Hip-Hop & Trap", "Travis Scott Kendrick Rap", Color(0xFFDB2777)),
        Triple("Electronic", "Daft Punk EDM Synthwave", Color(0xFF0891B2)),
        Triple("Phonk & Drift", "Phonk Drift Bass", Color(0xFF7C3AED)),
        Triple("Chill & Lo-Fi", "Lofi Beats Chill Study", Color(0xFF0D9488)),
        Triple("Indie Aurora", "Tame Impala Indie Dream Pop", Color(0xFF2563EB))
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .testTag("search_screen_list"),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "search_header") {
            Column {
                Text(
                    text = "Search & AI Vibe DJ",
                    style = MaterialTheme.typography.headlineLarge,
                    color = FrostWhite
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = {
                        Text(
                            text = stringResource(R.string.search_hint),
                            color = MistSecondary
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Rounded.Search, contentDescription = null, tint = palette.secondaryGlow)
                    },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(Icons.Rounded.Clear, contentDescription = "Clear search", tint = MistSecondary)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(22.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = FrostWhite,
                        unfocusedTextColor = FrostWhite,
                        focusedContainerColor = Color(0x6613192D),
                        unfocusedContainerColor = Color(0x4D101526),
                        focusedBorderColor = palette.primaryGlow.copy(alpha = 0.7f),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.16f),
                        cursorColor = palette.secondaryGlow
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_query_input")
                )
            }
        }

        // Dual Search Results when query is active
        if (uiState.searchQuery.isNotBlank()) {
            item(key = "search_filters") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SearchFilter.entries.forEach { filter ->
                        LiquidGlassChip(
                            label = filter.label,
                            selected = uiState.searchFilter == filter,
                            onClick = { onSearchFilterSelected(filter) },
                            modifier = Modifier.testTag("search_filter_${filter.name.lowercase()}")
                        )
                    }
                }
            }

            if (uiState.isSearching) {
                item(key = "search_loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = palette.secondaryGlow, strokeWidth = 2.5.dp)
                    }
                }
            } else if (uiState.displayedSearchResults.isEmpty()) {
                item(key = "search_empty") {
                    LiquidGlassSurface(modifier = Modifier.fillMaxWidth(), cornerRadius = 22.dp) {
                        Text(
                            text = "No tracks matched \"${uiState.searchQuery}\" in ${uiState.searchFilter.label}.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MistSecondary,
                            modifier = Modifier.padding(20.dp)
                        )
                    }
                }
            } else {
                val results = uiState.displayedSearchResults
                itemsIndexed(results, key = { _, t -> "search_${t.id}" }) { idx, track ->
                    TrackRowItem(
                        track = track,
                        isActive = track.id == activeTrackId,
                        isPlaying = isPlaying && track.id == activeTrackId,
                        isLiked = likedTrackIds.contains(track.id),
                        isDownloaded = downloadedTrackIds.contains(track.id),
                        onClick = { onPlayQueue(results, idx) },
                        onToggleLike = { onToggleLike(track) },
                        onToggleDownload = { onToggleDownload(track) },
                        onAddToPlaylist = { onAddToPlaylist(track) }
                    )
                }
            }
        } else {
            // Live Gemini AI Vibe DJ Hero Card
            item(key = "vibe_dj_card") {
                LiquidGlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 26.dp,
                    tintColor = DeepIndigoAurora,
                    surfaceAlpha = 0.56f,
                    highlightAccent = palette.secondaryGlow
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(palette.secondaryGlow.copy(alpha = 0.22f))
                                    .border(1.dp, palette.primaryGlow.copy(alpha = 0.5f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AutoAwesome,
                                    contentDescription = null,
                                    tint = palette.primaryGlow,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = stringResource(R.string.vibe_dj_title),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = FrostWhite
                                )
                                Text(
                                    text = "Live Gemini 3.8 Flash playlist curator",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = palette.secondaryGlow
                                )
                            }
                        }

                        OutlinedTextField(
                            value = uiState.vibePrompt,
                            onValueChange = onVibePromptChange,
                            placeholder = {
                                Text(stringResource(R.string.vibe_dj_hint), color = MistSecondary, style = MaterialTheme.typography.bodyMedium)
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(18.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = FrostWhite,
                                unfocusedTextColor = FrostWhite,
                                focusedContainerColor = Color(0x660B1022),
                                unfocusedContainerColor = Color(0x4D0B1022),
                                focusedBorderColor = palette.secondaryGlow,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.16f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("vibe_dj_prompt_input")
                        )

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(vibeIdeas, key = { it }) { idea ->
                                LiquidGlassChip(
                                    label = idea,
                                    selected = uiState.vibePrompt == idea,
                                    onClick = { onGenerateVibe(idea) }
                                )
                            }
                        }

                        LiquidGlassButton(
                            onClick = { onGenerateVibe(null) },
                            accentGlow = true,
                            enabled = uiState.vibePrompt.isNotBlank() && uiState.vibeState !is VibeDjUiState.Loading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("vibe_dj_generate_button")
                        ) {
                            Row(
                                modifier = Modifier.align(Alignment.Center),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (uiState.vibeState is VibeDjUiState.Loading) {
                                    CircularProgressIndicator(
                                        color = FrostWhite,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text("Curating with Gemini…", color = FrostWhite, style = MaterialTheme.typography.labelLarge)
                                } else {
                                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = FrostWhite, modifier = Modifier.size(18.dp))
                                    Text(stringResource(R.string.vibe_dj_generate), color = FrostWhite, style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }

                        // Vibe DJ State Output
                        when (val vibe = uiState.vibeState) {
                            is VibeDjUiState.Error -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color(0x552B1120))
                                        .border(1.dp, LuminousRose.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = vibe.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = FrostWhite
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        LiquidGlassChip(
                                            label = "Retry Gemini",
                                            selected = true,
                                            onClick = { onGenerateVibe(vibe.lastPrompt) }
                                        )
                                        LiquidGlassChip(
                                            label = "Use Offline Engine",
                                            selected = false,
                                            onClick = { onUseOfflineVibe(vibe.lastPrompt) }
                                        )
                                    }
                                }
                            }
                            is VibeDjUiState.Success -> {
                                VibeResultPreview(
                                    result = vibe.result,
                                    savedToLibrary = vibe.savedToLibrary,
                                    onPlayAll = { onPlayQueue(vibe.result.tracks, 0) },
                                    onSave = { onSaveVibePlaylist(vibe.result) }
                                )
                            }
                            else -> Unit
                        }
                    }
                }
            }

            // Colorful Glass Genre Browse Grid
            item(key = "genre_browse_header") {
                Text(
                    text = "Browse Atmospheres",
                    style = MaterialTheme.typography.headlineMedium,
                    color = FrostWhite
                )
            }

            item(key = "genre_browse_grid") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    genres.chunked(2).forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            rowItems.forEach { (label, query, color) ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(86.dp)
                                        .clip(RoundedCornerShape(22.dp))
                                        .background(
                                            Brush.linearGradient(
                                                listOf(color.copy(alpha = 0.65f), Color(0xAA0C1124))
                                            )
                                        )
                                        .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(22.dp))
                                        .clickable { onSearchQueryChange(query) }
                                        .padding(14.dp)
                                        .testTag("genre_tile_${label.lowercase().replace(" ", "_")}"),
                                    contentAlignment = Alignment.BottomStart
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = FrostWhite
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VibeResultPreview(
    result: VibeDjResult,
    savedToLibrary: Boolean,
    onPlayAll: () -> Unit,
    onSave: () -> Unit
) {
    val palette = LocalLiquidPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0x66090D1C))
            .border(1.dp, palette.primaryGlow.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(result.title, style = MaterialTheme.typography.titleLarge, color = FrostWhite)
                Text(result.manifesto, style = MaterialTheme.typography.bodySmall, color = MistSecondary)
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (result.poweredByGemini) ArcticCyan.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.1f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (result.poweredByGemini) "GEMINI LIVE" else "OFFLINE VIBE",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.primaryGlow
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            result.moodTags.take(3).forEach { tag ->
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(tag, style = MaterialTheme.typography.labelSmall, color = FrostWhite)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LiquidGlassButton(
                onClick = onPlayAll,
                accentGlow = true,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .testTag("vibe_play_all_button")
            ) {
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = FrostWhite, modifier = Modifier.size(18.dp))
                    Text("Play ${result.tracks.size} Tracks", style = MaterialTheme.typography.labelLarge, color = FrostWhite)
                }
            }

            LiquidGlassButton(
                onClick = onSave,
                enabled = !savedToLibrary,
                modifier = Modifier
                    .height(44.dp)
                    .testTag("vibe_save_playlist_button")
            ) {
                Row(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (savedToLibrary) Icons.Rounded.Check else Icons.Rounded.BookmarkAdd,
                        contentDescription = null,
                        tint = if (savedToLibrary) EmeraldPulse else FrostWhite,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = if (savedToLibrary) "Saved" else "Save",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (savedToLibrary) EmeraldPulse else FrostWhite
                    )
                }
            }
        }
    }
}

@Composable
fun TrackRowItem(
    track: Track,
    isActive: Boolean,
    isPlaying: Boolean,
    isLiked: Boolean,
    isDownloaded: Boolean,
    onClick: () -> Unit,
    onToggleLike: () -> Unit,
    onToggleDownload: () -> Unit,
    onAddToPlaylist: () -> Unit,
    trailingExtra: (@Composable () -> Unit)? = null
) {
    val palette = LocalLiquidPalette.current
    LiquidGlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("track_row_${track.id}"),
        cornerRadius = 18.dp,
        surfaceAlpha = if (isActive) 0.72f else 0.48f,
        highlightAccent = if (isActive) palette.secondaryGlow else null,
        useRefractionShell = false
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TrackArtwork(
                artworkUrl = track.artworkUrl,
                contentDescription = track.title,
                cornerRadius = 12.dp,
                modifier = Modifier.size(52.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isActive) palette.primaryGlow else FrostWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (track.sourceType == SourceType.LOCAL) {
                        Text(
                            text = "LOCAL",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.secondaryGlow
                        )
                    }
                }
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MistSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (isActive) {
                AnimatedWaveformBars(
                    isPlaying = isPlaying,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )
            }

            IconButton(onClick = onToggleLike, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = "Like track",
                    tint = if (isLiked) LuminousRose else MistSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            if (track.sourceType == SourceType.ONLINE) {
                IconButton(onClick = onToggleDownload, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = if (isDownloaded) Icons.Rounded.DownloadDone else Icons.Rounded.Download,
                        contentDescription = "Download offline",
                        tint = if (isDownloaded) EmeraldPulse else MistSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            IconButton(onClick = onAddToPlaylist, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.Rounded.PlaylistAdd,
                    contentDescription = "Add to playlist",
                    tint = MistSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            trailingExtra?.invoke()
        }
    }
}
