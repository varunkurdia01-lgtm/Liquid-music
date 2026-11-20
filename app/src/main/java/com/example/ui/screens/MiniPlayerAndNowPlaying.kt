package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.domain.model.LyricsState
import com.example.domain.model.RepeatModeState
import com.example.domain.model.SleepTimerOption
import com.example.domain.model.SoundLabSettings
import com.example.domain.model.SourceType
import com.example.domain.model.Track
import com.example.ui.glass.AnimatedWaveformBars
import com.example.ui.glass.LiquidAuroraCanvas
import com.example.ui.glass.LiquidGlassButton
import com.example.ui.glass.LiquidGlassSlider
import com.example.ui.glass.LiquidGlassSurface
import com.example.ui.glass.LocalLiquidBackdrop
import com.example.ui.glass.TrackArtwork
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.example.ui.theme.DeepVoid
import com.example.ui.theme.EmeraldPulse
import com.example.ui.theme.FrostWhite
import com.example.ui.theme.LocalLiquidPalette
import com.example.ui.theme.LuminousRose
import com.example.ui.theme.MistSecondary
import com.example.ui.theme.ObsidianBlack
import com.example.ui.viewmodel.PlayerUiState
import kotlinx.coroutines.launch

/**
 * Floating Liquid Glass Mini-Player pinned directly above the iOS 26 bottom tab capsule.
 * Supports horizontal swipe to skip Next/Previous with parallax + vertical swipe/tap to expand.
 */
@Composable
fun FloatingLiquidMiniPlayer(
    playerState: PlayerUiState,
    isLiked: Boolean,
    onExpand: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onToggleLike: () -> Unit,
    modifier: Modifier = Modifier
) {
    val track = playerState.currentTrack ?: return
    val palette = LocalLiquidPalette.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val swipeOffsetX = remember { Animatable(0f) }

    val progressFraction = if (playerState.durationMs > 0L) {
        (playerState.positionMs.toFloat() / playerState.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    LiquidGlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(66.dp)
            .testTag("floating_mini_player")
            .graphicsLayer {
                translationX = swipeOffsetX.value * 0.25f
            }
            .pointerInput(track.id) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        val delta = swipeOffsetX.value
                        scope.launch {
                            if (delta < -90f) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSkipNext()
                            } else if (delta > 90f) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSkipPrevious()
                            }
                            swipeOffsetX.animateTo(0f, spring(dampingRatio = 0.68f, stiffness = 420f))
                        }
                    },
                    onDragCancel = {
                        scope.launch { swipeOffsetX.animateTo(0f, spring()) }
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        scope.launch {
                            swipeOffsetX.snapTo((swipeOffsetX.value + dragAmount).coerceIn(-180f, 180f))
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dragAmount ->
                    if (dragAmount < -18f) {
                        onExpand()
                    }
                }
            }
            .clickable(onClick = onExpand),
        cornerRadius = 24.dp,
        tintColor = Color(0xFF0E1426),
        surfaceAlpha = 0.78f,
        highlightAccent = palette.primaryGlow
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TrackArtwork(
                    artworkUrl = track.artworkUrl,
                    contentDescription = track.title,
                    cornerRadius = 14.dp,
                    modifier = Modifier
                        .size(46.dp)
                        .graphicsLayer {
                            translationX = swipeOffsetX.value * 0.14f
                        }
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = FrostWhite,
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

                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onToggleLike()
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = "Like current track",
                        tint = if (isLiked) LuminousRose else MistSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onTogglePlayPause()
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("mini_player_play_pause")
                ) {
                    if (playerState.isBuffering) {
                        CircularProgressIndicator(
                            color = palette.primaryGlow,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Icon(
                            imageVector = if (playerState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                            tint = FrostWhite,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }

            // Ultra-thin luminous progress line along bottom edge
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(2.5.dp)
                    .background(Color.White.copy(alpha = 0.08f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progressFraction)
                        .height(2.5.dp)
                        .background(
                            Brush.horizontalGradient(listOf(palette.secondaryGlow, palette.primaryGlow))
                        )
                )
            }
        }
    }
}

/**
 * Full-Screen Now Playing experience using Full-Bleed Cover Art with Floating Liquid Glass controls,
 * one-tap interactive Synced Lyrics (tap any line to seek), and Draggable Queue bottom sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullBleedNowPlayingScreen(
    playerState: PlayerUiState,
    soundLabSettings: SoundLabSettings,
    isLiked: Boolean,
    isDownloaded: Boolean,
    downloadProgress: Float?,
    onDismiss: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleLike: () -> Unit,
    onToggleDownload: () -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onOpenSoundLab: () -> Unit,
    onJumpQueueIndex: (Int) -> Unit,
    onMoveQueueItem: (Int, Int) -> Unit,
    onRemoveQueueItem: (Int) -> Unit
) {
    val track = playerState.currentTrack ?: return
    val palette = LocalLiquidPalette.current
    val haptic = LocalHapticFeedback.current

    var showLyrics by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }
    val nowPlayingBackdrop = rememberLayerBackdrop()

    BackHandler {
        when {
            showQueueSheet -> showQueueSheet = false
            showLyrics -> showLyrics = false
            else -> onDismiss()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack)
            .pointerInput(showLyrics) {
                if (!showLyrics) {
                    detectVerticalDragGestures { _, dragAmount ->
                        if (dragAmount > 32f) onDismiss()
                    }
                }
            }
            .testTag("full_now_playing_screen")
    ) {
        // Captured Full-Bleed Cover Art + Dynamic Palette Vignette Backdrop for real-time lens refraction
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(nowPlayingBackdrop)
        ) {
            if (track.artworkUrl.isNotBlank()) {
                AsyncImage(
                    model = track.artworkUrl,
                    contentDescription = track.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = if (showLyrics) 0.24f else 0.94f
                        }
                )
            } else {
                LiquidAuroraCanvas(intensity = 1.25f)
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.00f to ObsidianBlack.copy(alpha = 0.56f),
                                0.32f to palette.deepAurora.copy(alpha = if (showLyrics) 0.55f else 0.12f),
                                0.62f to ObsidianBlack.copy(alpha = if (showLyrics) 0.84f else 0.44f),
                                0.88f to ObsidianBlack.copy(alpha = 0.82f),
                                1.00f to ObsidianBlack
                            )
                        )
                    )
            )
        }

        CompositionLocalProvider(LocalLiquidBackdrop provides nowPlayingBackdrop) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Floating Liquid Glass Top Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LiquidGlassButton(
                        onClick = onDismiss,
                        cornerRadius = 18.dp,
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("now_playing_collapse_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = "Collapse player",
                            tint = FrostWhite,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }

                    // Quality + Sleep Timer Pill
                    LiquidGlassSurface(
                        cornerRadius = 18.dp,
                        surfaceAlpha = 0.42f,
                        useRefractionShell = true
                    ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val badge = when {
                            track.sourceType == SourceType.LOCAL -> "LOCAL AUDIO"
                            isDownloaded -> "OFFLINE · ${soundLabSettings.audioQuality.label}"
                            else -> soundLabSettings.audioQuality.label
                        }
                        Text(badge, style = MaterialTheme.typography.labelMedium, color = palette.primaryGlow)
                        if (playerState.sleepTimerOption != SleepTimerOption.OFF) {
                            Icon(Icons.Rounded.Bedtime, contentDescription = null, tint = palette.secondaryGlow, modifier = Modifier.size(14.dp))
                        }
                    }
                }

                LiquidGlassButton(
                    onClick = { showQueueSheet = true },
                    cornerRadius = 18.dp,
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("now_playing_queue_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                        contentDescription = "Open queue",
                        tint = FrostWhite,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }

            // Middle Region: Either unobstructed Full-Bleed Art Showcase OR Full-Screen Synced Lyrics
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 12.dp)
            ) {
                AnimatedContent(targetState = showLyrics, label = "lyricsToggle") { lyricsVisible ->
                    if (lyricsVisible) {
                        SyncedLyricsPane(
                            lyricsState = playerState.lyricsState,
                            positionMs = playerState.positionMs,
                            onSeekTo = { ts ->
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSeekTo(ts)
                            }
                        )
                    } else {
                        // Subtle DSP / Playback error status badge hovering gently over full-bleed art
                        Box(modifier = Modifier.fillMaxSize()) {
                            if (playerState.playbackError != null) {
                                LiquidGlassSurface(
                                    modifier = Modifier.align(Alignment.BottomCenter),
                                    cornerRadius = 16.dp
                                ) {
                                    Text(
                                        text = playerState.playbackError,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = LuminousRose,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Floating Liquid Glass Control Dock
            LiquidGlassSurface(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 30.dp,
                tintColor = Color(0xFF0A0F1F),
                surfaceAlpha = 0.76f,
                highlightAccent = palette.primaryGlow
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Track Title + Heart + Download + Add-to-Playlist
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.headlineMedium,
                                color = FrostWhite,
                                maxLines = 1,
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

                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onToggleLike()
                            },
                            modifier = Modifier.testTag("now_playing_like_button")
                        ) {
                            Icon(
                                imageVector = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                contentDescription = "Toggle like",
                                tint = if (isLiked) LuminousRose else FrostWhite
                            )
                        }

                        if (track.sourceType == SourceType.ONLINE) {
                            IconButton(
                                onClick = onToggleDownload,
                                modifier = Modifier.testTag("now_playing_download_button")
                            ) {
                                if (downloadProgress != null) {
                                    CircularProgressIndicator(
                                        progress = { downloadProgress },
                                        color = palette.secondaryGlow,
                                        strokeWidth = 2.5.dp,
                                        modifier = Modifier.size(22.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = if (isDownloaded) Icons.Rounded.DownloadDone else Icons.Rounded.Download,
                                        contentDescription = "Download offline",
                                        tint = if (isDownloaded) EmeraldPulse else FrostWhite
                                    )
                                }
                            }
                        }

                        IconButton(onClick = { onAddToPlaylist(track) }) {
                            Icon(Icons.Rounded.PlaylistAdd, contentDescription = "Add to playlist", tint = FrostWhite)
                        }
                    }

                    // Zero-recomposition Seekbar
                    LiquidScrubBar(
                        positionMs = playerState.positionMs,
                        durationMs = playerState.durationMs,
                        onSeekTo = onSeekTo
                    )

                    // Primary Transport Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onToggleShuffle,
                            modifier = Modifier.testTag("now_playing_shuffle")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Shuffle,
                                contentDescription = "Shuffle",
                                tint = if (playerState.shuffleEnabled) palette.secondaryGlow else MistSecondary
                            )
                        }

                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSkipPrevious()
                            },
                            modifier = Modifier
                                .size(50.dp)
                                .testTag("now_playing_prev")
                        ) {
                            Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous", tint = FrostWhite, modifier = Modifier.size(34.dp))
                        }

                        LiquidGlassButton(
                            onClick = onTogglePlayPause,
                            accentGlow = true,
                            cornerRadius = 32.dp,
                            modifier = Modifier
                                .size(66.dp)
                                .testTag("now_playing_play_pause")
                        ) {
                            if (playerState.isBuffering) {
                                CircularProgressIndicator(
                                    color = FrostWhite,
                                    strokeWidth = 2.5.dp,
                                    modifier = Modifier
                                        .size(26.dp)
                                        .align(Alignment.Center)
                                )
                            } else {
                                Icon(
                                    imageVector = if (playerState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                                    tint = FrostWhite,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .align(Alignment.Center)
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSkipNext()
                            },
                            modifier = Modifier
                                .size(50.dp)
                                .testTag("now_playing_next")
                        ) {
                            Icon(Icons.Rounded.SkipNext, contentDescription = "Next", tint = FrostWhite, modifier = Modifier.size(34.dp))
                        }

                        IconButton(
                            onClick = onCycleRepeat,
                            modifier = Modifier.testTag("now_playing_repeat")
                        ) {
                            Icon(
                                imageVector = if (playerState.repeatMode == RepeatModeState.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                                contentDescription = "Repeat mode",
                                tint = if (playerState.repeatMode != RepeatModeState.OFF) palette.secondaryGlow else MistSecondary
                            )
                        }
                    }

                    // Bottom Action Strip: Synced Lyrics Toggle + Sound Lab Quick Indicator
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LiquidGlassButton(
                            onClick = { showLyrics = !showLyrics },
                            accentGlow = showLyrics,
                            cornerRadius = 16.dp,
                            modifier = Modifier
                                .height(40.dp)
                                .testTag("now_playing_lyrics_toggle")
                        ) {
                            Row(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Rounded.Lyrics, contentDescription = null, tint = FrostWhite, modifier = Modifier.size(16.dp))
                                Text(
                                    text = if (showLyrics) "Cover Art" else "Synced Lyrics",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = FrostWhite
                                )
                            }
                        }

                        LiquidGlassButton(
                            onClick = {
                                onDismiss()
                                onOpenSoundLab()
                            },
                            cornerRadius = 16.dp,
                            modifier = Modifier
                                .height(40.dp)
                                .testTag("now_playing_soundlab_shortcut")
                        ) {
                            Row(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Rounded.Tune, contentDescription = null, tint = palette.secondaryGlow, modifier = Modifier.size(16.dp))
                                Text(
                                    text = "${"%.2f".format(soundLabSettings.playbackSpeed)}x · ${soundLabSettings.eqPreset.label}",
                                    style = MaterialTheme.typography.labelMedium,
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

    // Draggable / Reorderable Live Queue Bottom Sheet
    if (showQueueSheet) {
        ModalBottomSheet(
            onDismissRequest = { showQueueSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = DeepVoid,
            contentColor = FrostWhite
        ) {
            QueueSheetContent(
                queue = playerState.queue,
                currentIndex = playerState.currentIndex,
                isPlaying = playerState.isPlaying,
                onJumpTo = { idx -> onJumpQueueIndex(idx) },
                onMoveItem = onMoveQueueItem,
                onRemoveItem = onRemoveQueueItem
            )
        }
    }
}

@Composable
private fun LiquidScrubBar(
    positionMs: Long,
    durationMs: Long,
    onSeekTo: (Long) -> Unit
) {
    val palette = LocalLiquidPalette.current
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }

    val safeDuration = durationMs.coerceAtLeast(1L)
    val liveFraction = (positionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
    val displayedFraction = if (isScrubbing) scrubFraction else liveFraction
    val displayedPosMs = if (isScrubbing) (scrubFraction * safeDuration).toLong() else positionMs

    Column {
        LiquidGlassSlider(
            value = displayedFraction,
            onValueChange = {
                isScrubbing = true
                scrubFraction = it
            },
            onValueChangeFinished = {
                onSeekTo((scrubFraction * safeDuration).toLong())
                isScrubbing = false
            },
            activeColor = palette.primaryGlow,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("now_playing_seekbar")
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(formatTimestamp(displayedPosMs), style = MaterialTheme.typography.labelMedium, color = MistSecondary)
            Text(formatTimestamp(durationMs), style = MaterialTheme.typography.labelMedium, color = MistSecondary)
        }
    }
}

@Composable
private fun SyncedLyricsPane(
    lyricsState: LyricsState,
    positionMs: Long,
    onSeekTo: (Long) -> Unit
) {
    val palette = LocalLiquidPalette.current
    val listState = rememberLazyListState()

    val activeLineIndex by remember(lyricsState.syncedLines, positionMs) {
        derivedStateOf {
            val lines = lyricsState.syncedLines
            if (lines.isEmpty()) -1
            else lines.indexOfLast { it.timestampMs <= positionMs + 180L }.coerceAtLeast(0)
        }
    }

    LaunchedEffect(activeLineIndex) {
        if (activeLineIndex >= 0 && lyricsState.syncedLines.isNotEmpty()) {
            listState.animateScrollToItem((activeLineIndex - 2).coerceAtLeast(0))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("synced_lyrics_pane"),
        contentAlignment = Alignment.Center
    ) {
        when {
            lyricsState.isLoading -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = palette.secondaryGlow, strokeWidth = 2.5.dp)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Fetching synced lyrics from LRCLIB…", style = MaterialTheme.typography.bodyMedium, color = MistSecondary)
                }
            }
            lyricsState.syncedLines.isNotEmpty() -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 48.dp, horizontal = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    itemsIndexed(lyricsState.syncedLines, key = { idx, line -> "${idx}_${line.timestampMs}" }) { index, line ->
                        val isActive = index == activeLineIndex
                        val scale by animateFloatAsState(
                            targetValue = if (isActive) 1.06f else 0.96f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 420f),
                            label = "lyricScale_$index"
                        )
                        Text(
                            text = line.text,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontSize = 23.sp,
                                lineHeight = 31.sp,
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = if (isActive) FrostWhite else MistSecondary.copy(alpha = 0.45f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                }
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onSeekTo(line.timestampMs) }
                                .padding(vertical = 4.dp, horizontal = 6.dp)
                        )
                    }
                }
            }
            lyricsState.plainLyrics.isNotBlank() -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 24.dp, horizontal = 12.dp)
                ) {
                    item {
                        Text(
                            text = lyricsState.plainLyrics,
                            style = MaterialTheme.typography.titleLarge.copy(lineHeight = 30.sp),
                            color = FrostWhite.copy(alpha = 0.88f)
                        )
                    }
                }
            }
            lyricsState.isInstrumental -> {
                Text("Instrumental Track · Pure Acoustic Flow", style = MaterialTheme.typography.titleLarge, color = palette.primaryGlow)
            }
            else -> {
                Text(
                    text = lyricsState.errorMessage ?: "Synced lyrics unavailable for this track.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MistSecondary
                )
            }
        }
    }
}

@Composable
private fun QueueSheetContent(
    queue: List<Track>,
    currentIndex: Int,
    isPlaying: Boolean,
    onJumpTo: (Int) -> Unit,
    onMoveItem: (Int, Int) -> Unit,
    onRemoveItem: (Int) -> Unit
) {
    val palette = LocalLiquidPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Up Next (${queue.size} Tracks)", style = MaterialTheme.typography.headlineMedium, color = FrostWhite)
        Text("Tap to jump, reorder live, or remove from queue", style = MaterialTheme.typography.bodySmall, color = MistSecondary)

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(380.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(queue, key = { idx, t -> "${idx}_${t.id}" }) { idx, track ->
                val isCurrent = idx == currentIndex
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isCurrent) palette.deepAurora.copy(alpha = 0.45f) else Color(0x44141A2E))
                        .border(
                            1.dp,
                            if (isCurrent) palette.primaryGlow.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.1f),
                            RoundedCornerShape(16.dp)
                        )
                        .clickable { onJumpTo(idx) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TrackArtwork(
                        artworkUrl = track.artworkUrl,
                        contentDescription = track.title,
                        cornerRadius = 10.dp,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (isCurrent) palette.primaryGlow else FrostWhite,
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
                    if (isCurrent) {
                        AnimatedWaveformBars(isPlaying = isPlaying, modifier = Modifier.padding(end = 6.dp))
                    }
                    if (idx > 0) {
                        IconButton(onClick = { onMoveItem(idx, idx - 1) }, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Rounded.ArrowUpward, contentDescription = "Move up", tint = MistSecondary, modifier = Modifier.size(18.dp))
                        }
                    }
                    if (idx < queue.lastIndex) {
                        IconButton(onClick = { onMoveItem(idx, idx + 1) }, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Rounded.ArrowDownward, contentDescription = "Move down", tint = MistSecondary, modifier = Modifier.size(18.dp))
                        }
                    }
                    if (!isCurrent) {
                        IconButton(onClick = { onRemoveItem(idx) }, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Rounded.Close, contentDescription = "Remove", tint = LuminousRose.copy(alpha = 0.85f), modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

private fun formatTimestamp(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val minutes = totalSec / 60L
    val seconds = totalSec % 60L
    return "%d:%02d".format(minutes, seconds)
}
