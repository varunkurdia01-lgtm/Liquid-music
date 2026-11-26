package com.example.player

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.PresetReverb
import android.util.Log
import com.example.domain.model.ReverbPreset
import com.example.domain.model.SoundLabSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "SoundLabController"

/**
 * Hardware Android AudioEffect controller:
 * 5-Band Equalizer, BassBoost, PresetReverb, and LoudnessEnhancer (Audio Volume Normalization).
 */
class SoundLabController {
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var presetReverb: PresetReverb? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var boundSessionId: Int = 0
    private var latestSettings: SoundLabSettings = SoundLabSettings()
    private var currentTrackLoudnessDb: Double? = null

    private val _bandLabels = MutableStateFlow(listOf("60Hz", "230Hz", "910Hz", "3.6k", "14k"))
    val bandLabels: StateFlow<List<String>> = _bandLabels.asStateFlow()

    private val _hardwareAttached = MutableStateFlow(false)
    val hardwareAttached: StateFlow<Boolean> = _hardwareAttached.asStateFlow()

    @Synchronized
    fun attachToAudioSession(audioSessionId: Int) {
        if (audioSessionId <= 0 || audioSessionId == boundSessionId) return
        releaseEffects()
        boundSessionId = audioSessionId

        var attachedAny = false

        runCatching {
            val eq = Equalizer(0, audioSessionId).apply { enabled = true }
            equalizer = eq
            attachedAny = true
            val count = eq.numberOfBands.toInt().coerceIn(1, 5)
            val labels = (0 until count).map { idx ->
                val hz = eq.getCenterFreq(idx.toShort()) / 1000
                if (hz >= 1000) "${"%.1f".format(hz / 1000f)}k" else "${hz}Hz"
            }
            if (labels.size == 5) {
                _bandLabels.value = labels
            }
        }.onFailure {
            Log.w(TAG, "Equalizer attach skipped on this audio device: ${it.message}")
        }

        runCatching {
            bassBoost = BassBoost(0, audioSessionId).apply { enabled = true }
            attachedAny = true
        }.onFailure {
            Log.w(TAG, "BassBoost attach skipped: ${it.message}")
        }

        runCatching {
            presetReverb = PresetReverb(0, audioSessionId).apply { enabled = false }
        }.onFailure {
            Log.w(TAG, "PresetReverb attach skipped: ${it.message}")
        }

        runCatching {
            loudnessEnhancer = LoudnessEnhancer(audioSessionId).apply { enabled = false }
            attachedAny = true
        }.onFailure {
            Log.w(TAG, "LoudnessEnhancer attach skipped: ${it.message}")
        }

        _hardwareAttached.value = attachedAny
        applySettings(latestSettings)
    }

    @Synchronized
    fun applySettings(settings: SoundLabSettings) {
        latestSettings = settings

        // Apply Equalizer bands
        equalizer?.let { eq ->
            runCatching {
                eq.enabled = true
                val range = eq.bandLevelRange
                val minMb = range[0].toInt()
                val maxMb = range[1].toInt()
                val bandsCount = eq.numberOfBands.toInt()
                for (i in 0 until bandsCount) {
                    val db = settings.eqBandLevelsDb.getOrElse(i) { 0f }
                    val mb = (db * 100f).toInt().coerceIn(minMb, maxMb).toShort()
                    eq.setBandLevel(i.toShort(), mb)
                }
            }.onFailure {
                Log.w(TAG, "Failed updating EQ bands: ${it.message}")
            }
        }

        // Apply Bass Boost
        bassBoost?.let { bb ->
            runCatching {
                val strength = settings.bassBoostStrength.coerceIn(0, 1000).toShort()
                bb.enabled = strength > 0
                if (bb.strengthSupported) {
                    bb.setStrength(strength)
                }
            }.onFailure {
                Log.w(TAG, "Failed updating BassBoost: ${it.message}")
            }
        }

        // Apply Reverb
        presetReverb?.let { rev ->
            runCatching {
                if (settings.reverbPreset == ReverbPreset.NONE) {
                    rev.preset = PresetReverb.PRESET_NONE
                    rev.enabled = false
                } else {
                    rev.preset = settings.reverbPreset.androidPreset
                    rev.enabled = true
                }
            }.onFailure {
                Log.w(TAG, "Failed updating PresetReverb: ${it.message}")
            }
        }

        // Apply real loudnessDb-based volume normalization via LoudnessEnhancer
        applyLoudnessNormalization(settings)
    }

    @Synchronized
    fun applyTrackLoudness(loudnessDb: Double?) {
        currentTrackLoudnessDb = loudnessDb
        applyLoudnessNormalization(latestSettings)
    }

    private fun applyLoudnessNormalization(settings: SoundLabSettings) {
        loudnessEnhancer?.let { le ->
            runCatching {
                if (settings.volumeNormalizationEnabled) {
                    val presetTargetDb = settings.loudnessPreset.targetGainDb
                    val computedGainDb = currentTrackLoudnessDb?.let { measuredDb ->
                        // YouTube reports loudnessDb relative to -14 LUFS; compensate toward preset target
                        (-measuredDb.toFloat() + presetTargetDb).coerceIn(-6.0f, 10.0f)
                    } ?: (presetTargetDb + 1.5f).coerceIn(0f, 9.0f)
                    val gainMillibels = (computedGainDb * 100f).toInt().coerceIn(-600, 1000)
                    le.setTargetGain(gainMillibels)
                    le.enabled = gainMillibels != 0
                } else {
                    le.setTargetGain(0)
                    le.enabled = false
                }
            }.onFailure {
                Log.w(TAG, "Failed updating LoudnessEnhancer: ${it.message}")
            }
        }
    }

    @Synchronized
    fun releaseEffects() {
        runCatching { equalizer?.release() }
        runCatching { bassBoost?.release() }
        runCatching { presetReverb?.release() }
        runCatching { loudnessEnhancer?.release() }
        equalizer = null
        bassBoost = null
        presetReverb = null
        loudnessEnhancer = null
        boundSessionId = 0
        _hardwareAttached.value = false
    }
}
