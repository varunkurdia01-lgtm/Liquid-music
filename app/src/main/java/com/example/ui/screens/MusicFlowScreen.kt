package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.domain.model.Track
import com.example.ui.components.AnimatedLikeButton
import com.example.ui.components.EchoHomeModePill
import com.example.ui.components.bounceClick
import com.example.ui.glass.TrackArtwork
import com.example.ui.theme.EmeraldPulse
import com.example.ui.theme.FrostWhite
import com.example.ui.theme.LocalLiquidPalette
import com.example.ui.theme.MistSecondary
import com.example.ui.viewmodel.PlayerUiState
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Echo Music "Music Flow" — full-screen TikTok/Reels-style vertical swipe music discovery feed.
 * Features dynamic blurred cover art, auto-play on settled page, live synced lyric spotlight,
 * and selective Liquid Glass floating side action pills.
 */
@Composable
fun MusicFlowScreen(
    feedTracks: List<Track>,
    playerState: PlayerUiState,
    lyricsOffsetMs: Long,
    likedTrackIds: Set<String>,
    downloadedTrackIds: Set<String>,
    bottomPadding: Dp,
    onSwitchToHome: () -> Unit,
    onPlayFlowTrack: (List<Track>, Int) -> Unit,
    onTogglePlayPause: () -> Unit,
    onToggleLike: (Track) -> Unit,
    onToggleDownload: (Track) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onStartRadio: (Track) -> Unit,
    onOpenDna: (Track) -> Unit,
    onExpandPlayer: () -> Unit
) {
    val palette = LocalLiquidPalette.current

    if (feedTracks.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF070A12))
                .statusBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(24.dp)
            ) {
                EchoHomeModePill(
                    isFlowActive = true,
                    onSelectHome = onSwitchToHome,
                    onSelectFlow = {}
                )
                Spacer(modifier = Modifier.height(24.dp))
                CircularProgressIndicator(color = palette.secondaryGlow, strokeWidth = 2.5.dp)
                Text("Curating your Music Flow stream…", style = MaterialTheme.typography.bodyMedium, color = MistSecondary)
            }
        }
        return
    }

    val initialPage = remember(feedTracks) {
        val currentIdx = feedTracks.indexOfFirst { it.id == playerState.currentTrack?.id }
        if (currentIdx >= 0) currentIdx else 0
    }
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { feedTracks.size })

    // Auto-play settled Flow track seamlessly
    LaunchedEffect(pagerState, feedTracks) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                val target = feedTracks.getOrNull(page) ?: return@collect
                if (playerState.currentTrack?.id != target.id) {
                    onPlayFlowTrack(feedTracks, page)
                }
            }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF060911))
            .testTag("music_flow_screen")
    ) {
        VerticalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val track = feedTracks[page]
            val isThisActive = playerState.currentTrack?.id == track.id
            val isLiked = likedTrackIds.contains(track.id)
            val isDownloaded = downloadedTrackIds.contains(track.id)

            Box(modifier = Modifier.fillMaxSize()) {
                // Full-bleed blurred artwork backdrop
                if (track.artworkUrl.isNotBlank()) {
                    AsyncImage(
                        model = track.artworkUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(32.dp)
                    )
                }
                // Contrast gradient scrim
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xAA05070E),
                                    Color(0x660A1020),
                                    Color(0xDD060812)
                                )
                            )
                        )
                )

                // Center Cover + Live Synced Lyrics Spotlight
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(start = 24.dp, end = 80.dp, top = 84.dp, bottom = bottomPadding + 16.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.Start
                ) {
                    Spacer(modifier = Modifier.height(6.dp))

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .bounceClick(scaleDown = 0.96f) {
                                    if (isThisActive) onTogglePlayPause() else onPlayFlowTrack(feedTracks, page)
                                }
                        ) {
                            TrackArtwork(
                                artworkUrl = track.artworkUrl,
                                contentDescription = track.title,
                                cornerRadius = 26.dp,
                                modifier = Modifier.size(224.dp)
                            )
                            // Center Play/Pause Glass Badge
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(10.dp)
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xCC0D1324))
                                    .border(1.dp, palette.primaryGlow.copy(alpha = 0.65f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isThisActive && playerState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = "Play or pause",
                                    tint = FrostWhite,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Live Synced Karaoke Lyric Snippet
                        if (isThisActive && playerState.lyricsState.syncedLines.isNotEmpty()) {
                            val effectivePos = (playerState.positionMs + lyricsOffsetMs).coerceAtLeast(0L)
                            val activeLineIdx = remember(playerState.lyricsState.syncedLines, effectivePos) {
                                playerState.lyricsState.syncedLines.indexOfLast { it.timestampMs <= effectivePos }
                                    .coerceAtLeast(0)
                            }
                            val currentLine = playerState.lyricsState.syncedLines.getOrNull(activeLineIdx)?.text.orEmpty()
                            val nextLine = playerState.lyricsState.syncedLines.getOrNull(activeLineIdx + 1)?.text.orEmpty()

                            AnimatedContent(
                                targetState = currentLine to nextLine,
                                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(180)) },
                                label = "flowLyricTransition"
                            ) { (curr, next) ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(Color(0x550C1222))
                                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(18.dp))
                                        .padding(horizontal = 14.dp, vertical = 12.dp)
                                ) {
                                    Text(
                                        text = curr,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = FrostWhite,
                                        textAlign = TextAlign.Center,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (next.isNotBlank()) {
                                        Text(
                                            text = next,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MistSecondary.copy(alpha = 0.78f),
                                            textAlign = TextAlign.Center,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.KeyboardArrowUp,
                                    contentDescription = null,
                                    tint = MistSecondary.copy(alpha = 0.7f),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Swipe up for next vibe",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MistSecondary.copy(alpha = 0.75f)
                                )
                            }
                        }
                    }

                    // Bottom-Left Track Metadata
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (isThisActive && playerState.activeStreamBadge.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(palette.primaryGlow.copy(alpha = 0.2f))
                                    .border(1.dp, palette.primaryGlow.copy(alpha = 0.45f), CircleShape)
                                    .padding(horizontal = 9.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = playerState.activeStreamBadge,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = palette.primaryGlow
                                )
                            }
                        }
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = FrostWhite,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = track.artist,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MistSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Right-side floating Selective Liquid Glass action rail
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 14.dp, bottom = bottomPadding + 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AnimatedLikeButton(
                        isLiked = isLiked,
                        onClick = { onToggleLike(track) },
                        size = 48.dp,
                        containerColor = Color(0x88101628)
                    )
                    FlowActionButton(
                        icon = Icons.Rounded.AutoAwesome,
                        label = "DNA",
                        tint = palette.secondaryGlow,
                        onClick = { onOpenDna(track) }
                    )
                    FlowActionButton(
                        icon = Icons.Rounded.QueueMusic,
                        label = "Next",
                        tint = FrostWhite,
                        onClick = { onPlayNext(track) }
                    )
                    FlowActionButton(
                        icon = Icons.Rounded.PlaylistAdd,
                        label = "Save",
                        tint = FrostWhite,
                        onClick = { onAddToPlaylist(track) }
                    )
                    FlowActionButton(
                        icon = if (isDownloaded) Icons.Rounded.DownloadDone else Icons.Rounded.Download,
                        label = if (isDownloaded) "Saved" else "Offline",
                        tint = if (isDownloaded) EmeraldPulse else FrostWhite,
                        onClick = { onToggleDownload(track) }
                    )
                    FlowActionButton(
                        icon = Icons.Rounded.Radio,
                        label = "Radio",
                        tint = palette.primaryGlow,
                        onClick = { onStartRadio(track) }
                    )
                    FlowActionButton(
                        icon = Icons.Rounded.OpenInFull,
                        label = "Player",
                        tint = FrostWhite,
                        onClick = onExpandPlayer
                    )
                }
            }
        }

        // Floating Top Mode Pill (Home | Flow)
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 14.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            EchoHomeModePill(
                isFlowActive = true,
                onSelectHome = onSwitchToHome,
                onSelectFlow = {}
            )
        }
    }
}

@Composable
private fun FlowActionButton(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(Color(0x88101628))
                .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape)
                .bounceClick(scaleDown = 0.88f, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(21.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = FrostWhite.copy(alpha = 0.86f)
        )
    }
}
