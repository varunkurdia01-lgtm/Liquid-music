package com.example

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.example.domain.model.PlayerLayoutStyle
import com.example.domain.model.SoundLabSettings
import com.example.domain.model.Track
import com.example.ui.glass.LocalLiquidBackdrop
import com.example.ui.glass.MiniPlayerSpringSpecs
import com.example.ui.screens.FloatingLiquidMiniPlayer
import com.example.ui.screens.FullBleedNowPlayingScreen
import com.example.ui.theme.LiquidMusicTheme
import com.example.ui.viewmodel.PlayerUiState
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PlayerTransitionRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `playing a song shows mini player and spring morphs to and from full player without crashing`() {
        val trackOne = Track(
            id = "test_track_1",
            title = "Midnight Velocity",
            artist = "Neon Pulse",
            album = "Liquid Horizons",
            durationMs = 215_000L,
            artworkUrl = ""
        )
        val trackTwo = Track(
            id = "test_track_2",
            title = "Glass Refraction",
            artist = "Aurora Drift",
            album = "Prism Echoes",
            durationMs = 198_000L,
            artworkUrl = ""
        )
        var playerState by mutableStateOf(PlayerUiState())
        var soundLabSettings by mutableStateOf(SoundLabSettings())
        var isExpanded by mutableStateOf(false)

        composeTestRule.setContent {
            LiquidMusicTheme {
                val mainBackdrop = rememberLayerBackdrop()
                val hasActiveTrack = playerState.currentTrack != null
                val isFullPlayerVisible = isExpanded && hasActiveTrack
                var miniPlayerBounds by remember { mutableStateOf<Rect?>(null) }
                val expandProgress = remember { Animatable(0f) }

                LaunchedEffect(isFullPlayerVisible, hasActiveTrack) {
                    if (!hasActiveTrack) {
                        expandProgress.snapTo(0f)
                    } else if (isFullPlayerVisible) {
                        expandProgress.animateTo(1f, MiniPlayerSpringSpecs.PlayerMorphExpand)
                    } else {
                        expandProgress.animateTo(0f, MiniPlayerSpringSpecs.PlayerMorphCollapse)
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .layerBackdrop(mainBackdrop)
                    )
                    CompositionLocalProvider(LocalLiquidBackdrop provides mainBackdrop) {
                        Column(modifier = Modifier.align(Alignment.BottomCenter)) {
                            AnimatedVisibility(
                                visible = hasActiveTrack,
                                enter = slideInVertically(
                                    initialOffsetY = { (it * 0.65f).toInt() },
                                    animationSpec = MiniPlayerSpringSpecs.PillEntranceOffset
                                ) + scaleIn(
                                    initialScale = 0.90f,
                                    animationSpec = MiniPlayerSpringSpecs.PillEntranceScale
                                ) + fadeIn(spring(stiffness = 620f)),
                                exit = slideOutVertically(
                                    targetOffsetY = { (it * 0.65f).toInt() },
                                    animationSpec = MiniPlayerSpringSpecs.PillEntranceOffset
                                ) + scaleOut(
                                    targetScale = 0.92f,
                                    animationSpec = MiniPlayerSpringSpecs.PillEntranceScale
                                ) + fadeOut(spring(stiffness = 680f))
                            ) {
                                if (playerState.currentTrack != null) {
                                    Column {
                                        FloatingLiquidMiniPlayer(
                                            playerState = playerState,
                                            isLiked = false,
                                            onExpand = { isExpanded = true },
                                            onTogglePlayPause = {},
                                            onSkipNext = {},
                                            onSkipPrevious = {},
                                            onToggleLike = {},
                                            onBoundsInRootChanged = { miniPlayerBounds = it }
                                        )
                                        Spacer(modifier = Modifier.height(10.dp))
                                    }
                                }
                            }
                        }
                    }

                    if (hasActiveTrack && (isFullPlayerVisible || expandProgress.value > 0.015f)) {
                        FullBleedNowPlayingScreen(
                            playerState = playerState,
                            soundLabSettings = soundLabSettings,
                            isLiked = false,
                            isDownloaded = false,
                            downloadProgress = null,
                            onDismiss = { isExpanded = false },
                            onTogglePlayPause = {},
                            onSkipNext = {},
                            onSkipPrevious = {},
                            onSeekTo = {},
                            onRetryTrack = {},
                            onStartRadio = {},
                            onToggleAutoPlayRadio = {},
                            onSaveQueueAsPlaylist = {},
                            onToggleLyricsTranslation = {},
                            onToggleShuffle = {},
                            onCycleRepeat = {},
                            onToggleLike = {},
                            onToggleDownload = {},
                            onAddToPlaylist = {},
                            onOpenSoundLab = {},
                            onJumpQueueIndex = {},
                            onMoveQueueItem = { _, _ -> },
                            onRemoveQueueItem = {},
                            expandProgress = expandProgress.value,
                            originBounds = miniPlayerBounds
                        )
                    }
                }
            }
        }

        composeTestRule.waitForIdle()

        // 1. Play track -> mini-player appears without crash
        composeTestRule.runOnUiThread {
            playerState = playerState.copy(
                currentTrack = trackOne,
                queue = listOf(trackOne, trackTwo),
                currentIndex = 0,
                isPlaying = true
            )
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("floating_mini_player").assertExists()

        // 2. Expand mini-player -> full Now Playing screen morphs in cleanly
        composeTestRule.runOnUiThread {
            isExpanded = true
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("full_now_playing_screen").assertExists()

        // 3. Switch tracks & layout style while expanded -> no crash
        composeTestRule.runOnUiThread {
            playerState = playerState.copy(currentTrack = trackTwo, currentIndex = 1)
            soundLabSettings = soundLabSettings.copy(playerLayoutStyle = PlayerLayoutStyle.ECHO_MATERIAL)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("full_now_playing_screen").assertExists()

        // 4. Collapse back to mini-player -> full player unmounts after spring settles
        composeTestRule.runOnUiThread {
            isExpanded = false
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("floating_mini_player").assertExists()
        composeTestRule.onNodeWithTag("full_now_playing_screen").assertDoesNotExist()
    }
}
