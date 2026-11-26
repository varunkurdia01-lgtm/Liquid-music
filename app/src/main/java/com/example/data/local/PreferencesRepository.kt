package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.AudioLoudnessPreset
import com.example.domain.model.AudioQuality
import com.example.domain.model.EchoPlaybackEngine
import com.example.domain.model.EqPreset
import com.example.domain.model.GlassOpticalPreset
import com.example.domain.model.GlassQualityTier
import com.example.domain.model.LiquidGlassConfig
import com.example.domain.model.LyricsAlignStyle
import com.example.domain.model.LyricsAnimationStyle
import com.example.domain.model.MagicSoundPreset
import com.example.domain.model.PlayerBackgroundStyle
import com.example.domain.model.PlayerLayoutStyle
import com.example.domain.model.PlayerSliderStyle
import com.example.domain.model.QuickPicksMode
import com.example.domain.model.RepeatModeState
import com.example.domain.model.ReverbPreset
import com.example.domain.model.SavedQueueCursor
import com.example.domain.model.SoundLabSettings
import com.example.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.liquidDataStore: DataStore<Preferences> by preferencesDataStore(name = "liquid_music_prefs")

interface AuthRepository {
    val userProfile: Flow<UserProfile>
    suspend fun signInWithEmail(displayName: String, email: String)
    suspend fun signInWithGoogleDemo()
    suspend fun continueAsGuest()
    suspend fun updateDisplayName(newName: String)
    suspend fun signOut()
}

class PreferencesRepository(private val context: Context) : AuthRepository {
    private object Keys {
        val DISPLAY_NAME = stringPreferencesKey("user_display_name")
        val EMAIL = stringPreferencesKey("user_email")
        val HANDLE = stringPreferencesKey("user_handle")
        val AVATAR_SEED = intPreferencesKey("user_avatar_seed")
        val IS_GUEST = booleanPreferencesKey("user_is_guest")
        val IS_SIGNED_IN = booleanPreferencesKey("user_is_signed_in")

        val AUDIO_QUALITY = stringPreferencesKey("audio_quality")
        val CELLULAR_AUDIO_QUALITY = stringPreferencesKey("cellular_audio_quality")
        val CROSSFADE_SECONDS = intPreferencesKey("crossfade_seconds")
        val SKIP_SILENCE = booleanPreferencesKey("skip_silence_enabled")
        val SKIP_SILENCE_INSTANT = booleanPreferencesKey("skip_silence_instant")
        val VOLUME_NORMALIZATION = booleanPreferencesKey("volume_normalization_enabled")
        val LOUDNESS_PRESET = stringPreferencesKey("loudness_preset")
        val GAPLESS_PLAYBACK = booleanPreferencesKey("gapless_playback_enabled")
        val AUTO_PLAY_RADIO = booleanPreferencesKey("auto_play_radio_enabled")
        val MAX_CACHE_MB = intPreferencesKey("max_cache_size_mb")
        val MAX_IMAGE_CACHE_MB = intPreferencesKey("max_image_cache_size_mb")
        val GLASS_QUALITY = stringPreferencesKey("glass_quality")
        val GLASS_OPTICAL_PRESET = stringPreferencesKey("glass_optical_preset")
        val GLASS_PILL_SCALE = floatPreferencesKey("glass_pill_scale")
        val GLASS_SPRING_STIFFNESS = floatPreferencesKey("glass_spring_stiffness")
        val GLASS_SPRING_DAMPING = floatPreferencesKey("glass_spring_damping")
        val GLASS_REFRACTION_HEIGHT = floatPreferencesKey("glass_refraction_height")
        val GLASS_REFRACTION_AMOUNT = floatPreferencesKey("glass_refraction_amount")
        val GLASS_BLUR_RADIUS = floatPreferencesKey("glass_blur_radius")
        val GLASS_CHROMATIC = booleanPreferencesKey("glass_chromatic")

        val EQ_PRESET = stringPreferencesKey("eq_preset")
        val EQ_BANDS = stringPreferencesKey("eq_bands_csv")
        val BASS_BOOST = intPreferencesKey("bass_boost_strength")
        val REVERB_PRESET = stringPreferencesKey("reverb_preset")
        val SPEED = floatPreferencesKey("playback_speed")
        val PITCH = floatPreferencesKey("playback_pitch")
        val EIGHT_D = booleanPreferencesKey("eight_d_enabled")
        val MAGIC_PRESET = stringPreferencesKey("magic_preset")

        // Echo Music Appearance & Customization
        val PURE_BLACK_AMOLED = booleanPreferencesKey("pure_black_amoled")
        val DYNAMIC_THEME = booleanPreferencesKey("dynamic_theme_enabled")
        val PLAYER_BG_STYLE = stringPreferencesKey("player_bg_style")
        val PLAYER_SLIDER_STYLE = stringPreferencesKey("player_slider_style")
        val GRID_CELL_BIG = booleanPreferencesKey("grid_cell_big")
        val SHOW_HOME_HERO = booleanPreferencesKey("show_home_hero")
        val SHOW_SPEED_DIAL = booleanPreferencesKey("show_speed_dial")
        val SHOW_HOME_MOODS = booleanPreferencesKey("show_home_moods")
        val RANDOMIZE_HOME = booleanPreferencesKey("randomize_home_order")
        val CROP_ALBUM_ART = booleanPreferencesKey("crop_album_art")
        val SWIPE_TO_SONG = booleanPreferencesKey("swipe_to_song")
        val LYRICS_ALIGN = stringPreferencesKey("lyrics_align")
        val LYRICS_ANIM_STYLE = stringPreferencesKey("lyrics_anim_style")
        val LYRICS_FONT_SIZE = floatPreferencesKey("lyrics_font_size_sp")
        val LYRICS_LINE_SPACING = floatPreferencesKey("lyrics_line_spacing")

        // Echo Music Player & Smart Playback
        val PLAYBACK_ENGINE = stringPreferencesKey("playback_engine")
        val PERSISTENT_QUEUE = booleanPreferencesKey("persistent_queue")
        val PRELOAD_NEXT_SONG = booleanPreferencesKey("preload_next_song")
        val PRELOAD_LYRICS = booleanPreferencesKey("preload_lyrics")
        val DATA_SAVER = booleanPreferencesKey("data_saver_enabled")
        val AUTO_DOWNLOAD_ON_LIKE = booleanPreferencesKey("auto_download_on_like")
        val DOWNLOAD_WIFI_ONLY = booleanPreferencesKey("download_wifi_only")
        val DOWNLOAD_WITH_METADATA = booleanPreferencesKey("download_with_metadata")
        val AUTO_SKIP_ON_ERROR = booleanPreferencesKey("auto_skip_on_error")
        val PREVENT_DUPLICATE_QUEUE = booleanPreferencesKey("prevent_duplicate_queue")
        val REMEMBER_SHUFFLE_REPEAT = booleanPreferencesKey("remember_shuffle_repeat")
        val STOP_ON_TASK_CLEAR = booleanPreferencesKey("stop_on_task_clear")
        val PAUSE_ON_MUTE = booleanPreferencesKey("pause_on_mute")
        val RESUME_ON_BLUETOOTH = booleanPreferencesKey("resume_on_bluetooth")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val SHOW_SEEK_BUTTONS = booleanPreferencesKey("show_seek_buttons")
        val FORCE_OPUS = booleanPreferencesKey("force_opus_audio")
        val BIT_PERFECT_DAC = booleanPreferencesKey("bit_perfect_usb_dac")
        val AUDIO_OFFLOAD = booleanPreferencesKey("audio_offload_enabled")

        // Echo Music Content, Localization & Lyrics
        val CONTENT_COUNTRY = stringPreferencesKey("content_country")
        val CONTENT_LANGUAGE = stringPreferencesKey("content_language")
        val QUICK_PICKS_MODE = stringPreferencesKey("quick_picks_mode")
        val HIDE_EXPLICIT = booleanPreferencesKey("hide_explicit")
        val HIDE_VIDEO_SONGS = booleanPreferencesKey("hide_video_songs")
        val HIDE_SHORTS = booleanPreferencesKey("hide_youtube_shorts")
        val LYRICS_PROVIDER_PRIORITY = stringPreferencesKey("lyrics_provider_priority")
        val ENABLE_LRCLIB = booleanPreferencesKey("enable_lrclib")
        val ENABLE_KUGOU = booleanPreferencesKey("enable_kugou")
        val ENABLE_YOULYPLUS = booleanPreferencesKey("enable_youlyplus")
        val PROXY_ENABLED = booleanPreferencesKey("proxy_enabled")
        val PROXY_URL = stringPreferencesKey("proxy_url")

        // Echo Music AI & Lyrics Translation
        val AI_PROVIDER = stringPreferencesKey("ai_provider")
        val AUTO_TRANSLATE_LYRICS = booleanPreferencesKey("auto_translate_lyrics")
        val TRANSLATE_TARGET_LANG = stringPreferencesKey("translate_target_lang")
        val TRANSLATE_MODE = stringPreferencesKey("translate_mode")
        val AI_RECOMMENDATIONS = booleanPreferencesKey("ai_recommendations_enabled")

        // Echo Music Account, Integrations & Extensions
        val INNERTUBE_COOKIE = stringPreferencesKey("innertube_cookie")
        val VISITOR_DATA = stringPreferencesKey("innertube_visitor_data")
        val YTM_SYNC = booleanPreferencesKey("ytm_sync_enabled")
        val DISCORD_RPC = booleanPreferencesKey("discord_rpc_enabled")
        val LASTFM_ENABLED = booleanPreferencesKey("lastfm_enabled")
        val LISTENBRAINZ_ENABLED = booleanPreferencesKey("listenbrainz_enabled")
        val LISTENBRAINZ_TOKEN = stringPreferencesKey("listenbrainz_token")
        val PREFER_EXTENSION_AUDIO = booleanPreferencesKey("prefer_extension_audio")
        val CUSTOM_EXTENSION_URL = stringPreferencesKey("custom_extension_url")
        val LOSSLESS_EXTENSION_URL = stringPreferencesKey("lossless_extension_url")

        // Echo Music Appearance & Customization
        val PLAYER_LAYOUT_STYLE = stringPreferencesKey("player_layout_style")
        val LYRICS_OFFSET_MS = longPreferencesKey("lyrics_offset_ms")

        // Echo Music Account & Multi-Addons
        val DATA_SYNC_ID = stringPreferencesKey("ytm_data_sync_id")
        val YTM_ACCOUNT_NAME = stringPreferencesKey("ytm_account_name")
        val YTM_ACCOUNT_HANDLE = stringPreferencesKey("ytm_account_handle")
        val YTM_ACCOUNT_AVATAR = stringPreferencesKey("ytm_account_avatar")
        val YTM_LAST_SYNCED_AT = longPreferencesKey("ytm_last_synced_at")
        val ADDONS_JSON = stringPreferencesKey("echo_addons_json")

        // Persistent Queue Cursor
        val QUEUE_ACTIVE_INDEX = intPreferencesKey("queue_active_index")
        val QUEUE_POSITION_MS = longPreferencesKey("queue_position_ms")
        val QUEUE_SHUFFLE = booleanPreferencesKey("queue_shuffle_enabled")
        val QUEUE_REPEAT = stringPreferencesKey("queue_repeat_mode")

        // Echo Music Privacy
        val PAUSE_LISTEN_HISTORY = booleanPreferencesKey("pause_listen_history")
        val PAUSE_SEARCH_HISTORY = booleanPreferencesKey("pause_search_history")
        val DISABLE_SCREENSHOT = booleanPreferencesKey("disable_screenshot_privacy")
        val BLOCKED_ARTISTS = stringPreferencesKey("blocked_artists_csv")
    }

    override val userProfile: Flow<UserProfile> = context.liquidDataStore.data.map { prefs ->
        UserProfile(
            displayName = prefs[Keys.DISPLAY_NAME] ?: "Listener",
            email = prefs[Keys.EMAIL] ?: "",
            handle = prefs[Keys.HANDLE] ?: "@liquid",
            avatarSeed = prefs[Keys.AVATAR_SEED] ?: 1,
            isGuest = prefs[Keys.IS_GUEST] ?: true,
            isSignedIn = prefs[Keys.IS_SIGNED_IN] ?: false
        )
    }

    override suspend fun signInWithEmail(displayName: String, email: String) {
        val cleanName = displayName.trim().ifBlank { email.substringBefore("@").ifBlank { "Liquid Listener" } }
        val cleanHandle = "@" + cleanName.lowercase().replace(Regex("[^a-z0-9]"), "").take(14).ifBlank { "liquid" }
        context.liquidDataStore.edit { prefs ->
            prefs[Keys.DISPLAY_NAME] = cleanName
            prefs[Keys.EMAIL] = email.trim()
            prefs[Keys.HANDLE] = cleanHandle
            prefs[Keys.AVATAR_SEED] = cleanName.hashCode()
            prefs[Keys.IS_GUEST] = false
            prefs[Keys.IS_SIGNED_IN] = true
        }
    }

    override suspend fun signInWithGoogleDemo() {
        context.liquidDataStore.edit { prefs ->
            prefs[Keys.DISPLAY_NAME] = "Alex Rivera"
            prefs[Keys.EMAIL] = "alex.rivera@liquid.audio"
            prefs[Keys.HANDLE] = "@alexrivera"
            prefs[Keys.AVATAR_SEED] = 42
            prefs[Keys.IS_GUEST] = false
            prefs[Keys.IS_SIGNED_IN] = true
        }
    }

    override suspend fun continueAsGuest() {
        context.liquidDataStore.edit { prefs ->
            prefs[Keys.DISPLAY_NAME] = "Guest Listener"
            prefs[Keys.EMAIL] = "local.guest@liquid"
            prefs[Keys.HANDLE] = "@guest"
            prefs[Keys.AVATAR_SEED] = 7
            prefs[Keys.IS_GUEST] = true
            prefs[Keys.IS_SIGNED_IN] = true
        }
    }

    override suspend fun updateDisplayName(newName: String) {
        val clean = newName.trim().ifBlank { return }
        context.liquidDataStore.edit { prefs ->
            prefs[Keys.DISPLAY_NAME] = clean
            prefs[Keys.HANDLE] = "@" + clean.lowercase().replace(Regex("[^a-z0-9]"), "").take(14).ifBlank { "liquid" }
        }
    }

    override suspend fun signOut() {
        context.liquidDataStore.edit { prefs ->
            prefs[Keys.IS_SIGNED_IN] = false
        }
    }

    val soundLabSettings: Flow<SoundLabSettings> = context.liquidDataStore.data.map { prefs ->
        decodeSoundLab(prefs)
    }

    suspend fun updateSoundLabSettings(transform: (SoundLabSettings) -> SoundLabSettings) {
        context.liquidDataStore.edit { prefs ->
            val current = decodeSoundLab(prefs)
            val updated = transform(current)
            val g = updated.glassConfig
            prefs[Keys.AUDIO_QUALITY] = updated.audioQuality.name
            prefs[Keys.CELLULAR_AUDIO_QUALITY] = updated.cellularAudioQuality.name
            prefs[Keys.CROSSFADE_SECONDS] = updated.crossfadeSeconds.coerceIn(0, 12)
            prefs[Keys.SKIP_SILENCE] = updated.skipSilenceEnabled
            prefs[Keys.SKIP_SILENCE_INSTANT] = updated.skipSilenceInstant
            prefs[Keys.VOLUME_NORMALIZATION] = updated.volumeNormalizationEnabled
            prefs[Keys.LOUDNESS_PRESET] = updated.loudnessPreset.name
            prefs[Keys.GAPLESS_PLAYBACK] = updated.gaplessPlaybackEnabled
            prefs[Keys.AUTO_PLAY_RADIO] = updated.autoPlayRadioEnabled
            prefs[Keys.MAX_CACHE_MB] = updated.maxCacheSizeMb.coerceIn(128, 4096)
            prefs[Keys.MAX_IMAGE_CACHE_MB] = updated.maxImageCacheSizeMb.coerceIn(128, 2048)
            prefs[Keys.GLASS_QUALITY] = updated.glassQualityTier.name
            prefs[Keys.GLASS_OPTICAL_PRESET] = g.opticalPreset.name
            prefs[Keys.GLASS_PILL_SCALE] = g.pillScale.coerceIn(0.85f, 1.25f)
            prefs[Keys.GLASS_SPRING_STIFFNESS] = g.springStiffness.coerceIn(180f, 750f)
            prefs[Keys.GLASS_SPRING_DAMPING] = g.springDamping.coerceIn(0.42f, 0.95f)
            prefs[Keys.GLASS_REFRACTION_HEIGHT] = g.refractionHeightDp.coerceIn(4f, 36f)
            prefs[Keys.GLASS_REFRACTION_AMOUNT] = g.refractionAmountDp.coerceIn(8f, 64f)
            prefs[Keys.GLASS_BLUR_RADIUS] = g.blurRadiusDp.coerceIn(0f, 32f)
            prefs[Keys.GLASS_CHROMATIC] = g.chromaticAberration

            prefs[Keys.EQ_PRESET] = updated.eqPreset.name
            prefs[Keys.EQ_BANDS] = updated.eqBandLevelsDb.joinToString(",")
            prefs[Keys.BASS_BOOST] = updated.bassBoostStrength.coerceIn(0, 1000)
            prefs[Keys.REVERB_PRESET] = updated.reverbPreset.name
            prefs[Keys.SPEED] = updated.playbackSpeed.coerceIn(0.5f, 2.0f)
            prefs[Keys.PITCH] = updated.playbackPitch.coerceIn(0.5f, 2.0f)
            prefs[Keys.EIGHT_D] = updated.eightDEnabled
            prefs[Keys.MAGIC_PRESET] = updated.activeMagicPreset.name

            // Echo Music Appearance & Player Customization
            prefs[Keys.PURE_BLACK_AMOLED] = updated.pureBlackAmoled
            prefs[Keys.DYNAMIC_THEME] = updated.dynamicThemeEnabled
            prefs[Keys.PLAYER_LAYOUT_STYLE] = updated.playerLayoutStyle.name
            prefs[Keys.PLAYER_BG_STYLE] = updated.playerBackgroundStyle.name
            prefs[Keys.PLAYER_SLIDER_STYLE] = updated.playerSliderStyle.name
            prefs[Keys.GRID_CELL_BIG] = updated.gridCellSizeBig
            prefs[Keys.SHOW_HOME_HERO] = updated.showHomeHeroCarousel
            prefs[Keys.SHOW_SPEED_DIAL] = updated.showSpeedDialQuickPicks
            prefs[Keys.SHOW_HOME_MOODS] = updated.showHomeMoodAndGenres
            prefs[Keys.RANDOMIZE_HOME] = updated.randomizeHomeOrder
            prefs[Keys.CROP_ALBUM_ART] = updated.cropAlbumArt
            prefs[Keys.SWIPE_TO_SONG] = updated.swipeToSongEnabled
            prefs[Keys.LYRICS_ALIGN] = updated.lyricsAlignment.name
            prefs[Keys.LYRICS_ANIM_STYLE] = updated.lyricsAnimationStyle.name
            prefs[Keys.LYRICS_FONT_SIZE] = updated.lyricsFontSizeSp.coerceIn(14f, 34f)
            prefs[Keys.LYRICS_LINE_SPACING] = updated.lyricsLineSpacing.coerceIn(1.0f, 2.0f)
            prefs[Keys.LYRICS_OFFSET_MS] = updated.lyricsOffsetMs.coerceIn(-10_000L, 10_000L)

            // Echo Music Player & Smart Playback
            prefs[Keys.PLAYBACK_ENGINE] = updated.playbackEngine.name
            prefs[Keys.PERSISTENT_QUEUE] = updated.persistentQueueEnabled
            prefs[Keys.PRELOAD_NEXT_SONG] = updated.preloadNextSongEnabled
            prefs[Keys.PRELOAD_LYRICS] = updated.preloadLyricsEnabled
            prefs[Keys.DATA_SAVER] = updated.dataSaverEnabled
            prefs[Keys.AUTO_DOWNLOAD_ON_LIKE] = updated.autoDownloadOnLike
            prefs[Keys.DOWNLOAD_WIFI_ONLY] = updated.downloadOnWifiOnly
            prefs[Keys.DOWNLOAD_WITH_METADATA] = updated.downloadWithMetadata
            prefs[Keys.AUTO_SKIP_ON_ERROR] = updated.autoSkipNextOnError
            prefs[Keys.PREVENT_DUPLICATE_QUEUE] = updated.preventDuplicateTracksInQueue
            prefs[Keys.REMEMBER_SHUFFLE_REPEAT] = updated.rememberShuffleAndRepeat
            prefs[Keys.STOP_ON_TASK_CLEAR] = updated.stopMusicOnTaskClear
            prefs[Keys.PAUSE_ON_MUTE] = updated.pauseOnMute
            prefs[Keys.RESUME_ON_BLUETOOTH] = updated.resumeOnBluetoothConnect
            prefs[Keys.KEEP_SCREEN_ON] = updated.keepScreenOn
            prefs[Keys.SHOW_SEEK_BUTTONS] = updated.showSeekButtons
            prefs[Keys.FORCE_OPUS] = updated.forceOpusAudio
            prefs[Keys.BIT_PERFECT_DAC] = updated.bitPerfectUsbDac
            prefs[Keys.AUDIO_OFFLOAD] = updated.audioOffloadEnabled

            // Echo Music Content, Localization & Lyrics
            prefs[Keys.CONTENT_COUNTRY] = updated.contentCountry
            prefs[Keys.CONTENT_LANGUAGE] = updated.contentLanguage
            prefs[Keys.QUICK_PICKS_MODE] = updated.quickPicksMode.name
            prefs[Keys.HIDE_EXPLICIT] = updated.hideExplicit
            prefs[Keys.HIDE_VIDEO_SONGS] = updated.hideVideoSongs
            prefs[Keys.HIDE_SHORTS] = updated.hideYoutubeShorts
            prefs[Keys.LYRICS_PROVIDER_PRIORITY] = updated.lyricsProviderPriority
            prefs[Keys.ENABLE_LRCLIB] = updated.enableLrcLib
            prefs[Keys.ENABLE_KUGOU] = updated.enableKuGou
            prefs[Keys.ENABLE_YOULYPLUS] = updated.enableYouLyPlus
            prefs[Keys.PROXY_ENABLED] = updated.proxyEnabled
            prefs[Keys.PROXY_URL] = updated.proxyUrl

            // Echo Music AI & Lyrics Translation
            prefs[Keys.AI_PROVIDER] = updated.aiProvider
            prefs[Keys.AUTO_TRANSLATE_LYRICS] = updated.autoTranslateLyrics
            prefs[Keys.TRANSLATE_TARGET_LANG] = updated.translateTargetLanguage
            prefs[Keys.TRANSLATE_MODE] = updated.translateMode
            prefs[Keys.AI_RECOMMENDATIONS] = updated.aiRecommendationsEnabled

            // Echo Music Account, Integrations & Extensions
            prefs[Keys.INNERTUBE_COOKIE] = updated.innerTubeCookie
            prefs[Keys.VISITOR_DATA] = updated.visitorData
            prefs[Keys.DATA_SYNC_ID] = updated.dataSyncId
            prefs[Keys.YTM_ACCOUNT_NAME] = updated.ytmAccountName
            prefs[Keys.YTM_ACCOUNT_HANDLE] = updated.ytmAccountHandle
            prefs[Keys.YTM_ACCOUNT_AVATAR] = updated.ytmAccountAvatarUrl
            prefs[Keys.YTM_LAST_SYNCED_AT] = updated.ytmLastSyncedAt
            prefs[Keys.YTM_SYNC] = updated.ytmSyncEnabled
            prefs[Keys.DISCORD_RPC] = updated.discordRpcEnabled
            prefs[Keys.LASTFM_ENABLED] = updated.lastFmEnabled
            prefs[Keys.LISTENBRAINZ_ENABLED] = updated.listenBrainzEnabled
            prefs[Keys.LISTENBRAINZ_TOKEN] = updated.listenBrainzToken
            prefs[Keys.PREFER_EXTENSION_AUDIO] = updated.preferExtensionAudio
            prefs[Keys.CUSTOM_EXTENSION_URL] = updated.customExtensionUrl
            prefs[Keys.LOSSLESS_EXTENSION_URL] = updated.losslessExtensionUrl
            prefs[Keys.ADDONS_JSON] = updated.addonsJson

            // Echo Music Privacy
            prefs[Keys.PAUSE_LISTEN_HISTORY] = updated.pauseListenHistory
            prefs[Keys.PAUSE_SEARCH_HISTORY] = updated.pauseSearchHistory
            prefs[Keys.DISABLE_SCREENSHOT] = updated.disableScreenshotPrivacy
            prefs[Keys.BLOCKED_ARTISTS] = updated.blockedArtistsCsv
        }
    }

    suspend fun saveQueueCursor(cursor: SavedQueueCursor) {
        context.liquidDataStore.edit { prefs ->
            prefs[Keys.QUEUE_ACTIVE_INDEX] = cursor.activeIndex.coerceAtLeast(0)
            prefs[Keys.QUEUE_POSITION_MS] = cursor.positionMs.coerceAtLeast(0L)
            prefs[Keys.QUEUE_SHUFFLE] = cursor.shuffleEnabled
            prefs[Keys.QUEUE_REPEAT] = cursor.repeatMode.name
        }
    }

    suspend fun getQueueCursorOnce(): SavedQueueCursor {
        val prefs = context.liquidDataStore.data.first()
        val repeat = runCatching { RepeatModeState.valueOf(prefs[Keys.QUEUE_REPEAT] ?: "") }
            .getOrDefault(RepeatModeState.OFF)
        return SavedQueueCursor(
            activeIndex = prefs[Keys.QUEUE_ACTIVE_INDEX] ?: 0,
            positionMs = prefs[Keys.QUEUE_POSITION_MS] ?: 0L,
            shuffleEnabled = prefs[Keys.QUEUE_SHUFFLE] ?: false,
            repeatMode = repeat
        )
    }

    private fun decodeSoundLab(prefs: Preferences): SoundLabSettings {
        val quality = runCatching { AudioQuality.valueOf(prefs[Keys.AUDIO_QUALITY] ?: "") }
            .getOrDefault(AudioQuality.HIGH_320)
        val cellQuality = runCatching { AudioQuality.valueOf(prefs[Keys.CELLULAR_AUDIO_QUALITY] ?: "") }
            .getOrDefault(AudioQuality.HIGH_320)
        val loudness = runCatching { AudioLoudnessPreset.valueOf(prefs[Keys.LOUDNESS_PRESET] ?: "") }
            .getOrDefault(AudioLoudnessPreset.NORMAL)
        val glass = runCatching { GlassQualityTier.valueOf(prefs[Keys.GLASS_QUALITY] ?: "") }
            .getOrDefault(GlassQualityTier.ULTRA_AGSL)
        val opticalPreset = runCatching { GlassOpticalPreset.valueOf(prefs[Keys.GLASS_OPTICAL_PRESET] ?: "") }
            .getOrDefault(GlassOpticalPreset.BALANCED_IOS26)
        val glassConfig = LiquidGlassConfig(
            opticalPreset = opticalPreset,
            pillScale = prefs[Keys.GLASS_PILL_SCALE] ?: opticalPreset.pillScale,
            springStiffness = prefs[Keys.GLASS_SPRING_STIFFNESS] ?: opticalPreset.springStiffness,
            springDamping = prefs[Keys.GLASS_SPRING_DAMPING] ?: opticalPreset.springDamping,
            refractionHeightDp = prefs[Keys.GLASS_REFRACTION_HEIGHT] ?: opticalPreset.refractionHeightDp,
            refractionAmountDp = prefs[Keys.GLASS_REFRACTION_AMOUNT] ?: opticalPreset.refractionAmountDp,
            blurRadiusDp = prefs[Keys.GLASS_BLUR_RADIUS] ?: opticalPreset.blurRadiusDp,
            chromaticAberration = prefs[Keys.GLASS_CHROMATIC] ?: opticalPreset.chromaticAberration
        )
        val eqPreset = runCatching { EqPreset.valueOf(prefs[Keys.EQ_PRESET] ?: "") }
            .getOrDefault(EqPreset.STUDIO)
        val reverb = runCatching { ReverbPreset.valueOf(prefs[Keys.REVERB_PRESET] ?: "") }
            .getOrDefault(ReverbPreset.NONE)
        val magic = runCatching { MagicSoundPreset.valueOf(prefs[Keys.MAGIC_PRESET] ?: "") }
            .getOrDefault(MagicSoundPreset.NORMAL)
        val parsedBands = prefs[Keys.EQ_BANDS]
            ?.split(",")
            ?.mapNotNull { it.toFloatOrNull() }
            ?.takeIf { it.size == 5 }
            ?: eqPreset.bandsDb

        val layoutStyle = runCatching { PlayerLayoutStyle.valueOf(prefs[Keys.PLAYER_LAYOUT_STYLE] ?: "") }
            .getOrDefault(PlayerLayoutStyle.ECHO_MATERIAL)
        val bgStyle = runCatching { PlayerBackgroundStyle.valueOf(prefs[Keys.PLAYER_BG_STYLE] ?: "") }
            .getOrDefault(PlayerBackgroundStyle.GRADIENT)
        val sliderStyle = runCatching { PlayerSliderStyle.valueOf(prefs[Keys.PLAYER_SLIDER_STYLE] ?: "") }
            .getOrDefault(PlayerSliderStyle.SQUIGGLY)
        val lyricsAlign = runCatching { LyricsAlignStyle.valueOf(prefs[Keys.LYRICS_ALIGN] ?: "") }
            .getOrDefault(LyricsAlignStyle.LEFT)
        val lyricsAnim = runCatching { LyricsAnimationStyle.valueOf(prefs[Keys.LYRICS_ANIM_STYLE] ?: "") }
            .getOrDefault(LyricsAnimationStyle.APPLE_FLUID)
        val engine = runCatching { EchoPlaybackEngine.valueOf(prefs[Keys.PLAYBACK_ENGINE] ?: "") }
            .getOrDefault(EchoPlaybackEngine.AUTO)
        val quickMode = runCatching { QuickPicksMode.valueOf(prefs[Keys.QUICK_PICKS_MODE] ?: "") }
            .getOrDefault(QuickPicksMode.QUICK_PICKS)

        return SoundLabSettings(
            audioQuality = quality,
            cellularAudioQuality = cellQuality,
            crossfadeSeconds = prefs[Keys.CROSSFADE_SECONDS] ?: 4,
            skipSilenceEnabled = prefs[Keys.SKIP_SILENCE] ?: false,
            skipSilenceInstant = prefs[Keys.SKIP_SILENCE_INSTANT] ?: false,
            volumeNormalizationEnabled = prefs[Keys.VOLUME_NORMALIZATION] ?: true,
            loudnessPreset = loudness,
            gaplessPlaybackEnabled = prefs[Keys.GAPLESS_PLAYBACK] ?: true,
            autoPlayRadioEnabled = prefs[Keys.AUTO_PLAY_RADIO] ?: true,
            maxCacheSizeMb = prefs[Keys.MAX_CACHE_MB] ?: 512,
            maxImageCacheSizeMb = prefs[Keys.MAX_IMAGE_CACHE_MB] ?: 512,
            glassQualityTier = glass,
            glassConfig = glassConfig,
            eqPreset = eqPreset,
            eqBandLevelsDb = parsedBands,
            bassBoostStrength = prefs[Keys.BASS_BOOST] ?: 350,
            reverbPreset = reverb,
            playbackSpeed = prefs[Keys.SPEED] ?: 1.0f,
            playbackPitch = prefs[Keys.PITCH] ?: 1.0f,
            eightDEnabled = prefs[Keys.EIGHT_D] ?: false,
            activeMagicPreset = magic,
            pureBlackAmoled = prefs[Keys.PURE_BLACK_AMOLED] ?: true,
            dynamicThemeEnabled = prefs[Keys.DYNAMIC_THEME] ?: true,
            playerLayoutStyle = layoutStyle,
            playerBackgroundStyle = bgStyle,
            playerSliderStyle = sliderStyle,
            gridCellSizeBig = prefs[Keys.GRID_CELL_BIG] ?: true,
            showHomeHeroCarousel = prefs[Keys.SHOW_HOME_HERO] ?: true,
            showSpeedDialQuickPicks = prefs[Keys.SHOW_SPEED_DIAL] ?: true,
            showHomeMoodAndGenres = prefs[Keys.SHOW_HOME_MOODS] ?: true,
            randomizeHomeOrder = prefs[Keys.RANDOMIZE_HOME] ?: false,
            cropAlbumArt = prefs[Keys.CROP_ALBUM_ART] ?: false,
            swipeToSongEnabled = prefs[Keys.SWIPE_TO_SONG] ?: true,
            lyricsAlignment = lyricsAlign,
            lyricsAnimationStyle = lyricsAnim,
            lyricsFontSizeSp = prefs[Keys.LYRICS_FONT_SIZE] ?: 22f,
            lyricsLineSpacing = prefs[Keys.LYRICS_LINE_SPACING] ?: 1.3f,
            lyricsOffsetMs = prefs[Keys.LYRICS_OFFSET_MS] ?: 0L,
            playbackEngine = engine,
            persistentQueueEnabled = prefs[Keys.PERSISTENT_QUEUE] ?: true,
            preloadNextSongEnabled = prefs[Keys.PRELOAD_NEXT_SONG] ?: true,
            preloadLyricsEnabled = prefs[Keys.PRELOAD_LYRICS] ?: true,
            dataSaverEnabled = prefs[Keys.DATA_SAVER] ?: false,
            autoDownloadOnLike = prefs[Keys.AUTO_DOWNLOAD_ON_LIKE] ?: false,
            downloadOnWifiOnly = prefs[Keys.DOWNLOAD_WIFI_ONLY] ?: false,
            downloadWithMetadata = prefs[Keys.DOWNLOAD_WITH_METADATA] ?: true,
            autoSkipNextOnError = prefs[Keys.AUTO_SKIP_ON_ERROR] ?: true,
            preventDuplicateTracksInQueue = prefs[Keys.PREVENT_DUPLICATE_QUEUE] ?: true,
            rememberShuffleAndRepeat = prefs[Keys.REMEMBER_SHUFFLE_REPEAT] ?: true,
            stopMusicOnTaskClear = prefs[Keys.STOP_ON_TASK_CLEAR] ?: true,
            pauseOnMute = prefs[Keys.PAUSE_ON_MUTE] ?: true,
            resumeOnBluetoothConnect = prefs[Keys.RESUME_ON_BLUETOOTH] ?: true,
            keepScreenOn = prefs[Keys.KEEP_SCREEN_ON] ?: false,
            showSeekButtons = prefs[Keys.SHOW_SEEK_BUTTONS] ?: true,
            forceOpusAudio = prefs[Keys.FORCE_OPUS] ?: false,
            bitPerfectUsbDac = prefs[Keys.BIT_PERFECT_DAC] ?: false,
            audioOffloadEnabled = prefs[Keys.AUDIO_OFFLOAD] ?: false,
            contentCountry = prefs[Keys.CONTENT_COUNTRY] ?: "US",
            contentLanguage = prefs[Keys.CONTENT_LANGUAGE] ?: "en",
            quickPicksMode = quickMode,
            hideExplicit = prefs[Keys.HIDE_EXPLICIT] ?: false,
            hideVideoSongs = prefs[Keys.HIDE_VIDEO_SONGS] ?: false,
            hideYoutubeShorts = prefs[Keys.HIDE_SHORTS] ?: true,
            lyricsProviderPriority = prefs[Keys.LYRICS_PROVIDER_PRIORITY] ?: "LRCLIB,KuGou,SimpMusic,YouTube Music",
            enableLrcLib = prefs[Keys.ENABLE_LRCLIB] ?: true,
            enableKuGou = prefs[Keys.ENABLE_KUGOU] ?: true,
            enableYouLyPlus = prefs[Keys.ENABLE_YOULYPLUS] ?: true,
            proxyEnabled = prefs[Keys.PROXY_ENABLED] ?: false,
            proxyUrl = prefs[Keys.PROXY_URL] ?: "",
            aiProvider = prefs[Keys.AI_PROVIDER] ?: "Gemini AI Logic",
            autoTranslateLyrics = prefs[Keys.AUTO_TRANSLATE_LYRICS] ?: false,
            translateTargetLanguage = prefs[Keys.TRANSLATE_TARGET_LANG] ?: "English",
            translateMode = prefs[Keys.TRANSLATE_MODE] ?: "Natural / Poetic",
            aiRecommendationsEnabled = prefs[Keys.AI_RECOMMENDATIONS] ?: true,
            innerTubeCookie = prefs[Keys.INNERTUBE_COOKIE] ?: "",
            visitorData = prefs[Keys.VISITOR_DATA] ?: "",
            dataSyncId = prefs[Keys.DATA_SYNC_ID] ?: "",
            ytmAccountName = prefs[Keys.YTM_ACCOUNT_NAME] ?: "",
            ytmAccountHandle = prefs[Keys.YTM_ACCOUNT_HANDLE] ?: "",
            ytmAccountAvatarUrl = prefs[Keys.YTM_ACCOUNT_AVATAR] ?: "",
            ytmLastSyncedAt = prefs[Keys.YTM_LAST_SYNCED_AT] ?: 0L,
            ytmSyncEnabled = prefs[Keys.YTM_SYNC] ?: true,
            discordRpcEnabled = prefs[Keys.DISCORD_RPC] ?: false,
            lastFmEnabled = prefs[Keys.LASTFM_ENABLED] ?: false,
            listenBrainzEnabled = prefs[Keys.LISTENBRAINZ_ENABLED] ?: false,
            listenBrainzToken = prefs[Keys.LISTENBRAINZ_TOKEN] ?: "",
            preferExtensionAudio = prefs[Keys.PREFER_EXTENSION_AUDIO] ?: false,
            customExtensionUrl = prefs[Keys.CUSTOM_EXTENSION_URL] ?: "https://saavn.sumit.co/api",
            losslessExtensionUrl = prefs[Keys.LOSSLESS_EXTENSION_URL] ?: "",
            addonsJson = prefs[Keys.ADDONS_JSON] ?: "",
            pauseListenHistory = prefs[Keys.PAUSE_LISTEN_HISTORY] ?: false,
            pauseSearchHistory = prefs[Keys.PAUSE_SEARCH_HISTORY] ?: false,
            disableScreenshotPrivacy = prefs[Keys.DISABLE_SCREENSHOT] ?: false,
            blockedArtistsCsv = prefs[Keys.BLOCKED_ARTISTS] ?: ""
        )
    }
}
