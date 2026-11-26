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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.domain.model.BrowseLayoutType
import com.example.domain.model.BrowseSection
import com.example.domain.model.MusicSource
import com.example.domain.model.SourceType
import com.example.domain.model.Track
import com.example.domain.model.UserProfile
import com.example.ui.components.EchoHomeModePill
import com.example.ui.components.EchoSurfaceCard
import com.example.ui.glass.AnimatedWaveformBars
import com.example.ui.glass.LiquidGlassButton
import com.example.ui.glass.LiquidGlassChip
import com.example.ui.glass.LiquidGlassIconButton
import com.example.ui.glass.LiquidGlassSurface
import com.example.ui.glass.TrackArtwork
import com.example.ui.theme.FrostWhite
import com.example.ui.theme.LocalLiquidPalette
import com.example.ui.theme.LuminousRose
import com.example.ui.theme.MistSecondary
import com.example.ui.viewmodel.DiscoveryUiState
import java.util.Calendar

@Composable
fun HomeScreen(
    uiState: DiscoveryUiState,
    userProfile: UserProfile,
    activeTrackId: String?,
    isPlaying: Boolean,
    likedTrackIds: Set<String>,
    downloadedTrackIds: Set<String>,
    bottomPadding: Dp,
    onMoodSelected: (String) -> Unit,
    onPlayQueue: (List<Track>, Int) -> Unit,
    onStartRadio: (Track) -> Unit,
    onToggleLike: (Track) -> Unit,
    onToggleDownload: (Track) -> Unit,
    onAddTrackToPlaylist: (Track) -> Unit,
    onOpenAlbum: (String, MusicSource) -> Unit,
    onOpenArtist: (String, MusicSource) -> Unit,
    onOpenProfile: () -> Unit,
    onOpenSoundLab: () -> Unit,
    onSurpriseMe: () -> Unit = {},
    onOpenMusicFlow: () -> Unit = {},
    onRefresh: () -> Unit
) {
    val palette = LocalLiquidPalette.current
    val greeting = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> "Good Morning"
            in 12..16 -> "Good Afternoon"
            in 17..21 -> "Good Evening"
            else -> "Late Night Aura"
        }
    }
    val moodLabels = remember(uiState.moods) {
        val dynamic = uiState.moods.map { it.title }
        (listOf("All") + dynamic + listOf("Local Files")).distinct()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .testTag("home_screen_list"),
        contentPadding = PaddingValues(top = 16.dp, bottom = bottomPadding)
    ) {
        // Header Row: Greeting + Active Provider Status + Surprise Me + Sound Lab + Settings Button
        item(key = "home_header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = greeting.uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            color = palette.secondaryGlow
                        )
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color(0x55131B32))
                                .border(1.dp, palette.primaryGlow.copy(alpha = 0.28f), CircleShape)
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = uiState.activeCatalogSource.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.primaryGlow
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = userProfile.displayName,
                        style = MaterialTheme.typography.headlineLarge,
                        color = FrostWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LiquidGlassIconButton(
                        icon = Icons.Rounded.Shuffle,
                        contentDescription = "Surprise Me Shuffle",
                        onClick = onSurpriseMe,
                        size = 42.dp,
                        iconSize = 19.dp,
                        iconTint = palette.secondaryGlow,
                        modifier = Modifier.testTag("home_surprise_me_button")
                    )

                    LiquidGlassIconButton(
                        icon = Icons.Rounded.Refresh,
                        contentDescription = "Refresh Discovery",
                        onClick = onRefresh,
                        size = 42.dp,
                        iconSize = 19.dp,
                        iconTint = FrostWhite,
                        modifier = Modifier.testTag("home_refresh_button")
                    )

                    LiquidGlassIconButton(
                        icon = Icons.Rounded.Tune,
                        contentDescription = "Open Sound Lab",
                        onClick = onOpenSoundLab,
                        size = 42.dp,
                        iconSize = 19.dp,
                        iconTint = palette.primaryGlow,
                        modifier = Modifier.testTag("home_sound_lab_shortcut")
                    )

                    LiquidGlassIconButton(
                        icon = Icons.Rounded.Settings,
                        contentDescription = "Open Echo Settings",
                        onClick = onOpenProfile,
                        size = 42.dp,
                        iconSize = 20.dp,
                        iconTint = FrostWhite,
                        tinted = true,
                        modifier = Modifier.testTag("home_settings_button")
                    )
                }
            }
        }

        // Echo Home | Flow Mode Switcher Row
        item(key = "home_mode_switcher") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                EchoHomeModePill(
                    isFlowActive = false,
                    onSelectHome = {},
                    onSelectFlow = onOpenMusicFlow
                )
                Text(
                    text = "Swipe Flow for vertical discovery",
                    style = MaterialTheme.typography.labelSmall,
                    color = MistSecondary
                )
            }
        }

        // Provider Notice Pill
        if (!uiState.providerNotice.isNullOrBlank()) {
            item(key = "provider_notice") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0x4D141C32))
                        .border(1.dp, palette.secondaryGlow.copy(alpha = 0.32f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Rounded.CloudDone, contentDescription = null, tint = palette.secondaryGlow, modifier = Modifier.size(16.dp))
                    Text(
                        text = uiState.providerNotice,
                        style = MaterialTheme.typography.bodySmall,
                        color = MistSecondary
                    )
                }
            }
        }

        // Dynamic Mood & Genre Glass Chips (Echo Music Chip Cloud)
        if (uiState.showMoodAndGenres) {
            item(key = "home_mood_chips") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(moodLabels, key = { it }) { mood ->
                        LiquidGlassChip(
                            label = mood,
                            selected = uiState.selectedHomeMood == mood,
                            onClick = { onMoodSelected(mood) },
                            modifier = Modifier.testTag("mood_chip_${mood.lowercase().replace(" ", "_")}")
                        )
                    }
                }
            }
        } else {
            item(key = "home_spacer") {
                Spacer(modifier = Modifier.height(14.dp))
            }
        }

        if (uiState.isLoadingHome) {
            item(key = "home_loading") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = palette.secondaryGlow, strokeWidth = 2.5.dp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Fetching live YouTube Music Home, Charts & New Releases…",
                            color = MistSecondary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        } else if (uiState.homeError != null && uiState.browseSections.isEmpty()) {
            item(key = "home_error") {
                LiquidGlassSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    cornerRadius = 24.dp,
                    useRefractionShell = false
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Could not reach streaming providers", style = MaterialTheme.typography.titleLarge, color = FrostWhite)
                        Text(uiState.homeError, style = MaterialTheme.typography.bodySmall, color = LuminousRose)
                        LiquidGlassButton(
                            onClick = onRefresh,
                            accentGlow = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("home_retry_button")
                        ) {
                            Text("Retry Catalog", style = MaterialTheme.typography.labelLarge, color = FrostWhite, modifier = Modifier.align(Alignment.Center))
                        }
                    }
                }
            }
        } else {
            val selectedMood = uiState.selectedHomeMood

            // Specific Mood Filter Feed
            if (selectedMood != "All" && selectedMood != "Local Files") {
                item(key = "mood_header") {
                    SectionHeader(
                        title = "$selectedMood Mix",
                        subtitle = "Live tracks curated from ${uiState.activeCatalogSource.displayName}"
                    )
                }
                if (uiState.isLoadingMood) {
                    item(key = "mood_loading") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = palette.secondaryGlow, strokeWidth = 2.5.dp)
                        }
                    }
                } else {
                    itemsIndexed(uiState.moodTracks, key = { idx, t -> "mood_${idx}_${t.id}" }) { idx, track ->
                        Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 5.dp)) {
                            TrackRowItem(
                                track = track,
                                isActive = track.id == activeTrackId,
                                isPlaying = isPlaying && track.id == activeTrackId,
                                isLiked = likedTrackIds.contains(track.id),
                                isDownloaded = downloadedTrackIds.contains(track.id),
                                onClick = { onPlayQueue(uiState.moodTracks, idx) },
                                onToggleLike = { onToggleLike(track) },
                                onToggleDownload = { onToggleDownload(track) },
                                onAddToPlaylist = { onAddTrackToPlaylist(track) },
                                trailingExtra = {
                                    IconButton(onClick = { onStartRadio(track) }, modifier = Modifier.size(38.dp)) {
                                        Icon(Icons.Rounded.Radio, contentDescription = "Start Radio", tint = palette.secondaryGlow, modifier = Modifier.size(19.dp))
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // Full Echo Music Home & Explore Sections
            if (selectedMood == "All") {
                // 1. Echo Quick Picks (4-row horizontal Speed Dial grid)
                if (uiState.showSpeedDial && uiState.quickPicks.isNotEmpty()) {
                    item(key = "echo_quick_picks_section") {
                        EchoQuickPicksGridSection(
                            tracks = uiState.quickPicks,
                            activeTrackId = activeTrackId,
                            isPlaying = isPlaying,
                            onPlayQueue = onPlayQueue,
                            onSurpriseMe = onSurpriseMe
                        )
                    }
                }

                // 2. Continue Listening / Keep Listening (from Room listening history)
                if (uiState.jumpBackIn.isNotEmpty()) {
                    item(key = "shelf_jump_back_in") {
                        TrackCarouselShelf(
                            title = "Keep Listening",
                            subtitle = "Your recent & most played rotations",
                            tracks = uiState.jumpBackIn,
                            activeTrackId = activeTrackId,
                            isPlaying = isPlaying,
                            onPlay = onPlayQueue,
                            onStartRadio = onStartRadio,
                            onAddToPlaylist = onAddTrackToPlaylist
                        )
                    }
                }

                // 3. Similar to [Your Recent Song] (Echo Music SimilarRecommendation shelf)
                if (uiState.similarRecommendationTracks.isNotEmpty() && uiState.similarRecommendationTitle.isNotBlank()) {
                    item(key = "shelf_similar_recommendation") {
                        TrackCarouselShelf(
                            title = uiState.similarRecommendationTitle,
                            subtitle = "From your taste by Echo Music",
                            tracks = uiState.similarRecommendationTracks,
                            activeTrackId = activeTrackId,
                            isPlaying = isPlaying,
                            onPlay = onPlayQueue,
                            onStartRadio = onStartRadio,
                            onAddToPlaylist = onAddTrackToPlaylist
                        )
                    }
                }

                // 4. Forgotten Favorites (Echo Music Forgotten Favorites shelf)
                if (uiState.forgottenFavorites.isNotEmpty()) {
                    item(key = "shelf_forgotten_favorites") {
                        TrackCarouselShelf(
                            title = "Forgotten Favorites",
                            subtitle = "Rediscover tracks from your library",
                            tracks = uiState.forgottenFavorites,
                            activeTrackId = activeTrackId,
                            isPlaying = isPlaying,
                            onPlay = onPlayQueue,
                            onStartRadio = onStartRadio,
                            onAddToPlaylist = onAddTrackToPlaylist
                        )
                    }
                }

                // 5. Dynamic Browse Sections from YouTube Music InnerTube (Charts, New Releases, Carousels)
                items(
                    items = uiState.browseSections.filterNot {
                        uiState.showSpeedDial && it.layoutType == BrowseLayoutType.QUICK_PICKS_GRID && it.title.equals("Quick Picks", ignoreCase = true)
                    },
                    key = { it.id }
                ) { section ->
                    BrowseSectionRenderer(
                        section = section,
                        activeTrackId = activeTrackId,
                        isPlaying = isPlaying,
                        onPlayQueue = onPlayQueue,
                        onStartRadio = onStartRadio,
                        onAddTrackToPlaylist = onAddTrackToPlaylist,
                        onOpenAlbum = onOpenAlbum,
                        onOpenArtist = onOpenArtist
                    )
                }
            }

            // On Your Device (Local MediaStore Audio)
            if (selectedMood == "All" || selectedMood == "Local Files") {
                item(key = "shelf_local") {
                    if (uiState.localTracks.isNotEmpty()) {
                        TrackCarouselShelf(
                            title = "On Your Device",
                            subtitle = "${uiState.localTracks.size} local MP3/FLAC tracks ready offline",
                            tracks = uiState.localTracks.take(12),
                            activeTrackId = activeTrackId,
                            isPlaying = isPlaying,
                            onPlay = onPlayQueue,
                            onStartRadio = onStartRadio,
                            onAddToPlaylist = onAddTrackToPlaylist
                        )
                    } else if (selectedMood == "Local Files") {
                        LiquidGlassSurface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                            cornerRadius = 22.dp,
                            useRefractionShell = false
                        ) {
                            Row(
                                modifier = Modifier.padding(18.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Folder,
                                    contentDescription = null,
                                    tint = palette.secondaryGlow,
                                    modifier = Modifier.size(28.dp)
                                )
                                Column {
                                    Text("No local audio files found yet", style = MaterialTheme.typography.titleMedium, color = FrostWhite)
                                    Text(
                                        text = "Open Library → Local Files to scan MP3, FLAC, WAV & M4A tracks on your device.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MistSecondary
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
private fun EchoQuickPicksGridSection(
    tracks: List<Track>,
    activeTrackId: String?,
    isPlaying: Boolean,
    onPlayQueue: (List<Track>, Int) -> Unit,
    onSurpriseMe: () -> Unit
) {
    val palette = LocalLiquidPalette.current
    val columns = tracks.take(16).chunked(4)

    Column(modifier = Modifier.padding(bottom = 24.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Quick Picks",
                    style = MaterialTheme.typography.headlineMedium,
                    color = FrostWhite
                )
                Text(
                    text = "Live YouTube Music speed dial · Swipe horizontally",
                    style = MaterialTheme.typography.bodySmall,
                    color = MistSecondary
                )
            }
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(palette.primaryGlow.copy(alpha = 0.16f))
                    .border(1.dp, palette.primaryGlow.copy(alpha = 0.45f), CircleShape)
                    .clickable(onClick = onSurpriseMe)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = palette.primaryGlow, modifier = Modifier.size(15.dp))
                Text("Radio Mix", style = MaterialTheme.typography.labelMedium, color = FrostWhite)
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(columns, key = { colIdx, _ -> "qp_col_$colIdx" }) { _, colTracks ->
                Column(
                    modifier = Modifier.width(278.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    colTracks.forEach { track ->
                        val globalIdx = tracks.indexOf(track).coerceAtLeast(0)
                        QuickPickTile(
                            track = track,
                            isActive = track.id == activeTrackId,
                            isPlaying = isPlaying && track.id == activeTrackId,
                            onClick = { onPlayQueue(tracks, globalIdx) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BrowseSectionRenderer(
    section: BrowseSection,
    activeTrackId: String?,
    isPlaying: Boolean,
    onPlayQueue: (List<Track>, Int) -> Unit,
    onStartRadio: (Track) -> Unit,
    onAddTrackToPlaylist: (Track) -> Unit,
    onOpenAlbum: (String, MusicSource) -> Unit,
    onOpenArtist: (String, MusicSource) -> Unit
) {
    when (section.layoutType) {
        BrowseLayoutType.QUICK_PICKS_GRID -> {
            if (section.tracks.isEmpty()) return
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                SectionHeader(
                    title = section.title,
                    subtitle = section.subtitle.ifBlank { "Tap to play · Radio button launches Endless Radio" }
                )
                val rows = section.tracks.take(8).chunked(2)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    rows.forEach { pair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            pair.forEach { track ->
                                val idx = section.tracks.indexOf(track)
                                QuickPickTile(
                                    track = track,
                                    isActive = track.id == activeTrackId,
                                    isPlaying = isPlaying && track.id == activeTrackId,
                                    onClick = { onPlayQueue(section.tracks, idx) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        BrowseLayoutType.CHART_LIST -> {
            if (section.tracks.isEmpty()) return
            val palette = LocalLiquidPalette.current
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                SectionHeader(
                    title = section.title,
                    subtitle = section.subtitle.ifBlank { "Top live chart rankings" }
                )
                Spacer(modifier = Modifier.height(4.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    section.tracks.take(10).forEachIndexed { idx, track ->
                        val isActive = track.id == activeTrackId
                        EchoSurfaceCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("chart_track_${track.id}"),
                            cornerRadius = 18.dp,
                            elevated = isActive,
                            accentBorder = if (isActive) palette.secondaryGlow.copy(alpha = 0.55f) else null,
                            onClick = { onPlayQueue(section.tracks, idx) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${idx + 1}",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (idx < 3) palette.primaryGlow else MistSecondary,
                                    modifier = Modifier.width(26.dp)
                                )
                                TrackArtwork(
                                    artworkUrl = track.artworkUrl,
                                    contentDescription = track.title,
                                    cornerRadius = 12.dp,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = track.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (isActive) palette.primaryGlow else FrostWhite,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = track.artist,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MistSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (isActive) {
                                    AnimatedWaveformBars(isPlaying = isPlaying, modifier = Modifier.padding(end = 4.dp))
                                }
                                IconButton(onClick = { onStartRadio(track) }, modifier = Modifier.size(38.dp)) {
                                    Icon(Icons.Rounded.Radio, contentDescription = "Start radio", tint = palette.secondaryGlow, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        BrowseLayoutType.CAROUSEL_ALBUMS -> {
            if (section.albums.isEmpty()) return
            Box(modifier = Modifier.padding(bottom = 24.dp)) {
                AlbumCarouselRow(
                    title = section.title,
                    albums = section.albums,
                    onOpenAlbum = onOpenAlbum
                )
            }
        }

        BrowseLayoutType.CAROUSEL_ARTISTS -> {
            if (section.artists.isEmpty()) return
            Box(modifier = Modifier.padding(bottom = 24.dp)) {
                ArtistCarouselRow(
                    title = section.title,
                    artists = section.artists,
                    onOpenArtist = onOpenArtist
                )
            }
        }

        BrowseLayoutType.CAROUSEL_TRACKS -> {
            TrackCarouselShelf(
                title = section.title,
                subtitle = section.subtitle.ifBlank { section.sourceProvider.displayName },
                tracks = section.tracks,
                activeTrackId = activeTrackId,
                isPlaying = isPlaying,
                onPlay = onPlayQueue,
                onStartRadio = onStartRadio,
                onAddToPlaylist = onAddTrackToPlaylist
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = FrostWhite
        )
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MistSecondary
            )
        }
    }
}

@Composable
private fun QuickPickTile(
    track: Track,
    isActive: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalLiquidPalette.current
    EchoSurfaceCard(
        modifier = modifier
            .height(62.dp)
            .testTag("quick_pick_${track.id}"),
        cornerRadius = 16.dp,
        elevated = isActive,
        accentBorder = if (isActive) palette.secondaryGlow.copy(alpha = 0.6f) else null,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(end = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TrackArtwork(
                artworkUrl = track.artworkUrl,
                contentDescription = track.title,
                cornerRadius = 14.dp,
                modifier = Modifier.size(62.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isActive) palette.primaryGlow else FrostWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MistSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (isActive) {
                AnimatedWaveformBars(isPlaying = isPlaying)
            }
        }
    }
}

@Composable
private fun TrackCarouselShelf(
    title: String,
    subtitle: String,
    tracks: List<Track>,
    activeTrackId: String?,
    isPlaying: Boolean,
    onPlay: (List<Track>, Int) -> Unit,
    onStartRadio: (Track) -> Unit,
    onAddToPlaylist: (Track) -> Unit
) {
    if (tracks.isEmpty()) return
    val palette = LocalLiquidPalette.current

    Column(modifier = Modifier.padding(bottom = 24.dp)) {
        SectionHeader(title = title, subtitle = subtitle)
        Spacer(modifier = Modifier.height(6.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            itemsIndexed(tracks, key = { idx, t -> "${title}_${idx}_${t.id}" }) { index, track ->
                val isActive = track.id == activeTrackId
                EchoSurfaceCard(
                    modifier = Modifier
                        .width(158.dp)
                        .testTag("carousel_track_${track.id}"),
                    cornerRadius = 20.dp,
                    elevated = isActive,
                    accentBorder = if (isActive) palette.secondaryGlow.copy(alpha = 0.6f) else null,
                    onClick = { onPlay(tracks, index) }
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Box(modifier = Modifier.size(138.dp)) {
                            TrackArtwork(
                                artworkUrl = track.artworkUrl,
                                contentDescription = track.title,
                                cornerRadius = 15.dp,
                                modifier = Modifier.fillMaxSize()
                            )
                            if (track.sourceType == SourceType.LOCAL) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(6.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xB3070B18))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("LOCAL", style = MaterialTheme.typography.labelSmall, color = palette.secondaryGlow)
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(6.dp)
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xB3070B18))
                                        .clickable { onStartRadio(track) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Radio,
                                        contentDescription = "Start radio",
                                        tint = palette.secondaryGlow,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                            if (isActive) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(6.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xCC060914))
                                        .padding(horizontal = 7.dp, vertical = 4.dp)
                                ) {
                                    AnimatedWaveformBars(isPlaying = isPlaying)
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(6.dp)
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xB3070B18))
                                        .clickable { onAddToPlaylist(track) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Add,
                                        contentDescription = "Add to playlist",
                                        tint = FrostWhite,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (isActive) palette.primaryGlow else FrostWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = track.artist,
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
