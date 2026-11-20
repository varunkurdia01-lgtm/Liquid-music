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
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Refresh
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.domain.model.SourceType
import com.example.domain.model.Track
import com.example.domain.model.UserProfile
import com.example.ui.glass.AnimatedWaveformBars
import com.example.ui.glass.LiquidGlassChip
import com.example.ui.glass.LiquidGlassSurface
import com.example.ui.glass.TrackArtwork
import com.example.ui.theme.FrostWhite
import com.example.ui.theme.LocalLiquidPalette
import com.example.ui.theme.MistSecondary
import com.example.ui.viewmodel.DiscoveryUiState
import java.util.Calendar

@Composable
fun HomeScreen(
    uiState: DiscoveryUiState,
    userProfile: UserProfile,
    activeTrackId: String?,
    isPlaying: Boolean,
    bottomPadding: Dp,
    onMoodSelected: (String) -> Unit,
    onPlayQueue: (List<Track>, Int) -> Unit,
    onAddTrackToPlaylist: (Track) -> Unit,
    onOpenProfile: () -> Unit,
    onOpenSoundLab: () -> Unit,
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
    val moods = listOf("All", "Pop", "Hip-Hop", "Electronic", "Chill", "Local Files")

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .testTag("home_screen_list"),
        contentPadding = PaddingValues(top = 16.dp, bottom = bottomPadding)
    ) {
        // Header Row: Time-of-day Greeting + Sound Lab quick trigger + Glass Avatar
        item(key = "home_header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = greeting.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.secondaryGlow
                    )
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
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(0x55141A2E))
                            .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape)
                            .testTag("home_refresh_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Refresh Discovery",
                            tint = FrostWhite,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = onOpenSoundLab,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(0x55141A2E))
                            .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape)
                            .testTag("home_sound_lab_shortcut")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = "Open Sound Lab",
                            tint = palette.primaryGlow,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(palette.primaryGlow.copy(alpha = 0.35f), palette.deepAurora)
                                )
                            )
                            .border(1.dp, palette.primaryGlow.copy(alpha = 0.6f), CircleShape)
                            .clickable(onClick = onOpenProfile)
                            .testTag("home_profile_avatar"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = userProfile.displayName.take(1).uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            color = FrostWhite
                        )
                    }
                }
            }
        }

        // Mood / Genre Filter Glass Chips
        item(key = "home_mood_chips") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(moods, key = { it }) { mood ->
                    LiquidGlassChip(
                        label = mood,
                        selected = uiState.selectedHomeMood == mood,
                        onClick = { onMoodSelected(mood) },
                        modifier = Modifier.testTag("mood_chip_${mood.lowercase().replace(" ", "_")}")
                    )
                }
            }
        }

        if (uiState.isLoadingHome) {
            item(key = "home_loading") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = palette.secondaryGlow, strokeWidth = 2.5.dp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Tuning your Liquid Aurora feed…", color = MistSecondary, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        } else {
            val mood = uiState.selectedHomeMood

            // 2-Column Quick Picks Grid (6 items)
            if ((mood == "All" || mood == "Pop") && uiState.quickPicks.isNotEmpty()) {
                item(key = "quick_picks_section") {
                    SectionHeader(title = "Quick Picks", subtitle = "Tap for instant 320 kbps playback")
                    val rows = uiState.quickPicks.chunked(2)
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
                                    val idx = uiState.quickPicks.indexOf(track)
                                    QuickPickTile(
                                        track = track,
                                        isActive = track.id == activeTrackId,
                                        isPlaying = isPlaying && track.id == activeTrackId,
                                        onClick = { onPlayQueue(uiState.quickPicks, idx) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            // Jump Back In (from Room listening history)
            if (mood == "All" && uiState.jumpBackIn.isNotEmpty()) {
                item(key = "shelf_jump_back_in") {
                    TrackCarouselShelf(
                        title = "Jump Back In",
                        subtitle = "Recently spun in your sessions",
                        tracks = uiState.jumpBackIn,
                        activeTrackId = activeTrackId,
                        isPlaying = isPlaying,
                        onPlay = onPlayQueue,
                        onAddToPlaylist = onAddTrackToPlaylist
                    )
                }
            }

            // Global Pop Hits
            if (mood == "All" || mood == "Pop") {
                item(key = "shelf_global_pop") {
                    TrackCarouselShelf(
                        title = "Global Pop Hits",
                        subtitle = "Chart-topping anthems & crystal vocals",
                        tracks = uiState.globalPop,
                        activeTrackId = activeTrackId,
                        isPlaying = isPlaying,
                        onPlay = onPlayQueue,
                        onAddToPlaylist = onAddTrackToPlaylist
                    )
                }
            }

            // Hip-Hop & Rap Pulse
            if (mood == "All" || mood == "Hip-Hop") {
                item(key = "shelf_hiphop") {
                    TrackCarouselShelf(
                        title = "Hip-Hop & Rap Pulse",
                        subtitle = "Heavy 808s, rhythmic flows & studio bass",
                        tracks = uiState.hipHopPulse,
                        activeTrackId = activeTrackId,
                        isPlaying = isPlaying,
                        onPlay = onPlayQueue,
                        onAddToPlaylist = onAddTrackToPlaylist
                    )
                }
            }

            // Electronic & Synthwave
            if (mood == "All" || mood == "Electronic") {
                item(key = "shelf_electronic") {
                    TrackCarouselShelf(
                        title = "Electronic & Synthwave",
                        subtitle = "Neon arpeggios, euphoric drops & spatial stereo",
                        tracks = uiState.electronicSynth,
                        activeTrackId = activeTrackId,
                        isPlaying = isPlaying,
                        onPlay = onPlayQueue,
                        onAddToPlaylist = onAddTrackToPlaylist
                    )
                }
            }

            // Chill & Late Night
            if (mood == "All" || mood == "Chill") {
                item(key = "shelf_chill") {
                    TrackCarouselShelf(
                        title = "Chill & Late Night",
                        subtitle = "Velvet lo-fi beats & nocturnal atmosphere",
                        tracks = uiState.chillLateNight,
                        activeTrackId = activeTrackId,
                        isPlaying = isPlaying,
                        onPlay = onPlayQueue,
                        onAddToPlaylist = onAddTrackToPlaylist
                    )
                }
            }

            // On Your Device (Local MediaStore Files)
            if (mood == "All" || mood == "Local Files") {
                item(key = "shelf_local") {
                    if (uiState.localTracks.isNotEmpty()) {
                        TrackCarouselShelf(
                            title = "On Your Device",
                            subtitle = "${uiState.localTracks.size} local audio tracks ready to mix",
                            tracks = uiState.localTracks.take(12),
                            activeTrackId = activeTrackId,
                            isPlaying = isPlaying,
                            onPlay = onPlayQueue,
                            onAddToPlaylist = onAddTrackToPlaylist
                        )
                    } else if (mood == "Local Files") {
                        LiquidGlassSurface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                            cornerRadius = 22.dp
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
                                    Text(
                                        text = "No local audio files found yet",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = FrostWhite
                                    )
                                    Text(
                                        text = "Visit Library → Local Files to grant audio permission and scan MP3, FLAC & WAV files.",
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
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MistSecondary
        )
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
    LiquidGlassSurface(
        modifier = modifier
            .height(62.dp)
            .clickable(onClick = onClick)
            .testTag("quick_pick_${track.id}"),
        cornerRadius = 16.dp,
        surfaceAlpha = if (isActive) 0.72f else 0.52f,
        highlightAccent = if (isActive) palette.secondaryGlow else null,
        useRefractionShell = false
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
            itemsIndexed(tracks, key = { _, t -> "${title}_${t.id}" }) { index, track ->
                val isActive = track.id == activeTrackId
                LiquidGlassSurface(
                    modifier = Modifier
                        .width(158.dp)
                        .clickable { onPlay(tracks, index) }
                        .testTag("carousel_track_${track.id}"),
                    cornerRadius = 20.dp,
                    surfaceAlpha = if (isActive) 0.72f else 0.50f,
                    highlightAccent = if (isActive) palette.secondaryGlow else null,
                    useRefractionShell = false
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
