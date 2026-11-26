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
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.domain.model.PlayerSliderStyle
import com.example.ui.theme.FrostWhite
import com.example.ui.theme.LocalLiquidPalette
import com.example.ui.theme.LuminousRose
import com.example.ui.theme.MistSecondary
import kotlin.math.PI
import kotlin.math.sin

/**
 * Echo Music Material 3 tonal surface container:
 * Crisp 120Hz dark surface (zero nested blur overhead on scrollable feeds/settings cards),
 * leaving optical Liquid Glass exclusively for interactive buttons, floating dock, and sliders.
 */
@Composable
fun EchoSurfaceCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    elevated: Boolean = false,
    accentBorder: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val bg = if (elevated) Color(0xFF161C2C) else Color(0xFF0F1422)
    val borderColor = accentBorder ?: Color.White.copy(alpha = if (elevated) 0.11f else 0.07f)

    val clickModifier = if (onClick != null) {
        Modifier.bounceClick(onClick = onClick)
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .then(clickModifier)
            .clip(shape)
            .background(bg)
            .border(1.dp, borderColor, shape),
        content = content
    )
}

/**
 * Spring-physics tactile bounce modifier for every interactive tap across the app.
 */
fun Modifier.bounceClick(
    scaleDown: Float = 0.96f,
    enableHaptics: Boolean = false,
    onClick: () -> Unit
): Modifier = composed {
    var pressed by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val scale by animateFloatAsState(
        targetValue = if (pressed) scaleDown else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "bounceClickScale"
    )
    this
        .scale(scale)
        .pointerInput(Unit) {
            while (true) {
                awaitPointerEventScope {
                    awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    waitForUpOrCancellation()
                    pressed = false
                }
            }
        }
        .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null
        ) {
            if (enableHaptics) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            onClick()
        }
}

/**
 * Animated Heart-Burst Like Button with spring bounce and luminous rose pulse.
 */
@Composable
fun AnimatedLikeButton(
    isLiked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
    containerColor: Color = Color(0x33161D30)
) {
    val haptics = LocalHapticFeedback.current
    val scale by animateFloatAsState(
        targetValue = if (isLiked) 1.14f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "likeHeartScale"
    )
    val tint by animateColorAsState(
        targetValue = if (isLiked) LuminousRose else FrostWhite,
        animationSpec = tween(220),
        label = "likeHeartTint"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isLiked) LuminousRose.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.14f),
        animationSpec = tween(220),
        label = "likeHeartBorder"
    )

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (isLiked) LuminousRose.copy(alpha = 0.18f) else containerColor)
            .border(1.dp, borderColor, CircleShape)
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
 * Echo Music Top-Bar Home / Flow mode pill switcher.
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
            .background(Color(0xFF13192B))
            .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
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
        targetValue = if (selected) accent.copy(alpha = 0.22f) else Color.Transparent,
        animationSpec = tween(220),
        label = "modePillBg"
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) FrostWhite else MistSecondary,
        animationSpec = tween(200),
        label = "modePillText"
    )
    val padH by animateDpAsState(
        targetValue = if (selected) 13.dp else 10.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "modePillPad"
    )

    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(bg)
            .border(
                width = 1.dp,
                color = if (selected) accent.copy(alpha = 0.55f) else Color.Transparent,
                shape = CircleShape
            )
            .bounceClick(scaleDown = 0.94f, onClick = onClick)
            .padding(horizontal = padH, vertical = 6.dp)
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
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = textColor
        )
    }
}

/**
 * Echo Music animated Now Playing seekbar supporting SQUIGGLY (live animated sine wave),
 * WAVY (gentle wave), and SLIM (minimal Material 3 bar) styles with smooth drag scrubbing.
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

    val displayProgress = (if (isDragging) dragFraction else progress).coerceIn(0f, 1f)

    val infiniteTransition = rememberInfiniteTransition(label = "squigglySeekWave")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )

    val waveAmpFactor by animateFloatAsState(
        targetValue = if (isPlaying && !isDragging && sliderStyle != PlayerSliderStyle.SLIM) 1f else 0f,
        animationSpec = tween(320),
        label = "waveAmp"
    )

    val thumbRadiusFactor by animateFloatAsState(
        targetValue = if (isDragging) 1.35f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "thumbScale"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(34.dp)
            .onSizeChanged { widthPx = it.width.coerceAtLeast(1).toFloat() }
            .pointerInput(onSeekProgress) {
                detectTapGestures { offset ->
                    val frac = (offset.x / widthPx).coerceIn(0f, 1f)
                    onSeekProgress(frac)
                }
            }
            .pointerInput(onSeekProgress) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        dragFraction = (offset.x / widthPx).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        isDragging = false
                        onSeekProgress(dragFraction)
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
                .height(24.dp)
        ) {
            val centerY = size.height / 2f
            val totalW = size.width
            val activeX = totalW * displayProgress
            val trackStroke = if (sliderStyle == PlayerSliderStyle.SLIM) 3.dp.toPx() else 4.dp.toPx()

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
                    val path = Path()
                    val step = 4f
                    var x = 0f
                    while (x <= activeX) {
                        val y = centerY + amplitude * sin((x / wavelength) * 2.0 * PI + wavePhase).toFloat()
                        if (x == 0f) path.moveTo(x, y) else path.lineTo(x, y)
                        x += step
                    }
                    drawPath(
                        path = path,
                        color = activeColor,
                        style = Stroke(width = trackStroke, cap = StrokeCap.Round)
                    )
                }
            }

            // Glowing Liquid Glass pill thumb
            val thumbR = (if (sliderStyle == PlayerSliderStyle.SLIM) 5.5.dp.toPx() else 7.dp.toPx()) * thumbRadiusFactor
            drawCircle(
                color = activeColor.copy(alpha = 0.32f),
                radius = thumbR * 1.85f,
                center = Offset(activeX, centerY)
            )
            drawCircle(
                color = FrostWhite,
                radius = thumbR,
                center = Offset(activeX, centerY)
            )
        }
    }
}
