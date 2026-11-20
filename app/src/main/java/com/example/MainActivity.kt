package com.example

import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.core.LiquidAppContainer
import com.example.core.configureAppCheck
import com.example.domain.model.MainTab
import com.example.domain.model.Track
import com.example.ui.glass.LiquidAuroraCanvas
import com.example.ui.glass.LiquidGlassBottomTabBar
import com.example.ui.glass.LocalLiquidBackdrop
import com.example.ui.screens.AddToPlaylistSheet
import com.example.ui.screens.FloatingLiquidMiniPlayer
import com.example.ui.screens.FullBleedNowPlayingScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.SoundLabScreen
import com.example.ui.theme.LiquidMusicTheme
import com.example.ui.viewmodel.DiscoveryViewModel
import com.example.ui.viewmodel.LibraryViewModel
import com.example.ui.viewmodel.PlayerViewModel
import com.example.ui.viewmodel.ProfileViewModel
import com.example.ui.viewmodel.SoundLabViewModel
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure Firebase App Check with launch intent before any UI or Firebase calls
        configureAppCheck(this, intent)

        // STEP 2: Enable full edge-to-edge AMOLED dark transparency & request highest refresh rate
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )
        requestHighestRefreshRate()

        val container = LiquidAppContainer.getInstance(applicationContext)
        val factory = LiquidViewModelFactory(container)

        setContent {
            LiquidMusicRoot(factory = factory)
        }
    }

    @Suppress("DEPRECATION")
    private fun requestHighestRefreshRate() {
        runCatching {
            val activeDisplay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                display
            } else {
                windowManager.defaultDisplay
            } ?: return

            val currentMode = activeDisplay.mode
            val bestMode = activeDisplay.supportedModes
                .filter {
                    it.physicalWidth == currentMode.physicalWidth &&
                        it.physicalHeight == currentMode.physicalHeight
                }
                .maxByOrNull { it.refreshRate }

            if (bestMode != null && bestMode.modeId != currentMode.modeId) {
                val params = window.attributes
                params.preferredDisplayModeId = bestMode.modeId
                window.attributes = params
            }
        }
    }
}

private class LiquidViewModelFactory(
    private val container: LiquidAppContainer
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(PlayerViewModel::class.java) -> PlayerViewModel(container) as T
        modelClass.isAssignableFrom(DiscoveryViewModel::class.java) -> DiscoveryViewModel(container) as T
        modelClass.isAssignableFrom(LibraryViewModel::class.java) -> LibraryViewModel(container) as T
        modelClass.isAssignableFrom(SoundLabViewModel::class.java) -> SoundLabViewModel(container) as T
        modelClass.isAssignableFrom(ProfileViewModel::class.java) -> ProfileViewModel(container) as T
        else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

@Composable
private fun LiquidMusicRoot(factory: ViewModelProvider.Factory) {
    val playerViewModel: PlayerViewModel = viewModel(factory = factory)
    val discoveryViewModel: DiscoveryViewModel = viewModel(factory = factory)
    val libraryViewModel: LibraryViewModel = viewModel(factory = factory)
    val soundLabViewModel: SoundLabViewModel = viewModel(factory = factory)
    val profileViewModel: ProfileViewModel = viewModel(factory = factory)

    val playerState by playerViewModel.uiState.collectAsStateWithLifecycle()
    val likedTrackIds by playerViewModel.likedTrackIds.collectAsStateWithLifecycle()
    val downloadedPaths by playerViewModel.downloadedPathsById.collectAsStateWithLifecycle()
    val downloadProgress by playerViewModel.downloadProgress.collectAsStateWithLifecycle()

    val discoveryState by discoveryViewModel.uiState.collectAsStateWithLifecycle()
    val playlists by libraryViewModel.playlists.collectAsStateWithLifecycle()
    val likedTracks by libraryViewModel.likedTracks.collectAsStateWithLifecycle()
    val downloadedTracks by libraryViewModel.downloadedTracks.collectAsStateWithLifecycle()
    val localTracks by libraryViewModel.localTracks.collectAsStateWithLifecycle()
    val hasAudioPermission by libraryViewModel.hasAudioPermission.collectAsStateWithLifecycle()

    val soundLabSettings by soundLabViewModel.settings.collectAsStateWithLifecycle()
    val bandLabels by soundLabViewModel.bandLabels.collectAsStateWithLifecycle()
    val hardwareAttached by soundLabViewModel.hardwareAttached.collectAsStateWithLifecycle()

    val userProfile by profileViewModel.userProfile.collectAsStateWithLifecycle()
    val wrappedStats by profileViewModel.wrappedStats.collectAsStateWithLifecycle()

    var selectedTab by rememberSaveable { mutableStateOf(MainTab.HOME) }
    var isNowPlayingExpanded by rememberSaveable { mutableStateOf(false) }
    var pendingPlaylistTrack by remember { mutableStateOf<Track?>(null) }

    val downloadedIds = remember(downloadedPaths) { downloadedPaths.keys }

    LiquidMusicTheme(
        targetPalette = playerState.palette,
        glassQualityTier = soundLabSettings.glassQualityTier,
        glassConfig = soundLabSettings.glassConfig
    ) {
        if (!userProfile.isSignedIn) {
            OnboardingScreen(
                onSignInWithEmail = { name, email -> profileViewModel.signInWithEmail(name, email) },
                onContinueWithGoogleDemo = { profileViewModel.signInWithGoogleDemo() },
                onSkipAsGuest = { profileViewModel.continueAsGuest() }
            )
        } else {
            BackHandler(enabled = selectedTab != MainTab.HOME && !isNowPlayingExpanded) {
                selectedTab = MainTab.HOME
            }

            val mainBackdrop = rememberLayerBackdrop()
            val auroraBackdrop = rememberLayerBackdrop()
            val hasActiveTrack = playerState.currentTrack != null
            val contentBottomPadding = if (hasActiveTrack) 186.dp else 112.dp

            Box(modifier = Modifier.fillMaxSize()) {
                // Scene Layer captured into mainBackdrop so floating chrome, dock & mini-player refract real scrolling content!
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .layerBackdrop(mainBackdrop)
                ) {
                    LiquidAuroraCanvas(
                        modifier = Modifier.layerBackdrop(auroraBackdrop),
                        intensity = 0.95f
                    )

                    // Active Tab Screen receives non-cyclic auroraBackdrop so Sound Lab sliders, toggles & buttons refract live aurora
                    CompositionLocalProvider(LocalLiquidBackdrop provides auroraBackdrop) {
                        AnimatedContent(
                            targetState = selectedTab,
                            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(180)) },
                            label = "mainTabContent"
                        ) { tab ->
                            when (tab) {
                                MainTab.HOME -> HomeScreen(
                                    uiState = discoveryState,
                                    userProfile = userProfile,
                                    activeTrackId = playerState.currentTrack?.id,
                                    isPlaying = playerState.isPlaying,
                                    bottomPadding = contentBottomPadding,
                                    onMoodSelected = discoveryViewModel::selectHomeMood,
                                    onPlayQueue = { tracks, idx -> playerViewModel.playQueue(tracks, idx) },
                                    onAddTrackToPlaylist = { pendingPlaylistTrack = it },
                                    onOpenProfile = { selectedTab = MainTab.PROFILE },
                                    onOpenSoundLab = { selectedTab = MainTab.SOUND_LAB },
                                    onRefresh = { discoveryViewModel.loadHomeShelves() }
                                )

                                MainTab.SEARCH -> SearchScreen(
                                    uiState = discoveryState,
                                    activeTrackId = playerState.currentTrack?.id,
                                    isPlaying = playerState.isPlaying,
                                    likedTrackIds = likedTrackIds,
                                    downloadedTrackIds = downloadedIds,
                                    bottomPadding = contentBottomPadding,
                                    onSearchQueryChange = discoveryViewModel::updateSearchQuery,
                                    onSearchFilterSelected = discoveryViewModel::selectSearchFilter,
                                    onVibePromptChange = discoveryViewModel::updateVibePrompt,
                                    onGenerateVibe = discoveryViewModel::triggerVibeDj,
                                    onUseOfflineVibe = discoveryViewModel::triggerOfflineVibeFallback,
                                    onSaveVibePlaylist = discoveryViewModel::saveVibePlaylistToLibrary,
                                    onPlayQueue = { tracks, idx -> playerViewModel.playQueue(tracks, idx) },
                                    onToggleLike = playerViewModel::toggleLike,
                                    onToggleDownload = playerViewModel::toggleDownload,
                                    onAddToPlaylist = { pendingPlaylistTrack = it }
                                )

                                MainTab.LIBRARY -> LibraryScreen(
                                    playlists = playlists,
                                    likedTracks = likedTracks,
                                    downloadedTracks = downloadedTracks,
                                    localTracks = localTracks,
                                    hasAudioPermission = hasAudioPermission,
                                    requiredAudioPermission = libraryViewModel.requiredAudioPermission,
                                    activeTrackId = playerState.currentTrack?.id,
                                    isPlaying = playerState.isPlaying,
                                    likedTrackIds = likedTrackIds,
                                    downloadedTrackIds = downloadedIds,
                                    bottomPadding = contentBottomPadding,
                                    onAudioPermissionGranted = {
                                        libraryViewModel.refreshLocalAudio()
                                        discoveryViewModel.refreshLocalTracks()
                                    },
                                    onCreatePlaylist = { name, desc, cover ->
                                        libraryViewModel.createPlaylist(name, desc, cover)
                                    },
                                    onUpdatePlaylist = libraryViewModel::updatePlaylist,
                                    onDeletePlaylist = libraryViewModel::deletePlaylist,
                                    onRemoveTrackFromPlaylist = libraryViewModel::removeTrackFromPlaylist,
                                    onPlayQueue = { tracks, idx -> playerViewModel.playQueue(tracks, idx) },
                                    onToggleLike = playerViewModel::toggleLike,
                                    onToggleDownload = playerViewModel::toggleDownload,
                                    onAddToPlaylist = { pendingPlaylistTrack = it }
                                )

                                MainTab.SOUND_LAB -> SoundLabScreen(
                                    settings = soundLabSettings,
                                    bandLabels = bandLabels,
                                    hardwareAttached = hardwareAttached,
                                    sleepTimerOption = playerState.sleepTimerOption,
                                    sleepTimerRemainingSec = playerState.sleepTimerRemainingSec,
                                    bottomPadding = contentBottomPadding,
                                    onApplyMagicPreset = soundLabViewModel::applyMagicPreset,
                                    onToggleEightD = soundLabViewModel::toggleEightD,
                                    onSelectEqPreset = soundLabViewModel::selectEqPreset,
                                    onUpdateBandLevel = soundLabViewModel::updateBandLevel,
                                    onUpdateBassBoost = soundLabViewModel::updateBassBoost,
                                    onSelectReverb = soundLabViewModel::selectReverb,
                                    onUpdateSpeed = soundLabViewModel::updateSpeed,
                                    onUpdatePitch = soundLabViewModel::updatePitch,
                                    onUpdateCrossfade = soundLabViewModel::updateCrossfade,
                                    onSelectSleepTimer = playerViewModel::setSleepTimer,
                                    onUpdateAudioQuality = soundLabViewModel::updateAudioQuality
                                )

                                MainTab.PROFILE -> ProfileScreen(
                                    userProfile = userProfile,
                                    wrappedStats = wrappedStats,
                                    glassQualityTier = soundLabSettings.glassQualityTier,
                                    glassConfig = soundLabSettings.glassConfig,
                                    shareText = profileViewModel.buildShareableWrappedText(userProfile, wrappedStats),
                                    bottomPadding = contentBottomPadding,
                                    onUpdateDisplayName = profileViewModel::updateDisplayName,
                                    onSelectGlassQuality = soundLabViewModel::updateGlassQuality,
                                    onApplyGlassOpticalPreset = soundLabViewModel::applyGlassOpticalPreset,
                                    onUpdateGlassConfig = soundLabViewModel::updateGlassConfig,
                                    onSignOut = profileViewModel::signOut
                                )
                            }
                        }
                    }
                }

                // Bottom Floating Liquid Glass Dock sampling live mainBackdrop
                CompositionLocalProvider(LocalLiquidBackdrop provides mainBackdrop) {
                    Column(
                        modifier = Modifier.align(Alignment.BottomCenter)
                    ) {
                        AnimatedVisibility(
                            visible = hasActiveTrack,
                            enter = slideInVertically { it / 2 } + fadeIn(),
                            exit = slideOutVertically { it / 2 } + fadeOut()
                        ) {
                            val activeTrack = playerState.currentTrack
                            if (activeTrack != null) {
                                Column {
                                    FloatingLiquidMiniPlayer(
                                        playerState = playerState,
                                        isLiked = likedTrackIds.contains(activeTrack.id),
                                        onExpand = { isNowPlayingExpanded = true },
                                        onTogglePlayPause = playerViewModel::togglePlayPause,
                                        onSkipNext = playerViewModel::skipNext,
                                        onSkipPrevious = playerViewModel::skipPrevious,
                                        onToggleLike = { playerViewModel.toggleLike(activeTrack) }
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                }
                            }
                        }

                        LiquidGlassBottomTabBar(
                            selectedTab = selectedTab,
                            onTabSelected = { selectedTab = it }
                        )
                    }
                }

                // Full-Bleed Cover Art Now Playing Overlay
                AnimatedVisibility(
                    visible = isNowPlayingExpanded && playerState.currentTrack != null,
                    enter = slideInVertically(
                        initialOffsetY = { it },
                        animationSpec = spring(dampingRatio = 0.82f, stiffness = 340f)
                    ) + fadeIn(tween(200)),
                    exit = slideOutVertically(
                        targetOffsetY = { it },
                        animationSpec = spring(dampingRatio = 0.88f, stiffness = 420f)
                    ) + fadeOut(tween(180))
                ) {
                    val activeTrack = playerState.currentTrack
                    if (activeTrack != null) {
                        FullBleedNowPlayingScreen(
                            playerState = playerState,
                            soundLabSettings = soundLabSettings,
                            isLiked = likedTrackIds.contains(activeTrack.id),
                            isDownloaded = downloadedIds.contains(activeTrack.id),
                            downloadProgress = downloadProgress[activeTrack.id],
                            onDismiss = { isNowPlayingExpanded = false },
                            onTogglePlayPause = playerViewModel::togglePlayPause,
                            onSkipNext = playerViewModel::skipNext,
                            onSkipPrevious = playerViewModel::skipPrevious,
                            onSeekTo = playerViewModel::seekTo,
                            onToggleShuffle = playerViewModel::toggleShuffle,
                            onCycleRepeat = playerViewModel::cycleRepeatMode,
                            onToggleLike = { playerViewModel.toggleLike(activeTrack) },
                            onToggleDownload = { playerViewModel.toggleDownload(activeTrack) },
                            onAddToPlaylist = { pendingPlaylistTrack = it },
                            onOpenSoundLab = { selectedTab = MainTab.SOUND_LAB },
                            onJumpQueueIndex = playerViewModel::jumpToQueueIndex,
                            onMoveQueueItem = playerViewModel::moveQueueItem,
                            onRemoveQueueItem = playerViewModel::removeQueueItem
                        )
                    }
                }

                // Add-to-Playlist Modal Sheet
                pendingPlaylistTrack?.let { trackToAdd ->
                    AddToPlaylistSheet(
                        track = trackToAdd,
                        playlists = playlists,
                        onDismiss = { pendingPlaylistTrack = null },
                        onAddToExisting = libraryViewModel::addTrackToPlaylist,
                        onCreateAndAdd = { name, track ->
                            libraryViewModel.createPlaylist(
                                name = name,
                                description = "",
                                coverArtUri = track.artworkUrl,
                                initialTrack = track
                            )
                        }
                    )
                }
            }
        }
    }
}
