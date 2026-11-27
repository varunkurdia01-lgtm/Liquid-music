package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.domain.model.PlayerSliderStyle
import com.example.ui.glass.AppleGlassTokens
import com.example.ui.theme.FrostWhite
import com.example.ui.theme.LocalLiquidPalette
import com.example.ui.theme.LuminousRose
import com.example.ui.theme.MistSecondary
import kotlin.math.PI
import kotlin.math.sin

/**
 * Apple iOS 26 Dark Material Glass container:
 * Crisp 120Hz neutral smoked-glass translucency with physical top-sheen and diagonal Fresnel specular
 * hairline rim, keeping scrollable feeds & settings silky smooth while matching the floating optical dock.
 */
@Composable
fun EchoSurfaceCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 22.dp,
    elevated: Boolean = false,
    accentBorder: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = remember(cornerRadius) { RoundedCornerShape(cornerRadius) }
    val bgBrush = remember(elevated) {
        val topBg = if (elevated) {
            AppleGlassTokens.ElevatedDarkTint.copy(alpha = 0.80f)
        } else {
            AppleGlassTokens.NeutralDarkTint.copy(alpha = 0.72f)
        }
        val bottomBg = if (elevated) {
            AppleGlassTokens.NeutralDarkTint.copy(alpha = 0.78f)
        } else {
            Color(0xFF0E121B).copy(alpha = 0.68f)
        }
        Brush.verticalGradient(listOf(topBg, bottomBg))
    }
    val borderBrush = remember(elevated, accentBorder) {
        if (accentBorder != null) {
            Brush.linearGradient(
                listOf(
                    accentBorder.copy(alpha = 0.68f),
                    accentBorder.copy(alpha = 0.22f),
                    Color.White.copy(alpha = 0.18f)
                )
            )
        } else {
            Brush.linearGradient(
                listOf(
                    Color.White.copy(alpha = if (elevated) 0.30f else 0.24f),
                    Color.White.copy(alpha = 0.05f),
                    Color.White.copy(alpha = if (elevated) 0.16f else 0.12f)
                )
            )
        }
    }

    val clickModifier = if (onClick != null) {
        Modifier.bounceClick(onClick = onClick)
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .then(clickModifier)
            .clip(shape)
            .background(bgBrush)
            .drawWithCache {
                val rPx = cornerRadius.toPx()
                val sheen = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (elevated) 0.08f else 0.055f),
                        Color.Transparent
                    ),
                    startY = 0f,
                    endY = size.height * 0.36f
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
                width = 0.7.dp,
                brush = borderBrush,
                shape = shape
            ),
        content = content
    )
}

/**
 * High-stiffness iOS 26 spring-physics tactile bounce modifier for interactive taps across the app.
 * Uses MutableInteractionSource press state without a competing pointerInput coroutine so LazyColumn
 * and LazyRow scrolling stay zero-jank.
 */
fun Modifier.bounceClick(
    scaleDown: Float = 0.955f,
    enableHaptics: Boolean = false,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val haptics = LocalHapticFeedback.current
    val scale by animateFloatAsState(
        targetValue = if (pressed) scaleDown else 1f,
        animationSpec = spring(
            dampingRatio = 0.68f,
            stiffness = 660f
        ),
        label = "bounceClickScale"
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null
        ) {
            if (enableHaptics) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            onClick()
        }
}

/**
 * Apple iOS 26 Circular Glass Heart-Burst Like Button with spring bounce and luminous rose tint.
 */
@Composable
fun AnimatedLikeButton(
    isLiked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
    containerColor: Color = Color.White.copy(alpha = 0.09f)
) {
    val haptics = LocalHapticFeedback.current
    val scale by animateFloatAsState(
        targetValue = if (isLiked) 1.14f else 1.0f,
        animationSpec = spring(
            dampingRatio = 0.56f,
            stiffness = 620f
        ),
        label = "likeHeartScale"
    )
    val tint by animateColorAsState(
        targetValue = if (isLiked) LuminousRose else FrostWhite,
        animationSpec = tween(220),
        label = "likeHeartTint"
    )
    val rimTop by animateColorAsState(
        targetValue = if (isLiked) LuminousRose.copy(alpha = 0.70f) else Color.White.copy(alpha = 0.32f),
        animationSpec = tween(220),
        label = "likeHeartBorder"
    )

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (isLiked) LuminousRose.copy(alpha = 0.22f) else containerColor)
            .border(
                width = 0.75.dp,
                brush = Brush.linearGradient(
                    listOf(rimTop, Color.White.copy(alpha = 0.08f), rimTop.copy(alpha = 0.45f))
                ),
                shape = CircleShape
            )
            .bounceClick(scaleDown = 0.88f) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            contentDescription = if (isLiked) "Remove from Liked Songs" else "Like song",
            tint = tint,
            modifier = Modifier
                .size(iconSize)
                .scale(scale)
        )
    }
}

/**
 * Apple iOS 26 Liquid Glass Top-Bar Home / Flow segmented pill switcher.
 */
@Composable
fun EchoHomeModePill(
    isFlowActive: Boolean,
    onSelectHome: () -> Unit,
    onSelectFlow: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalLiquidPalette.current
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(AppleGlassTokens.NeutralDarkTint.copy(alpha = 0.72f))
            .border(
                width = 0.7.dp,
                brush = Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.30f),
                        Color.White.copy(alpha = 0.07f),
                        Color.White.copy(alpha = 0.18f)
                    )
                ),
                shape = CircleShape
            )
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ModePillSegment(
            label = "Home",
            icon = Icons.Rounded.Home,
            selected = !isFlowActive,
            accent = palette.primaryGlow,
            onClick = onSelectHome,
            testTag = "mode_pill_home"
        )
        ModePillSegment(
            label = "Flow",
            icon = Icons.Rounded.AutoAwesome,
            selected = isFlowActive,
            accent = palette.secondaryGlow,
            onClick = onSelectFlow,
            testTag = "mode_pill_flow"
        )
    }
}

@Composable
private fun ModePillSegment(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
    testTag: String
) {
    val bg by animateColorAsState(
        targetValue = if (selected) Color.White.copy(alpha = 0.16f) else Color.Transparent,
        animationSpec = tween(220),
        label = "modePillBg"
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) FrostWhite else MistSecondary,
        animationSpec = tween(200),
        label = "modePillText"
    )
    val padH by animateDpAsState(
        targetValue = if (selected) 14.dp else 11.dp,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 560f),
        label = "modePillPad"
    )

    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(bg)
            .border(
                width = 0.65.dp,
                color = if (selected) Color.White.copy(alpha = 0.36f) else Color.Transparent,
                shape = CircleShape
            )
            .bounceClick(scaleDown = 0.94f, onClick = onClick)
            .padding(horizontal = padH, vertical = 6.5.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) accent else MistSecondary,
            modifier = Modifier.size(15.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = textColor
        )
    }
}

/**
 * Echo Music animated Now Playing seekbar supporting SQUIGGLY (live animated sine wave),
 * WAVY (gentle wave), and SLIM (minimal iOS 26 bar) styles with an expanding Apple capsule pill thumb.
 */
@Composable
fun EchoPlayerSeekBar(
    progress: Float,
    isPlaying: Boolean,
    sliderStyle: PlayerSliderStyle,
    onSeekProgress: (Float) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = LocalLiquidPalette.current.primaryGlow
) {
    var widthPx by remember { mutableFloatStateOf(1f) }
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(progress) }
    val currentOnSeekProgress by rememberUpdatedState(onSeekProgress)
    val wavePath = remember { Path() }

    val displayProgress = (if (isDragging) dragFraction else progress).coerceIn(0f, 1f)
    val shouldAnimateWave = isPlaying && !isDragging && sliderStyle != PlayerSliderStyle.SLIM

    val wavePhase = if (shouldAnimateWave) {
        val infiniteTransition = rememberInfiniteTransition(label = "squigglySeekWave")
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = (2.0 * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1500, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "wavePhase"
        ).value
    } else {
        0f
    }

    val waveAmpFactor by animateFloatAsState(
        targetValue = if (shouldAnimateWave) 1f else 0f,
        animationSpec = tween(320),
        label = "waveAmp"
    )

    val thumbMorph by animateFloatAsState(
        targetValue = if (isDragging) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.68f, stiffness = 580f),
        label = "thumbScale"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(38.dp)
            .onSizeChanged { widthPx = it.width.coerceAtLeast(1).toFloat() }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val frac = (offset.x / widthPx).coerceIn(0f, 1f)
                    currentOnSeekProgress(frac)
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        dragFraction = (offset.x / widthPx).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        isDragging = false
                        currentOnSeekProgress(dragFraction)
                    },
                    onDragCancel = {
                        isDragging = false
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        dragFraction = (change.position.x / widthPx).coerceIn(0f, 1f)
                    }
                )
            }
            .testTag("echo_player_seekbar"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
        ) {
            val centerY = size.height / 2f
            val totalW = size.width
            val activeX = totalW * displayProgress
            val trackStroke = (if (sliderStyle == PlayerSliderStyle.SLIM) 3.5.dp.toPx() else 4.5.dp.toPx()) +
                (1.5.dp.toPx() * thumbMorph)

            // Inactive track
            drawLine(
                color = Color.White.copy(alpha = 0.22f),
                start = Offset(activeX, centerY),
                end = Offset(totalW, centerY),
                strokeWidth = trackStroke,
                cap = StrokeCap.Round
            )

            // Active track (Squiggly / Wavy / Slim)
            if (activeX > 1f) {
                if (sliderStyle == PlayerSliderStyle.SLIM || waveAmpFactor <= 0.02f) {
                    drawLine(
                        color = activeColor,
                        start = Offset(0f, centerY),
                        end = Offset(activeX, centerY),
                        strokeWidth = trackStroke,
                        cap = StrokeCap.Round
                    )
                } else {
                    val amplitude = (if (sliderStyle == PlayerSliderStyle.SQUIGGLY) 4.2.dp.toPx() else 2.4.dp.toPx()) * waveAmpFactor
                    val wavelength = if (sliderStyle == PlayerSliderStyle.SQUIGGLY) 26.dp.toPx() else 44.dp.toPx()
                    wavePath.reset()
                    val step = 4f
                    var x = 0f
                    while (x <= activeX) {
                        val y = centerY + amplitude * sin((x / wavelength) * 2.0 * PI + wavePhase).toFloat()
                        if (x == 0f) wavePath.moveTo(x, y) else wavePath.lineTo(x, y)
                        x += step
                    }
                    drawPath(
                        path = wavePath,
                        color = activeColor,
                        style = Stroke(width = trackStroke, cap = StrokeCap.Round)
                    )
                }
            }

            // Apple iOS 26 elongated capsule pill thumb (morphs into translucent crystal glass while scrubbing!)
            val pillW = (18.dp.toPx() + 10.dp.toPx() * thumbMorph)
            val pillH = (12.dp.toPx() + 6.dp.toPx() * thumbMorph)
            val pillX = (activeX - pillW / 2f).coerceIn(0f, (totalW - pillW).coerceAtLeast(0f))
            val pillY = centerY - pillH / 2f
            val corner = CornerRadius(pillH / 2f, pillH / 2f)

            drawRoundRect(
                color = Color.White.copy(alpha = 0.96f - 0.72f * thumbMorph),
                topLeft = Offset(pillX, pillY),
                size = Size(pillW, pillH),
                cornerRadius = corner
            )
            drawRoundRect(
                color = Color.White.copy(alpha = 0.45f + 0.45f * thumbMorph),
                topLeft = Offset(pillX, pillY),
                size = Size(pillW, pillH),
                cornerRadius = corner,
                style = Stroke(width = 1.2.dp.toPx())
            )
        }
    }
}
