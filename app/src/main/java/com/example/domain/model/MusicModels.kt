package com.example.domain.model

import androidx.compose.runtime.Immutable

enum class SourceType {
    ONLINE,
    LOCAL
}

enum class MusicSource(val displayName: String) {
    YOUTUBE_MUSIC("YouTube Music"),
    JIOSAAVN("JioSaavn"),
    ECHO_ADDON("Echo Extension"),
    LOCAL("Device Local")
}

@Immutable
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val durationMs: Long = 0L,
    val artworkUrl: String = "",
    val streamUrl96: String = "",
    val streamUrl160: String = "",
    val streamUrl320: String = "",
    val localUri: String = "",
    val downloadedFilePath: String = "",
    val sourceType: SourceType = SourceType.ONLINE,
    val sourceProvider: MusicSource = if (sourceType == SourceType.LOCAL) MusicSource.LOCAL else MusicSource.YOUTUBE_MUSIC,
    val providerTrackId: String = id.removePrefix("ytm_").removePrefix("saavn_").removePrefix("local_"),
    val artistId: String = "",
    val albumId: String = "",
    val explicit: Boolean = false,
    val isVideoSong: Boolean = false,
    val addedAt: Long = System.currentTimeMillis()
) {
    fun bestPlaybackUri(quality: AudioQuality): String {
        if (downloadedFilePath.isNotBlank()) return downloadedFilePath
        if (sourceType == SourceType.LOCAL && localUri.isNotBlank()) return localUri
        return when (quality) {
            AudioQuality.LOW_96 -> streamUrl96.ifBlank { streamUrl160 }.ifBlank { streamUrl320 }
            AudioQuality.MEDIUM_160 -> streamUrl160.ifBlank { streamUrl320 }.ifBlank { streamUrl96 }
            AudioQuality.HIGH_320 -> streamUrl320.ifBlank { streamUrl160 }.ifBlank { streamUrl96 }
        }.ifBlank { localUri }
    }

    val isDownloaded: Boolean
        get() = downloadedFilePath.isNotBlank()

    val needsJustInTimeResolution: Boolean
        get() = !isDownloaded &&
            sourceType == SourceType.ONLINE &&
            (sourceProvider == MusicSource.YOUTUBE_MUSIC || bestPlaybackUri(AudioQuality.HIGH_320).isBlank())
}

@Immutable
data class StreamResolution(
    val uri: String,
    val resolvedBy: MusicSource,
    val bitrateKbps: Int = 160,
    val mimeType: String = "audio/mp4",
    val usedFallback: Boolean = false,
    val headers: Map<String, String> = emptyMap(),
    val clientName: String = "",
    val profileId: String = "",
    val contentLength: Long? = null,
    val loudnessDb: Double? = null,
    val perceptualLoudnessDb: Double? = null
)

@Immutable
data class AlbumSummary(
    val id: String,
    val browseId: String,
    val title: String,
    val artist: String,
    val artistId: String = "",
    val year: String = "",
    val artworkUrl: String = "",
    val sourceProvider: MusicSource = MusicSource.YOUTUBE_MUSIC,
    val isSingle: Boolean = false
)

@Immutable
data class AlbumDetail(
    val id: String,
    val browseId: String,
    val title: String,
    val artist: String,
    val artistId: String = "",
    val year: String = "",
    val description: String = "",
    val artworkUrl: String = "",
    val tracks: List<Track> = emptyList(),
    val sourceProvider: MusicSource = MusicSource.YOUTUBE_MUSIC
) {
    fun toSummary(): AlbumSummary = AlbumSummary(
        id = id,
        browseId = browseId,
        title = title,
        artist = artist,
        artistId = artistId,
        year = year,
        artworkUrl = artworkUrl,
        sourceProvider = sourceProvider
    )
}

@Immutable
data class ArtistSummary(
    val id: String,
    val browseId: String,
    val name: String,
    val subtitle: String = "",
    val thumbnailUrl: String = "",
    val sourceProvider: MusicSource = MusicSource.YOUTUBE_MUSIC
)

@Immutable
data class ArtistDetail(
    val id: String,
    val browseId: String,
    val name: String,
    val description: String = "",
    val subscribersText: String = "",
    val thumbnailUrl: String = "",
    val topSongs: List<Track> = emptyList(),
    val albums: List<AlbumSummary> = emptyList(),
    val singles: List<AlbumSummary> = emptyList(),
    val relatedArtists: List<ArtistSummary> = emptyList(),
    val radioPlaylistId: String = "",
    val sourceProvider: MusicSource = MusicSource.YOUTUBE_MUSIC
)

@Immutable
data class PlaylistSummary(
    val id: String,
    val browseId: String,
    val title: String,
    val author: String = "",
    val trackCountText: String = "",
    val artworkUrl: String = "",
    val sourceProvider: MusicSource = MusicSource.YOUTUBE_MUSIC
)

@Immutable
data class MoodItem(
    val title: String,
    val query: String,
    val browseId: String = "",
    val params: String = "",
    val accentHex: Long = 0xFF7BE8FF
)

enum class BrowseLayoutType {
    QUICK_PICKS_GRID,
    CAROUSEL_TRACKS,
    CAROUSEL_ALBUMS,
    CAROUSEL_ARTISTS,
    CHART_LIST
}

@Immutable
data class BrowseSection(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val layoutType: BrowseLayoutType = BrowseLayoutType.CAROUSEL_TRACKS,
    val tracks: List<Track> = emptyList(),
    val albums: List<AlbumSummary> = emptyList(),
    val artists: List<ArtistSummary> = emptyList(),
    val sourceProvider: MusicSource = MusicSource.YOUTUBE_MUSIC
)

@Immutable
data class UnifiedSearchResults(
    val songs: List<Track> = emptyList(),
    val extensionSongs: List<Track> = emptyList(),
    val albums: List<AlbumSummary> = emptyList(),
    val artists: List<ArtistSummary> = emptyList(),
    val playlists: List<PlaylistSummary> = emptyList(),
    val activeSource: MusicSource = MusicSource.YOUTUBE_MUSIC,
    val usedFallback: Boolean = false,
    val errorMessage: String? = null
) {
    val isEmpty: Boolean
        get() = songs.isEmpty() && extensionSongs.isEmpty() && albums.isEmpty() && artists.isEmpty() && playlists.isEmpty()
}

@Immutable
data class LyricWord(
    val startMs: Long,
    val endMs: Long,
    val text: String
)

@Immutable
data class LyricLine(
    val timestampMs: Long,
    val text: String,
    val endMs: Long = 0L,
    val words: List<LyricWord> = emptyList(),
    val translatedText: String? = null
)

@Immutable
data class LyricsState(
    val isLoading: Boolean = false,
    val syncedLines: List<LyricLine> = emptyList(),
    val plainLyrics: String = "",
    val isInstrumental: Boolean = false,
    val sourceName: String = "",
    val isTranslating: Boolean = false,
    val showTranslation: Boolean = false,
    val translationLanguage: String = "English",
    val translationError: String? = null,
    val errorMessage: String? = null
)

@Immutable
data class LyricsSearchCandidate(
    val id: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val durationSec: Int = 0,
    val syncedLyrics: String = "",
    val plainLyrics: String = "",
    val sourceName: String = "LRCLIB"
) {
    val hasSynced: Boolean get() = syncedLyrics.isNotBlank()
}

@Immutable
data class TrackDnaInfo(
    val trackId: String,
    val title: String,
    val artist: String,
    val storySummary: String = "",
    val annotations: List<String> = emptyList(),
    val releaseDate: String = "",
    val writers: List<String> = emptyList(),
    val producers: List<String> = emptyList(),
    val albumContext: String = "",
    val sourceLabel: String = "Genius · Wikipedia",
    val externalUrl: String = ""
)

@Immutable
data class EchoAddonConfig(
    val id: String,
    val baseUrl: String,
    val name: String = "Echo Extension",
    val version: String = "1.0",
    val supportsLossless: Boolean = false,
    val enabled: Boolean = true,
    val priority: Int = 0,
    val latencyMs: Long? = null,
    val statusMessage: String = "Ready"
)

@Immutable
data class YouTubeAccountInfo(
    val name: String = "",
    val handle: String = "",
    val email: String = "",
    val avatarUrl: String = "",
    val dataSyncId: String = "",
    val lastSyncedAtMs: Long = 0L
) {
    val isConnected: Boolean get() = name.isNotBlank() || handle.isNotBlank()
}

@Immutable
data class SavedQueueCursor(
    val activeIndex: Int = 0,
    val positionMs: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatModeState = RepeatModeState.OFF
)

@Immutable
data class Playlist(
    val id: Long = 0L,
    val name: String,
    val description: String = "",
    val coverArtUri: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val tracks: List<Track> = emptyList()
)

@Immutable
data class UserProfile(
    val displayName: String = "Listener",
    val email: String = "",
    val handle: String = "@liquid",
    val avatarSeed: Int = 0,
    val isGuest: Boolean = true,
    val isSignedIn: Boolean = false
)

enum class AudioQuality(val kbps: Int, val label: String) {
    LOW_96(96, "96 kbps (Data Saver)"),
    MEDIUM_160(160, "160 kbps (Standard)"),
    HIGH_320(320, "320 kbps / Lossless")
}

enum class GlassQualityTier(val title: String, val subtitle: String) {
    ULTRA_AGSL("Ultra Liquid Backdrop 120Hz", "Real-time AndroidLiquidGlass lens refraction & RGB prism"),
    BALANCED("Balanced Blur", "Smooth frosted backdrop blur with crisp Fresnel rims"),
    BATTERY_SAVER("Battery Saver", "Obsidian smoked glass with low GPU draw")
}

enum class GlassOpticalPreset(
    val label: String,
    val pillScale: Float,
    val springStiffness: Float,
    val springDamping: Float,
    val refractionHeightDp: Float,
    val refractionAmountDp: Float,
    val blurRadiusDp: Float,
    val chromaticAberration: Boolean
) {
    BALANCED_IOS26("iOS 26 Balanced", 1.0f, 380f, 0.62f, 18f, 34f, 10f, true),
    ULTRA_PRISM("Ultra Prism", 1.12f, 310f, 0.50f, 26f, 54f, 6f, true),
    SOFT_FROSTED("Soft Frosted", 0.96f, 460f, 0.78f, 10f, 18f, 20f, false),
    CUSTOM("Custom", 1.0f, 380f, 0.62f, 18f, 34f, 10f, true)
}

@Immutable
data class LiquidGlassConfig(
    val opticalPreset: GlassOpticalPreset = GlassOpticalPreset.BALANCED_IOS26,
    val pillScale: Float = 1.0f,
    val springStiffness: Float = 380f,
    val springDamping: Float = 0.62f,
    val refractionHeightDp: Float = 18f,
    val refractionAmountDp: Float = 34f,
    val blurRadiusDp: Float = 10f,
    val chromaticAberration: Boolean = true
)

enum class EqPreset(val label: String, val bandsDb: List<Float>) {
    FLAT("Flat", listOf(0f, 0f, 0f, 0f, 0f)),
    BASS_BOOST("Bass Boost", listOf(6.5f, 4.5f, 0.5f, 1.0f, 2.0f)),
    PHONK_HEAVY("Phonk Heavy", listOf(8.0f, 5.0f, -1.5f, 3.0f, 5.5f)),
    VOCAL_CLARITY("Vocal Clarity", listOf(-1.5f, 1.0f, 4.5f, 4.0f, 1.5f)),
    LATE_NIGHT("Late Night", listOf(3.0f, 2.0f, -1.0f, -1.5f, -2.5f)),
    STUDIO("Studio", listOf(2.0f, 1.0f, 0.0f, 2.5f, 3.5f)),
    CUSTOM("Custom", listOf(0f, 0f, 0f, 0f, 0f))
}

enum class ReverbPreset(val label: String, val androidPreset: Short) {
    NONE("None", 0),
    SMALL_ROOM("Small Room", 1),
    MEDIUM_HALL("Medium Hall", 4),
    LARGE_HALL("Large Hall", 5),
    PLATE("Plate", 6)
}

enum class MagicSoundPreset(val label: String, val subtitle: String) {
    NORMAL("Normal", "Pure studio reference"),
    SLOWED_REVERB("Slowed + Reverb", "0.84x pitch-down & lush hall"),
    NIGHTCORE("Nightcore", "1.24x energetic tempo & pitch"),
    EIGHT_D("8D / Spacious", "Rotating stereo aura + plate")
}

enum class SleepTimerOption(val label: String, val minutes: Int?) {
    OFF("Off", null),
    MIN_10("10m", 10),
    MIN_20("20m", 20),
    MIN_30("30m", 30),
    MIN_45("45m", 45),
    MIN_60("60m", 60),
    END_OF_TRACK("End of Track", -1)
}

// Echo Music Comprehensive Settings Enums & Models
enum class PlayerBackgroundStyle(val label: String) {
    LIQUID_GLASS("Liquid Glass"),
    BLUR("Dynamic Blur"),
    GRADIENT("Aurora Gradient"),
    PURE_BLACK("AMOLED Black")
}

enum class PlayerLayoutStyle(val label: String) {
    ECHO_MATERIAL("Echo Material 3"),
    LIQUID_GLASS("Liquid Glass Chrome"),
    APPLE_FULL_BLEED("Apple Full-Bleed")
}

enum class PlayerSliderStyle(val label: String) {
    WAVY("Wavy"),
    SLIM("Slim"),
    SQUIGGLY("Squiggly")
}

enum class LyricsAlignStyle(val label: String) {
    LEFT("Left"),
    CENTER("Center"),
    RIGHT("Right")
}

enum class LyricsAnimationStyle(val label: String) {
    APPLE_FLUID("Apple Music Fluid"),
    KARAOKE("Word-by-Word Karaoke"),
    GLOW("Neon Glow"),
    FADE("Smooth Fade"),
    SLIDE("Vertical Slide")
}

enum class AudioLoudnessPreset(val label: String, val targetGainDb: Float) {
    QUIET("Quiet (-3 dB)", -3f),
    NORMAL("Normal (0 dB)", 0f),
    LOUD("Loud (+3 dB)", 3f),
    AGGRESSIVE("Aggressive (+6 dB)", 6f)
}

enum class EchoPlaybackEngine(val label: String, val description: String) {
    AUTO("YouTube Music First (Highest Quality)", "Always resolves YouTube Music first at maximum bitrate with automatic failover"),
    EXTENDED_INNERTUBE("eXtended InnerTube", "Direct YouTube Music InnerTube multi-client stream extraction"),
    EXTENSION_FIRST("Prefer 320kbps / Lossless Extension", "Prioritize high-bitrate 320kbps extension streams before InnerTube")
}

enum class QuickPicksMode(val label: String) {
    QUICK_PICKS("Personalized Quick Picks"),
    LAST_LISTENED("Based on Last Song Listened")
}



@Immutable
data class SoundLabSettings(
    val audioQuality: AudioQuality = AudioQuality.HIGH_320,
    val cellularAudioQuality: AudioQuality = AudioQuality.HIGH_320,
    val crossfadeSeconds: Int = 4,
    val skipSilenceEnabled: Boolean = false,
    val skipSilenceInstant: Boolean = false,
    val volumeNormalizationEnabled: Boolean = true,
    val loudnessPreset: AudioLoudnessPreset = AudioLoudnessPreset.NORMAL,
    val gaplessPlaybackEnabled: Boolean = true,
    val autoPlayRadioEnabled: Boolean = true,
    val maxCacheSizeMb: Int = 512,
    val maxImageCacheSizeMb: Int = 512,
    val glassQualityTier: GlassQualityTier = GlassQualityTier.ULTRA_AGSL,
    val glassConfig: LiquidGlassConfig = LiquidGlassConfig(),
    val eqPreset: EqPreset = EqPreset.STUDIO,
    val eqBandLevelsDb: List<Float> = EqPreset.STUDIO.bandsDb,
    val bassBoostStrength: Int = 350, // 0..1000
    val reverbPreset: ReverbPreset = ReverbPreset.NONE,
    val playbackSpeed: Float = 1.0f,
    val playbackPitch: Float = 1.0f,
    val eightDEnabled: Boolean = false,
    val activeMagicPreset: MagicSoundPreset = MagicSoundPreset.NORMAL,
    // Echo Music Appearance & Player Customization
    val pureBlackAmoled: Boolean = true,
    val dynamicThemeEnabled: Boolean = true,
    val playerBackgroundStyle: PlayerBackgroundStyle = PlayerBackgroundStyle.BLUR,
    val playerLayoutStyle: PlayerLayoutStyle = PlayerLayoutStyle.ECHO_MATERIAL,
    val playerSliderStyle: PlayerSliderStyle = PlayerSliderStyle.SQUIGGLY,
    val gridCellSizeBig: Boolean = true,
    val showHomeHeroCarousel: Boolean = true,
    val showSpeedDialQuickPicks: Boolean = true,
    val showHomeMoodAndGenres: Boolean = true,
    val randomizeHomeOrder: Boolean = false,
    val cropAlbumArt: Boolean = false,
    val swipeToSongEnabled: Boolean = true,
    val lyricsAlignment: LyricsAlignStyle = LyricsAlignStyle.LEFT,
    val lyricsAnimationStyle: LyricsAnimationStyle = LyricsAnimationStyle.APPLE_FLUID,
    val lyricsFontSizeSp: Float = 22f,
    val lyricsLineSpacing: Float = 1.3f,
    val lyricsOffsetMs: Long = 0L,
    // Echo Music Player & Smart Playback
    val playbackEngine: EchoPlaybackEngine = EchoPlaybackEngine.AUTO,
    val persistentQueueEnabled: Boolean = true,
    val preloadNextSongEnabled: Boolean = true,
    val preloadLyricsEnabled: Boolean = true,
    val dataSaverEnabled: Boolean = false,
    val autoDownloadOnLike: Boolean = false,
    val downloadOnWifiOnly: Boolean = false,
    val downloadWithMetadata: Boolean = true,
    val autoSkipNextOnError: Boolean = true,
    val preventDuplicateTracksInQueue: Boolean = true,
    val rememberShuffleAndRepeat: Boolean = true,
    val stopMusicOnTaskClear: Boolean = true,
    val pauseOnMute: Boolean = true,
    val resumeOnBluetoothConnect: Boolean = true,
    val keepScreenOn: Boolean = false,
    val showSeekButtons: Boolean = true,
    val forceOpusAudio: Boolean = false,
    val bitPerfectUsbDac: Boolean = false,
    val audioOffloadEnabled: Boolean = false,
    // Echo Music Content, Localization & Lyrics Providers
    val contentCountry: String = "US",
    val contentLanguage: String = "en",
    val quickPicksMode: QuickPicksMode = QuickPicksMode.QUICK_PICKS,
    val hideExplicit: Boolean = false,
    val hideVideoSongs: Boolean = false,
    val hideYoutubeShorts: Boolean = true,
    val lyricsProviderPriority: String = "LRCLIB,KuGou,SimpMusic,YouTube Music",
    val enableLrcLib: Boolean = true,
    val enableKuGou: Boolean = true,
    val enableYouLyPlus: Boolean = true,
    val proxyEnabled: Boolean = false,
    val proxyUrl: String = "",
    // Echo Music AI & Lyrics Translation
    val aiProvider: String = "Gemini AI Logic",
    val autoTranslateLyrics: Boolean = false,
    val translateTargetLanguage: String = "English",
    val translateMode: String = "Natural / Poetic",
    val aiRecommendationsEnabled: Boolean = true,
    // Echo Music Account, Integrations & Extensions
    val innerTubeCookie: String = "",
    val visitorData: String = "",
    val dataSyncId: String = "",
    val ytmAccountName: String = "",
    val ytmAccountHandle: String = "",
    val ytmAccountAvatarUrl: String = "",
    val ytmLastSyncedAt: Long = 0L,
    val ytmSyncEnabled: Boolean = true,
    val discordRpcEnabled: Boolean = false,
    val lastFmEnabled: Boolean = false,
    val listenBrainzEnabled: Boolean = false,
    val listenBrainzToken: String = "",
    val preferExtensionAudio: Boolean = false,
    val customExtensionUrl: String = "https://saavn.sumit.co/api",
    val losslessExtensionUrl: String = "",
    val addonsJson: String = "",
    // Echo Music Privacy
    val pauseListenHistory: Boolean = false,
    val pauseSearchHistory: Boolean = false,
    val disableScreenshotPrivacy: Boolean = false,
    val blockedArtistsCsv: String = ""
)

@Immutable
data class VibeDjResult(
    val title: String,
    val manifesto: String,
    val moodTags: List<String>,
    val tracks: List<Track>,
    val poweredByGemini: Boolean
)

@Immutable
data class TopStatItem(
    val title: String,
    val subtitle: String,
    val artworkUrl: String,
    val playCount: Int,
    val totalMinutes: Int
)

@Immutable
data class WrappedStats(
    val totalMinutes: Int = 0,
    val totalPlays: Int = 0,
    val streakDays: Int = 0,
    val topSongs: List<TopStatItem> = emptyList(),
    val topArtists: List<TopStatItem> = emptyList(),
    val personalityTitle: String = "Aurora Explorer",
    val personalityDescription: String = "Play a few tracks to unlock your live Liquid Wrapped sonic signature."
)

enum class MainTab(val label: String) {
    HOME("Home"),
    SEARCH("Search"),
    LIBRARY("Library"),
    SOUND_LAB("Sound Lab"),
    PROFILE("Settings")
}

enum class RepeatModeState {
    OFF,
    ALL,
    ONE
}
