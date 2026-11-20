package com.example.ui.glass

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.domain.model.GlassQualityTier
import com.example.ui.theme.FrostWhite
import com.example.ui.theme.LocalGlassQuality
import com.example.ui.theme.LocalLiquidGlassConfig
import com.example.ui.theme.LocalLiquidPalette
import com.example.ui.theme.ObsidianBlack
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Provides the active Kyant0/AndroidLiquidGlass scene LayerBackdrop to all floating glass chrome,
 * buttons, sliders, toggles, and pills.
 */
val LocalLiquidBackdrop = compositionLocalOf<Backdrop?> { null }

/**
 * Animated Crystal Ice & Deep Indigo (or dynamic track Palette) ambient aurora backdrop over pure AMOLED black.
 */
@Composable
fun LiquidAuroraCanvas(
    modifier: Modifier = Modifier,
    intensity: Float = 1f
) {
    val palette = LocalLiquidPalette.current
    val glassTier = LocalGlassQuality.current
    val animateDrift = glassTier != GlassQualityTier.BATTERY_SAVER

    val transition = rememberInfiniteTransition(label = "auroraMesh")
    val driftPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 16_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "auroraDrift"
    )
    val phase = if (animateDrift) driftPhase else 0.7f

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBlack)
    ) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        val c1 = Offset(
            x = w * (0.22f + 0.14f * cos(phase)),
            y = h * (0.14f + 0.08f * sin(phase * 0.8f))
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    palette.deepAurora.copy(alpha = 0.62f * intensity),
                    palette.deepAurora.copy(alpha = 0.20f * intensity),
                    Color.Transparent
                ),
                center = c1,
                radius = w * 0.95f
            ),
            radius = w * 0.95f,
            center = c1
        )

        val c2 = Offset(
            x = w * (0.82f + 0.12f * sin(phase * 0.9f)),
            y = h * (0.28f + 0.10f * cos(phase * 0.7f))
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    palette.secondaryGlow.copy(alpha = 0.34f * intensity),
                    palette.secondaryGlow.copy(alpha = 0.10f * intensity),
                    Color.Transparent
                ),
                center = c2,
                radius = w * 0.82f
            ),
            radius = w * 0.82f,
            center = c2
        )

        val c3 = Offset(
            x = w * (0.45f + 0.18f * cos(phase * 0.6f + 1.4f)),
            y = h * (0.82f + 0.08f * sin(phase * 0.5f))
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    palette.primaryGlow.copy(alpha = 0.26f * intensity),
                    palette.deepAurora.copy(alpha = 0.12f * intensity),
                    Color.Transparent
                ),
                center = c3,
                radius = w * 0.85f
            ),
            radius = w * 0.85f,
            center = c3
        )
    }
}

/**
 * Optical Liquid Glass container powered by Kyant0/AndroidLiquidGlass (`io.github.kyant0:backdrop:2.0.0`).
 *
 * When [useRefractionShell] is true and a [LocalLiquidBackdrop] is present (and not in Battery Saver),
 * renders real-time GPU backdrop sampling with vibrancy, frosted blur, curved-edge SDF lens refraction,
 * optional RGB chromatic dispersion, Fresnel Highlight, and ambient Shadow.
 */
@Composable
fun LiquidGlassSurface(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    tintColor: Color = Color(0xFF0E1426),
    surfaceAlpha: Float = 0.42f,
    highlightAccent: Color? = null,
    useRefractionShell: Boolean = true,
    backdropOverride: Backdrop? = null,
    refractionBoost: Float = 1f,
    content: @Composable BoxScope.() -> Unit
) {
    val palette = LocalLiquidPalette.current
    val glassTier = LocalGlassQuality.current
    val glassConfig = LocalLiquidGlassConfig.current
    val backdrop = backdropOverride ?: LocalLiquidBackdrop.current
    val accent = highlightAccent ?: palette.primaryGlow
    val shape: Shape = RoundedCornerShape(cornerRadius)

    val useRealBackdrop = useRefractionShell &&
        backdrop != null &&
        glassTier != GlassQualityTier.BATTERY_SAVER &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    if (useRealBackdrop && backdrop != null) {
        val enableLens = glassTier == GlassQualityTier.ULTRA_AGSL &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        val veilAlpha = (surfaceAlpha * 0.52f).coerceIn(0.14f, 0.48f)

        Box(
            modifier = modifier
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { shape },
                    effects = {
                        vibrancy()
                        if (glassConfig.blurRadiusDp > 0.5f) {
                            blur(glassConfig.blurRadiusDp.dp.toPx())
                        }
                        if (enableLens) {
                            lens(
                                refractionHeight = (glassConfig.refractionHeightDp * refractionBoost).dp.toPx(),
                                refractionAmount = (glassConfig.refractionAmountDp * refractionBoost).dp.toPx(),
                                chromaticAberration = glassConfig.chromaticAberration
                            )
                        }
                    },
                    highlight = { Highlight.Default },
                    shadow = { Shadow.Default },
                    onDrawSurface = {
                        drawRect(tintColor.copy(alpha = veilAlpha))
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    accent.copy(alpha = 0.14f),
                                    Color.Transparent
                                )
                            )
                        )
                    }
                )
                .border(
                    width = 0.85.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.42f),
                            accent.copy(alpha = 0.24f),
                            Color.White.copy(alpha = 0.08f)
                        )
                    ),
                    shape = shape
                ),
            content = content
        )
    } else {
        // Lightweight smoked obsidian glass for dense scrolling lists & Battery Saver tier
        Box(
            modifier = modifier
                .clip(shape)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            tintColor.copy(alpha = (surfaceAlpha + 0.14f).coerceAtMost(0.86f)),
                            palette.deepAurora.copy(alpha = 0.20f),
                            tintColor.copy(alpha = surfaceAlpha.coerceAtMost(0.76f))
                        )
                    )
                )
                .drawWithContent {
                    drawContent()
                    val rPx = cornerRadius.toPx()
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                accent.copy(alpha = 0.13f),
                                Color.Transparent
                            ),
                            startY = 0f,
                            endY = size.height * 0.42f
                        ),
                        cornerRadius = CornerRadius(rPx, rPx)
                    )
                }
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            accent.copy(alpha = 0.40f),
                            Color.White.copy(alpha = 0.14f),
                            palette.secondaryGlow.copy(alpha = 0.24f),
                            Color.White.copy(alpha = 0.08f)
                        )
                    ),
                    shape = shape
                ),
            content = content
        )
    }
}

/**
 * AndroidLiquidGlass-style interactive Liquid Button with live backdrop refraction,
 * pointer-following radial caustic InteractiveHighlight, and spring physics driven by LiquidGlassConfig.
 */
@Composable
fun LiquidGlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    accentGlow: Boolean = false,
    enabled: Boolean = true,
    useLiveBackdrop: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val palette = LocalLiquidPalette.current
    val glassConfig = LocalLiquidGlassConfig.current

    var isPressed by remember { mutableStateOf(false) }
    var touchOffset by remember { mutableStateOf(Offset.Unspecified) }
    var buttonSize by remember { mutableStateOf(IntSize.Zero) }

    val pressScale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.945f else 1f,
        animationSpec = spring(
            dampingRatio = glassConfig.springDamping,
            stiffness = glassConfig.springStiffness * 1.25f
        ),
        label = "liquidButtonScale"
    )
    val highlightAlpha by animateFloatAsState(
        targetValue = if (isPressed && enabled) 1f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "interactiveHighlightAlpha"
    )

    LiquidGlassSurface(
        modifier = modifier
            .onSizeChanged { buttonSize = it }
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
                alpha = if (enabled) 1f else 0.45f
            }
            .pointerInput(enabled, onClick) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    isPressed = true
                    touchOffset = down.position
                    var cancelled = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        touchOffset = change.position
                        if (!change.pressed) {
                            break
                        }
                        val outOfBounds = change.position.x < -24f ||
                            change.position.y < -24f ||
                            change.position.x > size.width + 24f ||
                            change.position.y > size.height + 24f
                        if (outOfBounds) {
                            cancelled = true
                        }
                    }
                    isPressed = false
                    if (!cancelled) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onClick()
                    }
                }
            },
        cornerRadius = cornerRadius,
        tintColor = if (accentGlow) palette.deepAurora else Color(0xFF131A2F),
        surfaceAlpha = if (accentGlow) 0.56f else 0.42f,
        highlightAccent = if (accentGlow) palette.primaryGlow else Color.White,
        useRefractionShell = useLiveBackdrop,
        refractionBoost = if (isPressed) 1.18f else 0.85f
    ) {
        // Pointer-following InteractiveHighlight radial specular shine
        if (highlightAlpha > 0.01f && touchOffset != Offset.Unspecified && buttonSize.width > 0) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val radius = maxOf(size.width, size.height) * 0.72f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.32f * highlightAlpha),
                            palette.primaryGlow.copy(alpha = 0.16f * highlightAlpha),
                            Color.Transparent
                        ),
                        center = touchOffset,
                        radius = radius
                    ),
                    radius = radius,
                    center = touchOffset
                )
            }
        }
        content()
    }
}

/**
 * AndroidLiquidGlass-style interactive Liquid Slider with a refracting crystal droplet thumb
 * that enlarges and intensifies optical lens edge refraction during drag.
 */
@Composable
fun LiquidGlassSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChangeFinished: (() -> Unit)? = null,
    activeColor: Color = LocalLiquidPalette.current.primaryGlow
) {
    val palette = LocalLiquidPalette.current
    val glassConfig = LocalLiquidGlassConfig.current
    val glassTier = LocalGlassQuality.current
    val backdrop = LocalLiquidBackdrop.current
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    var trackWidthPx by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    val rangeSpan = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0.0001f)
    val fraction = ((value - valueRange.start) / rangeSpan).coerceIn(0f, 1f)

    val thumbMorph by animateFloatAsState(
        targetValue = if (isDragging) 1f else 0f,
        animationSpec = spring(
            dampingRatio = glassConfig.springDamping,
            stiffness = glassConfig.springStiffness
        ),
        label = "sliderThumbMorph"
    )

    val thumbWidthDp = (32f * glassConfig.pillScale).dp
    val thumbHeightDp = (22f * glassConfig.pillScale).dp
    val thumbWidthPx = with(density) { thumbWidthDp.toPx() }

    fun updateFromX(x: Float) {
        val usable = (trackWidthPx - thumbWidthPx).coerceAtLeast(1f)
        val norm = ((x - thumbWidthPx * 0.5f) / usable).coerceIn(0f, 1f)
        onValueChange(valueRange.start + norm * rangeSpan)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(38.dp)
            .onSizeChanged { trackWidthPx = it.width.toFloat() }
            .pointerInput(valueRange) {
                detectTapGestures { offset ->
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    updateFromX(offset.x)
                    onValueChangeFinished?.invoke()
                }
            }
            .pointerInput(valueRange) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        updateFromX(offset.x)
                    },
                    onDragEnd = {
                        isDragging = false
                        onValueChangeFinished?.invoke()
                    },
                    onDragCancel = {
                        isDragging = false
                        onValueChangeFinished?.invoke()
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        updateFromX(change.position.x)
                    }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        // Track rail
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height((7f + 2f * thumbMorph).dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.14f))
                .border(0.75.dp, Color.White.copy(alpha = 0.16f), CircleShape)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceAtLeast(0.01f))
                    .height((7f + 2f * thumbMorph).dp)
                    .clip(CircleShape)
                    .background(
                        Brush.horizontalGradient(
                            listOf(palette.deepAurora, palette.secondaryGlow, activeColor)
                        )
                    )
            )
        }

        // Refracting Liquid Glass Droplet Thumb
        val usablePx = (trackWidthPx - thumbWidthPx).coerceAtLeast(0f)
        val thumbOffsetX = usablePx * fraction
        val useLiveLens = backdrop != null &&
            glassTier == GlassQualityTier.ULTRA_AGSL &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

        val thumbModifier = Modifier
            .graphicsLayer {
                translationX = thumbOffsetX
                scaleX = 1f + 0.24f * thumbMorph
                scaleY = 1f + 0.28f * thumbMorph
            }
            .width(thumbWidthDp)
            .height(thumbHeightDp)

        if (useLiveLens && backdrop != null) {
            Box(
                modifier = thumbModifier
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { CircleShape },
                        effects = {
                            vibrancy()
                            blur((glassConfig.blurRadiusDp * 0.25f).dp.toPx())
                            lens(
                                refractionHeight = (glassConfig.refractionHeightDp * (0.7f + 0.45f * thumbMorph)).dp.toPx(),
                                refractionAmount = (glassConfig.refractionAmountDp * (0.85f + 0.55f * thumbMorph)).dp.toPx(),
                                chromaticAberration = glassConfig.chromaticAberration
                            )
                        },
                        highlight = { Highlight.Default },
                        shadow = { Shadow.Default },
                        onDrawSurface = {
                            drawRect(Color.White.copy(alpha = 0.22f - 0.10f * thumbMorph))
                        }
                    )
                    .border(
                        width = 1.1.dp,
                        brush = Brush.linearGradient(
                            listOf(Color.White.copy(alpha = 0.90f), palette.primaryGlow.copy(alpha = 0.55f))
                        ),
                        shape = CircleShape
                    )
            )
        } else {
            Box(
                modifier = thumbModifier
                    .clip(CircleShape)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = 0.90f), palette.primaryGlow.copy(alpha = 0.72f))
                        )
                    )
                    .border(1.dp, Color.White, CircleShape)
            )
        }
    }
}

/**
 * AndroidLiquidGlass-style fluid Liquid Toggle pill with velocity-stretched refracting glass knob.
 */
@Composable
fun LiquidGlassToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalLiquidPalette.current
    val glassConfig = LocalLiquidGlassConfig.current
    val glassTier = LocalGlassQuality.current
    val backdrop = LocalLiquidBackdrop.current
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val trackWidth = (60f * glassConfig.pillScale).dp
    val trackHeight = (34f * glassConfig.pillScale).dp
    val knobWidth = (30f * glassConfig.pillScale).dp
    val knobHeight = (26f * glassConfig.pillScale).dp
    val travelPx = with(density) { (trackWidth - knobWidth - 8.dp).toPx().coerceAtLeast(1f) }

    val progress = remember { Animatable(if (checked) 1f else 0f) }

    androidx.compose.runtime.LaunchedEffect(checked, glassConfig.springStiffness, glassConfig.springDamping) {
        progress.animateTo(
            targetValue = if (checked) 1f else 0f,
            animationSpec = spring(
                dampingRatio = glassConfig.springDamping,
                stiffness = glassConfig.springStiffness
            )
        )
    }

    val motionStretch = (abs(progress.velocity) / 14f).coerceIn(0f, 0.22f)
    val useLiveLens = backdrop != null &&
        glassTier == GlassQualityTier.ULTRA_AGSL &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    Box(
        modifier = modifier
            .width(trackWidth)
            .height(trackHeight)
            .clip(CircleShape)
            .background(
                if (progress.value > 0.5f) {
                    Brush.horizontalGradient(listOf(palette.deepAurora, palette.secondaryGlow))
                } else {
                    Brush.horizontalGradient(listOf(Color(0x66161C2E), Color(0x4D111624)))
                }
            )
            .border(
                width = 1.dp,
                color = if (checked) palette.primaryGlow.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.2f),
                shape = CircleShape
            )
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                scope.launch { onCheckedChange(!checked) }
            }
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        val knobModifier = Modifier
            .graphicsLayer {
                translationX = travelPx * progress.value
                scaleX = 1f + motionStretch
                scaleY = 1f - motionStretch * 0.35f
            }
            .width(knobWidth)
            .height(knobHeight)

        if (useLiveLens && backdrop != null) {
            Box(
                modifier = knobModifier
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { CircleShape },
                        effects = {
                            vibrancy()
                            lens(
                                refractionHeight = (glassConfig.refractionHeightDp * 0.65f).dp.toPx(),
                                refractionAmount = (glassConfig.refractionAmountDp * 0.85f).dp.toPx(),
                                chromaticAberration = glassConfig.chromaticAberration
                            )
                        },
                        highlight = { Highlight.Default },
                        shadow = { Shadow.Default },
                        onDrawSurface = { drawRect(Color.White.copy(alpha = 0.35f)) }
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.88f), CircleShape)
            )
        } else {
            Box(
                modifier = knobModifier
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.92f))
            )
        }
    }
}

@Composable
fun LiquidGlassChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalLiquidPalette.current
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(50)

    Box(
        modifier = modifier
            .clip(shape)
            .background(
                if (selected) {
                    Brush.horizontalGradient(
                        listOf(
                            palette.primaryGlow.copy(alpha = 0.28f),
                            palette.secondaryGlow.copy(alpha = 0.22f)
                        )
                    )
                } else {
                    Brush.horizontalGradient(
                        listOf(Color(0x66161C2E), Color(0x4D111624))
                    )
                }
            )
            .border(
                width = 1.dp,
                color = if (selected) palette.primaryGlow.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.14f),
                shape = shape
            )
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) FrostWhite else Color(0xFFB5C4DA)
        )
    }
}

@Composable
fun TrackArtwork(
    artworkUrl: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 14.dp
) {
    val palette = LocalLiquidPalette.current
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        palette.deepAurora.copy(alpha = 0.72f),
                        Color(0xFF0E1426)
                    )
                )
            )
            .border(0.75.dp, Color.White.copy(alpha = 0.16f), shape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = palette.primaryGlow.copy(alpha = 0.55f),
            modifier = Modifier.size(24.dp)
        )
        if (artworkUrl.isNotBlank()) {
            AsyncImage(
                model = artworkUrl,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun AnimatedWaveformBars(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    barColor: Color = LocalLiquidPalette.current.secondaryGlow
) {
    val transition = rememberInfiniteTransition(label = "waveformBars")
    val bar1 by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(420, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "b1"
    )
    val bar2 by transition.animateFloat(
        initialValue = 0.9f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(tween(340, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "b2"
    )
    val bar3 by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(510, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "b3"
    )
    val bar4 by transition.animateFloat(
        initialValue = 0.75f,
        targetValue = 0.28f,
        animationSpec = infiniteRepeatable(tween(390, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "b4"
    )

    val levels = if (isPlaying) listOf(bar1, bar2, bar3, bar4) else listOf(0.28f, 0.45f, 0.32f, 0.22f)
    Row(
        modifier = modifier.height(16.dp),
        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        levels.forEach { lvl ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height((16f * lvl).dp)
                    .clip(CircleShape)
                    .background(barColor)
            )
        }
    }
}
