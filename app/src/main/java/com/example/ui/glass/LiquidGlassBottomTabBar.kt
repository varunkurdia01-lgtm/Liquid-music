package com.example.ui.glass

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Equalizer
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.GlassQualityTier
import com.example.domain.model.MainTab
import com.example.ui.theme.FrostWhite
import com.example.ui.theme.LocalGlassQuality
import com.example.ui.theme.LocalLiquidGlassConfig
import com.example.ui.theme.LocalLiquidPalette
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private val PrimaryDockTabs = listOf(
    MainTab.HOME,
    MainTab.LIBRARY,
    MainTab.SOUND_LAB,
    MainTab.PROFILE
)

/**
 * High-Refraction Apple iOS 26 Split Liquid Glass Dock powered by `io.github.kyant0:backdrop`.
 *
 * Split Layout:
 * - **Left Floating Capsule (`weight(1f)`)**: 4 primary tabs (Home, Library, Sound Lab, Profile)
 *   rendered inside Smoked Obsidian Crystal glass with sculpted dual-edge Fresnel rims.
 * - **Right Detached Circular Orb**: Dedicated floating glass Search button (`MainTab.SEARCH`)
 *   with high-stiffness spring compression and album-art dynamic accent glow.
 *
 * On-Demand Two-Layer Optical Architecture (Zero Idle Offscreen Capture):
 * - **Layer 1 (Smoked Obsidian Dock + Resting Capsule + Icons/Labels)**:
 *   Captured into `tabsBackdrop` strictly while dragging, pressing, or gliding between tabs.
 * - **Layer 2 (Unclipped Convex Crystal Droplet Lens — DRAWN ON TOP OF LAYER 1!)**:
 *   Appears exclusively on drag, press, or tap transition; swells proud of the dock bounds with
 *   zero internal blur, boosted curved-edge refraction, chromatic prism dispersion, bottom inner
 *   glass thickness bevel, and bright double-arc Fresnel specular highlights.
 */
@Composable
fun LiquidGlassBottomTabBar(
    selectedTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val primaryTabs = PrimaryDockTabs
    val tabCount = primaryTabs.size
    val isSearchSelected = selectedTab == MainTab.SEARCH
    val selectedPrimaryIndex = primaryTabs.indexOf(selectedTab)

    val palette = LocalLiquidPalette.current
    val activeAccent = remember(palette.primaryGlow) {
        AppleGlassTokens.ensureVibrantOnDarkGlass(palette.primaryGlow)
    }
    val glassConfig = LocalLiquidGlassConfig.current
    val glassTier = LocalGlassQuality.current
    val sceneBackdrop = LocalLiquidBackdrop.current
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val tabsBackdrop = rememberLayerBackdrop()
    val combinedPillBackdrop = if (sceneBackdrop != null) {
        rememberCombinedBackdrop(sceneBackdrop, tabsBackdrop)
    } else {
        tabsBackdrop
    }

    var barWidthPx by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var isTapGliding by remember { mutableStateOf(false) }
    var tapGlideJob by remember { mutableStateOf<Job?>(null) }
    var dragVelocityX by remember { mutableFloatStateOf(0f) }
    var lastHapticIndex by remember { mutableIntStateOf(selectedPrimaryIndex.coerceAtLeast(0)) }
    var wasOnSearch by remember { mutableStateOf(isSearchSelected) }

    val currentPrimaryIndex by rememberUpdatedState(selectedPrimaryIndex)
    val currentIsSearchSelected by rememberUpdatedState(isSearchSelected)
    val currentOnTabSelected by rememberUpdatedState(onTabSelected)

    fun triggerTapGlide(durationMs: Long = 380L) {
        tapGlideJob?.cancel()
        isTapGliding = true
        tapGlideJob = scope.launch {
            delay(durationMs)
            isTapGliding = false
        }
    }

    val lensPosition = remember { Animatable(selectedPrimaryIndex.coerceAtLeast(0).toFloat()) }

    LaunchedEffect(selectedTab, glassConfig.springStiffness, glassConfig.springDamping) {
        val prevWasSearch = wasOnSearch
        wasOnSearch = isSearchSelected
        if (selectedPrimaryIndex >= 0 && !isDragging) {
            val target = selectedPrimaryIndex.toFloat()
            if (prevWasSearch) {
                lensPosition.snapTo(target)
                triggerTapGlide(durationMs = 260L)
            } else {
                if (abs(lensPosition.value - target) > 0.05f) {
                    triggerTapGlide(durationMs = 380L)
                }
                lensPosition.animateTo(
                    targetValue = target,
                    animationSpec = spring(
                        dampingRatio = glassConfig.springDamping,
                        stiffness = glassConfig.springStiffness
                    )
                )
            }
            lastHapticIndex = selectedPrimaryIndex
        }
    }

    val isLensActive = isDragging || isTapGliding
    val dropletMorph by animateFloatAsState(
        targetValue = if (isLensActive) 1f else 0f,
        animationSpec = spring(
            dampingRatio = (glassConfig.springDamping + 0.04f).coerceIn(0.60f, 0.84f),
            stiffness = (glassConfig.springStiffness * 1.18f).coerceAtLeast(520f)
        ),
        label = "dropletMorph"
    )

    val restingPresence by animateFloatAsState(
        targetValue = if (isSearchSelected) 0f else 1f,
        animationSpec = spring(
            dampingRatio = 0.82f,
            stiffness = 620f
        ),
        label = "restingPresence"
    )

    val stretchFactor by animateFloatAsState(
        targetValue = if (isDragging) {
            (abs(dragVelocityX) / 2000f).coerceIn(0f, 1f)
        } else {
            (abs(lensPosition.velocity) / 13f).coerceIn(0f, 0.78f)
        },
        animationSpec = spring(
            dampingRatio = 0.70f,
            stiffness = 600f
        ),
        label = "liquidStretch"
    )

    val pillScale = glassConfig.pillScale
    val dockHeightDp = (64f + 4f * (pillScale - 1f)).coerceIn(60f, 70f).dp
    val horizontalDockPadding = 4.dp

    val useRealDockBackdrop = sceneBackdrop != null &&
        glassTier != GlassQualityTier.BATTERY_SAVER &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val useRealLensDroplet = glassTier != GlassQualityTier.BATTERY_SAVER &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    // Outer unclipped host: vertical padding leaves clear headroom for the crystal droplet to swell proud!
    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // =========================================================================
        // LEFT SPLIT DOCK: 4-Tab Smoked Obsidian Crystal Capsule + On-Demand Droplet
        // =========================================================================
        Box(
            modifier = Modifier
                .weight(1f)
                .height(dockHeightDp)
                .testTag("liquid_bottom_tab_bar")
                .onSizeChanged { barWidthPx = it.width.toFloat() }
                .pointerInput(tabCount, glassConfig.springStiffness, glassConfig.springDamping) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (barWidthPx <= 0f) return@awaitEachGesture

                        val padPx = with(density) { horizontalDockPadding.toPx() }
                        val innerWidth = (barWidthPx - padPx * 2f).coerceAtLeast(1f)
                        val tabWidth = innerWidth / tabCount.toFloat()
                        val velocityTracker = VelocityTracker()
                        velocityTracker.addPosition(down.uptimeMillis, down.position)

                        var movedSignificantly = false
                        val startX = down.position.x
                        val downTabIdx = ((startX - padPx) / tabWidth).toInt().coerceIn(0, tabCount - 1)
                        tapGlideJob?.cancel()
                        isTapGliding = false

                        // Swell the crystal droplet immediately when pressing the active primary tab
                        if (!currentIsSearchSelected && downTabIdx == currentPrimaryIndex) {
                            isDragging = true
                        }

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break

                            if (!change.pressed) {
                                val velX = velocityTracker.calculateVelocity().x
                                dragVelocityX = 0f
                                isDragging = false

                                val adjustedX = (change.position.x - padPx).coerceIn(0f, innerWidth)
                                val targetIdx = if (!movedSignificantly) {
                                    (adjustedX / tabWidth).toInt().coerceIn(0, tabCount - 1)
                                } else {
                                    val projected = lensPosition.value + (velX / (tabWidth * 18f)).coerceIn(-0.46f, 0.46f)
                                    projected.roundToInt().coerceIn(0, tabCount - 1)
                                }

                                val chosenTab = primaryTabs[targetIdx]
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)

                                if (!movedSignificantly && currentIsSearchSelected) {
                                    scope.launch { lensPosition.snapTo(targetIdx.toFloat()) }
                                    triggerTapGlide(durationMs = 260L)
                                    currentOnTabSelected(chosenTab)
                                } else {
                                    if (abs(lensPosition.value - targetIdx.toFloat()) > 0.1f) {
                                        triggerTapGlide(durationMs = 380L)
                                    }
                                    currentOnTabSelected(chosenTab)
                                    scope.launch {
                                        lensPosition.animateTo(
                                            targetValue = targetIdx.toFloat(),
                                            initialVelocity = (velX / tabWidth).coerceIn(-14f, 14f),
                                            animationSpec = spring(
                                                dampingRatio = glassConfig.springDamping,
                                                stiffness = glassConfig.springStiffness
                                            )
                                        )
                                    }
                                }
                                break
                            }

                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                            val dxFromStart = abs(change.position.x - startX)
                            if (dxFromStart > 8f) {
                                movedSignificantly = true
                                isDragging = true
                                change.consume()
                            }

                            if (movedSignificantly) {
                                val adjustedX = (change.position.x - padPx).coerceIn(0f, innerWidth)
                                val rawIndex = ((adjustedX / tabWidth) - 0.5f)
                                    .coerceIn(0f, (tabCount - 1).toFloat())
                                dragVelocityX = velocityTracker.calculateVelocity().x

                                scope.launch {
                                    lensPosition.snapTo(rawIndex)
                                }

                                val nearest = rawIndex.roundToInt().coerceIn(0, tabCount - 1)
                                if (nearest != lastHapticIndex) {
                                    lastHapticIndex = nearest
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }
                        }
                    }
                },
            contentAlignment = Alignment.CenterStart
        ) {
            val padPx = with(density) { horizontalDockPadding.toPx() }
            val innerWidthPx = (barWidthPx - padPx * 2f).coerceAtLeast(0f)
            val tabWidthPx = if (tabCount > 0) innerWidthPx / tabCount.toFloat() else 0f

            // =========================================================================
            // LAYER 1: Smoked Obsidian Frosted 4-Tab Dock + Resting Pill + Icons/Labels
            // Offscreen `tabsBackdrop` capture stays OFF at rest for 120fps scrolling!
            // =========================================================================
            val captureTabsForDroplet = useRealLensDroplet && (isLensActive || dropletMorph > 0.01f)
            val dockModifier = Modifier
                .fillMaxSize()
                .then(if (captureTabsForDroplet) Modifier.layerBackdrop(tabsBackdrop) else Modifier)
                .then(
                    if (useRealDockBackdrop && sceneBackdrop != null) {
                        val enableDockLens = glassTier == GlassQualityTier.ULTRA_AGSL &&
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                        Modifier.drawBackdrop(
                            backdrop = sceneBackdrop,
                            shape = { CircleShape },
                            effects = {
                                vibrancy()
                                // Crisp low-radius blur keeps underlying album colors & cards vivid like the demo
                                val dockBlur = (glassConfig.blurRadiusDp * 0.62f).coerceIn(3.5f, 10f)
                                blur(dockBlur.dp.toPx())
                                if (enableDockLens) {
                                    lens(
                                        refractionHeight = (glassConfig.refractionHeightDp * 1.05f).dp.toPx(),
                                        refractionAmount = (glassConfig.refractionAmountDp * 1.05f).dp.toPx(),
                                        depthEffect = true,
                                        chromaticAberration = false
                                    )
                                }
                            },
                            highlight = { Highlight.Default },
                            shadow = { Shadow.Default },
                            onDrawSurface = {
                                drawRect(AppleGlassTokens.SmokedCrystalBase.copy(alpha = 0.29f))
                                // Convex top glass sheen + subtle bottom internal thickness shadow
                                drawRect(
                                    brush = Brush.verticalGradient(
                                        colorStops = arrayOf(
                                            0.0f to Color.White.copy(alpha = 0.14f),
                                            0.28f to Color.White.copy(alpha = 0.02f),
                                            0.74f to Color.Transparent,
                                            1.0f to AppleGlassTokens.SmokedCrystalInnerShadow.copy(alpha = 0.30f)
                                        )
                                    )
                                )
                                // Crisp dual-edge Fresnel glass rim
                                val radiusPx = size.height / 2f
                                drawRoundRect(
                                    brush = Brush.verticalGradient(
                                        colorStops = arrayOf(
                                            0.0f to Color.White.copy(alpha = 0.46f),
                                            0.38f to Color.White.copy(alpha = 0.08f),
                                            0.72f to Color.White.copy(alpha = 0.08f),
                                            1.0f to Color.White.copy(alpha = 0.28f)
                                        )
                                    ),
                                    cornerRadius = CornerRadius(radiusPx, radiusPx),
                                    style = Stroke(width = 0.9.dp.toPx())
                                )
                            }
                        )
                    } else {
                        Modifier
                            .clip(CircleShape)
                            .background(
                                Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        0.0f to AppleGlassTokens.SmokedCrystalBase.copy(alpha = 0.86f),
                                        0.75f to AppleGlassTokens.SmokedCrystalBase.copy(alpha = 0.82f),
                                        1.0f to AppleGlassTokens.SmokedCrystalInnerShadow.copy(alpha = 0.92f)
                                    )
                                )
                            )
                            .border(
                                width = 0.9.dp,
                                brush = Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        0.0f to Color.White.copy(alpha = 0.44f),
                                        0.40f to Color.White.copy(alpha = 0.08f),
                                        0.74f to Color.White.copy(alpha = 0.10f),
                                        1.0f to Color.White.copy(alpha = 0.26f)
                                    )
                                ),
                                shape = CircleShape
                            )
                    }
                )

            Box(
                modifier = dockModifier.padding(horizontal = horizontalDockPadding),
                contentAlignment = Alignment.CenterStart
            ) {
                // Sleek frosted resting capsule behind the active primary tab
                // Fades smoothly out whenever the Search orb is active OR while the crystal droplet is morphed
                if (tabWidthPx > 0f) {
                    val restingAlpha = ((1f - dropletMorph) * restingPresence).coerceIn(0f, 1f)
                    if (restingAlpha > 0.01f) {
                        val restingWidthDp = with(density) { tabWidthPx.toDp() }
                        val restingHeightDp = (dockHeightDp - 8.dp).coerceAtLeast(48.dp)
                        Box(
                            modifier = Modifier
                                .width(restingWidthDp)
                                .height(restingHeightDp)
                                .graphicsLayer {
                                    translationX = lensPosition.value * tabWidthPx
                                    alpha = restingAlpha
                                }
                                .clip(CircleShape)
                                .background(
                                    Brush.verticalGradient(
                                        colorStops = arrayOf(
                                            0.0f to Color.White.copy(alpha = 0.17f),
                                            0.55f to activeAccent.copy(alpha = 0.10f),
                                            1.0f to Color.White.copy(alpha = 0.06f)
                                        )
                                    )
                                )
                                .border(
                                    width = 0.75.dp,
                                    brush = Brush.verticalGradient(
                                        colorStops = arrayOf(
                                            0.0f to Color.White.copy(alpha = 0.44f),
                                            0.45f to activeAccent.copy(alpha = 0.18f),
                                            1.0f to Color.White.copy(alpha = 0.24f)
                                        )
                                    ),
                                    shape = CircleShape
                                )
                        )
                    }
                }

                // 4 Crisp Primary Tab Icons & Labels
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    primaryTabs.forEachIndexed { index, tab ->
                        val distance = abs(lensPosition.value - index.toFloat()).coerceIn(0f, 1f)
                        val proximity = 1f - distance
                        val isSelected = selectedTab == tab
                        val isHighlighted = if (isSearchSelected && !isLensActive) {
                            false
                        } else {
                            proximity > 0.45f
                        }
                        val iconVector = tabIcon(tab, filled = isHighlighted || isSelected)

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .testTag("tab_${tab.name.lowercase()}"),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = iconVector,
                                contentDescription = tab.label,
                                tint = if (isHighlighted) {
                                    activeAccent
                                } else {
                                    FrostWhite.copy(alpha = 0.90f)
                                },
                                modifier = Modifier.size(23.dp)
                            )
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = if (isHighlighted) FontWeight.SemiBold else FontWeight.Medium
                                ),
                                color = if (isHighlighted) {
                                    activeAccent
                                } else {
                                    FrostWhite.copy(alpha = 0.84f)
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }

            // =========================================================================
            // LAYER 2 (DRAWN ON TOP OF LAYER 1, UNCLIPPED — ON-DEMAND ONLY!):
            // High-Refraction Convex Crystal Droplet Lens matching the reference demo!
            // Zero internal blur, strong curved-edge magnification, chromatic prism edge,
            // physical bottom inner glass thickness shadow & bright double-arc Fresnel rim!
            // =========================================================================
            if (tabWidthPx > 0f && dropletMorph > 0.01f) {
                val dropletWidthPx = (tabWidthPx * (1.06f * pillScale))
                    .coerceIn(tabWidthPx * 0.92f, tabWidthPx * 1.24f)
                val dropletWidthDp = with(density) { dropletWidthPx.toDp() }
                val dropletHeightDp = (dockHeightDp - 2.dp).coerceAtLeast(56.dp)
                val dropletStartOffsetPx = padPx + (tabWidthPx - dropletWidthPx) * 0.5f

                if (useRealLensDroplet) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .width(dropletWidthDp)
                            .height(dropletHeightDp)
                            .drawBackdrop(
                                backdrop = combinedPillBackdrop,
                                shape = { CircleShape },
                                layerBlock = {
                                    translationX = dropletStartOffsetPx + lensPosition.value * tabWidthPx
                                    // Swells vertically & horizontally proud of the dock bounds like the demo!
                                    val sx = 1f + 0.14f * dropletMorph + 0.16f * stretchFactor
                                    val sy = 1f + 0.24f * dropletMorph - 0.08f * stretchFactor
                                    scaleX = sx.coerceIn(0.96f, 1.36f)
                                    scaleY = sy.coerceIn(0.96f, 1.32f)
                                    alpha = (dropletMorph * 1.18f).coerceIn(0f, 1f)
                                },
                                effects = {
                                    vibrancy()
                                    // Zero blur inside the crystal droplet: razor-sharp optical magnification!
                                    lens(
                                        refractionHeight = (glassConfig.refractionHeightDp * (1.28f + 0.56f * dropletMorph)).dp.toPx(),
                                        refractionAmount = (glassConfig.refractionAmountDp * (1.48f + 0.76f * dropletMorph)).dp.toPx(),
                                        depthEffect = true,
                                        chromaticAberration = glassConfig.chromaticAberration || glassTier == GlassQualityTier.ULTRA_AGSL
                                    )
                                },
                                highlight = { Highlight.Default },
                                shadow = { Shadow.Default },
                                onDrawSurface = {
                                    val m = dropletMorph.coerceIn(0f, 1f)
                                    val radiusPx = size.height / 2f
                                    // Sculpted 3D molded glass interior: bright top crown, crystal clear center,
                                    // and dark internal lower bevel thickness shadow matching the demo droplet!
                                    drawRect(
                                        brush = Brush.verticalGradient(
                                            colorStops = arrayOf(
                                                0.0f to Color.White.copy(alpha = 0.18f * m),
                                                0.24f to Color.White.copy(alpha = 0.03f * m),
                                                0.55f to activeAccent.copy(alpha = 0.07f * m),
                                                0.78f to Color.Transparent,
                                                1.0f to AppleGlassTokens.SmokedCrystalInnerShadow.copy(alpha = 0.36f * m)
                                            )
                                        )
                                    )
                                    // Razor-crisp bright double-arc Fresnel specular rim
                                    drawRoundRect(
                                        brush = Brush.verticalGradient(
                                            colorStops = arrayOf(
                                                0.0f to Color.White.copy(alpha = 0.90f * m),
                                                0.30f to Color.White.copy(alpha = 0.18f * m),
                                                0.68f to Color.White.copy(alpha = 0.14f * m),
                                                1.0f to Color.White.copy(alpha = 0.66f * m)
                                            )
                                        ),
                                        cornerRadius = CornerRadius(radiusPx, radiusPx),
                                        style = Stroke(width = 1.35.dp.toPx())
                                    )
                                }
                            )
                    )
                } else {
                    // Clean translucent crystal droplet fallback on API < 33 / Battery Saver
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .width(dropletWidthDp)
                            .height(dropletHeightDp)
                            .graphicsLayer {
                                translationX = dropletStartOffsetPx + lensPosition.value * tabWidthPx
                                scaleX = 1f + 0.14f * dropletMorph + 0.12f * stretchFactor
                                scaleY = 1f + 0.20f * dropletMorph - 0.06f * stretchFactor
                                alpha = dropletMorph
                            }
                            .clip(CircleShape)
                            .background(
                                Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        0.0f to Color.White.copy(alpha = 0.20f),
                                        0.50f to activeAccent.copy(alpha = 0.10f),
                                        1.0f to AppleGlassTokens.SmokedCrystalInnerShadow.copy(alpha = 0.34f)
                                    )
                                )
                            )
                            .border(
                                width = 1.3.dp,
                                brush = Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        0.0f to Color.White.copy(alpha = 0.88f),
                                        0.36f to Color.White.copy(alpha = 0.18f),
                                        0.72f to Color.White.copy(alpha = 0.14f),
                                        1.0f to Color.White.copy(alpha = 0.64f)
                                    )
                                ),
                                shape = CircleShape
                            )
                    )
                }
            }
        }

        // =========================================================================
        // RIGHT DETACHED FLOATING CIRCULAR GLASS ORB: Search Tab (`MainTab.SEARCH`)
        // =========================================================================
        DetachedSearchOrb(
            sizeDp = dockHeightDp,
            isSelected = isSearchSelected,
            activeAccent = activeAccent,
            sceneBackdrop = sceneBackdrop,
            useRealDockBackdrop = useRealDockBackdrop,
            glassTier = glassTier,
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                currentOnTabSelected(MainTab.SEARCH)
            }
        )
    }
}

@Composable
private fun DetachedSearchOrb(
    sizeDp: Dp,
    isSelected: Boolean,
    activeAccent: Color,
    sceneBackdrop: Backdrop?,
    useRealDockBackdrop: Boolean,
    glassTier: GlassQualityTier,
    onClick: () -> Unit
) {
    val glassConfig = LocalLiquidGlassConfig.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val orbScale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.89f
            isSelected -> 1.03f
            else -> 1f
        },
        animationSpec = spring(
            dampingRatio = 0.56f,
            stiffness = 700f
        ),
        label = "searchOrbScale"
    )

    val activeProgress by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.78f,
            stiffness = 620f
        ),
        label = "searchOrbActive"
    )

    val orbModifier = Modifier
        .size(sizeDp)
        .testTag("tab_search")
        .graphicsLayer {
            scaleX = orbScale
            scaleY = orbScale
        }
        .then(
            if (useRealDockBackdrop && sceneBackdrop != null) {
                val enableOrbLens = glassTier == GlassQualityTier.ULTRA_AGSL &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                Modifier.drawBackdrop(
                    backdrop = sceneBackdrop,
                    shape = { CircleShape },
                    effects = {
                        vibrancy()
                        val orbBlur = if (isPressed) {
                            2.5f
                        } else {
                            (glassConfig.blurRadiusDp * 0.62f).coerceIn(3.5f, 10f)
                        }
                        blur(orbBlur.dp.toPx())
                        if (enableOrbLens) {
                            val boost = if (isPressed) 1.48f else if (isSelected) 1.24f else 1.10f
                            lens(
                                refractionHeight = (glassConfig.refractionHeightDp * boost).dp.toPx(),
                                refractionAmount = (glassConfig.refractionAmountDp * boost).dp.toPx(),
                                depthEffect = true,
                                chromaticAberration = isPressed || isSelected
                            )
                        }
                    },
                    highlight = { Highlight.Default },
                    shadow = { Shadow.Default },
                    onDrawSurface = {
                        val rPx = size.minDimension / 2f
                        drawRect(AppleGlassTokens.SmokedCrystalBase.copy(alpha = if (isSelected) 0.25f else 0.30f))
                        if (activeProgress > 0.01f) {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        activeAccent.copy(alpha = 0.28f * activeProgress),
                                        activeAccent.copy(alpha = 0.08f * activeProgress),
                                        Color.Transparent
                                    ),
                                    center = Offset(size.width * 0.5f, size.height * 0.5f),
                                    radius = rPx * 0.95f
                                )
                            )
                        }
                        drawRect(
                            brush = Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.0f to Color.White.copy(alpha = if (isPressed) 0.22f else 0.15f),
                                    0.30f to Color.White.copy(alpha = 0.02f),
                                    0.72f to Color.Transparent,
                                    1.0f to AppleGlassTokens.SmokedCrystalInnerShadow.copy(alpha = 0.32f)
                                )
                            )
                        )
                        val topRim = if (isSelected) activeAccent.copy(alpha = 0.72f) else Color.White.copy(alpha = 0.48f)
                        val bottomRim = if (isSelected) activeAccent.copy(alpha = 0.48f) else Color.White.copy(alpha = 0.30f)
                        drawCircle(
                            brush = Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.0f to topRim,
                                    0.40f to Color.White.copy(alpha = 0.10f),
                                    0.72f to Color.White.copy(alpha = 0.10f),
                                    1.0f to bottomRim
                                )
                            ),
                            radius = rPx - 0.5.dp.toPx(),
                            style = Stroke(width = if (isSelected || isPressed) 1.2.dp.toPx() else 0.95.dp.toPx())
                        )
                    }
                )
            } else {
                Modifier
                    .clip(CircleShape)
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to AppleGlassTokens.SmokedCrystalBase.copy(alpha = 0.88f),
                                0.70f to if (isSelected) activeAccent.copy(alpha = 0.22f) else AppleGlassTokens.SmokedCrystalBase.copy(alpha = 0.84f),
                                1.0f to AppleGlassTokens.SmokedCrystalInnerShadow.copy(alpha = 0.92f)
                            )
                        )
                    )
                    .border(
                        width = if (isSelected) 1.2.dp else 0.95.dp,
                        brush = Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to if (isSelected) activeAccent.copy(alpha = 0.75f) else Color.White.copy(alpha = 0.46f),
                                0.45f to Color.White.copy(alpha = 0.10f),
                                1.0f to if (isSelected) activeAccent.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.28f)
                            )
                        ),
                        shape = CircleShape
                    )
            }
        )
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )

    Box(
        modifier = orbModifier,
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isSelected) Icons.Rounded.Search else Icons.Outlined.Search,
            contentDescription = MainTab.SEARCH.label,
            tint = if (isSelected) activeAccent else FrostWhite.copy(alpha = 0.92f),
            modifier = Modifier.size(if (isSelected) 25.dp else 24.dp)
        )
    }
}

private fun tabIcon(tab: MainTab, filled: Boolean): ImageVector = when (tab) {
    MainTab.HOME -> if (filled) Icons.Rounded.Home else Icons.Outlined.Home
    MainTab.SEARCH -> if (filled) Icons.Rounded.Search else Icons.Outlined.Search
    MainTab.LIBRARY -> if (filled) Icons.Rounded.LibraryMusic else Icons.Outlined.LibraryMusic
    MainTab.SOUND_LAB -> if (filled) Icons.Rounded.Equalizer else Icons.Outlined.Equalizer
    MainTab.PROFILE -> if (filled) Icons.Rounded.Person else Icons.Outlined.Person
}
