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
import com.example.domain.model.LiquidGlassConfig
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
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private val SplitPrimaryTabs = listOf(
    MainTab.HOME,
    MainTab.LIBRARY,
    MainTab.SOUND_LAB,
    MainTab.PROFILE
)

private val UnifiedAllTabs = MainTab.entries.toList()

/**
 * Authentic Kyant0/AndroidLiquidGlass (`LiquidBottomTabs`) + Apple iOS 26 Split Dock.
 *
 * Clean Three-Layer Optical Separation (prevents double-blurred muddiness & press flicker):
 * - **Layer 1A (Dock Shell)**: Draws blurred smoked glass from `sceneBackdrop`. NOT captured into `tabsBackdrop`.
 * - **Layer 1B (Transparent Tab Content Layer)**: Always stably attached to `Modifier.layerBackdrop(tabsBackdrop)`
 *   with a 100% transparent background containing the resting highlight pill and crisp tab icons/labels.
 * - **Layer 2 (Unclipped Crystal Droplet Lens — ON TOP OF LAYER 1B!)**:
 *   Samples `rememberCombinedBackdrop(sceneBackdrop, tabsBackdrop)` — compositing the SHARP live
 *   background scene directly behind the SHARP transparent tab icons — then applies pure GPU
 *   `vibrancy()` / `colorControls()` + non-tearing curved `lens()` + native Fresnel `Highlight`
 *   with crystal-clear `onDrawSurface` (no muddy painted dark slabs or thick double strokes!).
 */
@Composable
fun LiquidGlassBottomTabBar(
    selectedTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
    backdropOverride: Backdrop? = null,
    configOverride: LiquidGlassConfig? = null,
    applyNavigationBarsPadding: Boolean = true,
    forceHoldPreview: Boolean = false,
    previewTabsOverride: List<MainTab>? = null
) {
    val glassConfig = configOverride ?: LocalLiquidGlassConfig.current
    val showDetachedSearch = previewTabsOverride == null && glassConfig.detachedSearchOrb
    val primaryTabs = previewTabsOverride ?: if (showDetachedSearch) SplitPrimaryTabs else UnifiedAllTabs
    val tabCount = primaryTabs.size
    val isSearchDetachedActive = showDetachedSearch && selectedTab == MainTab.SEARCH
    val selectedPrimaryIndex = primaryTabs.indexOf(selectedTab)

    val palette = LocalLiquidPalette.current
    val activeAccent = remember(palette.primaryGlow, glassConfig.accentMode) {
        AppleGlassTokens.resolveAccent(palette.primaryGlow, glassConfig.accentMode)
    }
    val glassTier = LocalGlassQuality.current
    val sceneBackdrop = backdropOverride ?: LocalLiquidBackdrop.current
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
    var isHolding by remember { mutableStateOf(false) }
    var isTapGliding by remember { mutableStateOf(false) }
    var tapGlideJob by remember { mutableStateOf<Job?>(null) }
    var holdActivateJob by remember { mutableStateOf<Job?>(null) }
    var dragVelocityX by remember { mutableFloatStateOf(0f) }
    var lastHapticIndex by remember { mutableIntStateOf(selectedPrimaryIndex.coerceAtLeast(0)) }
    var wasOnDetachedSearch by remember { mutableStateOf(isSearchDetachedActive) }

    val currentPrimaryIndex by rememberUpdatedState(selectedPrimaryIndex)
    val currentIsDetachedSearch by rememberUpdatedState(isSearchDetachedActive)
    val currentOnTabSelected by rememberUpdatedState(onTabSelected)
    val currentPrimaryTabs by rememberUpdatedState(primaryTabs)
    val currentConfig by rememberUpdatedState(glassConfig)

    fun triggerTapGlide(durationMs: Long = currentConfig.tapGlideDurationMs.toLong()) {
        tapGlideJob?.cancel()
        isTapGliding = true
        tapGlideJob = scope.launch {
            delay(durationMs.coerceIn(100L, 700L))
            isTapGliding = false
        }
    }

    val lensPosition = remember { Animatable(selectedPrimaryIndex.coerceAtLeast(0).toFloat()) }

    // Keep lens index in bounds if user toggles 4-tab split vs 5-tab unified dock live in Settings
    LaunchedEffect(tabCount) {
        val clamped = lensPosition.value.coerceIn(0f, (tabCount - 1).coerceAtLeast(0).toFloat())
        if (abs(lensPosition.value - clamped) > 0.01f) {
            lensPosition.snapTo(clamped)
        }
    }

    LaunchedEffect(selectedTab, tabCount, glassConfig.springStiffness, glassConfig.springDamping) {
        val prevWasSearch = wasOnDetachedSearch
        wasOnDetachedSearch = isSearchDetachedActive
        if (selectedPrimaryIndex >= 0 && !isHolding) {
            val target = selectedPrimaryIndex.toFloat()
            if (prevWasSearch) {
                lensPosition.snapTo(target)
                triggerTapGlide(durationMs = (glassConfig.tapGlideDurationMs * 0.75f).roundToInt().toLong())
            } else {
                if (abs(lensPosition.value - target) > 0.05f) {
                    triggerTapGlide()
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

    val isInteracting = isHolding || isTapGliding || forceHoldPreview
    val isLensVisibleTarget = isInteracting || (glassConfig.alwaysShowLensAtRest && !isSearchDetachedActive)

    val dropletVisibility by animateFloatAsState(
        targetValue = if (isLensVisibleTarget) 1f else 0f,
        animationSpec = spring(
            dampingRatio = (glassConfig.springDamping + 0.06f).coerceIn(0.62f, 0.88f),
            stiffness = (glassConfig.springStiffness * 1.15f).coerceAtLeast(480f)
        ),
        label = "dropletVisibility"
    )

    val holdMorph by animateFloatAsState(
        targetValue = if (isInteracting) 1f else 0f,
        animationSpec = spring(
            dampingRatio = glassConfig.springDamping.coerceIn(0.45f, 0.90f),
            stiffness = (glassConfig.springStiffness * 1.12f).coerceAtLeast(420f)
        ),
        label = "holdMorph"
    )

    val restingPresence by animateFloatAsState(
        targetValue = if (isSearchDetachedActive) 0f else 1f,
        animationSpec = spring(
            dampingRatio = 0.82f,
            stiffness = 620f
        ),
        label = "restingPresence"
    )

    val stretchFactor by animateFloatAsState(
        targetValue = if (isHolding) {
            (abs(dragVelocityX) / 2000f).coerceIn(0f, 1f)
        } else {
            (abs(lensPosition.velocity) / 14f).coerceIn(0f, 0.72f)
        },
        animationSpec = spring(
            dampingRatio = 0.68f,
            stiffness = 580f
        ),
        label = "liquidStretch"
    )

    val pillScale = glassConfig.pillScale.coerceIn(0.85f, 1.25f)
    val dockHeightDp = (64f + 6f * (pillScale - 1f)).coerceIn(58f, 72f).dp
    val horizontalDockPadding = 4.dp

    val useRealDockBackdrop = sceneBackdrop != null &&
        glassTier != GlassQualityTier.BATTERY_SAVER &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val useRealLensDroplet = glassTier != GlassQualityTier.BATTERY_SAVER &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    val outerModifier = modifier
        .fillMaxWidth()
        .then(if (applyNavigationBarsPadding) Modifier.navigationBarsPadding() else Modifier)
        .padding(
            horizontal = if (previewTabsOverride != null) 8.dp else 16.dp,
            vertical = if (previewTabsOverride != null) 8.dp else 10.dp
        )

    Row(
        modifier = outerModifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // =========================================================================
        // MAIN FLOATING GLASS DOCK (4-Tab Split Pill OR 5-Tab Unified Pill)
        // =========================================================================
        Box(
            modifier = Modifier
                .weight(1f)
                .height(dockHeightDp)
                .then(if (previewTabsOverride == null) Modifier.testTag("liquid_bottom_tab_bar") else Modifier)
                .onSizeChanged { barWidthPx = it.width.toFloat() }
                .pointerInput(tabCount) {
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
                        holdActivateJob?.cancel()
                        isTapGliding = false

                        // Immediate droplet bloom when pressing the active tab;
                        // smooth 60ms hold bloom when pressing and holding any other tab!
                        if (!currentIsDetachedSearch && downTabIdx == currentPrimaryIndex) {
                            isHolding = true
                        } else {
                            holdActivateJob = scope.launch {
                                delay(60L)
                                isHolding = true
                                lensPosition.animateTo(
                                    targetValue = downTabIdx.toFloat(),
                                    animationSpec = spring(
                                        dampingRatio = currentConfig.springDamping,
                                        stiffness = currentConfig.springStiffness
                                    )
                                )
                            }
                        }

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break

                            if (!change.pressed) {
                                holdActivateJob?.cancel()
                                val velX = velocityTracker.calculateVelocity().x
                                dragVelocityX = 0f
                                isHolding = false

                                val adjustedX = (change.position.x - padPx).coerceIn(0f, innerWidth)
                                val targetIdx = if (!movedSignificantly) {
                                    (adjustedX / tabWidth).toInt().coerceIn(0, tabCount - 1)
                                } else {
                                    val projected = lensPosition.value + (velX / (tabWidth * 18f)).coerceIn(-0.46f, 0.46f)
                                    projected.roundToInt().coerceIn(0, tabCount - 1)
                                }

                                val chosenTab = currentPrimaryTabs[targetIdx]
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)

                                if (!movedSignificantly && currentIsDetachedSearch) {
                                    scope.launch { lensPosition.snapTo(targetIdx.toFloat()) }
                                    triggerTapGlide(durationMs = (currentConfig.tapGlideDurationMs * 0.75f).roundToInt().toLong())
                                    currentOnTabSelected(chosenTab)
                                } else {
                                    if (abs(lensPosition.value - targetIdx.toFloat()) > 0.08f) {
                                        triggerTapGlide()
                                    }
                                    currentOnTabSelected(chosenTab)
                                    scope.launch {
                                        lensPosition.animateTo(
                                            targetValue = targetIdx.toFloat(),
                                            initialVelocity = (velX / tabWidth).coerceIn(-14f, 14f),
                                            animationSpec = spring(
                                                dampingRatio = currentConfig.springDamping,
                                                stiffness = currentConfig.springStiffness
                                            )
                                        )
                                    }
                                }
                                break
                            }

                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                            val dxFromStart = abs(change.position.x - startX)
                            if (dxFromStart > 6f) {
                                holdActivateJob?.cancel()
                                movedSignificantly = true
                                isHolding = true
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
            // LAYER 1A: Smoked Obsidian Dock Shell (SEPARATED from `tabsBackdrop`!)
            // =========================================================================
            val dockShellModifier = Modifier
                .fillMaxSize()
                .then(
                    if (useRealDockBackdrop && sceneBackdrop != null) {
                        val enableDockLens = glassTier == GlassQualityTier.ULTRA_AGSL &&
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                        Modifier.drawBackdrop(
                            backdrop = sceneBackdrop,
                            shape = { CircleShape },
                            effects = {
                                if (glassConfig.vibrancyEnabled) {
                                    vibrancy()
                                }
                                if (abs(glassConfig.backdropSaturation - 1f) > 0.02f || abs(glassConfig.backdropBrightness) > 0.005f) {
                                    colorControls(
                                        brightness = glassConfig.backdropBrightness,
                                        contrast = 1f,
                                        saturation = glassConfig.backdropSaturation
                                    )
                                }
                                if (glassConfig.blurRadiusDp > 0.35f) {
                                    blur(glassConfig.blurRadiusDp.dp.toPx())
                                }
                                if (enableDockLens && glassConfig.refractionHeightDp > 0.25f && glassConfig.refractionAmountDp > 0.25f) {
                                    val maxH = (size.height * 0.42f).coerceAtLeast(1f)
                                    val maxAmt = (size.height * 0.85f).coerceAtLeast(1f)
                                    lens(
                                        refractionHeight = glassConfig.refractionHeightDp.dp.toPx().coerceIn(0.5f, maxH),
                                        refractionAmount = glassConfig.refractionAmountDp.dp.toPx().coerceIn(0.5f, maxAmt),
                                        depthEffect = glassConfig.depthEffectEnabled,
                                        chromaticAberration = false
                                    )
                                }
                            },
                            highlight = { AppleGlassTokens.resolveHighlight(glassConfig.highlightStyle) },
                            shadow = { Shadow.Default },
                            onDrawSurface = {
                                val tintAlpha = glassConfig.idleTintAlpha.coerceIn(0f, 0.65f)
                                if (tintAlpha > 0.005f) {
                                    drawRect(AppleGlassTokens.SmokedCrystalBase.copy(alpha = tintAlpha))
                                }
                                val rim = glassConfig.rimHighlightAlpha.coerceIn(0f, 1f)
                                if (rim > 0.01f) {
                                    drawRect(
                                        brush = Brush.verticalGradient(
                                            colorStops = arrayOf(
                                                0.0f to Color.White.copy(alpha = 0.10f * rim),
                                                0.28f to Color.Transparent,
                                                0.82f to Color.Transparent,
                                                1.0f to AppleGlassTokens.SmokedCrystalInnerShadow.copy(alpha = 0.20f * rim)
                                            )
                                        )
                                    )
                                    val rPx = size.height / 2f
                                    drawRoundRect(
                                        brush = Brush.verticalGradient(
                                            colorStops = arrayOf(
                                                0.0f to Color.White.copy(alpha = 0.36f * rim),
                                                0.38f to Color.Transparent,
                                                0.76f to Color.Transparent,
                                                1.0f to Color.White.copy(alpha = 0.22f * rim)
                                            )
                                        ),
                                        cornerRadius = CornerRadius(rPx, rPx),
                                        style = Stroke(width = 0.75.dp.toPx())
                                    )
                                }
                            }
                        )
                    } else {
                        val rim = glassConfig.rimHighlightAlpha.coerceIn(0f, 1f)
                        Modifier
                            .clip(CircleShape)
                            .background(
                                AppleGlassTokens.SmokedCrystalBase.copy(
                                    alpha = (glassConfig.idleTintAlpha + 0.56f).coerceIn(0.68f, 0.90f)
                                )
                            )
                            .border(
                                width = 0.85.dp,
                                brush = Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        0.0f to Color.White.copy(alpha = 0.44f * rim),
                                        0.40f to Color.White.copy(alpha = 0.08f * rim),
                                        1.0f to Color.White.copy(alpha = 0.24f * rim)
                                    )
                                ),
                                shape = CircleShape
                            )
                    }
                )

            Box(modifier = dockShellModifier)

            // =========================================================================
            // LAYER 1B: Transparent Tab Content Layer — STABLY attached to `tabsBackdrop`
            // Because background is transparent, `combinedPillBackdrop` feeds SHARP scene
            // + SHARP tab icons into Layer 2's crystal droplet with ZERO blur muddiness!
            // =========================================================================
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (useRealLensDroplet) Modifier.layerBackdrop(tabsBackdrop) else Modifier)
                    .padding(horizontal = horizontalDockPadding),
                contentAlignment = Alignment.CenterStart
            ) {
                // Sleek translucent resting capsule (fades cleanly to 0 when droplet rises OR Search is active)
                if (tabWidthPx > 0f && glassConfig.restingPillAlpha > 0.005f) {
                    val restingAlpha = ((1f - dropletVisibility) * restingPresence).coerceIn(0f, 1f)
                    if (restingAlpha > 0.01f) {
                        val restingWidthDp = with(density) { tabWidthPx.toDp() }
                        val restingHeightDp = (dockHeightDp - 8.dp).coerceAtLeast(46.dp)
                        val baseAlpha = glassConfig.restingPillAlpha.coerceIn(0f, 0.45f)
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
                                            0.0f to Color.White.copy(alpha = (baseAlpha * 1.15f).coerceAtMost(0.45f)),
                                            0.55f to activeAccent.copy(alpha = (baseAlpha * 0.45f).coerceAtMost(0.24f)),
                                            1.0f to Color.White.copy(alpha = (baseAlpha * 0.40f).coerceAtMost(0.22f))
                                        )
                                    )
                                )
                                .border(
                                    width = 0.65.dp,
                                    brush = Brush.verticalGradient(
                                        colorStops = arrayOf(
                                            0.0f to Color.White.copy(alpha = (baseAlpha * 2.2f).coerceAtMost(0.42f)),
                                            0.50f to Color.Transparent,
                                            1.0f to Color.White.copy(alpha = (baseAlpha * 1.2f).coerceAtMost(0.24f))
                                        )
                                    ),
                                    shape = CircleShape
                                )
                        )
                    }
                }

                // Crisp Tab Icons & Labels
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    primaryTabs.forEachIndexed { index, tab ->
                        val distance = abs(lensPosition.value - index.toFloat()).coerceIn(0f, 1f)
                        val proximity = 1f - distance
                        val isSelected = selectedTab == tab
                        val isHighlighted = if (isSearchDetachedActive && !isInteracting) {
                            false
                        } else {
                            proximity > 0.45f
                        }
                        val iconVector = tabIcon(tab, filled = isHighlighted || isSelected)

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .then(
                                    if (previewTabsOverride == null) {
                                        Modifier.testTag("tab_${tab.name.lowercase()}")
                                    } else {
                                        Modifier
                                    }
                                ),
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
                                modifier = Modifier
                                    .size(if (tabCount <= 4) 23.dp else 21.dp)
                                    .graphicsLayer {
                                        val s = 1f + 0.05f * proximity * holdMorph
                                        scaleX = s
                                        scaleY = s
                                    }
                            )
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = if (tabCount <= 4) 10.sp else 9.sp,
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
            // LAYER 2 (UNCLIPPED PURE CRYSTAL GLASS DROPLET LENS — ON TOP OF LAYER 1B!)
            // Authentic Kyant0 `LiquidBottomTabs` crystal lens:
            // - Refracts SHARP `sceneBackdrop` + SHARP transparent `tabsBackdrop`
            // - Clamped non-tearing `lens()` curves edges cleanly without inverting icons
            // - Native AGSL Fresnel `Highlight` + `Shadow.Default` + ultra-clean surface!
            // =========================================================================
            if (tabWidthPx > 0f && dropletVisibility > 0.01f) {
                val dropletWidthPx = (tabWidthPx * (1.02f * pillScale))
                    .coerceIn(tabWidthPx * 0.90f, tabWidthPx * 1.22f)
                val dropletWidthDp = with(density) { dropletWidthPx.toDp() }
                val dropletHeightDp = (dockHeightDp - 4.dp).coerceAtLeast(52.dp)
                val dropletStartOffsetPx = padPx + (tabWidthPx - dropletWidthPx) * 0.5f

                val restSwellX = 1.03f
                val restSwellY = 1.07f
                val targetHoldX = glassConfig.heldScaleX.coerceIn(1.0f, 1.32f)
                val targetHoldY = glassConfig.heldScaleY.coerceIn(1.0f, 1.38f)
                val stretchGain = glassConfig.velocityStretchStrength.coerceIn(0f, 0.30f)

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
                                    val baseX = restSwellX + (targetHoldX - restSwellX) * holdMorph
                                    val baseY = restSwellY + (targetHoldY - restSwellY) * holdMorph
                                    val sx = baseX + (stretchFactor * stretchGain)
                                    val sy = baseY - (stretchFactor * stretchGain * 0.45f)
                                    scaleX = sx.coerceIn(0.96f, 1.42f)
                                    scaleY = sy.coerceIn(0.96f, 1.42f)
                                    alpha = dropletVisibility.coerceIn(0f, 1f)
                                },
                                effects = {
                                    if (glassConfig.vibrancyEnabled) {
                                        vibrancy()
                                    }
                                    if (abs(glassConfig.backdropSaturation - 1f) > 0.02f || abs(glassConfig.backdropBrightness) > 0.005f) {
                                        colorControls(
                                            brightness = glassConfig.backdropBrightness,
                                            contrast = 1f,
                                            saturation = glassConfig.backdropSaturation
                                        )
                                    }
                                    if (glassConfig.heldBlurRadiusDp > 0.35f) {
                                        blur(glassConfig.heldBlurRadiusDp.dp.toPx())
                                    }
                                    // Safely clamped optical refraction: bends glass edges dramatically while
                                    // keeping the center icon crisp, magnified, and never inverted/torn!
                                    val maxH = (size.height * 0.42f).coerceAtLeast(1f)
                                    val maxAmt = (size.height * 0.85f).coerceAtLeast(1f)
                                    val morphBoost = 0.84f + 0.16f * holdMorph
                                    val safeH = (glassConfig.heldRefractionHeightDp * morphBoost)
                                        .dp.toPx().coerceIn(0.5f, maxH)
                                    val safeAmt = (glassConfig.heldRefractionAmountDp * morphBoost)
                                        .dp.toPx().coerceIn(0.5f, maxAmt)
                                    lens(
                                        refractionHeight = safeH,
                                        refractionAmount = safeAmt,
                                        depthEffect = glassConfig.depthEffectEnabled,
                                        chromaticAberration = glassConfig.chromaticAberration
                                    )
                                },
                                highlight = { AppleGlassTokens.resolveHighlight(glassConfig.highlightStyle) },
                                shadow = { Shadow.Default },
                                onDrawSurface = {
                                    val vis = dropletVisibility.coerceIn(0f, 1f)
                                    // Pure crystal clear glass interior — never muddies the refracted icon!
                                    val tint = glassConfig.heldTintAlpha.coerceIn(0f, 0.35f)
                                    if (tint > 0.004f) {
                                        drawRect(Color.White.copy(alpha = tint * vis))
                                    }
                                    // Optional delicate Fresnel arc controlled cleanly by user's Held Rim slider
                                    val heldRim = glassConfig.heldRimAlpha.coerceIn(0f, 1f)
                                    if (heldRim > 0.01f) {
                                        val rPx = size.height / 2f
                                        drawRoundRect(
                                            brush = Brush.verticalGradient(
                                                colorStops = arrayOf(
                                                    0.0f to Color.White.copy(alpha = 0.45f * heldRim * vis),
                                                    0.30f to Color.Transparent,
                                                    0.74f to Color.Transparent,
                                                    1.0f to Color.White.copy(alpha = 0.28f * heldRim * vis)
                                                )
                                            ),
                                            cornerRadius = CornerRadius(rPx, rPx),
                                            style = Stroke(width = 0.85.dp.toPx())
                                        )
                                    }
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
                                val baseX = restSwellX + (targetHoldX - restSwellX) * holdMorph
                                val baseY = restSwellY + (targetHoldY - restSwellY) * holdMorph
                                scaleX = (baseX + stretchFactor * stretchGain).coerceIn(0.96f, 1.38f)
                                scaleY = (baseY - stretchFactor * stretchGain * 0.4f).coerceIn(0.96f, 1.38f)
                                alpha = dropletVisibility
                            }
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = (glassConfig.heldTintAlpha + 0.08f).coerceAtMost(0.30f)))
                            .border(
                                width = 1.dp,
                                brush = Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        0.0f to Color.White.copy(alpha = 0.72f),
                                        0.40f to Color.White.copy(alpha = 0.14f),
                                        1.0f to Color.White.copy(alpha = 0.48f)
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
        if (showDetachedSearch) {
            DetachedSearchOrb(
                sizeDp = dockHeightDp,
                isSelected = isSearchDetachedActive,
                activeAccent = activeAccent,
                sceneBackdrop = sceneBackdrop,
                useRealDockBackdrop = useRealDockBackdrop,
                glassTier = glassTier,
                glassConfig = glassConfig,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    currentOnTabSelected(MainTab.SEARCH)
                }
            )
        }
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
    glassConfig: LiquidGlassConfig,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val orbScale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.89f
            isSelected -> 1.03f
            else -> 1f
        },
        animationSpec = spring(
            dampingRatio = glassConfig.springDamping.coerceIn(0.45f, 0.85f),
            stiffness = (glassConfig.springStiffness * 1.25f).coerceAtLeast(520f)
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
                        if (glassConfig.vibrancyEnabled) {
                            vibrancy()
                        }
                        if (abs(glassConfig.backdropSaturation - 1f) > 0.02f || abs(glassConfig.backdropBrightness) > 0.005f) {
                            colorControls(
                                brightness = glassConfig.backdropBrightness,
                                contrast = 1f,
                                saturation = glassConfig.backdropSaturation
                            )
                        }
                        val orbBlur = if (isPressed) {
                            glassConfig.heldBlurRadiusDp
                        } else {
                            glassConfig.blurRadiusDp
                        }
                        if (orbBlur > 0.35f) {
                            blur(orbBlur.dp.toPx())
                        }
                        if (enableOrbLens) {
                            val maxH = (size.minDimension * 0.42f).coerceAtLeast(1f)
                            val maxAmt = (size.minDimension * 0.85f).coerceAtLeast(1f)
                            val hDp = if (isPressed) glassConfig.heldRefractionHeightDp else glassConfig.refractionHeightDp
                            val aDp = if (isPressed) glassConfig.heldRefractionAmountDp else glassConfig.refractionAmountDp
                            if (hDp > 0.25f && aDp > 0.25f) {
                                lens(
                                    refractionHeight = hDp.dp.toPx().coerceIn(0.5f, maxH),
                                    refractionAmount = aDp.dp.toPx().coerceIn(0.5f, maxAmt),
                                    depthEffect = glassConfig.depthEffectEnabled,
                                    chromaticAberration = glassConfig.chromaticAberration && (isPressed || isSelected)
                                )
                            }
                        }
                    },
                    highlight = { AppleGlassTokens.resolveHighlight(glassConfig.highlightStyle) },
                    shadow = { Shadow.Default },
                    onDrawSurface = {
                        val rPx = size.minDimension / 2f
                        val tintAlpha = if (isPressed) {
                            glassConfig.heldTintAlpha.coerceIn(0f, 0.35f)
                        } else {
                            glassConfig.idleTintAlpha.coerceIn(0f, 0.65f)
                        }
                        if (tintAlpha > 0.005f) {
                            drawRect(AppleGlassTokens.SmokedCrystalBase.copy(alpha = tintAlpha))
                        }
                        if (activeProgress > 0.01f) {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        activeAccent.copy(alpha = 0.24f * activeProgress),
                                        activeAccent.copy(alpha = 0.06f * activeProgress),
                                        Color.Transparent
                                    ),
                                    center = Offset(size.width * 0.5f, size.height * 0.5f),
                                    radius = rPx * 0.92f
                                )
                            )
                        }
                        val rim = glassConfig.rimHighlightAlpha.coerceIn(0f, 1f)
                        if (rim > 0.01f || isSelected) {
                            val topRim = if (isSelected) activeAccent.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.38f * rim)
                            val bottomRim = if (isSelected) activeAccent.copy(alpha = 0.42f) else Color.White.copy(alpha = 0.22f * rim)
                            drawCircle(
                                brush = Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        0.0f to topRim,
                                        0.40f to Color.Transparent,
                                        0.76f to Color.Transparent,
                                        1.0f to bottomRim
                                    )
                                ),
                                radius = rPx - 0.5.dp.toPx(),
                                style = Stroke(width = if (isSelected || isPressed) 1.05.dp.toPx() else 0.75.dp.toPx())
                            )
                        }
                    }
                )
            } else {
                Modifier
                    .clip(CircleShape)
                    .background(
                        AppleGlassTokens.SmokedCrystalBase.copy(
                            alpha = (glassConfig.idleTintAlpha + 0.56f).coerceIn(0.68f, 0.90f)
                        )
                    )
                    .border(
                        width = if (isSelected) 1.1.dp else 0.85.dp,
                        brush = Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to if (isSelected) activeAccent.copy(alpha = 0.72f) else Color.White.copy(alpha = 0.42f),
                                0.48f to Color.White.copy(alpha = 0.08f),
                                1.0f to if (isSelected) activeAccent.copy(alpha = 0.42f) else Color.White.copy(alpha = 0.24f)
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
