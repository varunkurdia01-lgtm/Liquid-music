package com.example.ui.screens

import android.content.Intent
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
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.GlassOpticalPreset
import com.example.domain.model.GlassQualityTier
import com.example.domain.model.LiquidGlassConfig
import com.example.domain.model.TopStatItem
import com.example.domain.model.UserProfile
import com.example.domain.model.WrappedStats
import com.example.ui.glass.LiquidGlassButton
import com.example.ui.glass.LiquidGlassChip
import com.example.ui.glass.LiquidGlassSlider
import com.example.ui.glass.LiquidGlassSurface
import com.example.ui.glass.LiquidGlassToggle
import com.example.ui.glass.TrackArtwork
import com.example.ui.theme.AmberGlow
import com.example.ui.theme.ArcticCyan
import com.example.ui.theme.CrystalIce
import com.example.ui.theme.DeepIndigoAurora
import com.example.ui.theme.DeepVoid
import com.example.ui.theme.FrostWhite
import com.example.ui.theme.LocalLiquidPalette
import com.example.ui.theme.LuminousRose
import com.example.ui.theme.MistSecondary
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun ProfileScreen(
    userProfile: UserProfile,
    wrappedStats: WrappedStats,
    glassQualityTier: GlassQualityTier,
    glassConfig: LiquidGlassConfig,
    shareText: String,
    bottomPadding: Dp,
    onUpdateDisplayName: (String) -> Unit,
    onSelectGlassQuality: (GlassQualityTier) -> Unit,
    onApplyGlassOpticalPreset: (GlassOpticalPreset) -> Unit,
    onUpdateGlassConfig: ((LiquidGlassConfig) -> LiquidGlassConfig) -> Unit,
    onSignOut: () -> Unit
) {
    val palette = LocalLiquidPalette.current
    val context = LocalContext.current
    val shareChooserTitle = stringResource(R.string.share_wrapped_chooser)
    var showEditNameDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .testTag("profile_screen"),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Profile Header Card
        item(key = "profile_account_card") {
            LiquidGlassSurface(modifier = Modifier.fillMaxWidth(), cornerRadius = 26.dp, useRefractionShell = false) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(62.dp)
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
                            style = MaterialTheme.typography.headlineMedium,
                            color = FrostWhite
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = userProfile.displayName,
                            style = MaterialTheme.typography.titleLarge,
                            color = FrostWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = userProfile.handle + if (userProfile.isGuest) " · Guest" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.secondaryGlow
                        )
                        if (userProfile.email.isNotBlank()) {
                            Text(
                                text = userProfile.email,
                                style = MaterialTheme.typography.bodySmall,
                                color = MistSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    IconButton(
                        onClick = { showEditNameDialog = true },
                        modifier = Modifier.testTag("profile_edit_name_button")
                    ) {
                        Icon(Icons.Rounded.Edit, contentDescription = "Edit display name", tint = FrostWhite)
                    }
                    IconButton(
                        onClick = onSignOut,
                        modifier = Modifier.testTag("profile_sign_out_button")
                    ) {
                        Icon(Icons.Rounded.Logout, contentDescription = "Sign out", tint = LuminousRose)
                    }
                }
            }
        }

        // Dedicated Glass Tuning Studio (AndroidLiquidGlass backdrop 2.0.0 live optics & spring lab)
        item(key = "glass_tuning_studio") {
            GlassTuningStudioCard(
                glassConfig = glassConfig,
                onApplyPreset = onApplyGlassOpticalPreset,
                onUpdateConfig = onUpdateGlassConfig
            )
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
                            modifier = Modifier
                                .height(40.dp)
                                .testTag("share_wrapped_button")
                        ) {
                            Row(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Rounded.Share, contentDescription = null, tint = FrostWhite, modifier = Modifier.size(16.dp))
                                Text("Share", style = MaterialTheme.typography.labelLarge, color = FrostWhite)
                            }
                        }
                    }

                    Text(
                        text = wrappedStats.personalityDescription,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MistSecondary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        WrappedMetricPill(
                            value = "${wrappedStats.totalMinutes}",
                            label = "MINUTES",
                            modifier = Modifier.weight(1f)
                        )
                        WrappedMetricPill(
                            value = "${wrappedStats.totalPlays}",
                            label = "PLAYS",
                            modifier = Modifier.weight(1f)
                        )
                        WrappedMetricPill(
                            value = "${wrappedStats.streakDays}d",
                            label = "STREAK",
                            accent = AmberGlow,
                            showFlame = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Top 5 Songs Card
        item(key = "wrapped_top_songs") {
            WrappedLeaderboardCard(
                title = "Top 5 Tracks",
                emptyHint = "Listen to tracks for at least 15 seconds to populate your live Top 5 Songs.",
                items = wrappedStats.topSongs
            )
        }

        // Top 5 Artists Card
        item(key = "wrapped_top_artists") {
            WrappedLeaderboardCard(
                title = "Top 5 Artists",
                emptyHint = "Your most spun artists will appear here automatically as you explore.",
                items = wrappedStats.topArtists
            )
        }

        // Glass Quality & 120Hz Rendering Performance Selector
        item(key = "glass_quality_selector") {
            LiquidGlassSurface(modifier = Modifier.fillMaxWidth(), cornerRadius = 26.dp, useRefractionShell = false) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.BlurOn, contentDescription = null, tint = palette.secondaryGlow)
                        Text("Liquid Glass Performance Tier", style = MaterialTheme.typography.titleMedium, color = FrostWhite)
                    }
                    GlassQualityTier.entries.forEach { tier ->
                        val selected = glassQualityTier == tier
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(
                                    if (selected) palette.primaryGlow.copy(alpha = 0.18f) else Color(0x400E1324)
                                )
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
}

@Composable
private fun GlassTuningStudioCard(
    glassConfig: LiquidGlassConfig,
    onApplyPreset: (GlassOpticalPreset) -> Unit,
    onUpdateConfig: ((LiquidGlassConfig) -> LiquidGlassConfig) -> Unit
) {
    val palette = LocalLiquidPalette.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val previewBackdrop = rememberLayerBackdrop()
    var stageSize by remember { mutableStateOf(IntSize.Zero) }
    val pillOffsetX = remember { Animatable(0f) }
    val pillOffsetY = remember { Animatable(0f) }
    var isDraggingPreview by remember { mutableStateOf(false) }

    val dragMorph by animateFloatAsState(
        targetValue = if (isDraggingPreview) 1f else 0f,
        animationSpec = spring(
            dampingRatio = glassConfig.springDamping,
            stiffness = glassConfig.springStiffness
        ),
        label = "previewPillMorph"
    )

    LiquidGlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("glass_tuning_studio_card"),
        cornerRadius = 28.dp,
        useRefractionShell = false
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Rounded.Tune, contentDescription = null, tint = palette.secondaryGlow, modifier = Modifier.size(20.dp))
                    Column {
                        Text("Liquid Glass Studio", style = MaterialTheme.typography.titleLarge, color = FrostWhite)
                        Text("AndroidLiquidGlass · Real-time lens & spring physics", style = MaterialTheme.typography.labelSmall, color = palette.secondaryGlow)
                    }
                }
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onApplyPreset(GlassOpticalPreset.BALANCED_IOS26)
                    },
                    modifier = Modifier.testTag("glass_studio_reset_button")
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Reset defaults", tint = MistSecondary)
                }
            }

            // Live Interactive Refractive Preview Stage
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(132.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(22.dp))
                    .onSizeChanged { stageSize = it },
                contentAlignment = Alignment.Center
            ) {
                // Vivid high-contrast geometric scene captured by LayerBackdrop so edge bending & RGB prism pop!
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .layerBackdrop(previewBackdrop)
                        .background(Color(0xFF070A16))
                ) {
                    val stripeCount = 9
                    val stripeW = size.width / stripeCount
                    for (i in 0 until stripeCount) {
                        val stripeColor = when (i % 3) {
                            0 -> palette.deepAurora.copy(alpha = 0.85f)
                            1 -> ArcticCyan.copy(alpha = 0.65f)
                            else -> LuminousRose.copy(alpha = 0.55f)
                        }
                        drawRect(
                            color = stripeColor,
                            topLeft = Offset(i * stripeW, 0f),
                            size = androidx.compose.ui.geometry.Size(stripeW * 0.55f, size.height)
                        )
                    }
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(CrystalIce.copy(alpha = 0.9f), palette.secondaryGlow.copy(alpha = 0.35f), Color.Transparent),
                            center = Offset(size.width * 0.25f, size.height * 0.3f),
                            radius = size.height * 0.7f
                        ),
                        radius = size.height * 0.7f,
                        center = Offset(size.width * 0.25f, size.height * 0.3f)
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(LuminousRose.copy(alpha = 0.85f), DeepIndigoAurora.copy(alpha = 0.3f), Color.Transparent),
                            center = Offset(size.width * 0.78f, size.height * 0.72f),
                            radius = size.height * 0.65f
                        ),
                        radius = size.height * 0.65f,
                        center = Offset(size.width * 0.78f, size.height * 0.72f)
                    )
                }

                // Draggable Live-Refracting Preview Pill
                val stretchX = (abs(pillOffsetX.velocity) / 2800f).coerceIn(0f, 0.16f)
                LiquidGlassSurface(
                    modifier = Modifier
                        .width((154f * glassConfig.pillScale).dp)
                        .height((56f * glassConfig.pillScale).dp)
                        .graphicsLayer {
                            translationX = pillOffsetX.value
                            translationY = pillOffsetY.value
                            scaleX = 1f + 0.08f * dragMorph + stretchX
                            scaleY = 1f + 0.08f * dragMorph - stretchX * 0.4f
                        }
                        .pointerInput(glassConfig.springStiffness, glassConfig.springDamping) {
                            detectDragGestures(
                                onDragStart = {
                                    isDraggingPreview = true
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                },
                                onDragEnd = {
                                    isDraggingPreview = false
                                    scope.launch {
                                        launch {
                                            pillOffsetX.animateTo(
                                                0f,
                                                spring(
                                                    dampingRatio = glassConfig.springDamping,
                                                    stiffness = glassConfig.springStiffness
                                                )
                                            )
                                        }
                                        launch {
                                            pillOffsetY.animateTo(
                                                0f,
                                                spring(
                                                    dampingRatio = glassConfig.springDamping,
                                                    stiffness = glassConfig.springStiffness
                                                )
                                            )
                                        }
                                    }
                                },
                                onDragCancel = {
                                    isDraggingPreview = false
                                    scope.launch {
                                        pillOffsetX.animateTo(0f, spring())
                                        pillOffsetY.animateTo(0f, spring())
                                    }
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val maxX = (stageSize.width * 0.28f).coerceAtLeast(40f)
                                    val maxY = (stageSize.height * 0.22f).coerceAtLeast(20f)
                                    scope.launch {
                                        pillOffsetX.snapTo((pillOffsetX.value + dragAmount.x).coerceIn(-maxX, maxX))
                                        pillOffsetY.snapTo((pillOffsetY.value + dragAmount.y).coerceIn(-maxY, maxY))
                                    }
                                }
                            )
                        }
                        .testTag("glass_studio_preview_pill"),
                    cornerRadius = 30.dp,
                    tintColor = Color(0xFF0A0E1B),
                    surfaceAlpha = 0.24f,
                    useRefractionShell = true,
                    backdropOverride = previewBackdrop,
                    refractionBoost = 1f + 0.25f * dragMorph
                ) {
                    Text(
                        text = if (isDraggingPreview) "Refracting…" else "Drag Liquid Pill",
                        style = MaterialTheme.typography.labelLarge,
                        color = FrostWhite,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }

            // One-Tap Optical Presets
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val presets = listOf(
                    GlassOpticalPreset.BALANCED_IOS26,
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

            // Pill Size
            GlassSliderRow(
                label = "Pill Size",
                readout = "${"%.2f".format(glassConfig.pillScale)}x",
                value = glassConfig.pillScale,
                range = 0.85f..1.25f,
                tag = "slider_pill_size",
                onChange = { v -> onUpdateConfig { it.copy(pillScale = v) } }
            )

            // Spring Stiffness
            GlassSliderRow(
                label = "Spring Stiffness",
                readout = "${glassConfig.springStiffness.roundToInt()}",
                value = glassConfig.springStiffness,
                range = 180f..750f,
                tag = "slider_spring_stiffness",
                onChange = { v -> onUpdateConfig { it.copy(springStiffness = v) } }
            )

            // Spring Damping
            GlassSliderRow(
                label = "Spring Damping (Bounce)",
                readout = "${"%.2f".format(glassConfig.springDamping)}",
                value = glassConfig.springDamping,
                range = 0.42f..0.95f,
                tag = "slider_spring_damping",
                onChange = { v -> onUpdateConfig { it.copy(springDamping = v) } }
            )

            // Edge Refraction Height
            GlassSliderRow(
                label = "Edge Refraction Height",
                readout = "${glassConfig.refractionHeightDp.roundToInt()} dp",
                value = glassConfig.refractionHeightDp,
                range = 4f..36f,
                tag = "slider_refraction_height",
                onChange = { v -> onUpdateConfig { it.copy(refractionHeightDp = v) } }
            )

            // Edge Refraction Amount
            GlassSliderRow(
                label = "Refraction Distortion",
                readout = "${glassConfig.refractionAmountDp.roundToInt()} dp",
                value = glassConfig.refractionAmountDp,
                range = 8f..64f,
                tag = "slider_refraction_amount",
                onChange = { v -> onUpdateConfig { it.copy(refractionAmountDp = v) } }
            )

            // Blur Radius
            GlassSliderRow(
                label = "Backdrop Blur",
                readout = "${glassConfig.blurRadiusDp.roundToInt()} dp",
                value = glassConfig.blurRadiusDp,
                range = 0f..32f,
                tag = "slider_blur_radius",
                onChange = { v -> onUpdateConfig { it.copy(blurRadiusDp = v) } }
            )

            // Chromatic Dispersion Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("RGB Chromatic Dispersion", style = MaterialTheme.typography.titleSmall, color = FrostWhite)
                    Text("Prism edge color splitting on curved lenses", style = MaterialTheme.typography.bodySmall, color = MistSecondary)
                }
                LiquidGlassToggle(
                    checked = glassConfig.chromaticAberration,
                    onCheckedChange = { enabled -> onUpdateConfig { it.copy(chromaticAberration = enabled) } },
                    modifier = Modifier.testTag("toggle_chromatic_dispersion")
                )
            }
        }
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

    LiquidGlassSurface(modifier = Modifier.fillMaxWidth(), cornerRadius = 26.dp, useRefractionShell = false) {
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
                                text = "${stat.playCount}×",
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
                                    .fillMaxHeight()
                                    .fillMaxWidth(animatedFraction)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(palette.deepAurora, palette.secondaryGlow, palette.primaryGlow)
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
