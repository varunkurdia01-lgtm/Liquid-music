package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.example.domain.model.LyricLine
import com.example.domain.model.LyricsAlignStyle
import com.example.domain.model.LyricsSearchCandidate
import com.example.domain.model.LyricsState
import com.example.domain.model.PlayerBackgroundStyle
import com.example.domain.model.PlayerLayoutStyle
import com.example.domain.model.PlayerSliderStyle
import com.example.domain.model.RepeatModeState
import com.example.domain.model.SleepTimerOption
import com.example.domain.model.SoundLabSettings
import com.example.domain.model.SourceType
import com.example.domain.model.Track
import com.example.ui.components.AnimatedLikeButton
import com.example.ui.components.EchoPlayerSeekBar
import com.example.ui.components.EchoSurfaceCard
import com.example.ui.glass.AnimatedWaveformBars
import com.example.ui.glass.AppleGlassTokens
import com.example.ui.glass.LiquidAuroraCanvas
import com.example.ui.glass.LiquidGlassButton
import com.example.ui.glass.LiquidGlassSurface
import com.example.ui.glass.LiquidGlassToggle
import com.example.ui.glass.LocalLiquidBackdrop
import com.example.ui.glass.MiniPlayerPillPhysicsState
import com.example.ui.glass.MiniPlayerSpringSpecs
import com.example.ui.glass.TrackArtwork
import com.example.ui.glass.miniPlayerPillGestures
import com.example.ui.glass.rememberMiniPlayerPillPhysics
import com.example.ui.theme.DeepVoid
import com.example.ui.theme.EmeraldPulse
import com.example.ui.theme.FrostWhite
import com.example.ui.theme.LocalLiquidPalette
import com.example.ui.theme.LuminousRose
import com.example.ui.theme.MistSecondary
import com.example.ui.theme.ObsidianBlack
import com.example.ui.viewmodel.PlayerUiState
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private fun lerpFloat(start: Float, stop: Float, fraction: Float): Float =
    start + (stop - start) * fraction

/**
 * Floating iOS 26-style Liquid Glass Mini-Player Pill pinned cleanly above the bottom tab capsule.
 * Powered by [MiniPlayerPillPhysicsState] high-stiffness Compose spring physics:
 * - Instant tactile press depression + 3D perspective tilt + thumb-tracking specular glass rim
 * - Rubber-banded horizontal swipe with haptic detent & velocity-preserving spring snap-back
 * - Elastic vertical lift gesture to launch Full-Bleed Now Playing
 * - Kinetic spring track-swap transitions and micro-bouncing inner playback controls
 */
@Composable
fun FloatingLiquidMiniPlayer(
    playerState: PlayerUiState,
    isLiked: Boolean,
    swipeToSongEnabled: Boolean = true,
    onExpand: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onToggleLike: () -> Unit,
    modifier: Modifier = Modifier,
    onBoundsInRootChanged: (Rect) -> Unit = {},
    positionMsFlow: StateFlow<Long>? = null
) {
    val track = playerState.currentTrack ?: return
    val palette = LocalLiquidPalette.current
    val activeAccent = remember(palette.primaryGlow) {
        AppleGlassTokens.ensureVibrantOnDarkGlass(palette.primaryGlow)
    }
    val haptic = LocalHapticFeedback.current
    val physics = rememberMiniPlayerPillPhysics()

    var previousTrackId by remember { mutableStateOf(track.id) }
    LaunchedEffect(track.id) {
        if (track.id != previousTrackId) {
            previousTrackId = track.id
            physics.pulseTrackChange()
        }
    }

    val playButtonScale = remember { Animatable(1f) }
    val likeButtonScale = remember { Animatable(1f) }
    val controlScope = rememberCoroutineScope()

    val horizontalProgress = physics.horizontalProgress
    val skipHintAlpha = ((kotlin.math.abs(horizontalProgress) - 0.35f) / 0.65f).coerceIn(0f, 1f)

    LiquidGlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(66.dp)
            .animateContentSize(animationSpec = MiniPlayerSpringSpecs.LayoutSizeSpring)
            .testTag("floating_mini_player")
            .onSizeChanged { physics.pillWidthPx = it.width.toFloat() }
            .onGloballyPositioned { coords -> onBoundsInRootChanged(coords.boundsInRoot()) }
            .graphicsLayer {
                with(physics) { applyPillPhysics() }
            }
            .miniPlayerPillGestures(
                key = "${track.id}_$swipeToSongEnabled",
                physics = physics,
                haptic = haptic,
                onExpand = onExpand,
                onSkipNext = if (swipeToSongEnabled) onSkipNext else ({}),
                onSkipPrevious = if (swipeToSongEnabled) onSkipPrevious else ({})
            ),
        cornerRadius = 33.dp,
        tintColor = AppleGlassTokens.SmokedCrystalBase,
        surfaceAlpha = 0.29f,
        highlightAccent = activeAccent,
        refractionBoost = 1.42f,
        blurOverrideDp = 5.5f,
        smokedCrystalRim = true
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    drawContent()
                    // iOS 26 directional Fresnel specular rim that glides smoothly under your thumb
                    val focusX = size.width * physics.specularFraction.value
                    val touchGlowVal = physics.touchGlow.value
                    val glowBoost = 0.34f + 0.46f * touchGlowVal
                    val radiusPx = size.height / 2f
                    drawRoundRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = glowBoost),
                                activeAccent.copy(alpha = (0.14f + 0.26f * touchGlowVal)),
                                Color.Transparent
                            ),
                            center = Offset(focusX, 0f),
                            radius = size.width * 0.56f
                        ),
                        cornerRadius = CornerRadius(radiusPx, radiusPx),
                        style = Stroke(width = (1.05f + 0.35f * touchGlowVal).dp.toPx())
                    )
                }
        ) {
            // Subtle directional skip detent indicator during horizontal drag
            if (swipeToSongEnabled && skipHintAlpha > 0.02f) {
                val isSkippingNext = horizontalProgress < 0f
                Icon(
                    imageVector = if (isSkippingNext) Icons.Rounded.SkipNext else Icons.Rounded.SkipPrevious,
                    contentDescription = null,
                    tint = FrostWhite.copy(alpha = skipHintAlpha * 0.85f),
                    modifier = Modifier
                        .align(if (isSkippingNext) Alignment.CenterEnd else Alignment.CenterStart)
                        .padding(horizontal = 14.dp)
                        .size(20.dp)
                        .graphicsLayer {
                            val s = 0.75f + 0.30f * skipHintAlpha
                            scaleX = s
                            scaleY = s
                        }
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 10.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AnimatedContent(
                    targetState = track,
                    transitionSpec = {
                        val dir = if (physics.lastSkipDirection > 0f) -1 else 1
                        (slideInHorizontally(
                            animationSpec = spring(dampingRatio = 0.76f, stiffness = 650f),
                            initialOffsetX = { fullWidth -> dir * (fullWidth / 4) }
                        ) + fadeIn(spring(stiffness = 650f))) togetherWith
                            (slideOutHorizontally(
                                animationSpec = spring(dampingRatio = 0.82f, stiffness = 680f),
                                targetOffsetX = { fullWidth -> -dir * (fullWidth / 4) }
                            ) + fadeOut(spring(stiffness = 700f)))
                    },
                    modifier = Modifier.weight(1f),
                    label = "miniPlayerTrackSwap"
                ) { active ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TrackArtwork(
                            artworkUrl = active.artworkUrl,
                            contentDescription = active.title,
                            cornerRadius = 23.dp,
                            modifier = Modifier
                                .size(46.dp)
                                .graphicsLayer {
                                    translationX = physics.offsetX.value * 0.22f
                                    val liftScale = 1f + 0.04f * physics.verticalLiftProgress
                                    scaleX = liftScale
                                    scaleY = liftScale
                                }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .graphicsLayer {
                                    translationX = physics.offsetX.value * 0.14f
                                }
                        ) {
                            Text(
                                text = active.title,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = FrostWhite,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = active.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = MistSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        controlScope.launch {
                            likeButtonScale.snapTo(0.76f)
                            likeButtonScale.animateTo(1f, MiniPlayerSpringSpecs.ControlBounce)
                        }
                        onToggleLike()
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("mini_player_like_button")
                        .graphicsLayer {
                            scaleX = likeButtonScale.value
                            scaleY = likeButtonScale.value
                        }
                ) {
                    Icon(
                        imageVector = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = "Like current track",
                        tint = if (isLiked) LuminousRose else MistSecondary,
                        modifier = Modifier.size(21.dp)
                    )
                }

                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        controlScope.launch {
                            playButtonScale.snapTo(0.78f)
                            playButtonScale.animateTo(1f, MiniPlayerSpringSpecs.ControlBounce)
                        }
                        onTogglePlayPause()
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("mini_player_play_pause")
                        .graphicsLayer {
                            scaleX = playButtonScale.value
                            scaleY = playButtonScale.value
                        }
                ) {
                    if (playerState.isBuffering) {
                        CircularProgressIndicator(
                            color = activeAccent,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        AnimatedContent(
                            targetState = playerState.isPlaying,
                            transitionSpec = {
                                (scaleIn(
                                    initialScale = 0.72f,
                                    animationSpec = MiniPlayerSpringSpecs.ControlBounce
                                ) + fadeIn(spring(stiffness = 750f))) togetherWith
                                    (scaleOut(
                                        targetScale = 0.72f,
                                        animationSpec = MiniPlayerSpringSpecs.ControlBounce
                                    ) + fadeOut(spring(stiffness = 750f)))
                            },
                            label = "miniPlayerPlayPauseIcon"
                        ) { playing ->
                            Icon(
                                imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (playing) "Pause" else "Play",
                                tint = FrostWhite,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }
            }

            // Ultra-thin luminous progress line isolated in a leaf composable so 10Hz ticks never recompose the pill!
            MiniPlayerProgressLine(
                positionMsFlow = positionMsFlow,
                fallbackPositionMs = playerState.positionMs,
                durationMs = playerState.durationMs,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            )
        }
    }
}

@Composable
private fun MiniPlayerProgressLine(
    positionMsFlow: StateFlow<Long>?,
    fallbackPositionMs: Long,
    durationMs: Long,
    modifier: Modifier = Modifier
) {
    val palette = LocalLiquidPalette.current
    val livePos = positionMsFlow?.collectAsStateWithLifecycle()?.value ?: fallbackPositionMs
    val rawProgress = if (durationMs > 0L) {
        (livePos.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = rawProgress,
        animationSpec = spring(dampingRatio = 0.86f, stiffness = 480f),
        label = "miniPlayerProgress"
    )
    val progressBrush = remember(palette.secondaryGlow, palette.primaryGlow) {
        val vibrantPrimary = AppleGlassTokens.ensureVibrantOnDarkGlass(palette.primaryGlow)
        val vibrantSecondary = AppleGlassTokens.ensureVibrantOnDarkGlass(palette.secondaryGlow)
        Brush.horizontalGradient(listOf(vibrantSecondary, vibrantPrimary))
    }
    Box(
        modifier = modifier
            .height(2.5.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.08f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animatedProgress)
                .height(2.5.dp)
                .clip(CircleShape)
                .background(progressBrush)
        )
    }
}

/**
 * Full-Screen Now Playing experience using Full-Bleed Cover Art with Floating Liquid Glass controls,
 * word/line-synced Karaoke Lyrics + Gemini AI Translation, Endless Radio, and Up Next Queue sheet.
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
    onRetryTrack: () -> Unit,
    onStartRadio: (Track) -> Unit,
    onToggleAutoPlayRadio: () -> Unit,
    onSaveQueueAsPlaylist: (String) -> Unit,
    onToggleLyricsTranslation: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleLike: () -> Unit,
    onToggleDownload: () -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onOpenSoundLab: () -> Unit,
    onJumpQueueIndex: (Int) -> Unit,
    onMoveQueueItem: (Int, Int) -> Unit,
    onRemoveQueueItem: (Int) -> Unit,
    onOpenMusicDna: () -> Unit = {},
    onAdjustLyricsOffset: (Long) -> Unit = {},
    onResetLyricsOffset: () -> Unit = {},
    onSearchLyrics: (String) -> Unit = {},
    onApplyManualLyrics: (LyricsSearchCandidate) -> Unit = {},
    onDismissDragProgress: (Float) -> Unit = {},
    expandProgress: Float = 1f,
    originBounds: Rect? = null,
    positionMsFlow: StateFlow<Long>? = null
) {
    val track = playerState.currentTrack ?: return
    val palette = LocalLiquidPalette.current
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val animScope = rememberCoroutineScope()

    var showLyrics by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }
    var showSaveQueueDialog by remember { mutableStateOf(false) }
    var showManualLyricsSheet by remember { mutableStateOf(false) }
    val nowPlayingBackdrop = rememberLayerBackdrop()

    val layoutStyle = soundLabSettings.playerLayoutStyle
    val bgStyle = soundLabSettings.playerBackgroundStyle

    // Staggered spring choreography when unfolding from the floating mini-player pill
    val topBarReveal = remember { Animatable(0f) }
    val dockReveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch {
            delay(30L)
            topBarReveal.animateTo(1f, MiniPlayerSpringSpecs.ElementRevealSpring)
        }
        launch {
            delay(55L)
            dockReveal.animateTo(1f, MiniPlayerSpringSpecs.ElementRevealSpring)
        }
    }

    // Interactive pull-down dismissal physics (elastic tracking + dynamic corner rounding + live scene un-recess)
    val dismissDragY = remember { Animatable(0f) }
    val dismissFraction = (dismissDragY.value / 240f).coerceIn(0f, 1f)
    val dynamicCornerDp = (dismissFraction * 34f).dp

    BackHandler {
        when {
            showManualLyricsSheet -> showManualLyricsSheet = false
            showQueueSheet -> showQueueSheet = false
            showLyrics -> showLyrics = false
            else -> onDismiss()
        }
    }

    val clampedExpand = expandProgress.coerceIn(0f, 1f)
    val morphCornerDp = ((1f - clampedExpand) * 34f).dp
    val combinedCornerDp = if (dynamicCornerDp > morphCornerDp) dynamicCornerDp else morphCornerDp
    val rimIntensity = maxOf(dismissFraction, (1f - clampedExpand) * 1.15f).coerceIn(0f, 1f)

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val fullW = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        val fullH = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = expandProgress.coerceIn(0f, 1.06f)
                    val dismissScale = 1f - (0.085f * dismissFraction)

                    val hasOrigin = originBounds != null && originBounds.width > 1f && originBounds.height > 1f
                    val startScaleX = if (hasOrigin) (originBounds!!.width / fullW).coerceIn(0.20f, 1f) else 0.90f
                    val startScaleY = if (hasOrigin) (originBounds!!.height / fullH).coerceIn(0.06f, 1f) else 0.16f
                    val startTransX = if (hasOrigin) originBounds!!.center.x - (fullW * 0.5f) else 0f
                    val startTransY = if (hasOrigin) originBounds!!.center.y - (fullH * 0.5f) else fullH * 0.36f

                    val morphScaleX = lerpFloat(startScaleX, 1f, p)
                    val morphScaleY = lerpFloat(startScaleY, 1f, p)
                    val morphTransX = startTransX * (1f - p)
                    val morphTransY = startTransY * (1f - p)

                    scaleX = (morphScaleX * dismissScale).coerceAtLeast(0.05f)
                    scaleY = (morphScaleY * dismissScale).coerceAtLeast(0.05f)
                    translationX = morphTransX
                    translationY = morphTransY + (dismissDragY.value * 0.72f)
                    alpha = (clampedExpand / 0.18f).coerceIn(0f, 1f)

                    val cornerPx = with(density) { combinedCornerDp.toPx() }
                    if (cornerPx > 0.5f) {
                        shape = RoundedCornerShape(combinedCornerDp)
                        clip = true
                    }
                }
                .background(ObsidianBlack)
                .drawWithContent {
                    drawContent()
                    if (rimIntensity > 0.02f) {
                        val cornerPx = with(density) { combinedCornerDp.toPx() }
                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.44f * rimIntensity),
                                    palette.primaryGlow.copy(alpha = 0.26f * rimIntensity),
                                    Color.White.copy(alpha = 0.12f * rimIntensity)
                                )
                            ),
                            cornerRadius = CornerRadius(cornerPx, cornerPx),
                            style = Stroke(width = 1.4.dp.toPx())
                        )
                    }
                }
                .pointerInput(showLyrics) {
                    if (!showLyrics) {
                        var rawDragY = 0f
                        detectVerticalDragGestures(
                            onDragStart = { rawDragY = 0f },
                            onDragEnd = {
                                val shouldDismiss = rawDragY > 140f || dismissDragY.value > 115f
                                rawDragY = 0f
                                if (shouldDismiss) {
                                    onDismissDragProgress(0f)
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    animScope.launch {
                                        dismissDragY.animateTo(0f, MiniPlayerSpringSpecs.PlayerMorphCollapse)
                                    }
                                    onDismiss()
                                } else {
                                    animScope.launch {
                                        dismissDragY.animateTo(0f, MiniPlayerSpringSpecs.DismissSnapBack)
                                        onDismissDragProgress(0f)
                                    }
                                }
                            },
                            onDragCancel = {
                                rawDragY = 0f
                                animScope.launch {
                                    dismissDragY.animateTo(0f, MiniPlayerSpringSpecs.DismissSnapBack)
                                    onDismissDragProgress(0f)
                                }
                            },
                            onVerticalDrag = { _, dragAmount ->
                                rawDragY = (rawDragY + dragAmount).coerceAtLeast(0f)
                                val damped = MiniPlayerPillPhysicsState.rubberBand(rawDragY, limit = 340f)
                                animScope.launch {
                                    dismissDragY.snapTo(damped)
                                }
                                onDismissDragProgress((damped / 240f).coerceIn(0f, 1f))
                            }
                        )
                    }
                }
                .testTag("full_now_playing_screen")
        ) {
            // Captured Backdrop honoring PlayerBackgroundStyle + PlayerLayoutStyle
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(nowPlayingBackdrop)
            ) {
                when (bgStyle) {
                    PlayerBackgroundStyle.PURE_BLACK -> {
                        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF04060B)))
                    }
                    PlayerBackgroundStyle.LIQUID_GLASS -> {
                        LiquidAuroraCanvas(intensity = 1.15f)
                        if (track.artworkUrl.isNotBlank() && layoutStyle == PlayerLayoutStyle.APPLE_FULL_BLEED) {
                            AsyncImage(
                                model = track.artworkUrl,
                                contentDescription = track.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer { alpha = if (showLyrics) 0.18f else 0.72f }
                            )
                        }
                    }
                    PlayerBackgroundStyle.BLUR -> {
                        if (track.artworkUrl.isNotBlank()) {
                            val heavyBlur = showLyrics || layoutStyle != PlayerLayoutStyle.APPLE_FULL_BLEED
                            val blurReq = remember(track.artworkUrl, heavyBlur) {
                                ImageRequest.Builder(context)
                                    .data(track.artworkUrl)
                                    .size(if (heavyBlur) 220 else 540)
                                    .precision(Precision.INEXACT)
                                    .crossfade(180)
                                    .build()
                            }
                            AsyncImage(
                                model = blurReq,
                                contentDescription = track.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .blur(if (heavyBlur) 42.dp else 14.dp)
                                    .graphicsLayer { alpha = if (showLyrics) 0.34f else 0.72f }
                            )
                        } else {
                            LiquidAuroraCanvas(intensity = 1.0f)
                        }
                    }
                    PlayerBackgroundStyle.GRADIENT -> {
                        if (track.artworkUrl.isNotBlank() && layoutStyle == PlayerLayoutStyle.APPLE_FULL_BLEED) {
                            AsyncImage(
                                model = track.artworkUrl,
                                contentDescription = track.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer { alpha = if (showLyrics) 0.24f else 0.94f }
                            )
                        } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            palette.deepAurora.copy(alpha = 0.85f),
                                            Color(0xFF0B1020),
                                            ObsidianBlack
                                        )
                                    )
                                )
                        )
                    }
                }
            }

            if (bgStyle != PlayerBackgroundStyle.PURE_BLACK) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.00f to ObsidianBlack.copy(alpha = 0.52f),
                                    0.32f to palette.deepAurora.copy(alpha = if (showLyrics) 0.55f else 0.14f),
                                    0.62f to ObsidianBlack.copy(alpha = if (showLyrics) 0.84f else 0.44f),
                                    0.88f to ObsidianBlack.copy(alpha = 0.84f),
                                    1.00f to ObsidianBlack
                                )
                            )
                        )
                )
            }
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
                // Floating Liquid Glass Top Bar (springs softly down into place on expansion)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            val r = (topBarReveal.value * ((clampedExpand - 0.25f) / 0.75f).coerceIn(0f, 1f))
                            alpha = r.coerceIn(0f, 1f)
                            translationY = -with(density) { 18.dp.toPx() } * (1f - r)
                        },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LiquidGlassButton(
                        onClick = onDismiss,
                        cornerRadius = 18.dp,
                        useLiveBackdrop = true,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("now_playing_collapse_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = "Collapse player",
                            tint = FrostWhite,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }

                    // Active Provider / Bitrate / Sleep Timer Pill
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
                            val badge = playerState.activeStreamBadge.ifBlank {
                                when {
                                    track.sourceType == SourceType.LOCAL -> "LOCAL AUDIO"
                                    isDownloaded -> "OFFLINE · ${soundLabSettings.audioQuality.label}"
                                    else -> "${track.sourceProvider.displayName} · ${soundLabSettings.audioQuality.label}"
                                }
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
                        useLiveBackdrop = true,
                        modifier = Modifier
                            .size(48.dp)
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

                // Middle Region: Centered Echo Material / Glass Artwork OR Full-Bleed Art OR Synced Karaoke Lyrics
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
                                positionMsFlow = positionMsFlow,
                                fallbackPositionMs = playerState.positionMs,
                                lyricsOffsetMs = soundLabSettings.lyricsOffsetMs,
                                lyricsAlignment = soundLabSettings.lyricsAlignment,
                                lyricsFontSizeSp = soundLabSettings.lyricsFontSizeSp,
                                lyricsLineSpacing = soundLabSettings.lyricsLineSpacing,
                                onSeekTo = { ts ->
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSeekTo(ts)
                                },
                                onToggleTranslation = onToggleLyricsTranslation,
                                onAdjustOffset = onAdjustLyricsOffset,
                                onResetOffset = onResetLyricsOffset,
                                onOpenManualSearch = {
                                    onSearchLyrics("${track.title} ${track.artist}")
                                    showManualLyricsSheet = true
                                }
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(track.id, soundLabSettings.swipeToSongEnabled) {
                                        if (soundLabSettings.swipeToSongEnabled) {
                                            var totalDragX = 0f
                                            detectHorizontalDragGestures(
                                                onDragStart = { totalDragX = 0f },
                                                onDragCancel = { totalDragX = 0f },
                                                onDragEnd = {
                                                    if (totalDragX < -100f) {
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        onSkipNext()
                                                    } else if (totalDragX > 100f) {
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        onSkipPrevious()
                                                    }
                                                    totalDragX = 0f
                                                },
                                                onHorizontalDrag = { _, dragAmount ->
                                                    totalDragX += dragAmount
                                                }
                                            )
                                        }
                                    }
                            ) {
                                if (layoutStyle != PlayerLayoutStyle.APPLE_FULL_BLEED) {
                                    val artSize = if (layoutStyle == PlayerLayoutStyle.ECHO_MATERIAL) 286.dp else 264.dp
                                    val artCorner = if (layoutStyle == PlayerLayoutStyle.ECHO_MATERIAL) 28.dp else 32.dp
                                    TrackArtwork(
                                        artworkUrl = track.artworkUrl,
                                        contentDescription = track.title,
                                        cornerRadius = artCorner,
                                        contentScale = if (soundLabSettings.cropAlbumArt) ContentScale.Crop else ContentScale.Fit,
                                        modifier = Modifier
                                            .size(artSize)
                                            .align(Alignment.Center)
                                            .graphicsLayer {
                                                val artScale = 0.84f + 0.16f * clampedExpand
                                                scaleX = artScale
                                                scaleY = artScale
                                            }
                                    )
                                }

                                if (playerState.playbackError != null) {
                                    LiquidGlassSurface(
                                        modifier = Modifier.align(Alignment.BottomCenter),
                                        cornerRadius = 18.dp
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Text(
                                                text = playerState.playbackError,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = LuminousRose,
                                                modifier = Modifier.weight(1f)
                                            )
                                            IconButton(
                                                onClick = onRetryTrack,
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .testTag("now_playing_retry_button")
                                            ) {
                                                Icon(Icons.Rounded.Refresh, contentDescription = "Retry stream", tint = FrostWhite)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Bottom Control Dock: Echo Material 3 Surface OR Optical Liquid Glass Dock
                val dockContent: @Composable () -> Unit = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize(animationSpec = MiniPlayerSpringSpecs.LayoutSizeSpring)
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Track Title + Animated Heart + Download + Add-to-Playlist
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
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

                            AnimatedLikeButton(
                                isLiked = isLiked,
                                onClick = onToggleLike,
                                size = 48.dp,
                                iconSize = 21.dp,
                                modifier = Modifier.testTag("now_playing_like_button")
                            )

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

                        // Echo Animated Seekbar (Squiggly / Wavy / Slim) — isolated 10Hz collector
                        LiquidScrubBar(
                            positionMsFlow = positionMsFlow,
                            fallbackPositionMs = playerState.positionMs,
                            durationMs = playerState.durationMs,
                            isPlaying = playerState.isPlaying,
                            sliderStyle = soundLabSettings.playerSliderStyle,
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
                                useLiveBackdrop = true,
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

                        if (soundLabSettings.showSeekButtons) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        val currentPos = positionMsFlow?.value ?: playerState.positionMs
                                        onSeekTo((currentPos - 10_000L).coerceAtLeast(0L))
                                    },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("now_playing_seek_back_10")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Replay10,
                                        contentDescription = "Seek back 10 seconds",
                                        tint = FrostWhite,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        val maxDuration = playerState.durationMs.coerceAtLeast(0L)
                                        val currentPos = positionMsFlow?.value ?: playerState.positionMs
                                        val target = currentPos + 10_000L
                                        onSeekTo(if (maxDuration > 0L) target.coerceAtMost(maxDuration) else target)
                                    },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("now_playing_seek_forward_10")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Forward10,
                                        contentDescription = "Seek forward 10 seconds",
                                        tint = FrostWhite,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }
                        }

                        // Bottom Action Strip: Lyrics + Music DNA + Radio + Sound Lab
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LiquidGlassButton(
                                onClick = { showLyrics = !showLyrics },
                                accentGlow = showLyrics,
                                cornerRadius = 16.dp,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("now_playing_lyrics_toggle")
                            ) {
                                Row(
                                    modifier = Modifier.align(Alignment.Center),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Rounded.Lyrics, contentDescription = null, tint = FrostWhite, modifier = Modifier.size(15.dp))
                                    Text(
                                        text = if (showLyrics) "Art" else "Lyrics",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = FrostWhite,
                                        maxLines = 1
                                    )
                                }
                            }

                            LiquidGlassButton(
                                onClick = onOpenMusicDna,
                                cornerRadius = 16.dp,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("now_playing_dna_button")
                            ) {
                                Row(
                                    modifier = Modifier.align(Alignment.Center),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = palette.secondaryGlow, modifier = Modifier.size(15.dp))
                                    Text("DNA", style = MaterialTheme.typography.labelMedium, color = FrostWhite, maxLines = 1)
                                }
                            }

                            if (track.sourceType == SourceType.ONLINE) {
                                LiquidGlassButton(
                                    onClick = { onStartRadio(track) },
                                    cornerRadius = 16.dp,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("now_playing_radio_button")
                                ) {
                                    Row(
                                        modifier = Modifier.align(Alignment.Center),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(Icons.Rounded.Radio, contentDescription = null, tint = palette.primaryGlow, modifier = Modifier.size(15.dp))
                                        Text("Radio", style = MaterialTheme.typography.labelMedium, color = FrostWhite, maxLines = 1)
                                    }
                                }
                            }

                            LiquidGlassButton(
                                onClick = {
                                    onDismiss()
                                    onOpenSoundLab()
                                },
                                cornerRadius = 16.dp,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("now_playing_soundlab_shortcut")
                            ) {
                                Row(
                                    modifier = Modifier.align(Alignment.Center),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Rounded.Tune, contentDescription = null, tint = palette.secondaryGlow, modifier = Modifier.size(15.dp))
                                    Text(
                                        text = soundLabSettings.eqPreset.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = FrostWhite,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                val dockMotionModifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        val r = (dockReveal.value * ((clampedExpand - 0.15f) / 0.85f).coerceIn(0f, 1f))
                        alpha = (0.30f + 0.70f * r).coerceIn(0f, 1f)
                        translationY = with(density) { 22.dp.toPx() } * (1f - r)
                    }

                if (layoutStyle == PlayerLayoutStyle.ECHO_MATERIAL) {
                    EchoSurfaceCard(
                        modifier = dockMotionModifier,
                        cornerRadius = 28.dp,
                        elevated = true,
                        accentBorder = palette.primaryGlow.copy(alpha = 0.25f)
                    ) {
                        dockContent()
                    }
                } else {
                    LiquidGlassSurface(
                        modifier = dockMotionModifier,
                        cornerRadius = 30.dp,
                        tintColor = Color(0xFF0A0F1F),
                        surfaceAlpha = 0.76f,
                        highlightAccent = palette.primaryGlow
                    ) {
                        dockContent()
                    }
                }
            }
        }
        }
    }

    // Draggable / Reorderable Live Queue Bottom Sheet + Endless Radio Toggle + Save Queue
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
                autoPlayRadioEnabled = playerState.autoPlayRadioEnabled,
                isLoadingRadio = playerState.isLoadingRadio,
                onToggleAutoPlayRadio = onToggleAutoPlayRadio,
                onOpenSaveQueue = { showSaveQueueDialog = true },
                onJumpTo = { idx -> onJumpQueueIndex(idx) },
                onMoveItem = onMoveQueueItem,
                onRemoveItem = onRemoveQueueItem
            )
        }
    }

    if (showSaveQueueDialog) {
        var playlistName by remember { mutableStateOf("Liquid Mix · ${track.title.take(18)}") }
        AlertDialog(
            onDismissRequest = { showSaveQueueDialog = false },
            containerColor = DeepVoid,
            titleContentColor = FrostWhite,
            title = { Text("Save Queue as Playlist") },
            text = {
                OutlinedTextField(
                    value = playlistName,
                    onValueChange = { playlistName = it },
                    label = { Text("Playlist Name", color = MistSecondary) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = FrostWhite,
                        unfocusedTextColor = FrostWhite,
                        focusedBorderColor = palette.primaryGlow,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSaveQueueAsPlaylist(playlistName)
                        showSaveQueueDialog = false
                    },
                    enabled = playlistName.isNotBlank()
                ) {
                    Text("Save", color = palette.primaryGlow)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveQueueDialog = false }) {
                    Text("Cancel", color = MistSecondary)
                }
            }
        )
    }
    if (showManualLyricsSheet) {
        var manualQuery by remember(track.id) { mutableStateOf("${track.title} ${track.artist}") }
        ModalBottomSheet(
            onDismissRequest = { showManualLyricsSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = DeepVoid,
            contentColor = FrostWhite
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .testTag("manual_lyrics_picker_sheet"),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Search & Pick Synced Lyrics", style = MaterialTheme.typography.titleLarge, color = FrostWhite)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = manualQuery,
                        onValueChange = { manualQuery = it },
                        placeholder = { Text("Song title & artist…", color = MistSecondary) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = FrostWhite,
                            unfocusedTextColor = FrostWhite,
                            focusedBorderColor = palette.primaryGlow,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.16f)
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    LiquidGlassButton(
                        onClick = { onSearchLyrics(manualQuery) },
                        accentGlow = true,
                        cornerRadius = 14.dp,
                        modifier = Modifier.height(52.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Search,
                            contentDescription = "Search lyrics",
                            tint = FrostWhite,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(horizontal = 14.dp)
                        )
                    }
                }

                if (playerState.isSearchingLyrics) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = palette.secondaryGlow, strokeWidth = 2.5.dp)
                    }
                } else if (playerState.lyricsSearchResults.isEmpty()) {
                    Text(
                        text = "No lyric candidates matched yet. Try removing featured artists or remix tags.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MistSecondary,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(playerState.lyricsSearchResults, key = { idx, c -> "${idx}_${c.id}" }) { _, candidate ->
                            EchoSurfaceCard(
                                modifier = Modifier.fillMaxWidth(),
                                elevated = candidate.hasSynced,
                                accentBorder = if (candidate.hasSynced) palette.primaryGlow.copy(alpha = 0.35f) else null,
                                onClick = {
                                    onApplyManualLyrics(candidate)
                                    showManualLyricsSheet = false
                                }
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = candidate.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = FrostWhite,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = if (candidate.hasSynced) "SYNCED · ${candidate.sourceName}" else candidate.sourceName,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (candidate.hasSynced) palette.primaryGlow else MistSecondary
                                        )
                                    }
                                    Text(
                                        text = candidate.artist,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MistSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val preview = candidate.plainLyrics.ifBlank { candidate.syncedLyrics }
                                        .lineSequence()
                                        .map { it.trim() }
                                        .filter { it.isNotBlank() }
                                        .take(2)
                                        .joinToString(" · ")
                                        .take(120)
                                    if (preview.isNotBlank()) {
                                        Text(
                                            text = preview,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = FrostWhite.copy(alpha = 0.72f),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun LiquidScrubBar(
    positionMsFlow: StateFlow<Long>?,
    fallbackPositionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    sliderStyle: PlayerSliderStyle,
    onSeekTo: (Long) -> Unit
) {
    val palette = LocalLiquidPalette.current
    val positionMs = positionMsFlow?.collectAsStateWithLifecycle()?.value ?: fallbackPositionMs
    val safeDuration = durationMs.coerceAtLeast(1L)
    val liveFraction = (positionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)

    Column {
        EchoPlayerSeekBar(
            progress = liveFraction,
            isPlaying = isPlaying,
            sliderStyle = sliderStyle,
            onSeekProgress = { frac ->
                onSeekTo((frac * safeDuration).toLong())
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
            Text(formatTimestamp(positionMs), style = MaterialTheme.typography.labelMedium, color = MistSecondary)
            Text(formatTimestamp(durationMs), style = MaterialTheme.typography.labelMedium, color = MistSecondary)
        }
    }
}

@Composable
private fun SyncedLyricsPane(
    lyricsState: LyricsState,
    positionMsFlow: StateFlow<Long>?,
    fallbackPositionMs: Long,
    lyricsOffsetMs: Long,
    lyricsAlignment: LyricsAlignStyle,
    lyricsFontSizeSp: Float,
    lyricsLineSpacing: Float,
    onSeekTo: (Long) -> Unit,
    onToggleTranslation: () -> Unit,
    onAdjustOffset: (Long) -> Unit,
    onResetOffset: () -> Unit,
    onOpenManualSearch: () -> Unit
) {
    val palette = LocalLiquidPalette.current
    val listState = rememberLazyListState()
    val positionMs = positionMsFlow?.collectAsStateWithLifecycle()?.value ?: fallbackPositionMs
    val effectivePositionMs = (positionMs + lyricsOffsetMs).coerceAtLeast(0L)
    val fontSizeInt = lyricsFontSizeSp.toInt().coerceIn(14, 34)

    val textAlign = when (lyricsAlignment) {
        LyricsAlignStyle.LEFT -> TextAlign.Start
        LyricsAlignStyle.CENTER -> TextAlign.Center
        LyricsAlignStyle.RIGHT -> TextAlign.End
    }
    val colAlign = when (lyricsAlignment) {
        LyricsAlignStyle.LEFT -> Alignment.Start
        LyricsAlignStyle.CENTER -> Alignment.CenterHorizontally
        LyricsAlignStyle.RIGHT -> Alignment.End
    }

    val activeLineIndex by remember(lyricsState.syncedLines, effectivePositionMs) {
        derivedStateOf {
            val lines = lyricsState.syncedLines
            if (lines.isEmpty()) -1
            else lines.indexOfLast { it.timestampMs <= effectivePositionMs + 180L }.coerceAtLeast(0)
        }
    }

    LaunchedEffect(activeLineIndex) {
        if (activeLineIndex >= 0 && lyricsState.syncedLines.isNotEmpty()) {
            listState.animateScrollToItem((activeLineIndex - 2).coerceAtLeast(0))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("synced_lyrics_pane")
    ) {
        // Lyrics Header: Source Badge + Manual Search + Live Timing Offset + AI Translation
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color(0x6612182B))
                        .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape)
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = lyricsState.sourceName.ifBlank { "Lyrics" },
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.secondaryGlow,
                        maxLines = 1
                    )
                }

                // Manual Lyrics Picker Trigger
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onOpenManualSearch)
                        .testTag("lyrics_manual_search_button")
                ) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color(0x6612182B))
                            .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Rounded.Search, contentDescription = "Search lyrics", tint = FrostWhite, modifier = Modifier.size(13.dp))
                            Text("Pick", style = MaterialTheme.typography.labelSmall, color = FrostWhite)
                        }
                    }
                }
            }

            if (!lyricsState.isLoading && (lyricsState.syncedLines.isNotEmpty() || lyricsState.plainLyrics.isNotBlank())) {
                LiquidGlassButton(
                    onClick = onToggleTranslation,
                    accentGlow = lyricsState.showTranslation,
                    cornerRadius = 16.dp,
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("lyrics_translate_button")
                ) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        if (lyricsState.isTranslating) {
                            CircularProgressIndicator(
                                color = FrostWhite,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(13.dp)
                            )
                            Text("Translating…", style = MaterialTheme.typography.labelSmall, color = FrostWhite)
                        } else {
                            Icon(Icons.Rounded.Translate, contentDescription = null, tint = FrostWhite, modifier = Modifier.size(14.dp))
                            Text(
                                text = if (lyricsState.showTranslation) "Original" else "Translate",
                                style = MaterialTheme.typography.labelSmall,
                                color = FrostWhite
                            )
                        }
                    }
                }
            }
        }

        // Live Sync Timing Offset Control Strip (-0.25s | Offset | +0.25s)
        if (lyricsState.syncedLines.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Sync", style = MaterialTheme.typography.labelSmall, color = MistSecondary)
                OffsetPill(label = "-0.25s", onClick = { onAdjustOffset(-250L) })
                val secVal = lyricsOffsetMs / 1000f
                val offsetLabel = if (lyricsOffsetMs == 0L) "0.00s" else "%+.2fs".format(secVal)
                OffsetPill(
                    label = offsetLabel,
                    highlighted = lyricsOffsetMs != 0L,
                    onClick = onResetOffset
                )
                OffsetPill(label = "+0.25s", onClick = { onAdjustOffset(250L) })
            }
        }

        if (!lyricsState.translationError.isNullOrBlank()) {
            Text(
                text = lyricsState.translationError,
                style = MaterialTheme.typography.bodySmall,
                color = LuminousRose,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            when {
                lyricsState.isLoading -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = palette.secondaryGlow, strokeWidth = 2.5.dp)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Fetching synced lyrics (LRCLIB · KuGou · YouLy+)…", style = MaterialTheme.typography.bodyMedium, color = MistSecondary)
                    }
                }
                lyricsState.syncedLines.isNotEmpty() -> {
                    val spacingDp = (14f * lyricsLineSpacing).coerceIn(10f, 34f).dp
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 36.dp, horizontal = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(spacingDp)
                    ) {
                        itemsIndexed(lyricsState.syncedLines, key = { idx, line -> "${idx}_${line.timestampMs}" }) { index, line ->
                            val isActive = index == activeLineIndex
                            val nextLineStart = lyricsState.syncedLines.getOrNull(index + 1)?.timestampMs
                                ?: (line.timestampMs + 4_000L)
                            KaraokeLyricRow(
                                line = line,
                                isActive = isActive,
                                positionMs = effectivePositionMs,
                                lineEndMs = line.endMs.takeIf { it > line.timestampMs } ?: nextLineStart,
                                showTranslation = lyricsState.showTranslation,
                                fontSizeSp = fontSizeInt,
                                textAlign = textAlign,
                                horizontalAlignment = colAlign,
                                onClick = { onSeekTo((line.timestampMs - lyricsOffsetMs).coerceAtLeast(0L)) }
                            )
                        }
                    }
                }
                lyricsState.plainLyrics.isNotBlank() -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 20.dp, horizontal = 10.dp)
                    ) {
                        item {
                            Text(
                                text = lyricsState.plainLyrics,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontSize = fontSizeInt.sp,
                                    lineHeight = (fontSizeInt * 1.4f).sp
                                ),
                                textAlign = textAlign,
                                color = FrostWhite.copy(alpha = 0.88f),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
                lyricsState.isInstrumental -> {
                    Text("Instrumental Track · Pure Acoustic Flow", style = MaterialTheme.typography.titleLarge, color = palette.primaryGlow)
                }
                else -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = lyricsState.errorMessage ?: "Synced lyrics unavailable for this track.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MistSecondary,
                            textAlign = TextAlign.Center
                        )
                        LiquidGlassButton(
                            onClick = onOpenManualSearch,
                            cornerRadius = 16.dp,
                            modifier = Modifier.height(40.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Rounded.Search, contentDescription = null, tint = FrostWhite, modifier = Modifier.size(16.dp))
                                Text("Search Lyrics Manually", style = MaterialTheme.typography.labelLarge, color = FrostWhite)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OffsetPill(
    label: String,
    highlighted: Boolean = false,
    onClick: () -> Unit
) {
    val palette = LocalLiquidPalette.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(if (highlighted) palette.primaryGlow.copy(alpha = 0.22f) else Color(0x5512182B))
                .border(
                    1.dp,
                    if (highlighted) palette.primaryGlow.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.12f),
                    CircleShape
                )
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (highlighted) palette.primaryGlow else FrostWhite
            )
        }
    }
}

@Composable
private fun KaraokeLyricRow(
    line: LyricLine,
    isActive: Boolean,
    positionMs: Long,
    lineEndMs: Long,
    showTranslation: Boolean,
    fontSizeSp: Int,
    textAlign: TextAlign,
    horizontalAlignment: Alignment.Horizontal,
    onClick: () -> Unit
) {
    val palette = LocalLiquidPalette.current
    val scale by animateFloatAsState(
        targetValue = if (isActive) 1.04f else 0.96f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 420f),
        label = "lyricScale_${line.timestampMs}"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp, horizontal = 6.dp),
        horizontalAlignment = horizontalAlignment,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val activeSize = (fontSizeSp + 2).sp
        val activeLineHeight = (fontSizeSp + 10).sp
        if (isActive && line.words.isNotEmpty()) {
            val annotated = buildAnnotatedString {
                line.words.forEachIndexed { i, word ->
                    val sung = positionMs >= word.startMs
                    withStyle(
                        SpanStyle(
                            color = if (sung) FrostWhite else MistSecondary.copy(alpha = 0.48f),
                            fontWeight = if (sung) FontWeight.Bold else FontWeight.Medium
                        )
                    ) {
                        append(word.text)
                        if (i < line.words.lastIndex) append(" ")
                    }
                }
            }
            Text(
                text = annotated,
                textAlign = textAlign,
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = activeSize, lineHeight = activeLineHeight),
                modifier = Modifier.fillMaxWidth()
            )
        } else if (isActive) {
            val duration = (lineEndMs - line.timestampMs).coerceAtLeast(1000L)
            val fraction = ((positionMs - line.timestampMs).toFloat() / duration.toFloat()).coerceIn(0f, 1f)
            val splitIndex = (line.text.length * fraction).toInt().coerceIn(0, line.text.length)
            val annotated = buildAnnotatedString {
                withStyle(SpanStyle(color = FrostWhite, fontWeight = FontWeight.Bold)) {
                    append(line.text.substring(0, splitIndex))
                }
                withStyle(SpanStyle(color = FrostWhite.copy(alpha = 0.58f), fontWeight = FontWeight.SemiBold)) {
                    append(line.text.substring(splitIndex))
                }
            }
            Text(
                text = annotated,
                textAlign = textAlign,
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = activeSize, lineHeight = activeLineHeight),
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Text(
                text = line.text,
                textAlign = textAlign,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = fontSizeSp.sp,
                    lineHeight = (fontSizeSp + 8).sp,
                    fontWeight = FontWeight.Medium
                ),
                color = MistSecondary.copy(alpha = 0.42f),
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (showTranslation && !line.translatedText.isNullOrBlank()) {
            Text(
                text = line.translatedText,
                textAlign = textAlign,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
                ),
                color = if (isActive) palette.primaryGlow else palette.secondaryGlow.copy(alpha = 0.55f),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun QueueSheetContent(
    queue: List<Track>,
    currentIndex: Int,
    isPlaying: Boolean,
    autoPlayRadioEnabled: Boolean,
    isLoadingRadio: Boolean,
    onToggleAutoPlayRadio: () -> Unit,
    onOpenSaveQueue: () -> Unit,
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Up Next (${queue.size} Tracks)", style = MaterialTheme.typography.headlineMedium, color = FrostWhite)
                Text("Tap to jump, reorder live, or save queue", style = MaterialTheme.typography.bodySmall, color = MistSecondary)
            }
            LiquidGlassButton(
                onClick = onOpenSaveQueue,
                cornerRadius = 14.dp,
                modifier = Modifier
                    .height(48.dp)
                    .testTag("queue_save_playlist_button")
            ) {
                Row(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(Icons.Rounded.BookmarkAdd, contentDescription = null, tint = palette.primaryGlow, modifier = Modifier.size(16.dp))
                    Text("Save", style = MaterialTheme.typography.labelMedium, color = FrostWhite)
                }
            }
        }

        // Auto-Play Endless Radio Toggle Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0x5512182E))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Rounded.Radio, contentDescription = null, tint = palette.secondaryGlow, modifier = Modifier.size(16.dp))
                    Text("Auto-Play Endless Radio", style = MaterialTheme.typography.titleSmall, color = FrostWhite)
                }
                Text(
                    text = if (isLoadingRadio) "Fetching related tracks…" else "Automatically queue similar songs before queue ends",
                    style = MaterialTheme.typography.bodySmall,
                    color = MistSecondary
                )
            }
            LiquidGlassToggle(
                checked = autoPlayRadioEnabled,
                onCheckedChange = { onToggleAutoPlayRadio() },
                modifier = Modifier.testTag("queue_autoplay_radio_toggle")
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp),
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
                        IconButton(onClick = { onMoveItem(idx, idx - 1) }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Rounded.ArrowUpward, contentDescription = "Move up", tint = MistSecondary, modifier = Modifier.size(20.dp))
                        }
                    }
                    if (idx < queue.lastIndex) {
                        IconButton(onClick = { onMoveItem(idx, idx + 1) }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Rounded.ArrowDownward, contentDescription = "Move down", tint = MistSecondary, modifier = Modifier.size(20.dp))
                        }
                    }
                    if (!isCurrent) {
                        IconButton(onClick = { onRemoveItem(idx) }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Rounded.Close, contentDescription = "Remove", tint = LuminousRose.copy(alpha = 0.85f), modifier = Modifier.size(20.dp))
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
