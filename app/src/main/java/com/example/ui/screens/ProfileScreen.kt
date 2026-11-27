package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.AudioLoudnessPreset
import com.example.domain.model.AudioQuality
import com.example.domain.model.EchoAddonConfig
import com.example.domain.model.EchoPlaybackEngine
import com.example.domain.model.GlassOpticalPreset
import com.example.domain.model.GlassQualityTier
import com.example.domain.model.LiquidGlassConfig
import com.example.domain.model.LyricsAlignStyle
import com.example.domain.model.PlayerBackgroundStyle
import com.example.domain.model.PlayerLayoutStyle
import com.example.domain.model.PlayerSliderStyle
import com.example.domain.model.QuickPicksMode
import com.example.domain.model.SoundLabSettings
import com.example.domain.model.TopStatItem
import com.example.domain.model.UserProfile
import com.example.domain.model.WrappedStats
import com.example.ui.components.EchoSurfaceCard
import com.example.ui.glass.LiquidGlassButton
import com.example.ui.glass.LiquidGlassChip
import com.example.ui.glass.LiquidGlassSlider
import com.example.ui.glass.LiquidGlassSurface
import com.example.ui.glass.LiquidGlassToggle
import com.example.ui.glass.TrackArtwork
import com.example.ui.theme.EmeraldPulse
import com.example.ui.theme.AmberGlow
import com.example.ui.theme.ArcticCyan
import com.example.ui.theme.CrystalIce
import com.example.ui.theme.DeepIndigoAurora
import com.example.ui.theme.DeepVoid
import com.example.ui.theme.FrostWhite
import com.example.ui.theme.LocalLiquidPalette
import com.example.ui.theme.LuminousRose
import com.example.ui.theme.MistSecondary
import com.example.ui.viewmodel.BackupUiState
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class EchoSettingsSection(val label: String) {
    ALL("All Settings"),
    ACCOUNT("Account & Sync"),
    APPEARANCE("Appearance"),
    PLAYER("Player & Audio"),
    CONTENT("Content & Lyrics"),
    AI("AI & Translation"),
    EXTENSIONS("320kbps & Lossless"),
    PRIVACY("Privacy"),
    STORAGE("Storage & Cache"),
    BACKUP("Backup & Wrapped")
}

private val COUNTRY_OPTIONS = listOf(
    "US" to "United States (US)",
    "IN" to "India (IN)",
    "GB" to "United Kingdom (GB)",
    "CA" to "Canada (CA)",
    "AU" to "Australia (AU)",
    "DE" to "Germany (DE)",
    "FR" to "France (FR)",
    "JP" to "Japan (JP)",
    "KR" to "South Korea (KR)",
    "BR" to "Brazil (BR)",
    "ES" to "Spain (ES)",
    "MX" to "Mexico (MX)",
    "ID" to "Indonesia (ID)",
    "PH" to "Philippines (PH)"
)

private val LANGUAGE_OPTIONS = listOf(
    "en" to "English (en)",
    "hi" to "Hindi (hi)",
    "es" to "Spanish (es)",
    "fr" to "French (fr)",
    "de" to "German (de)",
    "ja" to "Japanese (ja)",
    "ko" to "Korean (ko)",
    "pt" to "Portuguese (pt)"
)

@Composable
fun ProfileScreen(
    userProfile: UserProfile,
    wrappedStats: WrappedStats,
    settings: SoundLabSettings,
    glassQualityTier: GlassQualityTier,
    glassConfig: LiquidGlassConfig,
    cacheSizeBytes: Long,
    maxCacheSizeMb: Int,
    downloadedCount: Int = 0,
    activeVisitorData: String = "",
    backupState: BackupUiState,
    shareText: String,
    bottomPadding: Dp,
    onUpdateDisplayName: (String) -> Unit,
    onUpdateEchoSettings: (String?, (SoundLabSettings) -> SoundLabSettings) -> Unit,
    onSelectGlassQuality: (GlassQualityTier) -> Unit,
    onApplyGlassOpticalPreset: (GlassOpticalPreset) -> Unit,
    onUpdateGlassConfig: ((LiquidGlassConfig) -> LiquidGlassConfig) -> Unit,
    onUpdateMaxCacheSizeMb: (Int) -> Unit,
    onClearAudioCache: () -> Unit,
    onClearImageCache: () -> Unit = {},
    onClearListenHistory: () -> Unit = {},
    onClearSearchHistory: () -> Unit = {},
    onClearAllDownloads: () -> Unit = {},
    onRefreshVisitorData: () -> Unit = {},
    onImportPlaylistUri: (Uri) -> Unit = {},
    onExportBackup: (Uri) -> Unit,
    onRestoreBackup: (Uri) -> Unit,
    onClearBackupMessage: () -> Unit,
    onSignOut: () -> Unit,
    configuredAddons: List<EchoAddonConfig> = emptyList(),
    ytmSyncInProgress: Boolean = false,
    onOpenYouTubeLogin: () -> Unit = {},
    onSyncYouTubeLibrary: () -> Unit = {},
    onDisconnectYouTube: () -> Unit = {},
    onOpenAddonManager: () -> Unit = {}
) {
    val palette = LocalLiquidPalette.current
    val context = LocalContext.current
    val shareChooserTitle = stringResource(R.string.share_wrapped_chooser)

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedSection by rememberSaveable { mutableStateOf(EchoSettingsSection.ALL) }

    var showEditNameDialog by remember { mutableStateOf(false) }
    var showCookieDialog by remember { mutableStateOf(false) }
    var showExtensionServerDialog by remember { mutableStateOf(false) }
    var showProxyDialog by remember { mutableStateOf(false) }
    var showBlockedArtistsDialog by remember { mutableStateOf(false) }
    var showClearListenConfirm by remember { mutableStateOf(false) }
    var showClearSearchConfirm by remember { mutableStateOf(false) }
    var showClearDownloadsConfirm by remember { mutableStateOf(false) }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) onExportBackup(uri)
    }
    val restoreBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onRestoreBackup(uri)
    }
    val importPlaylistLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onImportPlaylistUri(uri)
    }

    val q = searchQuery.trim().lowercase()
    fun matchesSection(sec: EchoSettingsSection, keywords: String): Boolean {
        val sectionOk = selectedSection == EchoSettingsSection.ALL || selectedSection == sec
        val queryOk = q.isBlank() || keywords.lowercase().contains(q) || sec.label.lowercase().contains(q)
        return sectionOk && queryOk
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .testTag("profile_screen"),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Echo Music Settings Header & Searchable Settings Bar
        item(key = "echo_settings_header") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Echo Settings & Studio",
                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                    color = FrostWhite
                )
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search all Echo Music settings…", color = MistSecondary) },
                    leadingIcon = {
                        Icon(Icons.Rounded.Search, contentDescription = "Search settings", tint = palette.primaryGlow)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Rounded.Clear, contentDescription = "Clear", tint = MistSecondary)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(22.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = FrostWhite,
                        unfocusedTextColor = FrostWhite,
                        focusedContainerColor = Color(0x6610172B),
                        unfocusedContainerColor = Color(0x4D0D1324),
                        focusedBorderColor = palette.primaryGlow,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.14f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_search_input")
                )

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(EchoSettingsSection.entries, key = { it.name }) { sec ->
                        LiquidGlassChip(
                            label = sec.label,
                            selected = selectedSection == sec,
                            onClick = { selectedSection = sec },
                            modifier = Modifier.testTag("settings_tab_${sec.name.lowercase()}")
                        )
                    }
                }
            }
        }

        // 1. ACCOUNT & YOUTUBE SYNC
        if (matchesSection(EchoSettingsSection.ACCOUNT, "account login youtube cookie sapisid visitor data sync profile")) {
            item(key = "echo_account_section") {
                SettingsGroupCard(
                    icon = Icons.Rounded.AccountCircle,
                    title = "Account & YouTube Music Sync",
                    subtitle = "Profile & YouTube Music InnerTube login"
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(palette.primaryGlow.copy(alpha = 0.4f), palette.deepAurora)
                                    )
                                )
                                .border(1.2.dp, palette.primaryGlow.copy(alpha = 0.65f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = userProfile.displayName.take(1).uppercase(),
                                style = MaterialTheme.typography.headlineSmall,
                                color = FrostWhite
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(userProfile.displayName, style = MaterialTheme.typography.titleMedium, color = FrostWhite)
                            Text(
                                text = userProfile.handle + if (userProfile.isGuest) " · Guest" else " · Signed In",
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.secondaryGlow
                            )
                        }
                        IconButton(onClick = { showEditNameDialog = true }, modifier = Modifier.testTag("profile_edit_name_button")) {
                            Icon(Icons.Rounded.Edit, contentDescription = "Edit display name", tint = FrostWhite)
                        }
                        IconButton(onClick = onSignOut, modifier = Modifier.testTag("profile_sign_out_button")) {
                            Icon(Icons.Rounded.Logout, contentDescription = "Sign out", tint = LuminousRose)
                        }
                    }

                    // YouTube Music Account Connection Card (Echo Music WebView Login + Live Library Sync)
                    val isYtmConnected = settings.innerTubeCookie.isNotBlank()
                    EchoSurfaceCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 18.dp,
                        elevated = true,
                        accentBorder = if (isYtmConnected) EmeraldPulse.copy(alpha = 0.45f) else palette.primaryGlow.copy(alpha = 0.25f)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (settings.ytmAccountAvatarUrl.isNotBlank()) {
                                    TrackArtwork(
                                        artworkUrl = settings.ytmAccountAvatarUrl,
                                        contentDescription = settings.ytmAccountName,
                                        cornerRadius = 22.dp,
                                        modifier = Modifier.size(44.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (isYtmConnected) {
                                            settings.ytmAccountName.ifBlank { "YouTube Music Connected" }
                                        } else {
                                            "YouTube Music Account"
                                        },
                                        style = MaterialTheme.typography.titleSmall,
                                        color = FrostWhite
                                    )
                                    Text(
                                        text = if (isYtmConnected) {
                                            buildString {
                                                if (settings.ytmAccountHandle.isNotBlank()) append("${settings.ytmAccountHandle} · ")
                                                append(if (settings.ytmLastSyncedAt > 0L) "Library synced" else "Ready to sync likes & playlists")
                                            }
                                        } else {
                                            "Sign in via WebView to sync Liked Songs, Playlists & personalized Home"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isYtmConnected) EmeraldPulse else MistSecondary
                                    )
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                LiquidGlassButton(
                                    onClick = if (isYtmConnected) onSyncYouTubeLibrary else onOpenYouTubeLogin,
                                    accentGlow = true,
                                    enabled = !ytmSyncInProgress,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(42.dp)
                                        .testTag("ytm_primary_auth_sync_button")
                                ) {
                                    Row(
                                        modifier = Modifier.align(Alignment.Center),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        if (ytmSyncInProgress) {
                                            CircularProgressIndicator(color = FrostWhite, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                                            Text("Syncing…", style = MaterialTheme.typography.labelLarge, color = FrostWhite)
                                        } else {
                                            Text(
                                                text = if (isYtmConnected) "Sync Library Now" else "Sign in with YouTube Music",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = FrostWhite
                                            )
                                        }
                                    }
                                }
                                if (isYtmConnected) {
                                    LiquidGlassButton(
                                        onClick = onDisconnectYouTube,
                                        modifier = Modifier
                                            .height(42.dp)
                                            .testTag("ytm_disconnect_button")
                                    ) {
                                        Text(
                                            text = "Disconnect",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = LuminousRose,
                                            modifier = Modifier
                                                .align(Alignment.Center)
                                                .padding(horizontal = 12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    SettingsToggleRow(
                        title = "Auto-Sync YouTube Music Library",
                        subtitle = "Sync playlists, likes, albums & personalized recommendations",
                        checked = settings.ytmSyncEnabled,
                        onCheckedChange = { v -> onUpdateEchoSettings("YouTube Music sync ${if (v) "enabled" else "disabled"}") { it.copy(ytmSyncEnabled = v) } }
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        LiquidGlassButton(
                            onClick = { showCookieDialog = true },
                            modifier = Modifier.weight(1f).height(40.dp)
                        ) {
                            Text(
                                text = "Manual Cookie",
                                style = MaterialTheme.typography.labelMedium,
                                color = FrostWhite,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                        LiquidGlassButton(
                            onClick = onRefreshVisitorData,
                            modifier = Modifier.weight(1f).height(40.dp)
                        ) {
                            Text(
                                text = "Refresh VisitorData",
                                style = MaterialTheme.typography.labelMedium,
                                color = FrostWhite,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }
                }
            }
        }

        // 2. APPEARANCE, LYRICS UI & LIQUID GLASS STUDIO
        if (matchesSection(EchoSettingsSection.APPEARANCE, "appearance theme pure black amoled dynamic player background slider wavy squiggly lyrics alignment animation font size home quick picks hero")) {
            item(key = "echo_appearance_section") {
                SettingsGroupCard(
                    icon = Icons.Rounded.Palette,
                    title = "Appearance, Home Layout & Lyrics UI",
                    subtitle = "Echo Music visual customization, player style & lyrics animations"
                ) {
                    SettingsToggleRow(
                        title = "Dynamic Album-Art Theme",
                        subtitle = "Extract live aurora colors from currently playing track artwork",
                        checked = settings.dynamicThemeEnabled,
                        onCheckedChange = { v -> onUpdateEchoSettings(null) { it.copy(dynamicThemeEnabled = v) } }
                    )
                    SettingsToggleRow(
                        title = "Pure Black AMOLED Mode",
                        subtitle = "Deep void #050714 OLED background contrast",
                        checked = settings.pureBlackAmoled,
                        onCheckedChange = { v -> onUpdateEchoSettings(null) { it.copy(pureBlackAmoled = v) } }
                    )

                    Text("Now Playing Layout Style", style = MaterialTheme.typography.labelLarge, color = palette.secondaryGlow)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(PlayerLayoutStyle.entries, key = { it.name }) { layout ->
                            LiquidGlassChip(
                                label = layout.label,
                                selected = settings.playerLayoutStyle == layout,
                                onClick = {
                                    onUpdateEchoSettings("Now Playing: ${layout.label}") {
                                        it.copy(playerLayoutStyle = layout)
                                    }
                                }
                            )
                        }
                    }

                    Text("Player Background Style", style = MaterialTheme.typography.labelLarge, color = palette.secondaryGlow)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(PlayerBackgroundStyle.entries, key = { it.name }) { style ->
                            LiquidGlassChip(
                                label = style.label,
                                selected = settings.playerBackgroundStyle == style,
                                onClick = { onUpdateEchoSettings(null) { it.copy(playerBackgroundStyle = style) } }
                            )
                        }
                    }

                    Text("Player Seekbar Slider Style", style = MaterialTheme.typography.labelLarge, color = palette.secondaryGlow)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PlayerSliderStyle.entries.forEach { style ->
                            LiquidGlassChip(
                                label = style.label,
                                selected = settings.playerSliderStyle == style,
                                onClick = { onUpdateEchoSettings(null) { it.copy(playerSliderStyle = style) } }
                            )
                        }
                    }

                    SettingsToggleRow(
                        title = "Show Quick Picks Speed Dial on Home",
                        subtitle = "4-row horizontal swipeable quick picks grid at top of Home",
                        checked = settings.showSpeedDialQuickPicks,
                        onCheckedChange = { v -> onUpdateEchoSettings(null) { it.copy(showSpeedDialQuickPicks = v) } }
                    )
                    SettingsToggleRow(
                        title = "Show Mood & Genre Chips on Home",
                        subtitle = "Display live YouTube Music mood & genre filter chips",
                        checked = settings.showHomeMoodAndGenres,
                        onCheckedChange = { v -> onUpdateEchoSettings(null) { it.copy(showHomeMoodAndGenres = v) } }
                    )
                    SettingsToggleRow(
                        title = "Randomize Home Shelf Order",
                        subtitle = "Shuffle shelves on Home screen refresh",
                        checked = settings.randomizeHomeOrder,
                        onCheckedChange = { v -> onUpdateEchoSettings(null) { it.copy(randomizeHomeOrder = v) } }
                    )
                    SettingsToggleRow(
                        title = "Swipe Player Artwork to Change Song",
                        subtitle = "Horizontal swipe gesture on album art skips tracks",
                        checked = settings.swipeToSongEnabled,
                        onCheckedChange = { v -> onUpdateEchoSettings(null) { it.copy(swipeToSongEnabled = v) } }
                    )
                    SettingsToggleRow(
                        title = "Crop Album Art to Fill Square",
                        subtitle = "Center-crop non-square video thumbnails",
                        checked = settings.cropAlbumArt,
                        onCheckedChange = { v -> onUpdateEchoSettings(null) { it.copy(cropAlbumArt = v) } }
                    )

                    Text("Lyrics Text Position", style = MaterialTheme.typography.labelLarge, color = palette.secondaryGlow)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LyricsAlignStyle.entries.forEach { align ->
                            LiquidGlassChip(
                                label = align.label,
                                selected = settings.lyricsAlignment == align,
                                onClick = { onUpdateEchoSettings(null) { it.copy(lyricsAlignment = align) } }
                            )
                        }
                    }

                    GlassSliderRow(
                        label = "Lyrics Text Size",
                        readout = "${settings.lyricsFontSizeSp.roundToInt()} sp",
                        value = settings.lyricsFontSizeSp,
                        range = 14f..34f,
                        tag = "slider_lyrics_font_size"
                    ) { v -> onUpdateEchoSettings(null) { it.copy(lyricsFontSizeSp = v) } }

                    GlassSliderRow(
                        label = "Lyrics Line Spacing",
                        readout = "${"%.2f".format(settings.lyricsLineSpacing)}x",
                        value = settings.lyricsLineSpacing,
                        range = 1.0f..2.0f,
                        tag = "slider_lyrics_line_spacing"
                    ) { v -> onUpdateEchoSettings(null) { it.copy(lyricsLineSpacing = v) } }
                }
            }

            item(key = "glass_tuning_studio") {
                GlassTuningStudioCard(
                    glassConfig = glassConfig,
                    onApplyPreset = onApplyGlassOpticalPreset,
                    onUpdateConfig = onUpdateGlassConfig
                )
            }

            item(key = "glass_quality_selector") {
                EchoSurfaceCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 24.dp) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.BlurOn, contentDescription = null, tint = palette.secondaryGlow)
                            Text("Liquid Glass 120Hz Performance Tier", style = MaterialTheme.typography.titleMedium, color = FrostWhite)
                        }
                        GlassQualityTier.entries.forEach { tier ->
                            val selected = glassQualityTier == tier
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(if (selected) palette.primaryGlow.copy(alpha = 0.18f) else Color(0x400E1324))
                                    .border(
                                        width = 1.dp,
                                        color = if (selected) palette.primaryGlow.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(18.dp)
                                    )
                                    .clickable { onSelectGlassQuality(tier) }
                                    .padding(14.dp)
                                    .testTag("glass_tier_${tier.name.lowercase()}")
                            ) {
                                Column {
                                    Text(tier.title, style = MaterialTheme.typography.titleSmall, color = if (selected) palette.primaryGlow else FrostWhite)
                                    Text(tier.subtitle, style = MaterialTheme.typography.bodySmall, color = MistSecondary)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. PLAYER & AUDIO ENGINE SETTINGS
        if (matchesSection(EchoSettingsSection.PLAYER, "player audio quality wifi cellular lossless 320kbps fade crossfade gapless skip silence normalization loudness opus bluetooth queue seek")) {
            item(key = "echo_player_audio_section") {
                SettingsGroupCard(
                    icon = Icons.Rounded.GraphicEq,
                    title = "Player & Audio Engine",
                    subtitle = "Streaming quality, Playback Engine, Smooth Fade & Normalization"
                ) {
                    Text("Playback Stream Engine", style = MaterialTheme.typography.labelLarge, color = palette.secondaryGlow)
                    EchoPlaybackEngine.entries.forEach { engine ->
                        val selected = settings.playbackEngine == engine
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (selected) palette.primaryGlow.copy(alpha = 0.16f) else Color(0x400E1324))
                                .border(1.dp, if (selected) palette.primaryGlow else Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                                .clickable { onUpdateEchoSettings("Playback Engine: ${engine.label}") { it.copy(playbackEngine = engine) } }
                                .padding(12.dp)
                        ) {
                            Column {
                                Text(engine.label, style = MaterialTheme.typography.titleSmall, color = if (selected) palette.primaryGlow else FrostWhite)
                                Text(engine.description, style = MaterialTheme.typography.bodySmall, color = MistSecondary)
                            }
                        }
                    }

                    Text("Wi-Fi Streaming Audio Quality", style = MaterialTheme.typography.labelLarge, color = palette.secondaryGlow)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AudioQuality.entries.forEach { qOpt ->
                            LiquidGlassChip(
                                label = "${qOpt.kbps}k",
                                selected = settings.audioQuality == qOpt,
                                onClick = { onUpdateEchoSettings(null) { it.copy(audioQuality = qOpt) } },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Text("Cellular Streaming Audio Quality", style = MaterialTheme.typography.labelLarge, color = palette.secondaryGlow)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AudioQuality.entries.forEach { qOpt ->
                            LiquidGlassChip(
                                label = "${qOpt.kbps}k",
                                selected = settings.cellularAudioQuality == qOpt,
                                onClick = { onUpdateEchoSettings(null) { it.copy(cellularAudioQuality = qOpt) } },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    SettingsToggleRow(
                        title = "Data Saver Mode",
                        subtitle = "Reduce cellular data usage by capping bitrate and preloading",
                        checked = settings.dataSaverEnabled,
                        onCheckedChange = { v -> onUpdateEchoSettings(null) { it.copy(dataSaverEnabled = v) } }
                    )

                    GlassSliderRow(
                        label = "Smooth Fade Transition Duration",
                        readout = if (settings.crossfadeSeconds == 0) "Off" else "${settings.crossfadeSeconds}s",
                        value = settings.crossfadeSeconds.toFloat(),
                        range = 0f..12f,
                        tag = "settings_crossfade_slider"
                    ) { v -> onUpdateEchoSettings(null) { it.copy(crossfadeSeconds = v.roundToInt()) } }

                    SettingsToggleRow(
                        title = "Gapless Playback",
                        subtitle = "Eliminate silence between consecutive album tracks",
                        checked = settings.gaplessPlaybackEnabled,
                        onCheckedChange = { v -> onUpdateEchoSettings(null) { it.copy(gaplessPlaybackEnabled = v) } }
                    )
                    SettingsToggleRow(
                        title = "Skip Silence",
                        subtitle = "Automatically fast-forward through silent passages in tracks",
                        checked = settings.skipSilenceEnabled,
                        onCheckedChange = { v -> onUpdateEchoSettings(null) { it.copy(skipSilenceEnabled = v) } }
                    )
                    SettingsToggleRow(
                        title = "Audio Volume Normalization",
                        subtitle = "Balance loudness across tracks using ReplayGain / LoudnessEnhancer",
                        checked = settings.volumeNormalizationEnabled,
                        onCheckedChange = { v -> onUpdateEchoSettings(null) { it.copy(volumeNormalizationEnabled = v) } }
                    )

                    Text("Audio Loudness Target Preset", style = MaterialTheme.typography.labelLarge, color = palette.secondaryGlow)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(AudioLoudnessPreset.entries, key = { it.name }) { preset ->
                            LiquidGlassChip(
                                label = preset.label,
                                selected = settings.loudnessPreset == preset,
                                onClick = { onUpdateEchoSettings(null) { it.copy(loudnessPreset = preset) } }
                            )
                        }
                    }

                    SettingsToggleRow(
                        title = "Force Opus Audio (itag 251)",
                        subtitle = "Prefer YouTube Music high-efficiency WebM/Opus 160kbps stream format",
                        checked = settings.forceOpusAudio,
                        onCheckedChange = { v -> onUpdateEchoSettings("Force Opus (itag 251) ${if (v) "ON" else "OFF"}") { it.copy(forceOpusAudio = v) } }
                    )
                    SettingsToggleRow(
                        title = "Audio Hardware Offload",
                        subtitle = "Offload DSP decoding to hardware audio co-processor for battery saving",
                        checked = settings.audioOffloadEnabled,
                        onCheckedChange = { v -> onUpdateEchoSettings(null) { it.copy(audioOffloadEnabled = v) } }
                    )
                    SettingsToggleRow(
                        title = "Persistent Playback Queue",
                        subtitle = "Save and restore playback queue across app restarts",
                        checked = settings.persistentQueueEnabled,
                        onCheckedChange = { v -> onUpdateEchoSettings(null) { it.copy(persistentQueueEnabled = v) } }
                    )
                    SettingsToggleRow(
                        title = "Auto-Load More / Endless Radio (Similar Content)",
                        subtitle = "Automatically append YouTube Music radio tracks when queue nears end",
                        checked = settings.autoPlayRadioEnabled,
                        onCheckedChange = { v -> onUpdateEchoSettings(null) { it.copy(autoPlayRadioEnabled = v) } }
                    )
                    SettingsToggleRow(
                        title = "Preload Next Song & Lyrics",
                        subtitle = "Buffer upcoming track and synced lyrics in advance for zero-wait transitions",
                        checked = settings.preloadNextSongEnabled,
                        onCheckedChange = { v -> onUpdateEchoSettings(null) { it.copy(preloadNextSongEnabled = v, preloadLyricsEnabled = v) } }
                    )
                    SettingsToggleRow(
                        title = "Auto-Skip Next on Stream Error",
                        subtitle = "Automatically advance to next track if all providers fail",
                        checked = settings.autoSkipNextOnError,
                        onCheckedChange = { v -> onUpdateEchoSettings(null) { it.copy(autoSkipNextOnError = v) } }
                    )
                    SettingsToggleRow(
                        title = "Prevent Duplicate Tracks in Queue",
                        subtitle = "Deduplicate songs when adding playlists or radio tracks",
                        checked = settings.preventDuplicateTracksInQueue,
                        onCheckedChange = { v -> onUpdateEchoSettings(null) { it.copy(preventDuplicateTracksInQueue = v) } }
                    )
                    SettingsToggleRow(
                        title = "Stop Music on Task Clear",
                        subtitle = "Stop background playback service when app is swiped away from recents",
                        checked = settings.stopMusicOnTaskClear,
                        onCheckedChange = { v -> onUpdateEchoSettings(null) { it.copy(stopMusicOnTaskClear = v) } }
                    )
                    SettingsToggleRow(
                        title = "Pause on Device Mute & Resume on Bluetooth",
                        subtitle = "Auto-pause when volume reaches zero and resume when headphones connect",
                        checked = settings.pauseOnMute,
                        onCheckedChange = { v -> onUpdateEchoSettings(null) { it.copy(pauseOnMute = v, resumeOnBluetoothConnect = v) } }
                    )
                    SettingsToggleRow(
                        title = "Keep Screen On While Playing",
                        subtitle = "Prevent device screen timeout while Now Playing or Lyrics view is open",
                        checked = settings.keepScreenOn,
                        onCheckedChange = { v -> onUpdateEchoSettings(null) { it.copy(keepScreenOn = v) } }
                    )
                    SettingsToggleRow(
                        title = "Show 10s Seek Buttons",
                        subtitle = "Display quick -10s / +10s jump buttons in the Now Playing dock",
                        checked = settings.showSeekButtons,
                        onCheckedChange = { v -> onUpdateEchoSettings(null) { it.copy(showSeekButtons = v) } }
                    )
                }
            }
        }

        // 4. CONTENT, LOCALIZATION & LYRICS PROVIDERS
        if (matchesSection(EchoSettingsSection.CONTENT, "content country region language explicit video shorts quick picks lyrics provider lrclib kugou youlyplus proxy")) {
            item(key = "echo_content_section") {
                SettingsGroupCard(
                    icon = Icons.Rounded.Language,
                    title = "Content, Localization & Lyrics Providers",
                    subtitle = "YouTube Music Country (gl), Language (hl), Explicit filter & Lyrics priority"
                ) {
                    Text(
                        text = "Content Country / Charts Region (${settings.contentCountry})",
                        style = MaterialTheme.typography.labelLarge,
                        color = palette.secondaryGlow
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(COUNTRY_OPTIONS, key = { it.first }) { (code, label) ->
                            LiquidGlassChip(
                                label = label,
                                selected = settings.contentCountry == code,
                                onClick = {
                                    onUpdateEchoSettings("Switched YouTube Music region to $label") {
                                        it.copy(contentCountry = code)
                                    }
                                }
                            )
                        }
                    }

                    Text(
                        text = "Content Language (${settings.contentLanguage})",
                        style = MaterialTheme.typography.labelLarge,
                        color = palette.secondaryGlow
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(LANGUAGE_OPTIONS, key = { it.first }) { (code, label) ->
                            LiquidGlassChip(
                                label = label,
                                selected = settings.contentLanguage == code,
                                onClick = {
                                    onUpdateEchoSettings("Switched content language to $label") {
                                        it.copy(contentLanguage = code)
                                    }
                                }
                            )
                        }
                    }

                    Text("Set Quick Picks Source", style = MaterialTheme.typography.labelLarge, color = palette.secondaryGlow)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuickPicksMode.entries.forEach { mode ->
                            LiquidGlassChip(
                                label = mode.label,
                                selected = settings.quickPicksMode == mode,
                                onClick = { onUpdateEchoSettings(null) { it.copy(quickPicksMode = mode) } }
                            )
                        }
                    }

                    SettingsToggleRow(
                        title = "Hide Explicit Content",
                        subtitle = "Filter out explicit-badged songs from Home, Search & Radio",
                        checked = settings.hideExplicit,
                        onCheckedChange = { v -> onUpdateEchoSettings(null) { it.copy(hideExplicit = v) } }
                    )
                    SettingsToggleRow(
                        title = "Hide Video Songs",
                        subtitle = "Filter out music video uploads and prefer official album audio",
                        checked = settings.hideVideoSongs,
                        onCheckedChange = { v -> onUpdateEchoSettings(null) { it.copy(hideVideoSongs = v) } }
                    )
                    SettingsToggleRow(
                        title = "Hide YouTube Shorts",
                        subtitle = "Keep Shorts out of your music browsing and playlists",
                        checked = settings.hideYoutubeShorts,
                        onCheckedChange = { v -> onUpdateEchoSettings(null) { it.copy(hideYoutubeShorts = v) } }
                    )

                    Text("Active Lyrics Providers (Priority: ${settings.lyricsProviderPriority})", style = MaterialTheme.typography.labelLarge, color = palette.secondaryGlow)
                    SettingsToggleRow(
                        title = "LRCLIB Synced Karaoke Lyrics",
                        subtitle = "Primary time-synced LRC & word-by-word karaoke database",
                        checked = settings.enableLrcLib,
                        onCheckedChange = { v -> onUpdateEchoSettings(null) { it.copy(enableLrcLib = v) } }
                    )
                    SettingsToggleRow(
                        title = "KuGou & YouLyPlus / BetterLyrics",
                        subtitle = "High-coverage multi-provider synced lyrics fallback",
                        checked = settings.enableKuGou && settings.enableYouLyPlus,
                        onCheckedChange = { v -> onUpdateEchoSettings(null) { it.copy(enableKuGou = v, enableYouLyPlus = v) } }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Custom HTTP / SOCKS Proxy", style = MaterialTheme.typography.titleSmall, color = FrostWhite)
                            Text(
                                text = if (settings.proxyEnabled && settings.proxyUrl.isNotBlank()) "Active: ${settings.proxyUrl}" else "Route InnerTube requests through a custom proxy",
                                style = MaterialTheme.typography.bodySmall,
                                color = MistSecondary
                            )
                        }
                        LiquidGlassButton(
                            onClick = { showProxyDialog = true },
                            modifier = Modifier.height(38.dp)
                        ) {
                            Text("Configure", style = MaterialTheme.typography.labelMedium, color = FrostWhite, modifier = Modifier.padding(horizontal = 12.dp).align(Alignment.Center))
                        }
                    }
                }
            }
        }

        // 5. AI LYRICS TRANSLATION & ECHO BRAIN
        if (matchesSection(EchoSettingsSection.AI, "ai lyrics translation gemini openrouter api route literal poetic romanized echo brain vibe dj")) {
            item(key = "echo_ai_section") {
                SettingsGroupCard(
                    icon = Icons.Rounded.Translate,
                    title = "AI Lyrics Translation & Echo Brain",
                    subtitle = "Real-time AI lyrics translation, romanization & Vibe DJ intelligence"
                ) {
                    SettingsToggleRow(
                        title = "Auto-Translate Synced Lyrics",
                        subtitle = "Automatically translate foreign-language lyrics on track start",
                        checked = settings.autoTranslateLyrics,
                        onCheckedChange = { v -> onUpdateEchoSettings(null) { it.copy(autoTranslateLyrics = v) } }
                    )

                    Text("Target Translation Language (${settings.translateTargetLanguage})", style = MaterialTheme.typography.labelLarge, color = palette.secondaryGlow)
                    val targetLangs = listOf("English", "Hindi", "Spanish", "French", "Japanese", "Korean", "German", "Portuguese")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(targetLangs, key = { it }) { lang ->
                            LiquidGlassChip(
                                label = lang,
                                selected = settings.translateTargetLanguage == lang,
                                onClick = { onUpdateEchoSettings(null) { it.copy(translateTargetLanguage = lang) } }
                            )
                        }
                    }

                    Text("Translation Mode", style = MaterialTheme.typography.labelLarge, color = palette.secondaryGlow)
                    val modes = listOf("Natural / Poetic", "Literal", "Romanized")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        modes.forEach { mode ->
                            LiquidGlassChip(
                                label = mode,
                                selected = settings.translateMode == mode,
                                onClick = { onUpdateEchoSettings(null) { it.copy(translateMode = mode) } }
                            )
                        }
                    }
                }
            }
        }

        // 6. 320 KBPS & LOSSLESS AUDIO EXTENSIONS (Echo Music ExtensionSettings)
        if (matchesSection(EchoSettingsSection.EXTENSIONS, "extension 320kbps lossless hi-res flac custom server prefer extension audio jiosaavn")) {
            item(key = "echo_extensions_section") {
                SettingsGroupCard(
                    icon = Icons.Rounded.Extension,
                    title = "320 kbps & Lossless Audio Extensions",
                    subtitle = "High-bitrate 320 kbps AAC/M4A & Lossless FLAC extension sources"
                ) {
                    SettingsToggleRow(
                        title = "Prefer Extension Audio (320 kbps / Lossless)",
                        subtitle = "Search and stream 320 kbps audio from extension servers before falling back to YouTube Music",
                        checked = settings.preferExtensionAudio,
                        onCheckedChange = { v ->
                            onUpdateEchoSettings(
                                if (v) "320 kbps Extension Audio prioritized" else "Standard YouTube Music InnerTube prioritized"
                            ) { it.copy(preferExtensionAudio = v) }
                        }
                    )

                    val activeAddonCount = configuredAddons.count { it.enabled }
                    EchoSurfaceCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 16.dp,
                        elevated = true,
                        accentBorder = if (activeAddonCount > 0) EmeraldPulse.copy(alpha = 0.45f) else palette.primaryGlow.copy(alpha = 0.3f)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = if (activeAddonCount > 0) {
                                    "Echo Addon Manager · $activeAddonCount Active Extension(s)"
                                } else {
                                    "Built-in 320 kbps & Echo Extension Ready"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = if (activeAddonCount > 0) EmeraldPulse else palette.primaryGlow
                            )
                            Text(
                                text = if (configuredAddons.isNotEmpty()) {
                                    configuredAddons.joinToString(" · ") { it.name }
                                } else {
                                    "Install any Echo /manifest.json server to unlock FLAC lossless streaming & inline Search results."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MistSecondary
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        LiquidGlassButton(
                            onClick = onOpenAddonManager,
                            accentGlow = true,
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("open_addon_manager_button")
                        ) {
                            Text(
                                text = "Open Addon Manager",
                                style = MaterialTheme.typography.labelLarge,
                                color = FrostWhite,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                        LiquidGlassButton(
                            onClick = { showExtensionServerDialog = true },
                            modifier = Modifier
                                .height(44.dp)
                                .testTag("add_custom_extension_server_button")
                        ) {
                            Text(
                                text = "Legacy URL",
                                style = MaterialTheme.typography.labelMedium,
                                color = FrostWhite,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(horizontal = 12.dp)
                            )
                        }
                    }
                }
            }
        }

        // 7. PRIVACY & HISTORY
        if (matchesSection(EchoSettingsSection.PRIVACY, "privacy history listen search pause clear blocked artists screenshot")) {
            item(key = "echo_privacy_section") {
                SettingsGroupCard(
                    icon = Icons.Rounded.Lock,
                    title = "Privacy & History Controls",
                    subtitle = "Manage listening history, search history & blocked artists"
                ) {
                    SettingsToggleRow(
                        title = "Pause Listen History",
                        subtitle = "Stop recording played songs to local history & Wrapped analytics",
                        checked = settings.pauseListenHistory,
                        onCheckedChange = { v -> onUpdateEchoSettings(null) { it.copy(pauseListenHistory = v) } }
                    )
                    SettingsToggleRow(
                        title = "Pause Search History",
                        subtitle = "Do not save search queries in recent search history",
                        checked = settings.pauseSearchHistory,
                        onCheckedChange = { v -> onUpdateEchoSettings(null) { it.copy(pauseSearchHistory = v) } }
                    )
                    SettingsToggleRow(
                        title = "Disable Screenshots in Privacy Mode",
                        subtitle = "Prevent recent-apps thumbnail capture when enabled",
                        checked = settings.disableScreenshotPrivacy,
                        onCheckedChange = { v -> onUpdateEchoSettings(null) { it.copy(disableScreenshotPrivacy = v) } }
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        LiquidGlassButton(
                            onClick = { showClearListenConfirm = true },
                            modifier = Modifier.weight(1f).height(42.dp)
                        ) {
                            Text("Clear Listen History", style = MaterialTheme.typography.labelMedium, color = FrostWhite, modifier = Modifier.align(Alignment.Center))
                        }
                        LiquidGlassButton(
                            onClick = { showClearSearchConfirm = true },
                            modifier = Modifier.weight(1f).height(42.dp)
                        ) {
                            Text("Clear Search History", style = MaterialTheme.typography.labelMedium, color = FrostWhite, modifier = Modifier.align(Alignment.Center))
                        }
                    }

                    LiquidGlassButton(
                        onClick = { showBlockedArtistsDialog = true },
                        modifier = Modifier.fillMaxWidth().height(42.dp)
                    ) {
                        val count = settings.blockedArtistsCsv.split(",").count { it.isNotBlank() }
                        Text(
                            text = if (count > 0) "Manage Blocked Artists ($count blocked)" else "Manage Blocked Artists",
                            style = MaterialTheme.typography.labelLarge,
                            color = FrostWhite,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
            }
        }

        // 8. STORAGE, DOWNLOADS & SMART AUDIO CACHE
        if (matchesSection(EchoSettingsSection.STORAGE, "storage cache song audio image downloads offline clear wifi only")) {
            item(key = "smart_audio_cache_card") {
                val usedMb = cacheSizeBytes / (1024f * 1024f)
                val cacheCaps = listOf(256, 512, 1024, 2048, 4096)
                SettingsGroupCard(
                    icon = Icons.Rounded.Storage,
                    title = "Storage, Offline Downloads & Cache",
                    subtitle = "ExoPlayer SimpleCache, artwork cache & offline downloads manager"
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Smart Audio Song Cache Used", style = MaterialTheme.typography.bodyMedium, color = MistSecondary)
                        Text("${"%.1f".format(usedMb)} MB / $maxCacheSizeMb MB", style = MaterialTheme.typography.titleSmall, color = palette.primaryGlow)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        cacheCaps.forEach { capMb ->
                            val label = if (capMb >= 1024) "${capMb / 1024} GB" else "$capMb MB"
                            LiquidGlassChip(
                                label = label,
                                selected = maxCacheSizeMb == capMb,
                                onClick = { onUpdateMaxCacheSizeMb(capMb) },
                                modifier = Modifier.weight(1f).testTag("cache_cap_$capMb")
                            )
                        }
                    }

                    SettingsToggleRow(
                        title = "Auto-Download on Like",
                        subtitle = "Automatically download songs for offline playback when you tap Heart",
                        checked = settings.autoDownloadOnLike,
                        onCheckedChange = { v -> onUpdateEchoSettings(null) { it.copy(autoDownloadOnLike = v) } }
                    )
                    SettingsToggleRow(
                        title = "Download on Wi-Fi Only",
                        subtitle = "Pause offline track downloads on metered cellular networks",
                        checked = settings.downloadOnWifiOnly,
                        onCheckedChange = { v -> onUpdateEchoSettings(null) { it.copy(downloadOnWifiOnly = v) } }
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        LiquidGlassButton(
                            onClick = onClearAudioCache,
                            modifier = Modifier.weight(1f).height(42.dp).testTag("clear_audio_cache_button")
                        ) {
                            Row(
                                modifier = Modifier.align(Alignment.Center),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(Icons.Rounded.CleaningServices, contentDescription = null, tint = FrostWhite, modifier = Modifier.size(16.dp))
                                Text("Clear Song Cache", style = MaterialTheme.typography.labelMedium, color = FrostWhite)
                            }
                        }
                        LiquidGlassButton(
                            onClick = onClearImageCache,
                            modifier = Modifier.weight(1f).height(42.dp)
                        ) {
                            Text("Clear Image Cache", style = MaterialTheme.typography.labelMedium, color = FrostWhite, modifier = Modifier.align(Alignment.Center))
                        }
                    }

                    LiquidGlassButton(
                        onClick = { showClearDownloadsConfirm = true },
                        modifier = Modifier.fillMaxWidth().height(42.dp)
                    ) {
                        Row(
                            modifier = Modifier.align(Alignment.Center),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = LuminousRose, modifier = Modifier.size(18.dp))
                            Text("Clear All Offline Downloads ($downloadedCount songs)", style = MaterialTheme.typography.labelLarge, color = FrostWhite)
                        }
                    }
                }
            }
        }

        // 9. BACKUP, RESTORE, PLAYLIST IMPORT & ANYTIME LIQUID WRAPPED
        if (matchesSection(EchoSettingsSection.BACKUP, "backup restore export import m3u8 json csv spotify wrapped stats top songs artists")) {
            item(key = "backup_restore_card") {
                SettingsGroupCard(
                    icon = Icons.Rounded.Backup,
                    title = "Backup, Restore & Playlist Import",
                    subtitle = "Export full database JSON, restore backups, or import M3U8/CSV/Spotify playlists"
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        LiquidGlassButton(
                            onClick = { exportBackupLauncher.launch("echo_liquid_music_backup.json") },
                            accentGlow = true,
                            enabled = !backupState.isBusy,
                            modifier = Modifier.weight(1f).height(46.dp).testTag("export_backup_button")
                        ) {
                            Row(
                                modifier = Modifier.align(Alignment.Center),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Rounded.FileDownload, contentDescription = null, tint = FrostWhite, modifier = Modifier.size(18.dp))
                                Text("Export Backup", style = MaterialTheme.typography.labelLarge, color = FrostWhite)
                            }
                        }

                        LiquidGlassButton(
                            onClick = { restoreBackupLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                            enabled = !backupState.isBusy,
                            modifier = Modifier.weight(1f).height(46.dp).testTag("restore_backup_button")
                        ) {
                            Row(
                                modifier = Modifier.align(Alignment.Center),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Rounded.FileUpload, contentDescription = null, tint = FrostWhite, modifier = Modifier.size(18.dp))
                                Text("Restore Backup", style = MaterialTheme.typography.labelLarge, color = FrostWhite)
                            }
                        }
                    }

                    LiquidGlassButton(
                        onClick = { importPlaylistLauncher.launch(arrayOf("audio/x-mpegurl", "application/vnd.apple.mpegurl", "text/csv", "text/plain", "*/*")) },
                        modifier = Modifier.fillMaxWidth().height(44.dp)
                    ) {
                        Text(
                            text = "Import Playlist File (M3U / M3U8 / CSV / JSON)",
                            style = MaterialTheme.typography.labelLarge,
                            color = FrostWhite,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }

                    if (backupState.isBusy) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            CircularProgressIndicator(color = palette.primaryGlow, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                            Text(backupState.statusMessage ?: "Working…", style = MaterialTheme.typography.bodySmall, color = MistSecondary)
                        }
                    } else if (!backupState.statusMessage.isNullOrBlank()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0x55121B32))
                                .border(1.dp, palette.primaryGlow.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                                .clickable(onClick = onClearBackupMessage)
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(backupState.statusMessage, style = MaterialTheme.typography.bodySmall, color = FrostWhite, modifier = Modifier.weight(1f))
                            Text("OK", style = MaterialTheme.typography.labelSmall, color = palette.primaryGlow)
                        }
                    }
                }
            }

            // Anytime Liquid Wrapped Hero Card
            item(key = "liquid_wrapped_hero") {
                LiquidGlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 28.dp,
                    tintColor = DeepIndigoAurora,
                    surfaceAlpha = 0.62f,
                    highlightAccent = palette.primaryGlow,
                    useRefractionShell = false
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "ANYTIME LIQUID WRAPPED",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = palette.secondaryGlow
                                )
                                Text(
                                    text = wrappedStats.personalityTitle,
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = FrostWhite
                                )
                            }
                            LiquidGlassButton(
                                onClick = {
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, shareText)
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, shareChooserTitle))
                                },
                                accentGlow = true,
                                cornerRadius = 16.dp,
                                modifier = Modifier.height(40.dp).testTag("share_wrapped_button")
                            ) {
                                Row(
                                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Rounded.Share, contentDescription = null, tint = FrostWhite, modifier = Modifier.size(16.dp))
                                    Text("Share", style = MaterialTheme.typography.labelLarge, color = FrostWhite)
                                }
                            }
                        }

                        Text(wrappedStats.personalityDescription, style = MaterialTheme.typography.bodyMedium, color = MistSecondary)

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            WrappedMetricPill(value = "${wrappedStats.totalMinutes}", label = "MINUTES", modifier = Modifier.weight(1f))
                            WrappedMetricPill(value = "${wrappedStats.totalPlays}", label = "PLAYS", modifier = Modifier.weight(1f))
                            WrappedMetricPill(value = "${wrappedStats.streakDays}d", label = "STREAK", accent = AmberGlow, showFlame = true, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            item(key = "wrapped_top_songs") {
                WrappedLeaderboardCard(
                    title = "Top 5 Tracks",
                    emptyHint = "Listen to tracks for at least 15 seconds to populate your live Top 5 Songs.",
                    items = wrappedStats.topSongs
                )
            }

            item(key = "wrapped_top_artists") {
                WrappedLeaderboardCard(
                    title = "Top 5 Artists",
                    emptyHint = "Your most spun artists will appear here automatically as you explore.",
                    items = wrappedStats.topArtists
                )
            }

            // 10. ABOUT & INNERTUBE TELEMETRY
            item(key = "echo_about_card") {
                SettingsGroupCard(
                    icon = Icons.Rounded.Info,
                    title = "About & InnerTube Engine Status",
                    subtitle = "Echo Music Architecture · AndroidLiquidGlass iOS 26 UI"
                ) {
                    Text(
                        text = "Primary Engine: YouTube Music InnerTube (WEB_REMIX + ANDROID_VR 1.65.10 + IOS 20.10.4 + VISIONOS)",
                        style = MaterialTheme.typography.bodySmall,
                        color = FrostWhite
                    )
                    Text(
                        text = "320kbps Extension & Fallback: JioSaavn 320kbps AAC/M4A + Custom Extension Host",
                        style = MaterialTheme.typography.bodySmall,
                        color = MistSecondary
                    )
                    Text(
                        text = "Active Region: ${settings.contentCountry} · Language: ${settings.contentLanguage} · VisitorData: ${
                            activeVisitorData.ifBlank { settings.visitorData }.take(24).ifBlank { "Auto-bootstrapped on browse" }
                        }…",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.primaryGlow
                    )
                }
            }
        }
    }

    // DIALOGS
    if (showEditNameDialog) {
        var editedName by remember { mutableStateOf(userProfile.displayName) }
        AlertDialog(
            onDismissRequest = { showEditNameDialog = false },
            containerColor = DeepVoid,
            titleContentColor = FrostWhite,
            title = { Text("Update Display Name") },
            text = {
                OutlinedTextField(
                    value = editedName,
                    onValueChange = { editedName = it },
                    label = { Text("Display Name", color = MistSecondary) },
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
                        onUpdateDisplayName(editedName)
                        showEditNameDialog = false
                    },
                    enabled = editedName.isNotBlank()
                ) {
                    Text("Save", color = palette.primaryGlow)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditNameDialog = false }) {
                    Text("Cancel", color = MistSecondary)
                }
            }
        )
    }

    if (showCookieDialog) {
        var cookieVal by remember { mutableStateOf(settings.innerTubeCookie) }
        var visitorVal by remember { mutableStateOf(settings.visitorData.ifBlank { activeVisitorData }) }
        AlertDialog(
            onDismissRequest = { showCookieDialog = false },
            containerColor = DeepVoid,
            titleContentColor = FrostWhite,
            title = { Text("YouTube Music Advanced Login") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Optional: Paste your YouTube Music Cookie (including SAPISID) or custom VisitorData for personalized account shelves.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MistSecondary
                    )
                    OutlinedTextField(
                        value = cookieVal,
                        onValueChange = { cookieVal = it },
                        label = { Text("InnerTube Cookie (SAPISID=...)", color = MistSecondary) },
                        maxLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = FrostWhite,
                            unfocusedTextColor = FrostWhite,
                            focusedBorderColor = palette.primaryGlow
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = visitorVal,
                        onValueChange = { visitorVal = it },
                        label = { Text("VisitorData (Cgt...)", color = MistSecondary) },
                        maxLines = 2,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = FrostWhite,
                            unfocusedTextColor = FrostWhite,
                            focusedBorderColor = palette.primaryGlow
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onUpdateEchoSettings("Updated YouTube Music session credentials") {
                        it.copy(innerTubeCookie = cookieVal.trim(), visitorData = visitorVal.trim())
                    }
                    showCookieDialog = false
                }) {
                    Text("Save", color = palette.primaryGlow)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCookieDialog = false }) {
                    Text("Cancel", color = MistSecondary)
                }
            }
        )
    }

    if (showExtensionServerDialog) {
        var extUrl by remember { mutableStateOf(settings.customExtensionUrl) }
        var losslessUrl by remember { mutableStateOf(settings.losslessExtensionUrl) }
        AlertDialog(
            onDismissRequest = { showExtensionServerDialog = false },
            containerColor = DeepVoid,
            titleContentColor = FrostWhite,
            title = { Text("320 kbps & Lossless Extension Servers") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Configure your Echo 320kbps or Lossless FLAC extension endpoint URL.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MistSecondary
                    )
                    OutlinedTextField(
                        value = extUrl,
                        onValueChange = { extUrl = it },
                        label = { Text("320 kbps Extension Server URL", color = MistSecondary) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = FrostWhite,
                            unfocusedTextColor = FrostWhite,
                            focusedBorderColor = palette.primaryGlow
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = losslessUrl,
                        onValueChange = { losslessUrl = it },
                        label = { Text("Lossless FLAC Extension URL (Optional)", color = MistSecondary) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = FrostWhite,
                            unfocusedTextColor = FrostWhite,
                            focusedBorderColor = palette.primaryGlow
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onUpdateEchoSettings("Saved 320kbps / Lossless extension endpoint") {
                        it.copy(
                            customExtensionUrl = extUrl.trim().ifBlank { "https://saavn.sumit.co/api" },
                            losslessExtensionUrl = losslessUrl.trim()
                        )
                    }
                    showExtensionServerDialog = false
                }) {
                    Text("Save", color = palette.primaryGlow)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExtensionServerDialog = false }) {
                    Text("Cancel", color = MistSecondary)
                }
            }
        )
    }

    if (showProxyDialog) {
        var proxyEnabled by remember { mutableStateOf(settings.proxyEnabled) }
        var proxyHost by remember { mutableStateOf(settings.proxyUrl) }
        AlertDialog(
            onDismissRequest = { showProxyDialog = false },
            containerColor = DeepVoid,
            titleContentColor = FrostWhite,
            title = { Text("HTTP Proxy Configuration") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SettingsToggleRow(
                        title = "Enable Proxy",
                        subtitle = "Route requests via proxy host:port",
                        checked = proxyEnabled,
                        onCheckedChange = { proxyEnabled = it }
                    )
                    OutlinedTextField(
                        value = proxyHost,
                        onValueChange = { proxyHost = it },
                        label = { Text("Proxy Address (e.g. 127.0.0.1:8080)", color = MistSecondary) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = FrostWhite,
                            unfocusedTextColor = FrostWhite,
                            focusedBorderColor = palette.primaryGlow
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onUpdateEchoSettings("Proxy configuration updated") {
                        it.copy(proxyEnabled = proxyEnabled, proxyUrl = proxyHost.trim())
                    }
                    showProxyDialog = false
                }) {
                    Text("Save", color = palette.primaryGlow)
                }
            },
            dismissButton = {
                TextButton(onClick = { showProxyDialog = false }) {
                    Text("Cancel", color = MistSecondary)
                }
            }
        )
    }

    if (showBlockedArtistsDialog) {
        var blockedCsv by remember { mutableStateOf(settings.blockedArtistsCsv) }
        AlertDialog(
            onDismissRequest = { showBlockedArtistsDialog = false },
            containerColor = DeepVoid,
            titleContentColor = FrostWhite,
            title = { Text("Blocked Artists") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Enter artist names separated by commas to hide them from Quick Picks and Home recommendations.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MistSecondary
                    )
                    OutlinedTextField(
                        value = blockedCsv,
                        onValueChange = { blockedCsv = it },
                        label = { Text("Comma-separated artist names", color = MistSecondary) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = FrostWhite,
                            unfocusedTextColor = FrostWhite,
                            focusedBorderColor = palette.primaryGlow
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onUpdateEchoSettings("Blocked artists list updated") {
                        it.copy(blockedArtistsCsv = blockedCsv.trim())
                    }
                    showBlockedArtistsDialog = false
                }) {
                    Text("Save", color = palette.primaryGlow)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBlockedArtistsDialog = false }) {
                    Text("Cancel", color = MistSecondary)
                }
            }
        )
    }

    if (showClearListenConfirm) {
        AlertDialog(
            onDismissRequest = { showClearListenConfirm = false },
            containerColor = DeepVoid,
            titleContentColor = FrostWhite,
            title = { Text("Clear Listen History?") },
            text = { Text("This will clear your local listening events and reset Anytime Wrapped counters.", color = MistSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    onClearListenHistory()
                    showClearListenConfirm = false
                }) {
                    Text("Clear", color = LuminousRose)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearListenConfirm = false }) {
                    Text("Cancel", color = MistSecondary)
                }
            }
        )
    }

    if (showClearSearchConfirm) {
        AlertDialog(
            onDismissRequest = { showClearSearchConfirm = false },
            containerColor = DeepVoid,
            titleContentColor = FrostWhite,
            title = { Text("Clear Search History?") },
            text = { Text("All saved recent search queries will be removed.", color = MistSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    onClearSearchHistory()
                    showClearSearchConfirm = false
                }) {
                    Text("Clear", color = LuminousRose)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearSearchConfirm = false }) {
                    Text("Cancel", color = MistSecondary)
                }
            }
        )
    }

    if (showClearDownloadsConfirm) {
        AlertDialog(
            onDismissRequest = { showClearDownloadsConfirm = false },
            containerColor = DeepVoid,
            titleContentColor = FrostWhite,
            title = { Text("Remove All Downloaded Songs?") },
            text = { Text("This will delete all offline downloaded audio files from device storage.", color = MistSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    onClearAllDownloads()
                    showClearDownloadsConfirm = false
                }) {
                    Text("Delete All", color = LuminousRose)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDownloadsConfirm = false }) {
                    Text("Cancel", color = MistSecondary)
                }
            }
        )
    }
}

@Composable
private fun SettingsGroupCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    val palette = LocalLiquidPalette.current
    EchoSurfaceCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 24.dp
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(icon, contentDescription = null, tint = palette.secondaryGlow, modifier = Modifier.size(22.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = FrostWhite)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MistSecondary)
                }
            }
            content()
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = FrostWhite)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MistSecondary)
        }
        LiquidGlassToggle(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}


@Composable
private fun GlassSliderRow(
    label: String,
    readout: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    tag: String,
    onChange: (Float) -> Unit
) {
    val palette = LocalLiquidPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MistSecondary)
            Text(readout, style = MaterialTheme.typography.labelMedium, color = palette.primaryGlow)
        }
        LiquidGlassSlider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.testTag(tag)
        )
    }
}

@Composable
private fun WrappedMetricPill(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    accent: Color = LocalLiquidPalette.current.primaryGlow,
    showFlame: Boolean = false
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0x55080C1A))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(18.dp))
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (showFlame) {
                Icon(Icons.Rounded.LocalFireDepartment, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            }
            Text(value, style = MaterialTheme.typography.headlineMedium, color = FrostWhite)
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = accent)
    }
}

@Composable
private fun WrappedLeaderboardCard(
    title: String,
    emptyHint: String,
    items: List<TopStatItem>
) {
    val palette = LocalLiquidPalette.current
    val maxPlays = (items.maxOfOrNull { it.playCount } ?: 1).coerceAtLeast(1)

    EchoSurfaceCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 24.dp) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = palette.secondaryGlow, modifier = Modifier.size(18.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, color = FrostWhite)
            }

            if (items.isEmpty()) {
                Text(emptyHint, style = MaterialTheme.typography.bodySmall, color = MistSecondary)
            } else {
                items.forEachIndexed { idx, stat ->
                    val targetFraction = (stat.playCount.toFloat() / maxPlays.toFloat()).coerceIn(0.15f, 1f)
                    val animatedFraction by animateFloatAsState(
                        targetValue = targetFraction,
                        animationSpec = tween(650),
                        label = "wrappedBar_$idx"
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "#${idx + 1}",
                                style = MaterialTheme.typography.labelMedium,
                                color = palette.secondaryGlow,
                                modifier = Modifier.width(26.dp)
                            )
                            TrackArtwork(
                                artworkUrl = stat.artworkUrl,
                                contentDescription = stat.title,
                                cornerRadius = 10.dp,
                                modifier = Modifier.size(38.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stat.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = FrostWhite,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = stat.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MistSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = "${stat.playCount} plays · ${stat.totalMinutes}m",
                                style = MaterialTheme.typography.labelMedium,
                                color = palette.primaryGlow
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.08f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(animatedFraction)
                                    .height(6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(palette.secondaryGlow, palette.primaryGlow)
                                        )
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}
