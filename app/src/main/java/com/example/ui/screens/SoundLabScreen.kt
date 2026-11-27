package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SurroundSound
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.domain.model.AudioQuality
import com.example.domain.model.EqPreset
import com.example.domain.model.MagicSoundPreset
import com.example.domain.model.ReverbPreset
import com.example.domain.model.SleepTimerOption
import com.example.domain.model.SoundLabSettings
import com.example.ui.glass.LiquidGlassChip
import com.example.ui.glass.LiquidGlassSlider
import com.example.ui.glass.LiquidGlassSurface
import com.example.ui.glass.LiquidGlassToggle
import com.example.ui.theme.EmeraldPulse
import com.example.ui.theme.FrostWhite
import com.example.ui.theme.LocalLiquidPalette
import com.example.ui.theme.MistSecondary
import kotlin.math.roundToInt

@Composable
fun SoundLabScreen(
    settings: SoundLabSettings,
    bandLabels: List<String>,
    hardwareAttached: Boolean,
    sleepTimerOption: SleepTimerOption,
    sleepTimerRemainingSec: Int,
    bottomPadding: Dp,
    onToggleSkipSilence: (Boolean) -> Unit,
    onToggleVolumeNormalization: (Boolean) -> Unit,
    onToggleGaplessPlayback: (Boolean) -> Unit,
    onToggleAutoPlayRadio: (Boolean) -> Unit,
    onApplyMagicPreset: (MagicSoundPreset) -> Unit,
    onToggleEightD: (Boolean) -> Unit,
    onSelectEqPreset: (EqPreset) -> Unit,
    onUpdateBandLevel: (Int, Float) -> Unit,
    onUpdateBassBoost: (Int) -> Unit,
    onSelectReverb: (ReverbPreset) -> Unit,
    onUpdateSpeed: (Float) -> Unit,
    onUpdatePitch: (Float) -> Unit,
    onUpdateCrossfade: (Int) -> Unit,
    onSelectSleepTimer: (SleepTimerOption) -> Unit,
    onUpdateAudioQuality: (AudioQuality) -> Unit
) {
    val palette = LocalLiquidPalette.current
    val haptic = LocalHapticFeedback.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .testTag("sound_lab_screen"),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item(key = "soundlab_header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Sound Lab", style = MaterialTheme.typography.headlineLarge, color = FrostWhite)
                    Text("Pro Echo Audio Engine & Hardware DSP", style = MaterialTheme.typography.bodySmall, color = MistSecondary)
                }
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (hardwareAttached) EmeraldPulse.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.08f))
                        .border(1.dp, if (hardwareAttached) EmeraldPulse.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.18f), CircleShape)
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = if (hardwareAttached) "DSP ACTIVE" else "DSP READY",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (hardwareAttached) EmeraldPulse else palette.secondaryGlow
                    )
                }
            }
        }

        // Pro Audio Engine Card: Skip Silence, Volume Normalization, Gapless, Auto-Play Radio
        item(key = "pro_audio_engine") {
            LiquidGlassSurface(modifier = Modifier.fillMaxWidth(), cornerRadius = 26.dp, useRefractionShell = false) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    LabSectionTitle(icon = Icons.Rounded.Memory, title = "Pro Audio Engine")

                    EngineToggleRow(
                        title = "Skip Silence",
                        subtitle = "Automatically trim quiet pauses during tracks",
                        checked = settings.skipSilenceEnabled,
                        tag = "toggle_skip_silence",
                        onCheckedChange = onToggleSkipSilence
                    )

                    EngineToggleRow(
                        title = "Audio Volume Normalization",
                        subtitle = "Consistent target loudness via hardware LoudnessEnhancer",
                        checked = settings.volumeNormalizationEnabled,
                        tag = "toggle_volume_normalization",
                        onCheckedChange = onToggleVolumeNormalization
                    )

                    EngineToggleRow(
                        title = "Gapless Playback",
                        subtitle = "Seamless zero-gap transitions between album tracks",
                        checked = settings.gaplessPlaybackEnabled,
                        tag = "toggle_gapless_playback",
                        onCheckedChange = onToggleGaplessPlayback
                    )

                    EngineToggleRow(
                        title = "Auto-Play Endless Radio",
                        subtitle = "Queue related songs automatically before playback stops",
                        checked = settings.autoPlayRadioEnabled,
                        tag = "toggle_autoplay_radio",
                        onCheckedChange = onToggleAutoPlayRadio
                    )
                }
            }
        }

        // One-Tap Magic Sound Cards + LiquidToggle for 8D Spatial Aura
        item(key = "magic_presets") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LabSectionTitle(icon = Icons.Rounded.AutoAwesome, title = "Magic Audio Modes")
                MagicSoundPreset.entries.chunked(2).forEach { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        pair.forEach { preset ->
                            val selected = settings.activeMagicPreset == preset
                            LiquidGlassSurface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onApplyMagicPreset(preset)
                                    }
                                    .testTag("magic_preset_${preset.name.lowercase()}"),
                                cornerRadius = 20.dp,
                                surfaceAlpha = if (selected) 0.78f else 0.48f,
                                highlightAccent = if (selected) palette.secondaryGlow else null,
                                useRefractionShell = false
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = preset.label,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (selected) palette.primaryGlow else FrostWhite
                                    )
                                    Text(
                                        text = preset.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MistSecondary
                                    )
                                }
                            }
                        }
                    }
                }

                LiquidGlassSurface(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp, useRefractionShell = false) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("8D Spatial Aura Modulation", style = MaterialTheme.typography.titleSmall, color = FrostWhite)
                            Text("Fluid stereo breathing wave + plate hall", style = MaterialTheme.typography.bodySmall, color = MistSecondary)
                        }
                        LiquidGlassToggle(
                            checked = settings.eightDEnabled,
                            onCheckedChange = onToggleEightD,
                            modifier = Modifier.testTag("soundlab_8d_toggle")
                        )
                    }
                }
            }
        }

        // 5-Band Vertical Glass Equalizer + 7 Presets
        item(key = "equalizer_card") {
            LiquidGlassSurface(modifier = Modifier.fillMaxWidth(), cornerRadius = 26.dp, useRefractionShell = false) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    LabSectionTitle(icon = Icons.Rounded.Equalizer, title = "5-Band Graphic Equalizer")

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(EqPreset.entries, key = { it.name }) { preset ->
                            LiquidGlassChip(
                                label = preset.label,
                                selected = settings.eqPreset == preset,
                                onClick = { onSelectEqPreset(preset) },
                                modifier = Modifier.testTag("eq_preset_${preset.name.lowercase()}")
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(178.dp)
                            .padding(top = 6.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        settings.eqBandLevelsDb.forEachIndexed { idx, levelDb ->
                            VerticalGlassEqBand(
                                label = bandLabels.getOrElse(idx) { "B${idx + 1}" },
                                valueDb = levelDb,
                                onValueChange = { newVal -> onUpdateBandLevel(idx, newVal) }
                            )
                        }
                    }
                }
            }
        }

        // Bass Boost Tactile Dial + LiquidSlider + Spatial Hall Reverb
        item(key = "bass_and_reverb") {
            LiquidGlassSurface(modifier = Modifier.fillMaxWidth(), cornerRadius = 26.dp, useRefractionShell = false) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    LabSectionTitle(icon = Icons.Rounded.SurroundSound, title = "Bass Boost & Spatial Hall")

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(92.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            val progress = settings.bassBoostStrength / 1000f
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val stroke = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                                val inset = 6.dp.toPx()
                                val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                                drawArc(
                                    color = Color.White.copy(alpha = 0.12f),
                                    startAngle = 135f,
                                    sweepAngle = 270f,
                                    useCenter = false,
                                    topLeft = Offset(inset, inset),
                                    size = arcSize,
                                    style = stroke
                                )
                                drawArc(
                                    brush = Brush.sweepGradient(
                                        listOf(palette.deepAurora, palette.secondaryGlow, palette.primaryGlow)
                                    ),
                                    startAngle = 135f,
                                    sweepAngle = 270f * progress,
                                    useCenter = false,
                                    topLeft = Offset(inset, inset),
                                    size = arcSize,
                                    style = stroke
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "${(settings.bassBoostStrength / 10)}%",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = FrostWhite
                                )
                                Text("BASS", style = MaterialTheme.typography.labelSmall, color = palette.secondaryGlow)
                            }
                        }

                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Sub-Bass Strength (${settings.bassBoostStrength})",
                                style = MaterialTheme.typography.titleSmall,
                                color = FrostWhite
                            )
                            LiquidGlassSlider(
                                value = settings.bassBoostStrength.toFloat(),
                                onValueChange = { onUpdateBassBoost(it.roundToInt()) },
                                valueRange = 0f..1000f,
                                modifier = Modifier.testTag("bass_boost_slider")
                            )
                        }
                    }

                    Text("Spatial Hall Reverb", style = MaterialTheme.typography.titleSmall, color = MistSecondary)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(ReverbPreset.entries, key = { it.name }) { reverb ->
                            LiquidGlassChip(
                                label = reverb.label,
                                selected = settings.reverbPreset == reverb,
                                onClick = { onSelectReverb(reverb) },
                                modifier = Modifier.testTag("reverb_${reverb.name.lowercase()}")
                            )
                        }
                    }
                }
            }
        }

        // Independent Speed & Pitch LiquidSliders
        item(key = "speed_pitch_card") {
            LiquidGlassSurface(modifier = Modifier.fillMaxWidth(), cornerRadius = 26.dp, useRefractionShell = false) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LabSectionTitle(icon = Icons.Rounded.Speed, title = "Tempo & Pitch Console")

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Playback Speed", style = MaterialTheme.typography.bodyMedium, color = MistSecondary)
                        Text("${"%.2f".format(settings.playbackSpeed)}x", style = MaterialTheme.typography.labelMedium, color = palette.primaryGlow)
                    }
                    LiquidGlassSlider(
                        value = settings.playbackSpeed,
                        onValueChange = onUpdateSpeed,
                        valueRange = 0.5f..2.0f,
                        modifier = Modifier.testTag("speed_slider")
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Pitch Shift", style = MaterialTheme.typography.bodyMedium, color = MistSecondary)
                        Text("${"%.2f".format(settings.playbackPitch)}x", style = MaterialTheme.typography.labelMedium, color = palette.primaryGlow)
                    }
                    LiquidGlassSlider(
                        value = settings.playbackPitch,
                        onValueChange = onUpdatePitch,
                        valueRange = 0.5f..2.0f,
                        modifier = Modifier.testTag("pitch_slider")
                    )
                }
            }
        }

        // Crossfade, Sleep Timer & Streaming Quality
        item(key = "playback_utilities") {
            LiquidGlassSurface(modifier = Modifier.fillMaxWidth(), cornerRadius = 26.dp, useRefractionShell = false) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    LabSectionTitle(icon = Icons.Rounded.GraphicEq, title = "Smooth Fade Transitions")
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Fade Duration", style = MaterialTheme.typography.bodyMedium, color = MistSecondary)
                        Text(
                            text = if (settings.crossfadeSeconds == 0) "Off" else "${settings.crossfadeSeconds}s",
                            style = MaterialTheme.typography.labelMedium,
                            color = palette.primaryGlow
                        )
                    }
                    LiquidGlassSlider(
                        value = settings.crossfadeSeconds.toFloat(),
                        onValueChange = { onUpdateCrossfade(it.roundToInt()) },
                        valueRange = 0f..12f,
                        modifier = Modifier.testTag("crossfade_slider")
                    )

                    LabSectionTitle(
                        icon = Icons.Rounded.Bedtime,
                        title = if (sleepTimerRemainingSec > 0) {
                            "Sleep Timer (${sleepTimerRemainingSec / 60}m ${sleepTimerRemainingSec % 60}s left)"
                        } else if (sleepTimerOption == SleepTimerOption.END_OF_TRACK) {
                            "Sleep Timer (End of Track)"
                        } else "Sleep Timer"
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(SleepTimerOption.entries, key = { it.name }) { opt ->
                            LiquidGlassChip(
                                label = opt.label,
                                selected = sleepTimerOption == opt,
                                onClick = { onSelectSleepTimer(opt) },
                                modifier = Modifier.testTag("sleep_timer_${opt.name.lowercase()}")
                            )
                        }
                    }

                    LabSectionTitle(icon = Icons.Rounded.HighQuality, title = "Streaming & Download Quality")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AudioQuality.entries.forEach { quality ->
                            LiquidGlassChip(
                                label = quality.label,
                                selected = settings.audioQuality == quality,
                                onClick = { onUpdateAudioQuality(quality) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("audio_quality_${quality.kbps}")
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EngineToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    tag: String,
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
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(tag)
        )
    }
}

@Composable
private fun LabSectionTitle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String
) {
    val palette = LocalLiquidPalette.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, contentDescription = null, tint = palette.secondaryGlow, modifier = Modifier.size(19.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = FrostWhite)
    }
}

@Composable
private fun VerticalGlassEqBand(
    label: String,
    valueDb: Float,
    onValueChange: (Float) -> Unit
) {
    val palette = LocalLiquidPalette.current
    val latestOnValueChange by rememberUpdatedState(onValueChange)
    val normalized = ((valueDb + 12f) / 24f).coerceIn(0f, 1f)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.width(56.dp)
    ) {
        Text(
            text = "${if (valueDb >= 0) "+" else ""}${valueDb.roundToInt()}",
            style = MaterialTheme.typography.labelSmall,
            color = palette.primaryGlow,
            textAlign = TextAlign.Center
        )

        Box(
            modifier = Modifier
                .width(48.dp)
                .height(126.dp)
                .pointerInput(label) {
                    detectTapGestures { offset ->
                        val fraction = (1f - (offset.y / size.height.toFloat())).coerceIn(0f, 1f)
                        latestOnValueChange(fraction * 24f - 12f)
                    }
                }
                .pointerInput(label) {
                    detectVerticalDragGestures { change, _ ->
                        change.consume()
                        val fraction = (1f - (change.position.y / size.height.toFloat())).coerceIn(0f, 1f)
                        latestOnValueChange(fraction * 24f - 12f)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(34.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(17.dp))
                    .background(Color(0x550C1224))
                    .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(17.dp)),
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(normalized.coerceAtLeast(0.06f))
                        .clip(RoundedCornerShape(17.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(palette.primaryGlow, palette.secondaryGlow, palette.deepAurora)
                            )
                        )
                )
            }
        }

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MistSecondary,
            textAlign = TextAlign.Center
        )
    }
}
