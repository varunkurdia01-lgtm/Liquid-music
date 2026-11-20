package com.example.domain.model

import androidx.compose.runtime.Immutable

enum class SourceType {
    ONLINE,
    LOCAL
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
}

@Immutable
data class LyricLine(
    val timestampMs: Long,
    val text: String
)

@Immutable
data class LyricsState(
    val isLoading: Boolean = false,
    val syncedLines: List<LyricLine> = emptyList(),
    val plainLyrics: String = "",
    val isInstrumental: Boolean = false,
    val errorMessage: String? = null
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
    LOW_96(96, "96 kbps"),
    MEDIUM_160(160, "160 kbps"),
    HIGH_320(320, "320 kbps")
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

@Immutable
data class SoundLabSettings(
    val audioQuality: AudioQuality = AudioQuality.HIGH_320,
    val crossfadeSeconds: Int = 4,
    val glassQualityTier: GlassQualityTier = GlassQualityTier.ULTRA_AGSL,
    val glassConfig: LiquidGlassConfig = LiquidGlassConfig(),
    val eqPreset: EqPreset = EqPreset.STUDIO,
    val eqBandLevelsDb: List<Float> = EqPreset.STUDIO.bandsDb,
    val bassBoostStrength: Int = 350, // 0..1000
    val reverbPreset: ReverbPreset = ReverbPreset.NONE,
    val playbackSpeed: Float = 1.0f,
    val playbackPitch: Float = 1.0f,
    val eightDEnabled: Boolean = false,
    val activeMagicPreset: MagicSoundPreset = MagicSoundPreset.NORMAL
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
    PROFILE("Profile")
}

enum class RepeatModeState {
    OFF,
    ALL,
    ONE
}
