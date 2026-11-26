package com.example.ui.glass

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.GlassQualityTier
import com.example.domain.model.MainTab
import com.example.ui.theme.FrostWhite
import com.example.ui.theme.LocalGlassQuality
import com.example.ui.theme.LocalLiquidGlassConfig
import com.example.ui.theme.LocalLiquidPalette
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Authentic Apple iOS 26 / Kyant0 `LiquidBottomTabs` architecture powered by `io.github.kyant0:backdrop`.
 *
 * Two-Layer Optical Architecture:
 * - **Layer 1 (Frosted Dock + Tabs, captured into `tabsBackdrop`)**:
 *   Clean neutral iOS 26 frosted glass capsule sampling `LocalLiquidBackdrop`, containing a subtle
 *   frosted resting selection highlight and the 5 crisp tab icons/labels.
 * - **Layer 2 (Unclipped Crystal-Clear Refracting Droplet Pill — DRAWN ON TOP OF LAYER 1!)**:
 *   Sits ABOVE the entire tab bar without clipping so it swells past the dock edges on press, drag,
 *   or tab transition. Samples `rememberCombinedBackdrop(sceneBackdrop, tabsBackdrop)` with zero blur
 *   and strong `lens(..., depthEffect = true, chromaticAberration = ...)` so the tab icons and text
 *   literally bend, magnify, and split with subtle prism edges right inside the clear glass droplet.
 */
@Composable
fun LiquidGlassBottomTabBar(
    selectedTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = MainTab.entries
    val tabCount = tabs.size
    val palette = LocalLiquidPalette.current
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
    var dragVelocityX by remember { mutableFloatStateOf(0f) }
    var lastHapticIndex by remember { mutableIntStateOf(selectedTab.ordinal) }

    val lensPosition = remember { Animatable(selectedTab.ordinal.toFloat()) }

    LaunchedEffect(selectedTab, glassConfig.springStiffness, glassConfig.springDamping) {
        if (!isDragging) {
            val target = selectedTab.ordinal.toFloat()
            if (abs(lensPosition.value - target) > 0.05f) {
                isTapGliding = true
                launch {
                    delay(260L)
                    isTapGliding = false
                }
            }
            lensPosition.animateTo(
                targetValue = target,
                animationSpec = spring(
                    dampingRatio = glassConfig.springDamping,
                    stiffness = glassConfig.springStiffness
                )
            )
            lastHapticIndex = selectedTab.ordinal
        }
    }

    val isLensActive = isDragging || isTapGliding
    val dropletMorph by animateFloatAsState(
        targetValue = if (isLensActive) 1f else 0f,
        animationSpec = spring(
            dampingRatio = (glassConfig.springDamping + 0.06f).coerceIn(0.60f, 0.86f),
            stiffness = (glassConfig.springStiffness * 1.25f).coerceAtLeast(520f)
        ),
        label = "dropletMorph"
    )

    val stretchFactor by animateFloatAsState(
        targetValue = if (isDragging) {
            (abs(dragVelocityX) / 2200f).coerceIn(0f, 1f)
        } else {
            (abs(lensPosition.velocity) / 14f).coerceIn(0f, 0.7f)
        },
        animationSpec = spring(
            dampingRatio = 0.72f,
            stiffness = 620f
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

    // Outer unclipped host: vertical padding leaves clear space for the droplet to swell past the dock bounds!
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        // Shared container holding Layer 1 (Dock + Icons) and Layer 2 (Unclipped Droplet Pill ON TOP)
        Box(
            modifier = Modifier
                .fillMaxWidth()
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
                        isDragging = true
                        isTapGliding = false

                        // Snap/glide toward touched tab immediately on press-hold if dragging begins
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

                                val chosenTab = tabs[targetIdx]
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                if (abs(lensPosition.value - targetIdx.toFloat()) > 0.1f) {
                                    isTapGliding = true
                                    scope.launch {
                                        delay(260L)
                                        isTapGliding = false
                                    }
                                }
                                onTabSelected(chosenTab)
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
                                break
                            }

                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                            val dxFromStart = abs(change.position.x - startX)
                            if (dxFromStart > 8f) {
                                movedSignificantly = true
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
            // LAYER 1: Frosted Apple iOS 26 Outer Dock + Resting Pill + Tab Icons/Labels
            // Captured into `tabsBackdrop` so Layer 2's crystal droplet refracts them!
            // =========================================================================
            val dockModifier = Modifier
                .fillMaxSize()
                .layerBackdrop(tabsBackdrop)
                .then(
                    if (useRealDockBackdrop && sceneBackdrop != null) {
                        val enableDockLens = glassTier == GlassQualityTier.ULTRA_AGSL &&
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                        Modifier.drawBackdrop(
                            backdrop = sceneBackdrop,
                            shape = { CircleShape },
                            effects = {
                                vibrancy()
                                val dockBlur = glassConfig.blurRadiusDp.coerceIn(4f, 16f)
                                blur(dockBlur.dp.toPx())
                                if (enableDockLens) {
                                    lens(
                                        refractionHeight = (glassConfig.refractionHeightDp * 0.85f).dp.toPx(),
                                        refractionAmount = (glassConfig.refractionAmountDp * 0.85f).dp.toPx(),
                                        depthEffect = true,
                                        chromaticAberration = false
                                    )
                                }
                            },
                            highlight = { Highlight.Default },
                            shadow = { Shadow.Default },
                            onDrawSurface = {
                                drawRect(AppleGlassTokens.NeutralDarkTint.copy(alpha = 0.34f))
                                drawRect(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = 0.11f),
                                            Color.Transparent,
                                            Color.White.copy(alpha = 0.03f)
                                        )
                                    )
                                )
                            }
                        )
                    } else {
                        Modifier
                            .clip(CircleShape)
                            .background(AppleGlassTokens.NeutralDarkTint.copy(alpha = 0.84f))
                            .border(
                                width = 0.75.dp,
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.34f),
                                        Color.White.copy(alpha = 0.08f),
                                        Color.White.copy(alpha = 0.18f)
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
                // Subtle luminous Apple iOS 26 resting capsule behind the active icon
                // Smoothly fades out as the crystal magnifying droplet rises ON TOP during drag/glide
                if (tabWidthPx > 0f) {
                    val restingAlpha = (1f - dropletMorph).coerceIn(0f, 1f)
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
                                        colors = listOf(
                                            Color.White.copy(alpha = 0.18f),
                                            Color.White.copy(alpha = 0.10f)
                                        )
                                    )
                                )
                                .border(
                                    width = 0.65.dp,
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = 0.38f),
                                            Color.White.copy(alpha = 0.08f),
                                            Color.White.copy(alpha = 0.20f)
                                        )
                                    ),
                                    shape = CircleShape
                                )
                        )
                    }
                }

                // Crisp Apple iOS 26 Tab Icons & Labels
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    tabs.forEachIndexed { index, tab ->
                        val distance = abs(lensPosition.value - index.toFloat()).coerceIn(0f, 1f)
                        val proximity = 1f - distance
                        val isSelected = selectedTab == tab
                        val isHighlighted = proximity > 0.45f
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
                                    palette.primaryGlow
                                } else {
                                    FrostWhite.copy(alpha = 0.74f)
                                },
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.5.sp,
                                    fontWeight = if (isHighlighted) FontWeight.SemiBold else FontWeight.Medium
                                ),
                                color = if (isHighlighted) {
                                    palette.primaryGlow
                                } else {
                                    FrostWhite.copy(alpha = 0.66f)
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
            // LAYER 2 (DRAWN ON TOP OF THE ENTIRE TAB BAR, UNCLIPPED!):
            // Crystal-Clear Refracting Liquid Glass Droplet Pill Lens!
            // Expands above/below the dock bounds & refracts the tab icons right underneath it!
            // =========================================================================
            if (tabWidthPx > 0f && dropletMorph > 0.01f) {
                val dropletWidthPx = (tabWidthPx * (0.98f * pillScale)).coerceIn(tabWidthPx * 0.88f, tabWidthPx * 1.18f)
                val dropletWidthDp = with(density) { dropletWidthPx.toDp() }
                val dropletHeightDp = (dockHeightDp - 4.dp).coerceAtLeast(54.dp)
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
                                    // Swells vertically & horizontally past the dock capsule like Kyant0's LiquidBottomTabs!
                                    val sx = 1f + 0.16f * dropletMorph + 0.14f * stretchFactor
                                    val sy = 1f + 0.24f * dropletMorph - 0.09f * stretchFactor
                                    scaleX = sx.coerceIn(0.95f, 1.34f)
                                    scaleY = sy.coerceIn(0.95f, 1.30f)
                                    alpha = (dropletMorph * 1.15f).coerceIn(0f, 1f)
                                },
                                effects = {
                                    vibrancy()
                                    // Zero blur inside the droplet so icons stay razor-sharp & optically magnified!
                                    lens(
                                        refractionHeight = (glassConfig.refractionHeightDp * (0.95f + 0.45f * dropletMorph)).dp.toPx(),
                                        refractionAmount = (glassConfig.refractionAmountDp * (1.15f + 0.60f * dropletMorph)).dp.toPx(),
                                        depthEffect = true,
                                        chromaticAberration = glassConfig.chromaticAberration
                                    )
                                },
                                highlight = { Highlight.Default },
                                shadow = { Shadow.Default },
                                onDrawSurface = {
                                    // Nearly 100% transparent crystal-clear glass surface with subtle specular edge sheen
                                    drawRect(Color.White.copy(alpha = 0.05f * dropletMorph))
                                }
                            )
                    )
                } else {
                    // Clean translucent droplet fallback on API < 33 / Battery Saver
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .width(dropletWidthDp)
                            .height(dropletHeightDp)
                            .graphicsLayer {
                                translationX = dropletStartOffsetPx + lensPosition.value * tabWidthPx
                                scaleX = 1f + 0.14f * dropletMorph + 0.10f * stretchFactor
                                scaleY = 1f + 0.18f * dropletMorph - 0.06f * stretchFactor
                                alpha = dropletMorph
                            }
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.12f))
                            .border(
                                width = 1.2.dp,
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.85f),
                                        Color.White.copy(alpha = 0.18f),
                                        Color.White.copy(alpha = 0.60f)
                                    )
                                ),
                                shape = CircleShape
                            )
                    )
                }
            }
        }
    }
}

private fun tabIcon(tab: MainTab, filled: Boolean): ImageVector = when (tab) {
    MainTab.HOME -> if (filled) Icons.Rounded.Home else Icons.Outlined.Home
    MainTab.SEARCH -> if (filled) Icons.Rounded.Search else Icons.Outlined.Search
    MainTab.LIBRARY -> if (filled) Icons.Rounded.LibraryMusic else Icons.Outlined.LibraryMusic
    MainTab.SOUND_LAB -> if (filled) Icons.Rounded.Equalizer else Icons.Outlined.Equalizer
    MainTab.PROFILE -> if (filled) Icons.Rounded.Person else Icons.Outlined.Person
}
