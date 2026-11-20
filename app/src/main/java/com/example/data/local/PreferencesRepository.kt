package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.AudioQuality
import com.example.domain.model.EqPreset
import com.example.domain.model.GlassOpticalPreset
import com.example.domain.model.GlassQualityTier
import com.example.domain.model.LiquidGlassConfig
import com.example.domain.model.MagicSoundPreset
import com.example.domain.model.ReverbPreset
import com.example.domain.model.SoundLabSettings
import com.example.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.liquidDataStore: DataStore<Preferences> by preferencesDataStore(name = "liquid_music_prefs")

/**
 * Swappable authentication interface allowing seamless future upgrade to Firebase Auth.
 */
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
        val CROSSFADE_SECONDS = intPreferencesKey("crossfade_seconds")
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
            prefs[Keys.CROSSFADE_SECONDS] = updated.crossfadeSeconds.coerceIn(0, 12)
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
        }
    }

    private fun decodeSoundLab(prefs: Preferences): SoundLabSettings {
        val quality = runCatching { AudioQuality.valueOf(prefs[Keys.AUDIO_QUALITY] ?: "") }
            .getOrDefault(AudioQuality.HIGH_320)
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

        return SoundLabSettings(
            audioQuality = quality,
            crossfadeSeconds = prefs[Keys.CROSSFADE_SECONDS] ?: 4,
            glassQualityTier = glass,
            glassConfig = glassConfig,
            eqPreset = eqPreset,
            eqBandLevelsDb = parsedBands,
            bassBoostStrength = prefs[Keys.BASS_BOOST] ?: 350,
            reverbPreset = reverb,
            playbackSpeed = prefs[Keys.SPEED] ?: 1.0f,
            playbackPitch = prefs[Keys.PITCH] ?: 1.0f,
            eightDEnabled = prefs[Keys.EIGHT_D] ?: false,
            activeMagicPreset = magic
        )
    }
}
