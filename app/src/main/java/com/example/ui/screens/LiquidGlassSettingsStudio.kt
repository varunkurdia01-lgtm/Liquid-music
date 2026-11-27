package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.GlassAccentMode
import com.example.domain.model.GlassHighlightStyle
import com.example.domain.model.GlassOpticalPreset
import com.example.domain.model.LiquidGlassConfig
import com.example.domain.model.MainTab
import com.example.ui.components.EchoSurfaceCard
import com.example.ui.glass.LiquidGlassBottomTabBar
import com.example.ui.glass.LiquidGlassChip
import com.example.ui.glass.LiquidGlassSlider
import com.example.ui.glass.LiquidGlassToggle
import com.example.ui.theme.ArcticCyan
import com.example.ui.theme.CrystalIce
import com.example.ui.theme.DeepIndigoAurora
import com.example.ui.theme.EmeraldPulse
import com.example.ui.theme.FrostWhite
import com.example.ui.theme.LocalLiquidPalette
import com.example.ui.theme.LuminousRose
import com.example.ui.theme.MistSecondary
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlin.math.roundToInt

private val StudioSections = listOf("Idle", "Held Pill", "Optics", "Layout")
private val StudioPreviewTabs = listOf(MainTab.HOME, MainTab.LIBRARY, MainTab.PROFILE)

/**
 * Full real-time Liquid Glass Customization Studio for Settings.
 * Embeds a live interactive mini-dock running the exact same Kyant0/AndroidLiquidGlass
 * engine with a "Lock Held Preview" switch so users can tune both idle and held crystal
 * droplet parameters and see changes live.
 */
@Composable
fun GlassTuningStudioCard(
    glassConfig: LiquidGlassConfig,
    onApplyPreset: (GlassOpticalPreset) -> Unit,
    onUpdateConfig: ((LiquidGlassConfig) -> LiquidGlassConfig) -> Unit
) {
    val palette = LocalLiquidPalette.current
    val haptic = LocalHapticFeedback.current

    val previewBackdrop = rememberLayerBackdrop()
    var previewSelectedTab by remember { mutableStateOf(MainTab.HOME) }
    var lockHeldPreview by remember { mutableStateOf(false) }
    var activeSectionIndex by remember { mutableIntStateOf(1) }

    EchoSurfaceCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("glass_tuning_studio_card"),
        cornerRadius = 24.dp
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header + Reset Defaults
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Rounded.Tune,
                        contentDescription = null,
                        tint = palette.secondaryGlow,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text("Liquid Glass Studio", style = MaterialTheme.typography.titleLarge, color = FrostWhite)
                        Text(
                            "Full Idle, Held Droplet, Optics & Spring Controls",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.secondaryGlow
                        )
                    }
                }
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onApplyPreset(GlassOpticalPreset.BALANCED_IOS26)
                    },
                    modifier = Modifier.testTag("glass_studio_reset_button")
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Reset liquid glass defaults", tint = MistSecondary)
                }
            }

            // Live Real-Engine Interactive Preview Stage (Colorful Dark Cards + Real Tab Bar)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(148.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(22.dp))
                    .testTag("glass_studio_preview_stage"),
                contentAlignment = Alignment.BottomCenter
            ) {
                // High-contrast dark card & glowing dots backdrop mimicking the iOS 26 demo scene
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .layerBackdrop(previewBackdrop)
                        .background(Color(0xFF0A0C12))
                ) {
                    val cardGap = 10.dp.toPx()
                    val cardW = (size.width - cardGap * 4f) / 3f
                    val cardY = 12.dp.toPx()
                    val cardH = size.height - 24.dp.toPx()
                    val corner = CornerRadius(16.dp.toPx(), 16.dp.toPx())

                    for (i in 0 until 3) {
                        val left = cardGap + i * (cardW + cardGap)
                        drawRoundRect(
                            color = Color(0xFF1A1D26),
                            topLeft = Offset(left, cardY),
                            size = Size(cardW, cardH),
                            cornerRadius = corner
                        )
                        // Vibrant glowing indicator dots right behind the glass pill so refraction is crystal visible!
                        val dotColor = when (i) {
                            0 -> EmeraldPulse
                            1 -> ArcticCyan
                            else -> Color(0xFFFFB300)
                        }
                        val dotCenter = Offset(left + cardW * 0.52f, size.height * 0.62f)
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(dotColor.copy(alpha = 0.85f), dotColor.copy(alpha = 0.25f), Color.Transparent),
                                center = dotCenter,
                                radius = cardW * 0.44f
                            ),
                            radius = cardW * 0.44f,
                            center = dotCenter
                        )
                        drawCircle(
                            color = dotColor,
                            radius = 7.dp.toPx(),
                            center = dotCenter
                        )
                        // Accent header bar inside each mock card
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.14f),
                            topLeft = Offset(left + 12.dp.toPx(), cardY + 14.dp.toPx()),
                            size = Size(cardW * 0.55f, 6.dp.toPx()),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                        )
                        drawRoundRect(
                            color = when (i) {
                                0 -> LuminousRose.copy(alpha = 0.72f)
                                1 -> CrystalIce.copy(alpha = 0.75f)
                                else -> DeepIndigoAurora.copy(alpha = 0.85f)
                            },
                            topLeft = Offset(left + 12.dp.toPx(), cardY + 30.dp.toPx()),
                            size = Size(cardW * 0.36f, 16.dp.toPx()),
                            cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                        )
                    }
                }

                // Live real LiquidGlassBottomTabBar preview!
                LiquidGlassBottomTabBar(
                    selectedTab = previewSelectedTab,
                    onTabSelected = { previewSelectedTab = it },
                    backdropOverride = previewBackdrop,
                    configOverride = glassConfig,
                    applyNavigationBarsPadding = false,
                    forceHoldPreview = lockHeldPreview,
                    previewTabsOverride = StudioPreviewTabs,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 6.dp)
                        .testTag("glass_studio_preview_pill")
                )
            }

            // Lock Held Preview toggle strip so user can tune held droplet with one hand
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (lockHeldPreview) "Showing Held Droplet Live" else "Hold & drag preview bar above",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (lockHeldPreview) palette.primaryGlow else MistSecondary,
                    modifier = Modifier.weight(1f)
                )
                LiquidGlassChip(
                    label = if (lockHeldPreview) "Held Locked" else "Lock Held",
                    selected = lockHeldPreview,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        lockHeldPreview = !lockHeldPreview
                    },
                    modifier = Modifier.testTag("glass_studio_lock_held_preview")
                )
            }

            // 1-Tap Optical Presets
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val presets = listOf(
                    GlassOpticalPreset.BALANCED_IOS26,
                    GlassOpticalPreset.DEMO_ALWAYS_CRYSTAL,
                    GlassOpticalPreset.ULTRA_PRISM,
                    GlassOpticalPreset.SOFT_FROSTED
                )
                items(presets, key = { it.name }) { preset ->
                    LiquidGlassChip(
                        label = preset.label,
                        selected = glassConfig.opticalPreset == preset,
                        onClick = { onApplyPreset(preset) },
                        modifier = Modifier.testTag("optical_preset_${preset.name.lowercase()}")
                    )
                }
            }

            // Segmented Studio Category Switcher (Idle | Held Pill | Optics | Layout)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.06f))
                    .border(0.75.dp, Color.White.copy(alpha = 0.10f), CircleShape)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                StudioSections.forEachIndexed { idx, title ->
                    val selected = activeSectionIndex == idx
                    LiquidGlassChip(
                        label = title,
                        selected = selected,
                        onClick = {
                            activeSectionIndex = idx
                            if (idx == 1 && !lockHeldPreview) {
                                lockHeldPreview = true
                            } else if (idx == 0 && lockHeldPreview) {
                                lockHeldPreview = false
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("glass_studio_tab_${title.lowercase().replace(" ", "_")}")
                    )
                }
            }

            // Category Controls
            when (activeSectionIndex) {
                0 -> IdleGlassSettingsSection(
                    glassConfig = glassConfig,
                    onUpdateConfig = onUpdateConfig
                )
                1 -> HeldGlassSettingsSection(
                    glassConfig = glassConfig,
                    onUpdateConfig = onUpdateConfig
                )
                2 -> OpticsColorSettingsSection(
                    glassConfig = glassConfig,
                    onUpdateConfig = onUpdateConfig
                )
                else -> LayoutPhysicsSettingsSection(
                    glassConfig = glassConfig,
                    onUpdateConfig = onUpdateConfig
                )
            }
        }
    }
}

@Composable
private fun IdleGlassSettingsSection(
    glassConfig: LiquidGlassConfig,
    onUpdateConfig: ((LiquidGlassConfig) -> LiquidGlassConfig) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StudioToggleRow(
            title = "Always-On Crystal Lens at Rest",
            subtitle = "Keep the convex magnifying glass visible even when idle",
            checked = glassConfig.alwaysShowLensAtRest,
            tag = "toggle_always_lens_at_rest"
        ) { enabled -> onUpdateConfig { it.copy(alwaysShowLensAtRest = enabled) } }

        StudioSliderRow(
            label = "Idle Backdrop Blur",
            readout = "${glassConfig.blurRadiusDp.roundToInt()} dp",
            value = glassConfig.blurRadiusDp,
            range = 0f..28f,
            tag = "slider_blur_radius"
        ) { v -> onUpdateConfig { it.copy(blurRadiusDp = v) } }

        StudioSliderRow(
            label = "Idle Smoked Glass Tint",
            readout = "${(glassConfig.idleTintAlpha * 100f).roundToInt()}%",
            value = glassConfig.idleTintAlpha,
            range = 0f..0.65f,
            tag = "slider_idle_tint_alpha"
        ) { v -> onUpdateConfig { it.copy(idleTintAlpha = v) } }

        StudioSliderRow(
            label = "Idle Edge Refraction Height",
            readout = "${glassConfig.refractionHeightDp.roundToInt()} dp",
            value = glassConfig.refractionHeightDp,
            range = 0f..24f,
            tag = "slider_refraction_height"
        ) { v -> onUpdateConfig { it.copy(refractionHeightDp = v) } }

        StudioSliderRow(
            label = "Idle Refraction Distortion",
            readout = "${glassConfig.refractionAmountDp.roundToInt()} dp",
            value = glassConfig.refractionAmountDp,
            range = 0f..44f,
            tag = "slider_refraction_amount"
        ) { v -> onUpdateConfig { it.copy(refractionAmountDp = v) } }

        StudioSliderRow(
            label = "Resting Active Capsule Opacity",
            readout = "${(glassConfig.restingPillAlpha * 100f).roundToInt()}%",
            value = glassConfig.restingPillAlpha,
            range = 0f..0.45f,
            tag = "slider_resting_pill_alpha"
        ) { v -> onUpdateConfig { it.copy(restingPillAlpha = v) } }

        StudioSliderRow(
            label = "Idle Specular Rim Strength",
            readout = "${(glassConfig.rimHighlightAlpha * 100f).roundToInt()}%",
            value = glassConfig.rimHighlightAlpha,
            range = 0f..1f,
            tag = "slider_rim_highlight_alpha"
        ) { v -> onUpdateConfig { it.copy(rimHighlightAlpha = v) } }
    }
}

@Composable
private fun HeldGlassSettingsSection(
    glassConfig: LiquidGlassConfig,
    onUpdateConfig: ((LiquidGlassConfig) -> LiquidGlassConfig) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StudioSliderRow(
            label = "Held Lens Refraction Height",
            readout = "${glassConfig.heldRefractionHeightDp.roundToInt()} dp",
            value = glassConfig.heldRefractionHeightDp,
            range = 2f..24f,
            tag = "slider_held_refraction_height"
        ) { v -> onUpdateConfig { it.copy(heldRefractionHeightDp = v) } }

        StudioSliderRow(
            label = "Held Lens Distortion",
            readout = "${glassConfig.heldRefractionAmountDp.roundToInt()} dp",
            value = glassConfig.heldRefractionAmountDp,
            range = 4f..48f,
            tag = "slider_held_refraction_amount"
        ) { v -> onUpdateConfig { it.copy(heldRefractionAmountDp = v) } }

        StudioSliderRow(
            label = "Held Droplet Blur (0 = Crystal)",
            readout = "${glassConfig.heldBlurRadiusDp.roundToInt()} dp",
            value = glassConfig.heldBlurRadiusDp,
            range = 0f..16f,
            tag = "slider_held_blur_radius"
        ) { v -> onUpdateConfig { it.copy(heldBlurRadiusDp = v) } }

        StudioSliderRow(
            label = "Held Surface Tint (0% = Clear)",
            readout = "${(glassConfig.heldTintAlpha * 100f).roundToInt()}%",
            value = glassConfig.heldTintAlpha,
            range = 0f..0.35f,
            tag = "slider_held_tint_alpha"
        ) { v -> onUpdateConfig { it.copy(heldTintAlpha = v) } }

        StudioSliderRow(
            label = "Held Width Swell",
            readout = "${"%.2f".format(glassConfig.heldScaleX)}x",
            value = glassConfig.heldScaleX,
            range = 1.0f..1.32f,
            tag = "slider_held_scale_x"
        ) { v -> onUpdateConfig { it.copy(heldScaleX = v) } }

        StudioSliderRow(
            label = "Held Height Swell",
            readout = "${"%.2f".format(glassConfig.heldScaleY)}x",
            value = glassConfig.heldScaleY,
            range = 1.0f..1.38f,
            tag = "slider_held_scale_y"
        ) { v -> onUpdateConfig { it.copy(heldScaleY = v) } }

        StudioSliderRow(
            label = "Velocity Jelly Stretch",
            readout = "${(glassConfig.velocityStretchStrength * 100f).roundToInt()}%",
            value = glassConfig.velocityStretchStrength,
            range = 0f..0.30f,
            tag = "slider_velocity_stretch"
        ) { v -> onUpdateConfig { it.copy(velocityStretchStrength = v) } }

        StudioSliderRow(
            label = "Held Fresnel Rim Sheen",
            readout = "${(glassConfig.heldRimAlpha * 100f).roundToInt()}%",
            value = glassConfig.heldRimAlpha,
            range = 0f..1f,
            tag = "slider_held_rim_alpha"
        ) { v -> onUpdateConfig { it.copy(heldRimAlpha = v) } }

        StudioSliderRow(
            label = "Tap Glide Showcase Duration",
            readout = "${glassConfig.tapGlideDurationMs} ms",
            value = glassConfig.tapGlideDurationMs.toFloat(),
            range = 120f..650f,
            tag = "slider_tap_glide_ms"
        ) { v -> onUpdateConfig { it.copy(tapGlideDurationMs = v.roundToInt()) } }
    }
}

@Composable
private fun OpticsColorSettingsSection(
    glassConfig: LiquidGlassConfig,
    onUpdateConfig: ((LiquidGlassConfig) -> LiquidGlassConfig) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Active Tab Accent Color", style = MaterialTheme.typography.titleSmall, color = FrostWhite)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(GlassAccentMode.entries, key = { it.name }) { mode ->
                    LiquidGlassChip(
                        label = mode.label,
                        selected = glassConfig.accentMode == mode,
                        onClick = { onUpdateConfig { it.copy(accentMode = mode) } },
                        modifier = Modifier.testTag("accent_mode_${mode.name.lowercase()}")
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Specular Highlight Style", style = MaterialTheme.typography.titleSmall, color = FrostWhite)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(GlassHighlightStyle.entries, key = { it.name }) { style ->
                    LiquidGlassChip(
                        label = style.label,
                        selected = glassConfig.highlightStyle == style,
                        onClick = { onUpdateConfig { it.copy(highlightStyle = style) } },
                        modifier = Modifier.testTag("highlight_style_${style.name.lowercase()}")
                    )
                }
            }
        }

        StudioToggleRow(
            title = "RGB Chromatic Dispersion",
            subtitle = "Subtle prism color splitting along curved glass edges",
            checked = glassConfig.chromaticAberration,
            tag = "toggle_chromatic_dispersion"
        ) { enabled -> onUpdateConfig { it.copy(chromaticAberration = enabled) } }

        StudioToggleRow(
            title = "3D Curved Depth Normals",
            subtitle = "Bend edge refraction normals like real thick molded glass",
            checked = glassConfig.depthEffectEnabled,
            tag = "toggle_depth_effect"
        ) { enabled -> onUpdateConfig { it.copy(depthEffectEnabled = enabled) } }

        StudioToggleRow(
            title = "Backdrop Vibrancy Boost",
            subtitle = "Keep album artwork & icons luminous through dark glass",
            checked = glassConfig.vibrancyEnabled,
            tag = "toggle_vibrancy"
        ) { enabled -> onUpdateConfig { it.copy(vibrancyEnabled = enabled) } }

        StudioSliderRow(
            label = "Backdrop Color Saturation",
            readout = "${"%.2f".format(glassConfig.backdropSaturation)}x",
            value = glassConfig.backdropSaturation,
            range = 0.60f..1.80f,
            tag = "slider_backdrop_saturation"
        ) { v -> onUpdateConfig { it.copy(backdropSaturation = v) } }

        StudioSliderRow(
            label = "Backdrop Brightness",
            readout = "${if (glassConfig.backdropBrightness >= 0f) "+" else ""}${(glassConfig.backdropBrightness * 100f).roundToInt()}%",
            value = glassConfig.backdropBrightness,
            range = -0.25f..0.25f,
            tag = "slider_backdrop_brightness"
        ) { v -> onUpdateConfig { it.copy(backdropBrightness = v) } }
    }
}

@Composable
private fun LayoutPhysicsSettingsSection(
    glassConfig: LiquidGlassConfig,
    onUpdateConfig: ((LiquidGlassConfig) -> LiquidGlassConfig) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StudioToggleRow(
            title = "Split Dock + Detached Search Orb",
            subtitle = "4-tab glass pill on left with floating circular Search orb on right",
            checked = glassConfig.detachedSearchOrb,
            tag = "toggle_detached_search_orb"
        ) { enabled -> onUpdateConfig { it.copy(detachedSearchOrb = enabled) } }

        StudioSliderRow(
            label = "Dock & Pill Size",
            readout = "${"%.2f".format(glassConfig.pillScale)}x",
            value = glassConfig.pillScale,
            range = 0.85f..1.25f,
            tag = "slider_pill_size"
        ) { v -> onUpdateConfig { it.copy(pillScale = v) } }

        StudioSliderRow(
            label = "Mini-Player Refraction Boost",
            readout = "${"%.2f".format(glassConfig.miniPlayerRefractionBoost)}x",
            value = glassConfig.miniPlayerRefractionBoost,
            range = 0.50f..1.80f,
            tag = "slider_mini_player_refraction"
        ) { v -> onUpdateConfig { it.copy(miniPlayerRefractionBoost = v) } }

        StudioSliderRow(
            label = "Spring Stiffness (Snap Speed)",
            readout = "${glassConfig.springStiffness.roundToInt()}",
            value = glassConfig.springStiffness,
            range = 180f..850f,
            tag = "slider_spring_stiffness"
        ) { v -> onUpdateConfig { it.copy(springStiffness = v) } }

        StudioSliderRow(
            label = "Spring Damping (Bounce)",
            readout = "${"%.2f".format(glassConfig.springDamping)}",
            value = glassConfig.springDamping,
            range = 0.40f..0.95f,
            tag = "slider_spring_damping"
        ) { v -> onUpdateConfig { it.copy(springDamping = v) } }
    }
}

@Composable
private fun StudioSliderRow(
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
            Text(
                readout,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = palette.primaryGlow
            )
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
private fun StudioToggleRow(
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
