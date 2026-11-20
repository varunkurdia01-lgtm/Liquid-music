package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.LiquidAppContainer
import com.example.domain.model.AudioQuality
import com.example.domain.model.EqPreset
import com.example.domain.model.GlassQualityTier
import com.example.domain.model.MagicSoundPreset
import com.example.domain.model.ReverbPreset
import com.example.domain.model.SoundLabSettings
import com.example.player.PlaybackDspBridge
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SoundLabViewModel(
    private val container: LiquidAppContainer
) : ViewModel() {

    val settings: StateFlow<SoundLabSettings> = container.preferencesRepository.soundLabSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SoundLabSettings())

    val bandLabels: StateFlow<List<String>> = PlaybackDspBridge.soundLabController.bandLabels
    val hardwareAttached: StateFlow<Boolean> = PlaybackDspBridge.soundLabController.hardwareAttached

    fun selectEqPreset(preset: EqPreset) {
        updateSettings { current ->
            current.copy(
                eqPreset = preset,
                eqBandLevelsDb = if (preset == EqPreset.CUSTOM) current.eqBandLevelsDb else preset.bandsDb
            )
        }
    }

    fun updateBandLevel(bandIndex: Int, levelDb: Float) {
        updateSettings { current ->
            val updatedBands = current.eqBandLevelsDb.toMutableList()
            if (bandIndex in updatedBands.indices) {
                updatedBands[bandIndex] = levelDb.coerceIn(-12f, 12f)
            }
            current.copy(
                eqPreset = EqPreset.CUSTOM,
                eqBandLevelsDb = updatedBands
            )
        }
    }

    fun updateBassBoost(strength: Int) {
        updateSettings { it.copy(bassBoostStrength = strength.coerceIn(0, 1000)) }
    }

    fun selectReverb(reverb: ReverbPreset) {
        updateSettings { it.copy(reverbPreset = reverb) }
    }

    fun updateSpeed(speed: Float) {
        updateSettings {
            it.copy(
                playbackSpeed = speed.coerceIn(0.5f, 2.0f),
                activeMagicPreset = MagicSoundPreset.NORMAL
            )
        }
    }

    fun updatePitch(pitch: Float) {
        updateSettings {
            it.copy(
                playbackPitch = pitch.coerceIn(0.5f, 2.0f),
                activeMagicPreset = MagicSoundPreset.NORMAL
            )
        }
    }

    fun applyMagicPreset(preset: MagicSoundPreset) {
        updateSettings { current ->
            when (preset) {
                MagicSoundPreset.NORMAL -> current.copy(
                    playbackSpeed = 1.0f,
                    playbackPitch = 1.0f,
                    reverbPreset = ReverbPreset.NONE,
                    eightDEnabled = false,
                    activeMagicPreset = MagicSoundPreset.NORMAL
                )
                MagicSoundPreset.SLOWED_REVERB -> current.copy(
                    playbackSpeed = 0.84f,
                    playbackPitch = 0.86f,
                    reverbPreset = ReverbPreset.LARGE_HALL,
                    bassBoostStrength = 520,
                    eightDEnabled = false,
                    activeMagicPreset = MagicSoundPreset.SLOWED_REVERB
                )
                MagicSoundPreset.NIGHTCORE -> current.copy(
                    playbackSpeed = 1.24f,
                    playbackPitch = 1.22f,
                    reverbPreset = ReverbPreset.SMALL_ROOM,
                    eqPreset = EqPreset.STUDIO,
                    eqBandLevelsDb = EqPreset.STUDIO.bandsDb,
                    eightDEnabled = false,
                    activeMagicPreset = MagicSoundPreset.NIGHTCORE
                )
                MagicSoundPreset.EIGHT_D -> current.copy(
                    playbackSpeed = 1.0f,
                    playbackPitch = 1.0f,
                    reverbPreset = ReverbPreset.PLATE,
                    eightDEnabled = true,
                    activeMagicPreset = MagicSoundPreset.EIGHT_D
                )
            }
        }
    }

    fun updateCrossfade(seconds: Int) {
        updateSettings { it.copy(crossfadeSeconds = seconds.coerceIn(0, 12)) }
    }

    fun updateAudioQuality(quality: AudioQuality) {
        updateSettings { it.copy(audioQuality = quality) }
    }

    fun updateGlassQuality(tier: GlassQualityTier) {
        updateSettings { it.copy(glassQualityTier = tier) }
    }

    fun toggleEightD(enabled: Boolean) {
        updateSettings {
            it.copy(
                eightDEnabled = enabled,
                activeMagicPreset = if (enabled) MagicSoundPreset.EIGHT_D else MagicSoundPreset.NORMAL
            )
        }
    }

    fun applyGlassOpticalPreset(preset: com.example.domain.model.GlassOpticalPreset) {
        updateSettings { current ->
            current.copy(
                glassConfig = com.example.domain.model.LiquidGlassConfig(
                    opticalPreset = preset,
                    pillScale = preset.pillScale,
                    springStiffness = preset.springStiffness,
                    springDamping = preset.springDamping,
                    refractionHeightDp = preset.refractionHeightDp,
                    refractionAmountDp = preset.refractionAmountDp,
                    blurRadiusDp = preset.blurRadiusDp,
                    chromaticAberration = preset.chromaticAberration
                )
            )
        }
    }

    fun updateGlassConfig(transform: (com.example.domain.model.LiquidGlassConfig) -> com.example.domain.model.LiquidGlassConfig) {
        updateSettings { current ->
            val updated = transform(current.glassConfig).copy(
                opticalPreset = com.example.domain.model.GlassOpticalPreset.CUSTOM
            )
            current.copy(glassConfig = updated)
        }
    }

    private fun updateSettings(transform: (SoundLabSettings) -> SoundLabSettings) {
        viewModelScope.launch {
            container.preferencesRepository.updateSoundLabSettings { current ->
                val next = transform(current)
                PlaybackDspBridge.applySoundLab(next)
                next
            }
        }
    }
}
