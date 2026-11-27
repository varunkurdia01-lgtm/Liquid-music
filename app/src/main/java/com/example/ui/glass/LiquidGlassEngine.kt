package com.example.ui.glass

import android.os.Build
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.example.domain.model.GlassQualityTier
import com.example.ui.theme.FrostWhite
import com.example.ui.theme.LocalGlassQuality
import com.example.ui.theme.LocalLiquidGlassConfig
import com.example.ui.theme.LocalLiquidPalette
import com.example.ui.theme.ObsidianBlack
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
import kotlinx.coroutines.delay
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
 * Apple iOS 26 dark-mode optical material palette tokens.
 */
object AppleGlassTokens {
    val NeutralDarkTint = Color(0xFF141822)
    val SmokedCrystalBase = Color(0xFF10141D)
    val SmokedCrystalInnerShadow = Color(0xFF05070C)
    val ElevatedDarkTint = Color(0xFF1D2230)
    val AppleSystemGreen = Color(0xFF34C759)
    val AppleSystemBlue = Color(0xFF0A84FF)
    val TrackOffCharcoal = Color(0xFF3A3C46)
    val SpecularRimTop = Color.White.copy(alpha = 0.34f)
    val SpecularRimMid = Color.White.copy(alpha = 0.07f)
    val SpecularRimBottom = Color.White.copy(alpha = 0.16f)

    /**
     * Guarantees dynamic album-art accent colors stay luminous and high-contrast
     * over smoked dark optical glass without washing out vibrant hues.
     */
    fun ensureVibrantOnDarkGlass(accent: Color): Color {
        val lum = accent.luminance()
        if (lum >= 0.36f) return accent
        val lift = ((0.38f - lum) * 1.45f).coerceIn(0f, 0.58f)
        return lerp(accent, Color.White, lift)
    }
}

/**
 * Animated Crystal Ice & Deep Indigo (or dynamic track Palette) ambient aurora backdrop over pure AMOLED black.
 */
@Composable
fun LiquidAuroraCanvas(
    modifier: Modifier = Modifier,
    intensity: Float = 1f,
    pureBlack: Boolean = true,
    animate: Boolean = false
) {
    val palette = LocalLiquidPalette.current
    val glassTier = LocalGlassQuality.current
    val animateDrift = animate && glassTier != GlassQualityTier.BATTERY_SAVER
    val baseBg = if (pureBlack) ObsidianBlack else Color(0xFF0A0F1E)

    if (!animateDrift) {
        val phase = 0.7f
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(baseBg)
                .drawWithCache {
                    val w = size.width
                    val h = size.height
                    if (w <= 0f || h <= 0f) {
                        onDrawBehind { }
                    } else {
                        val c1 = Offset(
                            x = w * (0.22f + 0.14f * cos(phase)),
                            y = h * (0.14f + 0.08f * sin(phase * 0.8f))
                        )
                        val r1 = w * 0.95f
                        val b1 = Brush.radialGradient(
                            colors = listOf(
                                palette.deepAurora.copy(alpha = 0.64f * intensity),
                                palette.deepAurora.copy(alpha = 0.22f * intensity),
                                Color.Transparent
                            ),
                            center = c1,
                            radius = r1
                        )
                        val c2 = Offset(
                            x = w * (0.82f + 0.12f * sin(phase * 0.9f)),
                            y = h * (0.28f + 0.10f * cos(phase * 0.7f))
                        )
                        val r2 = w * 0.82f
                        val b2 = Brush.radialGradient(
                            colors = listOf(
                                palette.secondaryGlow.copy(alpha = 0.36f * intensity),
                                palette.secondaryGlow.copy(alpha = 0.12f * intensity),
                                Color.Transparent
                            ),
                            center = c2,
                            radius = r2
                        )
                        val c3 = Offset(
                            x = w * (0.45f + 0.18f * cos(phase * 0.6f + 1.4f)),
                            y = h * (0.82f + 0.08f * sin(phase * 0.5f))
                        )
                        val r3 = w * 0.85f
                        val b3 = Brush.radialGradient(
                            colors = listOf(
                                palette.primaryGlow.copy(alpha = 0.28f * intensity),
                                palette.deepAurora.copy(alpha = 0.14f * intensity),
                                Color.Transparent
                            ),
                            center = c3,
                            radius = r3
                        )
                        onDrawBehind {
                            drawCircle(brush = b1, radius = r1, center = c1)
                            drawCircle(brush = b2, radius = r2, center = c2)
                            drawCircle(brush = b3, radius = r3, center = c3)
                        }
                    }
                }
        )
        return
    }

    val transition = rememberInfiniteTransition(label = "auroraMesh")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 16_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "auroraDrift"
    )

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(baseBg)
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
                    palette.deepAurora.copy(alpha = 0.64f * intensity),
                    palette.deepAurora.copy(alpha = 0.22f * intensity),
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
                    palette.secondaryGlow.copy(alpha = 0.36f * intensity),
                    palette.secondaryGlow.copy(alpha = 0.12f * intensity),
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
                    palette.primaryGlow.copy(alpha = 0.28f * intensity),
                    palette.deepAurora.copy(alpha = 0.14f * intensity),
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
 * Authentic Apple iOS 26 Optical Liquid Glass container powered by Kyant0/AndroidLiquidGlass
 * (`io.github.kyant0:backdrop`).
 *
 * Relies on physically-based GPU `vibrancy()`, crisp low-radius `blur()`, curved-edge `lens()`
 * with `depthEffect = true`, optional chromatic dispersion, and native Fresnel `Highlight.Default`
 * + ambient `Shadow.Default` — avoiding muddy opaque veils or heavy painted outlines.
 */
@Composable
fun LiquidGlassSurface(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    tintColor: Color = AppleGlassTokens.NeutralDarkTint,
    surfaceAlpha: Float = 0.32f,
    highlightAccent: Color? = null,
    useRefractionShell: Boolean = true,
    backdropOverride: Backdrop? = null,
    refractionBoost: Float = 1f,
    blurOverrideDp: Float? = null,
    smokedCrystalRim: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val glassTier = LocalGlassQuality.current
    val glassConfig = LocalLiquidGlassConfig.current
    val backdrop = backdropOverride ?: LocalLiquidBackdrop.current
    val shape: Shape = remember(cornerRadius) { RoundedCornerShape(cornerRadius) }

    val useRealBackdrop = useRefractionShell &&
        backdrop != null &&
        glassTier != GlassQualityTier.BATTERY_SAVER &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    if (useRealBackdrop && backdrop != null) {
        val enableLens = glassTier == GlassQualityTier.ULTRA_AGSL &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        val effectiveBlurDp = blurOverrideDp ?: glassConfig.blurRadiusDp
        val veilAlpha = (surfaceAlpha * 0.68f).coerceIn(0.04f, 0.44f)

        Box(
            modifier = modifier
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { shape },
                    effects = {
                        vibrancy()
                        if (effectiveBlurDp > 0.35f) {
                            blur(effectiveBlurDp.dp.toPx())
                        }
                        if (enableLens) {
                            lens(
                                refractionHeight = (glassConfig.refractionHeightDp * refractionBoost).dp.toPx(),
                                refractionAmount = (glassConfig.refractionAmountDp * refractionBoost).dp.toPx(),
                                depthEffect = true,
                                chromaticAberration = glassConfig.chromaticAberration || smokedCrystalRim
                            )
                        }
                    },
                    highlight = { Highlight.Default },
                    shadow = { Shadow.Default },
                    onDrawSurface = {
                        if (veilAlpha > 0.01f) {
                            drawRect(tintColor.copy(alpha = veilAlpha))
                        }
                        if (smokedCrystalRim) {
                            // Convex top crown highlight + subtle bottom internal glass thickness bevel
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        0.0f to Color.White.copy(alpha = 0.14f),
                                        0.26f to Color.White.copy(alpha = 0.02f),
                                        0.74f to Color.Transparent,
                                        1.0f to AppleGlassTokens.SmokedCrystalInnerShadow.copy(alpha = 0.30f)
                                    )
                                )
                            )
                            val rPx = cornerRadius.toPx().coerceAtMost(size.minDimension / 2f)
                            val strokePx = 0.95.dp.toPx()
                            drawRoundRect(
                                brush = Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        0.0f to Color.White.copy(alpha = 0.50f),
                                        0.38f to Color.White.copy(alpha = 0.10f),
                                        0.70f to Color.White.copy(alpha = 0.09f),
                                        1.0f to Color.White.copy(alpha = 0.32f)
                                    )
                                ),
                                cornerRadius = CornerRadius(rPx, rPx),
                                style = Stroke(width = strokePx)
                            )
                        } else {
                            // Whisper-soft top optical sheen matching Apple iOS 26 curved glass
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.08f),
                                        Color.Transparent,
                                        Color.White.copy(alpha = 0.02f)
                                    )
                                )
                            )
                        }
                    }
                ),
            content = content
        )
    } else {
        // Lightweight Apple iOS 26 dark frosted material for high-frequency scrolling lists & Battery Saver
        val subtleAccent = highlightAccent ?: Color.White
        val bgBrush = remember(tintColor, surfaceAlpha, smokedCrystalRim) {
            if (smokedCrystalRim) {
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to tintColor.copy(alpha = (surfaceAlpha + 0.48f).coerceIn(0.68f, 0.88f)),
                        0.72f to tintColor.copy(alpha = (surfaceAlpha + 0.42f).coerceIn(0.62f, 0.84f)),
                        1.0f to AppleGlassTokens.SmokedCrystalInnerShadow.copy(alpha = 0.90f)
                    )
                )
            } else {
                Brush.verticalGradient(
                    colors = listOf(
                        tintColor.copy(alpha = (surfaceAlpha + 0.38f).coerceIn(0.58f, 0.84f)),
                        tintColor.copy(alpha = (surfaceAlpha + 0.30f).coerceIn(0.50f, 0.78f))
                    )
                )
            }
        }
        val borderBrush = remember(subtleAccent, smokedCrystalRim) {
            if (smokedCrystalRim) {
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to Color.White.copy(alpha = 0.46f),
                        0.38f to subtleAccent.copy(alpha = 0.14f),
                        0.72f to Color.White.copy(alpha = 0.08f),
                        1.0f to Color.White.copy(alpha = 0.28f)
                    )
                )
            } else {
                Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.28f),
                        subtleAccent.copy(alpha = 0.12f),
                        Color.White.copy(alpha = 0.06f),
                        Color.White.copy(alpha = 0.16f)
                    )
                )
            }
        }
        Box(
            modifier = modifier
                .clip(shape)
                .background(brush = bgBrush)
                .drawWithCache {
                    val rPx = cornerRadius.toPx()
                    val sheen = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = if (smokedCrystalRim) 0.12f else 0.07f),
                            Color.Transparent
                        ),
                        startY = 0f,
                        endY = size.height * 0.38f
                    )
                    onDrawWithContent {
                        drawContent()
                        drawRoundRect(
                            brush = sheen,
                            cornerRadius = CornerRadius(rPx, rPx)
                        )
                    }
                }
                .border(
                    width = if (smokedCrystalRim) 0.9.dp else 0.65.dp,
                    brush = borderBrush,
                    shape = shape
                ),
            content = content
        )
    }
}

/**
 * Apple iOS 26 Liquid Glass Button supporting both:
 * - **Surface Liquid Button** (`accentGlow = false`, `tintColor = null`): crystal-clear / frosted optical glass pill
 * - **Tinted Liquid Button** (`accentGlow = true` or explicit `tintColor`): translucent vibrant tinted Apple glass
 *
 * Features real-time backdrop refraction, native Fresnel `Highlight.Default`, pointer-tracking radial
 * specular InteractiveHighlight bloom, and responsive high-stiffness spring compression.
 */
@Composable
fun LiquidGlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    accentGlow: Boolean = false,
    tintColor: Color? = null,
    enabled: Boolean = true,
    useLiveBackdrop: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val palette = LocalLiquidPalette.current
    val glassConfig = LocalLiquidGlassConfig.current
    val glassTier = LocalGlassQuality.current
    val backdrop = LocalLiquidBackdrop.current
    val currentOnClick by rememberUpdatedState(onClick)

    var isPressed by remember { mutableStateOf(false) }
    var touchOffset by remember { mutableStateOf(Offset.Unspecified) }
    var buttonSize by remember { mutableStateOf(IntSize.Zero) }

    val pressScale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.945f else 1f,
        animationSpec = spring(
            dampingRatio = (glassConfig.springDamping + 0.04f).coerceIn(0.55f, 0.85f),
            stiffness = (glassConfig.springStiffness * 1.35f).coerceAtLeast(520f)
        ),
        label = "liquidButtonScale"
    )
    val highlightAlpha by animateFloatAsState(
        targetValue = if (isPressed && enabled) 1f else 0f,
        animationSpec = tween(durationMillis = 160),
        label = "interactiveHighlightAlpha"
    )

    val shape = remember(cornerRadius) { RoundedCornerShape(cornerRadius) }
    val resolvedTint = tintColor ?: if (accentGlow) palette.primaryGlow else null
    val isTinted = resolvedTint != null

    val useRealBackdrop = useLiveBackdrop &&
        backdrop != null &&
        glassTier != GlassQualityTier.BATTERY_SAVER &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val gestureModifier = modifier
        .onSizeChanged { buttonSize = it }
        .pointerInput(enabled) {
            if (!enabled) return@pointerInput
            val touchSlop = viewConfiguration.touchSlop
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val startPos = down.position
                isPressed = true
                touchOffset = down.position
                var cancelled = false
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull() ?: break
                    touchOffset = change.position
                    if (change.isConsumed || (change.position - startPos).getDistance() > touchSlop) {
                        cancelled = true
                        isPressed = false
                    }
                    if (!change.pressed) break
                    val outOfBounds = change.position.x < -24f ||
                        change.position.y < -24f ||
                        change.position.x > size.width + 24f ||
                        change.position.y > size.height + 24f
                    if (outOfBounds) {
                        cancelled = true
                        isPressed = false
                    }
                }
                isPressed = false
                if (!cancelled) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    currentOnClick()
                }
            }
        }

    if (useRealBackdrop && backdrop != null) {
        val enableLens = glassTier == GlassQualityTier.ULTRA_AGSL &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

        Box(
            modifier = gestureModifier
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { shape },
                    layerBlock = {
                        scaleX = pressScale
                        scaleY = pressScale
                        alpha = if (enabled) 1f else 0.45f
                    },
                    effects = {
                        vibrancy()
                        blur(4.dp.toPx())
                        if (enableLens) {
                            lens(
                                refractionHeight = (glassConfig.refractionHeightDp * (if (isPressed) 0.95f else 0.72f)).dp.toPx(),
                                refractionAmount = (glassConfig.refractionAmountDp * (if (isPressed) 1.05f else 0.78f)).dp.toPx(),
                                depthEffect = true,
                                chromaticAberration = glassConfig.chromaticAberration && isPressed
                            )
                        }
                    },
                    highlight = { Highlight.Default },
                    shadow = { Shadow.Default },
                    onDrawSurface = {
                        if (isTinted && resolvedTint != null) {
                            // Translucent Apple Tinted Liquid Button (like iOS 26 blue/orange pills)
                            drawRect(resolvedTint.copy(alpha = 0.56f))
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.22f),
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.10f)
                                    )
                                )
                            )
                        } else {
                            // Clear Surface Liquid Button
                            drawRect(Color.White.copy(alpha = 0.11f + 0.06f * highlightAlpha))
                            drawRect(AppleGlassTokens.NeutralDarkTint.copy(alpha = 0.24f))
                        }
                    }
                )
        ) {
            InteractiveSpecularBloom(
                highlightAlpha = highlightAlpha,
                touchOffset = touchOffset,
                buttonSize = buttonSize
            )
            content()
        }
    } else {
        Box(
            modifier = gestureModifier
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                    alpha = if (enabled) 1f else 0.45f
                }
                .clip(shape)
                .background(
                    if (isTinted && resolvedTint != null) {
                        Brush.verticalGradient(
                            listOf(
                                resolvedTint.copy(alpha = 0.74f),
                                resolvedTint.copy(alpha = 0.54f)
                            )
                        )
                    } else {
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.15f),
                                AppleGlassTokens.NeutralDarkTint.copy(alpha = 0.76f)
                            )
                        )
                    }
                )
                .border(
                    width = 0.7.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = if (isTinted) 0.48f else 0.30f),
                            Color.White.copy(alpha = 0.08f),
                            Color.White.copy(alpha = 0.20f)
                        )
                    ),
                    shape = shape
                )
        ) {
            InteractiveSpecularBloom(
                highlightAlpha = highlightAlpha,
                touchOffset = touchOffset,
                buttonSize = buttonSize
            )
            content()
        }
    }
}

@Composable
private fun BoxScope.InteractiveSpecularBloom(
    highlightAlpha: Float,
    touchOffset: Offset,
    buttonSize: IntSize
) {
    if (highlightAlpha > 0.01f && touchOffset != Offset.Unspecified && buttonSize.width > 0) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val radius = maxOf(size.width, size.height) * 0.72f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.34f * highlightAlpha),
                        Color.White.copy(alpha = 0.12f * highlightAlpha),
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
}

/**
 * Apple iOS 26 Circular Control Center / Toolbar Liquid Glass Icon Button.
 * Supports clear optical glass or vibrant tinted glass with spring bounce and Fresnel rim.
 */
@Composable
fun LiquidGlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 20.dp,
    iconTint: Color = FrostWhite,
    tinted: Boolean = false,
    tintColor: Color? = null,
    useLiveBackdrop: Boolean = false
) {
    LiquidGlassButton(
        onClick = onClick,
        modifier = modifier.size(size),
        cornerRadius = size / 2f,
        accentGlow = tinted,
        tintColor = tintColor,
        useLiveBackdrop = useLiveBackdrop
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier
                .size(iconSize)
                .align(Alignment.Center)
        )
    }
}

/**
 * Authentic Apple iOS 26 / Kyant0 Liquid Glass Slider.
 *
 * - Captures the clean track rail into a local `rememberLayerBackdrop()`.
 * - At rest: sleek elongated pure-white iOS 26 capsule pill thumb with subtle drop shadow.
 * - While pressed/dragged: the thumb expands past the track (`1.32x`, unclipped!) and morphs into
 *   a **crystal-clear refracting optical glass lens** sampling both the scene backdrop and track
 *   so the glowing active slider bar visibly bends & magnifies right through the glass pill.
 */
@Composable
fun LiquidGlassSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChangeFinished: (() -> Unit)? = null,
    activeColor: Color = AppleGlassTokens.AppleSystemBlue
) {
    val glassConfig = LocalLiquidGlassConfig.current
    val glassTier = LocalGlassQuality.current
    val sceneBackdrop = LocalLiquidBackdrop.current
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnValueChangeFinished by rememberUpdatedState(onValueChangeFinished)
    val currentValueRange by rememberUpdatedState(valueRange)

    val trackBackdrop = rememberLayerBackdrop()
    val combinedThumbBackdrop = if (sceneBackdrop != null) {
        rememberCombinedBackdrop(sceneBackdrop, trackBackdrop)
    } else {
        trackBackdrop
    }

    var trackWidthPx by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    val rangeSpan = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0.0001f)
    val fraction = ((value - valueRange.start) / rangeSpan).coerceIn(0f, 1f)

    val thumbMorph by animateFloatAsState(
        targetValue = if (isDragging) 1f else 0f,
        animationSpec = spring(
            dampingRatio = (glassConfig.springDamping + 0.04f).coerceIn(0.58f, 0.85f),
            stiffness = (glassConfig.springStiffness * 1.15f).coerceAtLeast(480f)
        ),
        label = "sliderThumbMorph"
    )

    val thumbWidthDp = (38f * glassConfig.pillScale).coerceIn(32f, 44f).dp
    val thumbHeightDp = (22f * glassConfig.pillScale).coerceIn(19f, 26f).dp
    val thumbWidthPx = with(density) { thumbWidthDp.toPx() }

    val useLiveLens = glassTier != GlassQualityTier.BATTERY_SAVER &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val isLensActive = useLiveLens && (isDragging || thumbMorph > 0.01f)

    fun updateFromX(x: Float) {
        val range = currentValueRange
        val span = (range.endInclusive - range.start).coerceAtLeast(0.0001f)
        val usable = (trackWidthPx - thumbWidthPx).coerceAtLeast(1f)
        val norm = ((x - thumbWidthPx * 0.5f) / usable).coerceIn(0f, 1f)
        currentOnValueChange(range.start + norm * span)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(42.dp)
            .onSizeChanged { trackWidthPx = it.width.toFloat() }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    updateFromX(offset.x)
                    currentOnValueChangeFinished?.invoke()
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        updateFromX(offset.x)
                    },
                    onDragEnd = {
                        isDragging = false
                        currentOnValueChangeFinished?.invoke()
                    },
                    onDragCancel = {
                        isDragging = false
                        currentOnValueChangeFinished?.invoke()
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        updateFromX(change.position.x)
                    }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        // LAYER 1: Track Rail captured into trackBackdrop ONLY while thumb lens is active (zero idle GPU copies!)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .then(if (isLensActive) Modifier.layerBackdrop(trackBackdrop) else Modifier),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height((6f + 1.5f * thumbMorph).dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.20f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction.coerceAtLeast(0.008f))
                        .height((6f + 1.5f * thumbMorph).dp)
                        .clip(CircleShape)
                        .background(activeColor)
                )
            }
        }

        // LAYER 2 (ON TOP, UNCLIPPED): Apple iOS 26 White Pill -> Crystal-Clear Magnifying Glass Droplet
        val usablePx = (trackWidthPx - thumbWidthPx).coerceAtLeast(0f)
        val thumbOffsetX = usablePx * fraction

        if (isLensActive) {
            Box(
                modifier = Modifier
                    .width(thumbWidthDp)
                    .height(thumbHeightDp)
                    .drawBackdrop(
                        backdrop = combinedThumbBackdrop,
                        shape = { CircleShape },
                        layerBlock = {
                            translationX = thumbOffsetX
                            scaleX = 1f + 0.30f * thumbMorph
                            scaleY = 1f + 0.34f * thumbMorph
                        },
                        effects = {
                            vibrancy()
                            if (thumbMorph > 0.02f) {
                                lens(
                                    refractionHeight = (10f * thumbMorph).dp.toPx(),
                                    refractionAmount = (26f * thumbMorph).dp.toPx(),
                                    depthEffect = true,
                                    chromaticAberration = glassConfig.chromaticAberration
                                )
                            }
                        },
                        highlight = { Highlight.Default },
                        shadow = { Shadow.Default },
                        onDrawSurface = {
                            // Opaque crisp white pill at rest -> 100% crystal-clear optical glass while scrubbing
                            val whiteAlpha = (0.98f - 0.93f * thumbMorph).coerceIn(0.05f, 0.98f)
                            drawRect(Color.White.copy(alpha = whiteAlpha))
                        }
                    )
            )
        } else {
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        translationX = thumbOffsetX
                        scaleX = 1f + 0.24f * thumbMorph
                        scaleY = 1f + 0.26f * thumbMorph
                    }
                    .width(thumbWidthDp)
                    .height(thumbHeightDp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = (0.98f - 0.68f * thumbMorph).coerceIn(0.28f, 0.98f)))
                    .border(
                        width = 0.8.dp,
                        color = Color.White.copy(alpha = 0.92f),
                        shape = CircleShape
                    )
            )
        }
    }
}

/**
 * Authentic Apple iOS 26 / Kyant0 Liquid Glass Toggle Switch.
 *
 * - Captures the smooth Apple green / charcoal pill track via `rememberLayerBackdrop()` ONLY while active.
 * - Supports both tap AND horizontal drag scrubbing.
 * - At rest: crisp pure-white iOS 26 capsule thumb with zero offscreen GPU cost while scrolling.
 * - While pressed, dragged, or snapping across: expands beyond the track height (`1.28x`, unclipped!)
 *   and turns into a **crystal-clear magnifying liquid glass lens** that visibly refracts & bends the
 *   vibrant green track directly through the glass capsule with subtle chromatic prism edges.
 */
@Composable
fun LiquidGlassToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val glassConfig = LocalLiquidGlassConfig.current
    val glassTier = LocalGlassQuality.current
    val sceneBackdrop = LocalLiquidBackdrop.current
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val currentChecked by rememberUpdatedState(checked)
    val currentOnCheckedChange by rememberUpdatedState(onCheckedChange)

    val trackBackdrop = rememberLayerBackdrop()
    val combinedTrackBackdrop = if (sceneBackdrop != null) {
        rememberCombinedBackdrop(sceneBackdrop, trackBackdrop)
    } else {
        trackBackdrop
    }

    val trackWidth = (62f * glassConfig.pillScale).coerceIn(56f, 70f).dp
    val trackHeight = (32f * glassConfig.pillScale).coerceIn(28f, 36f).dp
    val knobWidth = (35f * glassConfig.pillScale).coerceIn(31f, 40f).dp
    val knobHeight = (24f * glassConfig.pillScale).coerceIn(21f, 28f).dp
    val insetPadding = 4.dp
    val travelPx = with(density) { (trackWidth - knobWidth - insetPadding * 2).toPx().coerceAtLeast(1f) }

    val progress = remember { Animatable(if (checked) 1f else 0f) }
    var isPressed by remember { mutableStateOf(false) }
    var isMotionPopped by remember { mutableStateOf(false) }

    LaunchedEffect(checked, glassConfig.springStiffness, glassConfig.springDamping) {
        if (!isPressed) {
            val target = if (checked) 1f else 0f
            if (abs(progress.value - target) > 0.02f) {
                isMotionPopped = true
                launch {
                    delay(210L)
                    isMotionPopped = false
                }
            }
            progress.animateTo(
                targetValue = target,
                animationSpec = spring(
                    dampingRatio = (glassConfig.springDamping + 0.04f).coerceIn(0.58f, 0.84f),
                    stiffness = (glassConfig.springStiffness * 1.15f).coerceAtLeast(480f)
                )
            )
        }
    }

    val glassMorph by animateFloatAsState(
        targetValue = if (isPressed || isMotionPopped) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.72f,
            stiffness = 620f
        ),
        label = "toggleGlassMorph"
    )

    val motionStretch = (abs(progress.velocity) / 14f).coerceIn(0f, 0.16f)
    val trackColor = lerp(
        start = AppleGlassTokens.TrackOffCharcoal.copy(alpha = 0.76f),
        stop = AppleGlassTokens.AppleSystemGreen,
        fraction = progress.value.coerceIn(0f, 1f)
    )
    val useLiveLens = glassTier != GlassQualityTier.BATTERY_SAVER &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val isLensActive = useLiveLens && (isPressed || isMotionPopped || glassMorph > 0.01f)

    // Outer unclipped container gives generous vertical room so the expanded droplet never clips
    Box(
        modifier = modifier
            .width(trackWidth + 8.dp)
            .height(trackHeight + 12.dp)
            .pointerInput(travelPx, glassConfig.springStiffness, glassConfig.springDamping) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    isPressed = true
                    var dragged = false
                    val startX = down.position.x
                    val startProgress = progress.value

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) {
                            isPressed = false
                            val activeChecked = currentChecked
                            val nextChecked = if (dragged) progress.value >= 0.5f else !activeChecked
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            if (nextChecked != activeChecked) {
                                currentOnCheckedChange(nextChecked)
                            } else {
                                scope.launch {
                                    progress.animateTo(
                                        targetValue = if (activeChecked) 1f else 0f,
                                        animationSpec = spring(
                                            dampingRatio = (glassConfig.springDamping + 0.04f).coerceIn(0.58f, 0.84f),
                                            stiffness = (glassConfig.springStiffness * 1.15f).coerceAtLeast(480f)
                                        )
                                    )
                                }
                            }
                            break
                        }

                        val dx = change.position.x - startX
                        if (abs(dx) > 6f) {
                            dragged = true
                            change.consume()
                            val nextVal = (startProgress + dx / travelPx).coerceIn(0f, 1f)
                            scope.launch { progress.snapTo(nextVal) }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // LAYER 1: Track captured into trackBackdrop ONLY while actively pressed/snapping
        Box(
            modifier = Modifier
                .width(trackWidth)
                .height(trackHeight)
                .then(if (isLensActive) Modifier.layerBackdrop(trackBackdrop) else Modifier)
                .clip(CircleShape)
                .background(trackColor)
        )

        // LAYER 2 (ON TOP, UNCLIPPED): White Apple Pill -> Crystal-Clear Refracting Droplet Lens
        val startOffsetPx = with(density) { (insetPadding + 4.dp).toPx() }
        val knobX = startOffsetPx + travelPx * progress.value

        if (isLensActive) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(knobWidth)
                    .height(knobHeight)
                    .drawBackdrop(
                        backdrop = combinedTrackBackdrop,
                        shape = { CircleShape },
                        layerBlock = {
                            translationX = knobX
                            scaleX = 1f + 0.28f * glassMorph + motionStretch
                            scaleY = 1f + 0.30f * glassMorph - motionStretch * 0.22f
                        },
                        effects = {
                            vibrancy()
                            if (glassMorph > 0.02f) {
                                lens(
                                    refractionHeight = (10f * glassMorph).dp.toPx(),
                                    refractionAmount = (24f * glassMorph).dp.toPx(),
                                    depthEffect = true,
                                    chromaticAberration = glassConfig.chromaticAberration
                                )
                            }
                        },
                        highlight = { Highlight.Default },
                        shadow = { Shadow.Default },
                        onDrawSurface = {
                            // Pure white at rest -> crystal-clear refracting droplet while active!
                            val whiteAlpha = (0.98f - 0.93f * glassMorph).coerceIn(0.05f, 0.98f)
                            drawRect(Color.White.copy(alpha = whiteAlpha))
                        }
                    )
            )
        } else {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .graphicsLayer {
                        translationX = knobX
                        scaleX = 1f + 0.20f * glassMorph + motionStretch
                        scaleY = 1f + 0.20f * glassMorph - motionStretch * 0.22f
                    }
                    .width(knobWidth)
                    .height(knobHeight)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = (0.98f - 0.65f * glassMorph).coerceIn(0.30f, 0.98f)))
                    .border(0.8.dp, Color.White.copy(alpha = 0.92f), CircleShape)
            )
        }
    }
}

/**
 * Apple iOS 26 Liquid Glass Filter Chip with responsive spring tap bounce and clean Fresnel rim.
 */
@Composable
fun LiquidGlassChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalLiquidPalette.current
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.93f else 1f,
        animationSpec = spring(dampingRatio = 0.68f, stiffness = 680f),
        label = "chipPressScale"
    )
    val bgColor by animateColorAsState(
        targetValue = if (selected) {
            palette.primaryGlow.copy(alpha = 0.28f)
        } else {
            Color.White.copy(alpha = 0.08f)
        },
        animationSpec = tween(200),
        label = "chipBg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) {
            Color.White.copy(alpha = 0.52f)
        } else {
            Color.White.copy(alpha = 0.16f)
        },
        animationSpec = tween(200),
        label = "chipBorder"
    )
    val borderBrush = remember(borderColor) {
        Brush.linearGradient(
            colors = listOf(
                borderColor,
                borderColor.copy(alpha = 0.25f),
                borderColor.copy(alpha = 0.55f)
            )
        )
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(bgColor)
            .drawWithCache {
                val sheen = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (selected) 0.18f else 0.06f),
                        Color.Transparent
                    ),
                    startY = 0f,
                    endY = size.height * 0.55f
                )
                onDrawWithContent {
                    drawContent()
                    drawRect(brush = sheen)
                }
            }
            .border(
                width = 0.7.dp,
                brush = borderBrush,
                shape = CircleShape
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) FrostWhite else Color(0xFFD0D9E8)
        )
    }
}

@Composable
fun TrackArtwork(
    artworkUrl: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 14.dp,
    contentScale: ContentScale = ContentScale.Crop
) {
    val palette = LocalLiquidPalette.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val shape = remember(cornerRadius) { RoundedCornerShape(cornerRadius) }
    val bgBrush = remember(palette.deepAurora) {
        Brush.linearGradient(
            listOf(
                palette.deepAurora.copy(alpha = 0.72f),
                AppleGlassTokens.NeutralDarkTint
            )
        )
    }
    BoxWithConstraints(
        modifier = modifier
            .clip(shape)
            .background(bgBrush)
            .border(0.65.dp, Color.White.copy(alpha = 0.18f), shape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = palette.primaryGlow.copy(alpha = 0.55f),
            modifier = Modifier.size(24.dp)
        )
        if (artworkUrl.isNotBlank()) {
            val targetPx = remember(maxWidth, maxHeight, density) {
                val maxDp = if (maxWidth.value.isFinite() && maxHeight.value.isFinite()) {
                    maxOf(maxWidth.value, maxHeight.value)
                } else {
                    180f
                }
                (maxDp * density.density * 1.25f).toInt().coerceIn(96, 1080)
            }
            val request = remember(artworkUrl, targetPx) {
                ImageRequest.Builder(context)
                    .data(artworkUrl)
                    .size(targetPx)
                    .precision(Precision.INEXACT)
                    .crossfade(150)
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = contentDescription,
                contentScale = contentScale,
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
    val bar1: Float
    val bar2: Float
    val bar3: Float
    val bar4: Float
    if (isPlaying) {
        val transition = rememberInfiniteTransition(label = "waveformBars")
        bar1 = transition.animateFloat(
            initialValue = 0.25f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(420, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "b1"
        ).value
        bar2 = transition.animateFloat(
            initialValue = 0.9f,
            targetValue = 0.2f,
            animationSpec = infiniteRepeatable(tween(340, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "b2"
        ).value
        bar3 = transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 0.95f,
            animationSpec = infiniteRepeatable(tween(510, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "b3"
        ).value
        bar4 = transition.animateFloat(
            initialValue = 0.75f,
            targetValue = 0.28f,
            animationSpec = infiniteRepeatable(tween(390, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "b4"
        ).value
    } else {
        bar1 = 0.28f
        bar2 = 0.45f
        bar3 = 0.32f
        bar4 = 0.22f
    }

    Canvas(
        modifier = modifier
            .width(19.5.dp)
            .height(16.dp)
    ) {
        val barW = 3.dp.toPx()
        val gap = 2.5.dp.toPx()
        val corner = CornerRadius(barW / 2f, barW / 2f)
        val h = size.height
        val levels = floatArrayOf(bar1, bar2, bar3, bar4)
        for (i in levels.indices) {
            val barH = (h * levels[i]).coerceAtLeast(barW)
            drawRoundRect(
                color = barColor,
                topLeft = Offset(x = i * (barW + gap), y = h - barH),
                size = Size(barW, barH),
                cornerRadius = corner
            )
        }
    }
}
