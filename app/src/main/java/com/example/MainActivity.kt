package com.example

import android.app.Activity
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import com.example.ui.screens.AlbumArtistDetailOverlay
import com.example.ui.screens.EchoAddonManagerSheet
import com.example.ui.screens.FloatingLiquidMiniPlayer
import com.example.ui.screens.FullBleedNowPlayingScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.MusicDnaSheet
import com.example.ui.screens.MusicFlowScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.SoundLabScreen
import com.example.ui.screens.YouTubeLoginScreen
import com.example.ui.theme.FrostWhite
import com.example.ui.theme.LiquidMusicTheme
import com.example.ui.theme.LocalLiquidPalette
import com.example.ui.viewmodel.DetailOverlayState
import com.example.ui.viewmodel.DiscoveryViewModel
import com.example.ui.viewmodel.LibraryViewModel
import com.example.ui.viewmodel.PlayerViewModel
import com.example.ui.viewmodel.ProfileViewModel
import com.example.ui.viewmodel.SoundLabViewModel
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure Firebase App Check with launch intent before any UI or Firebase calls
        configureAppCheck(this, intent)

        // STEP 2: Enable full edge-to-edge AMOLED dark transparency
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )

        val container = LiquidAppContainer.getInstance(applicationContext)
        val factory = LiquidViewModelFactory(container)

        setContent {
            LiquidMusicRoot(factory = factory)
        }

        // Apply preferred high refresh rate after the initial SurfaceSyncGroup frame commits
        window.decorView.post {
            requestHighestRefreshRate()
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
    val searchHistory by discoveryViewModel.searchHistory.collectAsStateWithLifecycle()

    val playlists by libraryViewModel.playlists.collectAsStateWithLifecycle()
    val likedTracks by libraryViewModel.likedTracks.collectAsStateWithLifecycle()
    val savedAlbums by libraryViewModel.savedAlbums.collectAsStateWithLifecycle()
    val savedAlbumIds by libraryViewModel.savedAlbumIds.collectAsStateWithLifecycle()
    val downloadedTracks by libraryViewModel.downloadedTracks.collectAsStateWithLifecycle()
    val localTracks by libraryViewModel.localTracks.collectAsStateWithLifecycle()
    val hasAudioPermission by libraryViewModel.hasAudioPermission.collectAsStateWithLifecycle()
    val transferStatus by libraryViewModel.transferStatus.collectAsStateWithLifecycle()

    val soundLabSettings by soundLabViewModel.settings.collectAsStateWithLifecycle()
    val bandLabels by soundLabViewModel.bandLabels.collectAsStateWithLifecycle()
    val hardwareAttached by soundLabViewModel.hardwareAttached.collectAsStateWithLifecycle()
    val cacheSizeBytes by soundLabViewModel.cacheSizeBytes.collectAsStateWithLifecycle()
    val activeVisitorData by soundLabViewModel.activeVisitorData.collectAsStateWithLifecycle()
    val settingsActionNotice by soundLabViewModel.settingsActionNotice.collectAsStateWithLifecycle()
    val configuredAddons by soundLabViewModel.configuredAddons.collectAsStateWithLifecycle()
    val ytmSyncInProgress by soundLabViewModel.ytmSyncInProgress.collectAsStateWithLifecycle()

    val userProfile by profileViewModel.userProfile.collectAsStateWithLifecycle()
    val wrappedStats by profileViewModel.wrappedStats.collectAsStateWithLifecycle()
    val backupState by profileViewModel.backupState.collectAsStateWithLifecycle()

    var selectedTab by rememberSaveable { mutableStateOf(MainTab.HOME) }
    var isFlowModeActive by rememberSaveable { mutableStateOf(false) }
    var isNowPlayingExpanded by rememberSaveable { mutableStateOf(false) }
    var pendingPlaylistTrack by remember { mutableStateOf<Track?>(null) }
    var showYouTubeLogin by remember { mutableStateOf(false) }
    var showAddonManager by remember { mutableStateOf(false) }
    var dnaTargetTrack by remember { mutableStateOf<Track?>(null) }

    val downloadedIds = remember(downloadedPaths) { downloadedPaths.keys }
    val activeToastMessage = playerState.statusToast ?: settingsActionNotice

    // Enforce real Android Window privacy (FLAG_SECURE) & Keep Screen On (FLAG_KEEP_SCREEN_ON)
    val activityWindow = (LocalContext.current as? Activity)?.window
    LaunchedEffect(soundLabSettings.disableScreenshotPrivacy, soundLabSettings.keepScreenOn, playerState.isPlaying) {
        val window = activityWindow ?: return@LaunchedEffect
        if (soundLabSettings.disableScreenshotPrivacy) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        if (soundLabSettings.keepScreenOn && playerState.isPlaying) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Auto-dismiss transient player or settings status toast after 2.6s
    LaunchedEffect(activeToastMessage) {
        if (activeToastMessage != null) {
            delay(2600L)
            playerViewModel.clearStatusToast()
            soundLabViewModel.clearActionNotice()
        }
    }

    // Defer heavy AGSL backdrop shader compilation & offscreen layer capture until after the initial
    // SurfaceSyncGroup frames commit so cold launch never stalls past the 1000ms WMS sync deadline.
    var isBackdropReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        withFrameNanos { }
        delay(120L)
        isBackdropReady = true
    }

    LiquidMusicTheme(
        targetPalette = playerState.palette,
        glassQualityTier = soundLabSettings.glassQualityTier,
        glassConfig = soundLabSettings.glassConfig
    ) {
        if (!userProfile.isSignedIn) {
            OnboardingScreen(
                isBackdropReady = isBackdropReady,
                onSignInWithEmail = { name, email -> profileViewModel.signInWithEmail(name, email) },
                onContinueWithGoogleDemo = { profileViewModel.signInWithGoogleDemo() },
                onSkipAsGuest = { profileViewModel.continueAsGuest() }
            )
        } else {
            val isDetailOverlayOpen = discoveryState.detailOverlay !is DetailOverlayState.None
            BackHandler(enabled = selectedTab != MainTab.HOME && !isNowPlayingExpanded && !isDetailOverlayOpen) {
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
                        .then(if (isBackdropReady) Modifier.layerBackdrop(mainBackdrop) else Modifier)
                ) {
                    LiquidAuroraCanvas(
                        modifier = if (isBackdropReady) Modifier.layerBackdrop(auroraBackdrop) else Modifier,
                        intensity = 0.95f
                    )

                    // Active Tab Screen receives non-cyclic auroraBackdrop so Sound Lab sliders, toggles & buttons refract live aurora
                    CompositionLocalProvider(
                        LocalLiquidBackdrop provides (if (isBackdropReady) auroraBackdrop else null)
                    ) {
                        AnimatedContent(
                            targetState = selectedTab,
                            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(180)) },
                            label = "mainTabContent"
                        ) { tab ->
                            when (tab) {
                                MainTab.HOME -> {
                                    BackHandler(enabled = isFlowModeActive && !isNowPlayingExpanded) {
                                        isFlowModeActive = false
                                    }
                                    if (isFlowModeActive) {
                                        val flowTracks = remember(
                                            discoveryState.quickPicks,
                                            discoveryState.jumpBackIn,
                                            discoveryState.browseSections
                                        ) {
                                            (discoveryState.quickPicks +
                                                discoveryState.jumpBackIn +
                                                discoveryState.browseSections.flatMap { it.tracks })
                                                .distinctBy { it.id }
                                        }
                                        MusicFlowScreen(
                                            feedTracks = flowTracks,
                                            playerState = playerState,
                                            lyricsOffsetMs = soundLabSettings.lyricsOffsetMs,
                                            likedTrackIds = likedTrackIds,
                                            downloadedTrackIds = downloadedIds,
                                            bottomPadding = contentBottomPadding,
                                            onSwitchToHome = { isFlowModeActive = false },
                                            onPlayFlowTrack = { tracks, idx -> playerViewModel.playQueue(tracks, idx) },
                                            onTogglePlayPause = playerViewModel::togglePlayPause,
                                            onToggleLike = playerViewModel::toggleLike,
                                            onToggleDownload = playerViewModel::toggleDownload,
                                            onPlayNext = playerViewModel::playNext,
                                            onAddToPlaylist = { pendingPlaylistTrack = it },
                                            onStartRadio = playerViewModel::startRadio,
                                            onOpenDna = { track ->
                                                dnaTargetTrack = track
                                                playerViewModel.loadTrackDna(track)
                                            },
                                            onExpandPlayer = { isNowPlayingExpanded = true }
                                        )
                                    } else {
                                        HomeScreen(
                                            uiState = discoveryState,
                                            userProfile = userProfile,
                                            activeTrackId = playerState.currentTrack?.id,
                                            isPlaying = playerState.isPlaying,
                                            likedTrackIds = likedTrackIds,
                                            downloadedTrackIds = downloadedIds,
                                            bottomPadding = contentBottomPadding,
                                            onMoodSelected = discoveryViewModel::selectHomeMood,
                                            onPlayQueue = { tracks, idx -> playerViewModel.playQueue(tracks, idx) },
                                            onStartRadio = playerViewModel::startRadio,
                                            onToggleLike = playerViewModel::toggleLike,
                                            onToggleDownload = playerViewModel::toggleDownload,
                                            onAddTrackToPlaylist = { pendingPlaylistTrack = it },
                                            onOpenAlbum = discoveryViewModel::openAlbum,
                                            onOpenArtist = discoveryViewModel::openArtist,
                                            onOpenProfile = { selectedTab = MainTab.PROFILE },
                                            onOpenSoundLab = { selectedTab = MainTab.SOUND_LAB },
                                            onSurpriseMe = {
                                                val mix = discoveryViewModel.buildSurpriseMeQueue()
                                                if (mix.isNotEmpty()) {
                                                    playerViewModel.playQueue(mix, 0, shuffle = true)
                                                }
                                            },
                                            onOpenMusicFlow = { isFlowModeActive = true },
                                            onRefresh = { discoveryViewModel.loadHomeShelves() }
                                        )
                                    }
                                }

                                MainTab.SEARCH -> SearchScreen(
                                    uiState = discoveryState,
                                    searchHistory = searchHistory,
                                    activeTrackId = playerState.currentTrack?.id,
                                    isPlaying = playerState.isPlaying,
                                    likedTrackIds = likedTrackIds,
                                    downloadedTrackIds = downloadedIds,
                                    bottomPadding = contentBottomPadding,
                                    onSearchQueryChange = discoveryViewModel::updateSearchQuery,
                                    onSubmitSearch = discoveryViewModel::submitSearch,
                                    onDeleteHistoryItem = discoveryViewModel::deleteSearchHistoryItem,
                                    onClearHistory = discoveryViewModel::clearSearchHistory,
                                    onSearchFilterSelected = discoveryViewModel::selectSearchFilter,
                                    onVibePromptChange = discoveryViewModel::updateVibePrompt,
                                    onGenerateVibe = discoveryViewModel::triggerVibeDj,
                                    onUseOfflineVibe = discoveryViewModel::triggerOfflineVibeFallback,
                                    onSaveVibePlaylist = discoveryViewModel::saveVibePlaylistToLibrary,
                                    onPlayQueue = { tracks, idx -> playerViewModel.playQueue(tracks, idx) },
                                    onStartRadio = playerViewModel::startRadio,
                                    onOpenAlbum = discoveryViewModel::openAlbum,
                                    onOpenArtist = discoveryViewModel::openArtist,
                                    onOpenPlaylistSummary = { summary ->
                                        discoveryViewModel.loadPlaylistTracksAndPlay(summary) { tracks ->
                                            playerViewModel.playQueue(tracks, 0)
                                        }
                                    },
                                    onToggleLike = playerViewModel::toggleLike,
                                    onToggleDownload = playerViewModel::toggleDownload,
                                    onAddToPlaylist = { pendingPlaylistTrack = it }
                                )

                                MainTab.LIBRARY -> LibraryScreen(
                                    playlists = playlists,
                                    likedTracks = likedTracks,
                                    savedAlbums = savedAlbums,
                                    downloadedTracks = downloadedTracks,
                                    localTracks = localTracks,
                                    hasAudioPermission = hasAudioPermission,
                                    requiredAudioPermission = libraryViewModel.requiredAudioPermission,
                                    transferStatus = transferStatus,
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
                                    onExportPlaylistM3u8 = libraryViewModel::exportPlaylistAsM3u8,
                                    onImportPlaylistUri = libraryViewModel::importPlaylistFromUri,
                                    onClearTransferStatus = libraryViewModel::clearTransferStatus,
                                    onOpenAlbum = discoveryViewModel::openAlbum,
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
                                    onToggleSkipSilence = soundLabViewModel::toggleSkipSilence,
                                    onToggleVolumeNormalization = soundLabViewModel::toggleVolumeNormalization,
                                    onToggleGaplessPlayback = soundLabViewModel::toggleGaplessPlayback,
                                    onToggleAutoPlayRadio = soundLabViewModel::toggleAutoPlayRadio,
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
                                    settings = soundLabSettings,
                                    glassQualityTier = soundLabSettings.glassQualityTier,
                                    glassConfig = soundLabSettings.glassConfig,
                                    cacheSizeBytes = cacheSizeBytes,
                                    maxCacheSizeMb = soundLabSettings.maxCacheSizeMb,
                                    downloadedCount = downloadedTracks.size,
                                    activeVisitorData = activeVisitorData,
                                    backupState = backupState,
                                    shareText = profileViewModel.buildShareableWrappedText(userProfile, wrappedStats),
                                    bottomPadding = contentBottomPadding,
                                    onUpdateDisplayName = profileViewModel::updateDisplayName,
                                    onUpdateEchoSettings = soundLabViewModel::updateEchoSettings,
                                    onSelectGlassQuality = soundLabViewModel::updateGlassQuality,
                                    onApplyGlassOpticalPreset = soundLabViewModel::applyGlassOpticalPreset,
                                    onUpdateGlassConfig = soundLabViewModel::updateGlassConfig,
                                    onUpdateMaxCacheSizeMb = soundLabViewModel::updateMaxCacheSizeMb,
                                    onClearAudioCache = soundLabViewModel::clearAudioCache,
                                    onClearImageCache = soundLabViewModel::clearImageCache,
                                    onClearListenHistory = soundLabViewModel::clearListenHistory,
                                    onClearSearchHistory = soundLabViewModel::clearSearchHistory,
                                    onClearAllDownloads = soundLabViewModel::clearAllDownloads,
                                    onRefreshVisitorData = {
                                        soundLabViewModel.refreshInnerTubeVisitorData {
                                            discoveryViewModel.loadHomeShelves()
                                        }
                                    },
                                    onImportPlaylistUri = libraryViewModel::importPlaylistFromUri,
                                    onExportBackup = profileViewModel::exportFullBackup,
                                    onRestoreBackup = profileViewModel::restoreFullBackup,
                                    onClearBackupMessage = profileViewModel::clearBackupMessage,
                                    onSignOut = profileViewModel::signOut,
                                    configuredAddons = configuredAddons,
                                    ytmSyncInProgress = ytmSyncInProgress,
                                    onOpenYouTubeLogin = { showYouTubeLogin = true },
                                    onSyncYouTubeLibrary = {
                                        soundLabViewModel.syncYouTubeLibraryNow {
                                            discoveryViewModel.loadHomeShelves()
                                        }
                                    },
                                    onDisconnectYouTube = soundLabViewModel::disconnectYouTubeAccount,
                                    onOpenAddonManager = { showAddonManager = true }
                                )
                            }
                        }

                        // Album / Artist Detail Overlay (stays inside mainBackdrop so bottom mini-player still refracts it!)
                        AnimatedVisibility(
                            visible = isDetailOverlayOpen,
                            enter = fadeIn(tween(200)) + slideInVertically(initialOffsetY = { it / 8 }),
                            exit = fadeOut(tween(160)) + slideOutVertically(targetOffsetY = { it / 8 })
                        ) {
                            AlbumArtistDetailOverlay(
                                overlayState = discoveryState.detailOverlay,
                                activeTrackId = playerState.currentTrack?.id,
                                isPlaying = playerState.isPlaying,
                                likedTrackIds = likedTrackIds,
                                downloadedTrackIds = downloadedIds,
                                savedAlbumIds = savedAlbumIds,
                                bottomPadding = contentBottomPadding,
                                onClose = discoveryViewModel::closeDetailOverlay,
                                onRetry = {
                                    when (val overlay = discoveryState.detailOverlay) {
                                        is DetailOverlayState.Album -> discoveryViewModel.openAlbum(overlay.browseId, overlay.source)
                                        is DetailOverlayState.Artist -> discoveryViewModel.openArtist(overlay.browseId, overlay.source)
                                        DetailOverlayState.None -> Unit
                                    }
                                },
                                onPlayQueue = { tracks, idx, shuffle ->
                                    playerViewModel.playQueue(tracks, idx, shuffle)
                                },
                                onStartTrackRadio = playerViewModel::startRadio,
                                onStartArtistRadio = { artist ->
                                    discoveryViewModel.loadArtistRadioAndPlay(artist) { tracks ->
                                        playerViewModel.playQueue(tracks, 0)
                                    }
                                },
                                onToggleSaveAlbum = libraryViewModel::toggleSaveAlbum,
                                onDownloadBatch = playerViewModel::downloadTracksBatch,
                                onToggleLike = playerViewModel::toggleLike,
                                onToggleDownload = playerViewModel::toggleDownload,
                                onAddToPlaylist = { pendingPlaylistTrack = it },
                                onOpenAlbum = discoveryViewModel::openAlbum,
                                onOpenArtist = discoveryViewModel::openArtist
                            )
                        }
                    }
                }

                // Floating Status Toast Pill (e.g., Radio started, Settings updated, Batch downloaded)
                AnimatedVisibility(
                    visible = !activeToastMessage.isNullOrBlank(),
                    enter = fadeIn(tween(180)) + slideInVertically(initialOffsetY = { -it }),
                    exit = fadeOut(tween(180)) + slideOutVertically(targetOffsetY = { -it }),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 12.dp, start = 24.dp, end = 24.dp)
                ) {
                    val palette = LocalLiquidPalette.current
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color(0xDD0C1226))
                            .border(1.dp, palette.primaryGlow.copy(alpha = 0.5f), CircleShape)
                            .clickable {
                                playerViewModel.clearStatusToast()
                                soundLabViewModel.clearActionNotice()
                            }
                            .padding(horizontal = 16.dp, vertical = 9.dp)
                    ) {
                        Text(
                            text = activeToastMessage.orEmpty(),
                            style = MaterialTheme.typography.labelMedium,
                            color = FrostWhite
                        )
                    }
                }

                // Bottom Floating Liquid Glass Dock sampling live mainBackdrop
                CompositionLocalProvider(
                    LocalLiquidBackdrop provides (if (isBackdropReady) mainBackdrop else null)
                ) {
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
                            onTabSelected = {
                                if (isDetailOverlayOpen) discoveryViewModel.closeDetailOverlay()
                                selectedTab = it
                            }
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
                            onRetryTrack = playerViewModel::retryCurrentTrack,
                            onStartRadio = playerViewModel::startRadio,
                            onToggleAutoPlayRadio = playerViewModel::toggleAutoPlayRadio,
                            onSaveQueueAsPlaylist = playerViewModel::saveQueueAsPlaylist,
                            onToggleLyricsTranslation = { playerViewModel.toggleLyricsTranslation() },
                            onToggleShuffle = playerViewModel::toggleShuffle,
                            onCycleRepeat = playerViewModel::cycleRepeatMode,
                            onToggleLike = { playerViewModel.toggleLike(activeTrack) },
                            onToggleDownload = { playerViewModel.toggleDownload(activeTrack) },
                            onAddToPlaylist = { pendingPlaylistTrack = it },
                            onOpenSoundLab = { selectedTab = MainTab.SOUND_LAB },
                            onJumpQueueIndex = playerViewModel::jumpToQueueIndex,
                            onMoveQueueItem = playerViewModel::moveQueueItem,
                            onRemoveQueueItem = playerViewModel::removeQueueItem,
                            onOpenMusicDna = {
                                dnaTargetTrack = activeTrack
                                playerViewModel.loadTrackDna(activeTrack)
                            },
                            onAdjustLyricsOffset = soundLabViewModel::adjustLyricsOffset,
                            onResetLyricsOffset = soundLabViewModel::resetLyricsOffset,
                            onSearchLyrics = playerViewModel::searchLyricsCandidates,
                            onApplyManualLyrics = playerViewModel::applyManualLyrics
                        )
                    }
                }

                // Music DNA Song Meaning & Credits Bottom Sheet
                dnaTargetTrack?.let { trackForDna ->
                    MusicDnaSheet(
                        track = trackForDna,
                        dnaInfo = playerState.trackDna?.takeIf { it.trackId == trackForDna.id },
                        isLoading = playerState.isLoadingDna,
                        onDismiss = { dnaTargetTrack = null }
                    )
                }

                // Echo Addon Manager Bottom Sheet
                if (showAddonManager) {
                    EchoAddonManagerSheet(
                        addons = configuredAddons,
                        onAddAndTestAddon = { url, cb -> soundLabViewModel.addAndTestAddon(url, cb) },
                        onToggleAddon = soundLabViewModel::toggleAddonEnabled,
                        onMovePriority = soundLabViewModel::moveAddonPriority,
                        onRemoveAddon = soundLabViewModel::removeAddon,
                        onDismiss = { showAddonManager = false }
                    )
                }

                // In-App WebView YouTube Music Sign-In Overlay
                AnimatedVisibility(
                    visible = showYouTubeLogin,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(tween(200)),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(tween(180))
                ) {
                    YouTubeLoginScreen(
                        onLoginCaptured = { cookie, visitorData, dataSyncId ->
                            showYouTubeLogin = false
                            soundLabViewModel.completeYouTubeWebViewLogin(cookie, visitorData, dataSyncId) {
                                discoveryViewModel.loadHomeShelves()
                            }
                        },
                        onDismiss = { showYouTubeLogin = false }
                    )
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
