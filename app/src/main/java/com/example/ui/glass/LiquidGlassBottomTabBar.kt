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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.GlassQualityTier
import com.example.domain.model.MainTab
import com.example.ui.theme.FrostWhite
import com.example.ui.theme.LocalGlassQuality
import com.example.ui.theme.LocalLiquidGlassConfig
import com.example.ui.theme.LocalLiquidPalette
import com.example.ui.theme.MistSecondary
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Real-time iOS 26 Liquid Glass Floating Capsule Bottom Tab Bar powered by
 * Kyant0/AndroidLiquidGlass (`io.github.kyant0:backdrop:2.0.0`).
 *
 * - Both the outer dock capsule AND the sliding Droplet Pill Lens sample and optically refract
 *   the live scrolling scene behind them (`LocalLiquidBackdrop`).
 * - Pill Size, Spring Stiffness, Spring Damping, Edge Refraction Height/Amount, Blur, and
 *   RGB Chromatic Dispersion respond instantaneously to `LocalLiquidGlassConfig`.
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
    val backdrop = LocalLiquidBackdrop.current
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    var barWidthPx by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var dragVelocityX by remember { mutableFloatStateOf(0f) }
    var lastHapticIndex by remember { mutableIntStateOf(selectedTab.ordinal) }

    val lensPosition = remember { Animatable(selectedTab.ordinal.toFloat()) }

    LaunchedEffect(selectedTab, glassConfig.springStiffness, glassConfig.springDamping) {
        if (!isDragging) {
            lensPosition.animateTo(
                targetValue = selectedTab.ordinal.toFloat(),
                animationSpec = spring(
                    dampingRatio = glassConfig.springDamping,
                    stiffness = glassConfig.springStiffness
                )
            )
            lastHapticIndex = selectedTab.ordinal
        }
    }

    val dropletMorph by animateFloatAsState(
        targetValue = if (isDragging) 1f else 0f,
        animationSpec = spring(
            dampingRatio = glassConfig.springDamping,
            stiffness = glassConfig.springStiffness * 1.1f
        ),
        label = "dropletMorph"
    )

    val stretchFactor by animateFloatAsState(
        targetValue = if (isDragging) {
            (abs(dragVelocityX) / 2400f).coerceIn(0f, 1f)
        } else 0f,
        animationSpec = spring(
            dampingRatio = (glassConfig.springDamping + 0.08f).coerceAtMost(0.95f),
            stiffness = glassConfig.springStiffness * 1.3f
        ),
        label = "liquidStretch"
    )

    val pillScale = glassConfig.pillScale

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        LiquidGlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .height((66f + 4f * (pillScale - 1f)).coerceIn(62f, 74f).dp)
                .testTag("liquid_bottom_tab_bar")
                .onSizeChanged { barWidthPx = it.width.toFloat() }
                .pointerInput(tabCount, glassConfig.springStiffness, glassConfig.springDamping) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (barWidthPx <= 0f) return@awaitEachGesture

                        val tabWidth = barWidthPx / tabCount.toFloat()
                        val velocityTracker = VelocityTracker()
                        velocityTracker.addPosition(down.uptimeMillis, down.position)

                        var movedSignificantly = false
                        val startX = down.position.x
                        isDragging = true

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break

                            if (!change.pressed) {
                                val velX = velocityTracker.calculateVelocity().x
                                dragVelocityX = 0f
                                isDragging = false

                                val targetIdx = if (!movedSignificantly) {
                                    (change.position.x / tabWidth).toInt().coerceIn(0, tabCount - 1)
                                } else {
                                    val projected = lensPosition.value + (velX / (tabWidth * 18f)).coerceIn(-0.48f, 0.48f)
                                    projected.roundToInt().coerceIn(0, tabCount - 1)
                                }

                                val chosenTab = tabs[targetIdx]
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
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
                            if (dxFromStart > 10f) {
                                movedSignificantly = true
                                change.consume()
                            }

                            if (movedSignificantly) {
                                val rawIndex = ((change.position.x / tabWidth) - 0.5f)
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
            cornerRadius = 35.dp,
            tintColor = Color(0xFF0A0F1F),
            surfaceAlpha = 0.52f,
            highlightAccent = palette.primaryGlow,
            useRefractionShell = true,
            refractionBoost = 1.0f
        ) {
            // Sliding Real-Time Refracting Liquid Glass Droplet Pill Lens
            if (barWidthPx > 0f) {
                val tabWidthPx = barWidthPx / tabCount.toFloat()
                val widthFactor = (0.86f * pillScale).coerceIn(0.72f, 1.04f)
                val pillWidthPx = tabWidthPx * widthFactor
                val lensWidthDp = with(density) { pillWidthPx.toDp() }
                val lensHeightDp = (52f * pillScale).coerceIn(44f, 62f).dp
                val lensStartOffsetPx = (tabWidthPx - pillWidthPx) * 0.5f
                val pillShape = RoundedCornerShape(30.dp)

                val useRealPillLens = backdrop != null &&
                    glassTier == GlassQualityTier.ULTRA_AGSL &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

                val pillModifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(lensWidthDp)
                    .height(lensHeightDp)
                    .graphicsLayer {
                        translationX = lensStartOffsetPx + lensPosition.value * tabWidthPx
                        val baseScaleX = 1f + 0.10f * dropletMorph + 0.12f * stretchFactor
                        val baseScaleY = 1f + 0.14f * dropletMorph - 0.10f * stretchFactor
                        scaleX = baseScaleX.coerceAtMost(1.24f)
                        scaleY = baseScaleY.coerceIn(0.88f, 1.18f)
                    }

                if (useRealPillLens && backdrop != null) {
                    Box(
                        modifier = pillModifier
                            .drawBackdrop(
                                backdrop = backdrop,
                                shape = { pillShape },
                                effects = {
                                    vibrancy()
                                    if (glassConfig.blurRadiusDp > 1f) {
                                        // Crystal-clear droplet lens during drag!
                                        blur((glassConfig.blurRadiusDp * (0.35f - 0.25f * dropletMorph)).coerceAtLeast(0.5f).dp.toPx())
                                    }
                                    lens(
                                        refractionHeight = (glassConfig.refractionHeightDp * (0.95f + 0.45f * dropletMorph)).dp.toPx(),
                                        refractionAmount = (glassConfig.refractionAmountDp * (1.10f + 0.55f * dropletMorph)).dp.toPx(),
                                        chromaticAberration = glassConfig.chromaticAberration
                                    )
                                },
                                highlight = { Highlight.Default },
                                shadow = { Shadow.Default },
                                onDrawSurface = {
                                    drawRect(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(
                                                palette.primaryGlow.copy(alpha = 0.20f + 0.14f * dropletMorph),
                                                palette.secondaryGlow.copy(alpha = 0.12f + 0.08f * dropletMorph),
                                                palette.deepAurora.copy(alpha = 0.18f)
                                            )
                                        )
                                    )
                                }
                            )
                            .border(
                                width = if (isDragging) 1.35.dp else 1.dp,
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.72f + 0.22f * dropletMorph),
                                        palette.primaryGlow.copy(alpha = 0.36f),
                                        palette.secondaryGlow.copy(alpha = 0.52f)
                                    )
                                ),
                                shape = pillShape
                            )
                    )
                } else {
                    Box(
                        modifier = pillModifier
                            .clip(pillShape)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        palette.primaryGlow.copy(alpha = 0.24f + 0.16f * dropletMorph),
                                        palette.secondaryGlow.copy(alpha = 0.16f + 0.10f * dropletMorph),
                                        palette.deepAurora.copy(alpha = 0.26f)
                                    )
                                )
                            )
                            .border(
                                width = if (isDragging) 1.35.dp else 1.dp,
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.65f + 0.25f * dropletMorph),
                                        palette.primaryGlow.copy(alpha = 0.35f),
                                        palette.secondaryGlow.copy(alpha = 0.52f)
                                    )
                                ),
                                shape = pillShape
                            )
                    )
                }
            }

            // Tab Icons & Labels with dynamic lens proximity magnification
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEachIndexed { index, tab ->
                    val distance = abs(lensPosition.value - index.toFloat()).coerceIn(0f, 1f)
                    val proximity = 1f - distance
                    val isSelected = selectedTab == tab
                    val iconVector = tabIcon(tab, filled = proximity > 0.5f || isSelected)

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .testTag("tab_${tab.name.lowercase()}")
                            .graphicsLayer {
                                val mag = 1f + (0.16f * proximity * (0.4f + 0.6f * dropletMorph) * pillScale)
                                scaleX = mag
                                scaleY = mag
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = iconVector,
                            contentDescription = tab.label,
                            tint = if (proximity > 0.45f) FrostWhite else MistSecondary.copy(alpha = 0.78f),
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                            color = if (proximity > 0.45f) palette.primaryGlow else MistSecondary.copy(alpha = 0.72f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
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
