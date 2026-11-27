package com.example.ui.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.tanh

/**
 * Calibrated iOS 26-style high-stiffness spring presets for the floating mini-player capsule pill.
 * Uses responsive high stiffness (520–780) paired with crisp damping (0.68–0.80) so every touch,
 * swipe, track skip, and vertical lift feels immediate, taut, and physical without mushy wobble.
 */
object MiniPlayerSpringSpecs {
    val TactilePress: SpringSpec<Float> = spring(
        dampingRatio = 0.72f,
        stiffness = 740f
    )

    val HorizontalSnap: SpringSpec<Float> = spring(
        dampingRatio = 0.74f,
        stiffness = 620f
    )

    val VerticalLift: SpringSpec<Float> = spring(
        dampingRatio = 0.76f,
        stiffness = 680f
    )

    val TiltReturn: SpringSpec<Float> = spring(
        dampingRatio = 0.75f,
        stiffness = 640f
    )

    val TrackSwapKick: SpringSpec<Float> = spring(
        dampingRatio = 0.70f,
        stiffness = 650f
    )

    val ControlBounce: SpringSpec<Float> = spring(
        dampingRatio = 0.64f,
        stiffness = 780f
    )

    val SpecularFollow: SpringSpec<Float> = spring(
        dampingRatio = 0.80f,
        stiffness = 560f
    )

    val PillEntranceOffset: SpringSpec<IntOffset> = spring(
        dampingRatio = 0.78f,
        stiffness = 560f
    )

    val PillEntranceScale: SpringSpec<Float> = spring(
        dampingRatio = 0.76f,
        stiffness = 580f
    )

    val SheetExpandOffset: SpringSpec<IntOffset> = spring(
        dampingRatio = 0.80f,
        stiffness = 520f
    )

    val SheetDismissOffset: SpringSpec<IntOffset> = spring(
        dampingRatio = 0.86f,
        stiffness = 600f
    )

    /** Primary spring driving the Liquid Glass pill-to-full-screen container layout morph on expand. */
    val PlayerMorphExpand: SpringSpec<Float> = spring(
        dampingRatio = 0.78f,
        stiffness = 430f
    )

    /** Crisp high-stiffness spring driving the full-screen player back into the mini-player pill. */
    val PlayerMorphCollapse: SpringSpec<Float> = spring(
        dampingRatio = 0.84f,
        stiffness = 500f
    )

    /** Spring for subtle iOS 26 background scene depth recess when full player expands. */
    val BackdropRecessScale: SpringSpec<Float> = spring(
        dampingRatio = 0.82f,
        stiffness = 440f
    )

    /** High-stiffness spring for ducking the floating bottom tab bar out of view on expansion. */
    val TabBarDuckOffset: SpringSpec<Float> = spring(
        dampingRatio = 0.80f,
        stiffness = 540f
    )

    /** Staggered spring choreography for secondary Now Playing chrome unfolding. */
    val ElementRevealSpring: SpringSpec<Float> = spring(
        dampingRatio = 0.78f,
        stiffness = 500f
    )

    /** Spring for smooth dynamic layout size changes inside the player glass dock. */
    val LayoutSizeSpring: SpringSpec<IntSize> = spring(
        dampingRatio = 0.76f,
        stiffness = 520f
    )

    /** Elastic spring-back when an interactive pull-down dismiss gesture is cancelled. */
    val DismissSnapBack: SpringSpec<Float> = spring(
        dampingRatio = 0.74f,
        stiffness = 560f
    )
}

/**
 * Spring-driven physics controller for [com.example.ui.screens.FloatingLiquidMiniPlayer].
 * Coordinates multi-axis rubber-band drag, velocity-preserving spring returns, tactile 3D tilt,
 * velocity-coupled liquid squash/stretch, specular rim tracking, and track-change micro-kicks.
 */
@Stable
class MiniPlayerPillPhysicsState(
    private val scope: CoroutineScope
) {
    val offsetX = Animatable(0f)
    val offsetY = Animatable(0f)
    val pressScale = Animatable(1f)
    val tiltY = Animatable(0f)
    val kineticKickX = Animatable(0f)
    val touchGlow = Animatable(0f)
    val specularFraction = Animatable(0.32f)

    var isPressed by mutableStateOf(false)
        private set
    var pillWidthPx by mutableFloatStateOf(0f)
    var lastSkipDirection by mutableFloatStateOf(0f)
        private set

    /** Normalized horizontal drag progress in [-1f, 1f] relative to the skip trigger detent. */
    val horizontalProgress: Float
        get() = (offsetX.value / SKIP_DETENT_PX).coerceIn(-1f, 1f)

    /** Normalized upward pull progress in [0f, 1f] relative to the expand trigger detent. */
    val verticalLiftProgress: Float
        get() = (-offsetY.value / EXPAND_DETENT_PX).coerceIn(0f, 1f)

    fun onPressDown(pointerX: Float) {
        isPressed = true
        val normX = if (pillWidthPx > 1f) (pointerX / pillWidthPx).coerceIn(0f, 1f) else 0.5f
        val centered = (normX - 0.5f) * 2f // -1..1
        scope.launch {
            launch { pressScale.animateTo(0.964f, MiniPlayerSpringSpecs.TactilePress) }
            launch { touchGlow.animateTo(1f, MiniPlayerSpringSpecs.TactilePress) }
            launch { tiltY.animateTo(centered * 2.4f, MiniPlayerSpringSpecs.TactilePress) }
            launch { specularFraction.animateTo(normX, MiniPlayerSpringSpecs.SpecularFollow) }
        }
    }

    fun onHorizontalDragDelta(rawDeltaX: Float, pointerX: Float) {
        val current = offsetX.value
        val nextRaw = current + rawDeltaX
        // Smooth iOS rubber-band resistance beyond the skip detent
        val damped = rubberBand(nextRaw, limit = MAX_DRAG_X_PX)
        val normX = if (pillWidthPx > 1f) (pointerX / pillWidthPx).coerceIn(0f, 1f) else 0.5f
        scope.launch {
            offsetX.snapTo(damped)
            specularFraction.snapTo(normX)
            tiltY.snapTo(((damped / MAX_DRAG_X_PX) * 3.2f).coerceIn(-3.2f, 3.2f))
        }
    }

    fun onVerticalDragDelta(rawDeltaY: Float) {
        val nextRaw = offsetY.value + rawDeltaY
        // Allow crisp elastic upward pull and subtle downward resistance
        val damped = if (nextRaw < 0f) {
            -rubberBand(-nextRaw, limit = MAX_DRAG_UP_PX)
        } else {
            rubberBand(nextRaw, limit = MAX_DRAG_DOWN_PX)
        }
        scope.launch {
            offsetY.snapTo(damped)
        }
    }

    fun onRelease(
        velocityX: Float,
        velocityY: Float,
        shouldTriggerNext: Boolean,
        shouldTriggerPrev: Boolean
    ) {
        isPressed = false
        val clampedVelX = (velocityX * 0.22f).coerceIn(-2200f, 2200f)
        val clampedVelY = (velocityY * 0.22f).coerceIn(-2200f, 2200f)
        if (shouldTriggerNext) lastSkipDirection = -1f
        else if (shouldTriggerPrev) lastSkipDirection = 1f

        scope.launch {
            launch {
                offsetX.animateTo(
                    targetValue = 0f,
                    initialVelocity = clampedVelX,
                    animationSpec = MiniPlayerSpringSpecs.HorizontalSnap
                )
            }
            launch {
                offsetY.animateTo(
                    targetValue = 0f,
                    initialVelocity = clampedVelY,
                    animationSpec = MiniPlayerSpringSpecs.VerticalLift
                )
            }
            launch {
                pressScale.animateTo(1f, MiniPlayerSpringSpecs.TactilePress)
            }
            launch {
                tiltY.animateTo(0f, MiniPlayerSpringSpecs.TiltReturn)
            }
            launch {
                touchGlow.animateTo(0f, MiniPlayerSpringSpecs.SpecularFollow)
            }
            launch {
                specularFraction.animateTo(0.32f, MiniPlayerSpringSpecs.SpecularFollow)
            }
        }
    }

    /** Fires a crisp high-stiffness horizontal impulse whenever the active track changes. */
    fun pulseTrackChange(direction: Float = lastSkipDirection) {
        val dir = if (direction == 0f) -1f else direction.sign
        scope.launch {
            launch {
                kineticKickX.snapTo(dir * 16f)
                kineticKickX.animateTo(0f, MiniPlayerSpringSpecs.TrackSwapKick)
            }
            launch {
                pressScale.snapTo(0.978f)
                pressScale.animateTo(1f, MiniPlayerSpringSpecs.TactilePress)
            }
        }
        lastSkipDirection = 0f
    }

    /** Applies the combined high-stiffness spring translation, liquid stretch, press compression, and 3D tilt. */
    fun GraphicsLayerScope.applyPillPhysics() {
        val hFrac = abs(horizontalProgress)
        val vFrac = verticalLiftProgress

        // Subtle iOS 26 liquid squash/stretch coupled to drag displacement
        val stretchX = 1f + (0.036f * hFrac) - (0.018f * vFrac)
        val stretchY = 1f - (0.022f * hFrac) + (0.034f * vFrac)
        val scale = pressScale.value

        translationX = (offsetX.value * 0.42f) + kineticKickX.value
        translationY = offsetY.value * 0.55f
        scaleX = (scale * stretchX).coerceIn(0.92f, 1.06f)
        scaleY = (scale * stretchY).coerceIn(0.92f, 1.06f)
        rotationY = tiltY.value
        cameraDistance = 14f * density
    }

    companion object {
        const val SKIP_DETENT_PX = 104f
        const val EXPAND_DETENT_PX = 58f
        const val SKIP_VELOCITY_PX = 780f
        const val EXPAND_VELOCITY_PX = 680f
        private const val MAX_DRAG_X_PX = 172f
        private const val MAX_DRAG_UP_PX = 96f
        private const val MAX_DRAG_DOWN_PX = 24f

        fun rubberBand(value: Float, limit: Float): Float {
            if (limit <= 0f) return 0f
            return limit * tanh((value / limit).toDouble()).toFloat()
        }
    }
}

@Composable
fun rememberMiniPlayerPillPhysics(): MiniPlayerPillPhysicsState {
    val scope = rememberCoroutineScope()
    return remember(scope) { MiniPlayerPillPhysicsState(scope) }
}

/**
 * Unified pointer gesture engine for the mini-player pill:
 * - Immediate tactile press depression & specular rim tracking on touch down
 * - Axis-locked horizontal swipe with haptic detent crossing + velocity-preserving spring snap-back
 * - Axis-locked upward elastic lift to expand Now Playing (by distance OR fast upward flick)
 * - Clean tap detection that never swallows inner icon button clicks
 */
fun Modifier.miniPlayerPillGestures(
    key: Any,
    physics: MiniPlayerPillPhysicsState,
    haptic: HapticFeedback,
    onExpand: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit
): Modifier = pointerInput(key) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        if (down.isConsumed) return@awaitEachGesture

        physics.onPressDown(down.position.x)

        val velocityTracker = VelocityTracker()
        velocityTracker.addPosition(down.uptimeMillis, down.position)

        var totalDx = 0f
        var totalDy = 0f
        var lockedAxis = 0 // 0 = undecided, 1 = horizontal, 2 = vertical
        var passedHorizontalDetent = false
        var passedVerticalDetent = false
        var pointerConsumedByChild = false

        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break

            if (change.isConsumed && lockedAxis == 0) {
                pointerConsumedByChild = true
            }

            if (!change.pressed) {
                val velocity = velocityTracker.calculateVelocity()
                val vx = velocity.x
                val vy = velocity.y

                val triggerNext = lockedAxis == 1 && (
                    physics.offsetX.value <= -MiniPlayerPillPhysicsState.SKIP_DETENT_PX ||
                        (vx <= -MiniPlayerPillPhysicsState.SKIP_VELOCITY_PX && totalDx < -36f)
                    )
                val triggerPrev = lockedAxis == 1 && (
                    physics.offsetX.value >= MiniPlayerPillPhysicsState.SKIP_DETENT_PX ||
                        (vx >= MiniPlayerPillPhysicsState.SKIP_VELOCITY_PX && totalDx > 36f)
                    )
                val triggerExpand = lockedAxis == 2 && (
                    physics.offsetY.value <= -MiniPlayerPillPhysicsState.EXPAND_DETENT_PX ||
                        (vy <= -MiniPlayerPillPhysicsState.EXPAND_VELOCITY_PX && totalDy < -22f)
                    )
                val isCleanTap = lockedAxis == 0 && !pointerConsumedByChild &&
                    abs(totalDx) < 16f && abs(totalDy) < 16f

                physics.onRelease(
                    velocityX = vx,
                    velocityY = vy,
                    shouldTriggerNext = triggerNext,
                    shouldTriggerPrev = triggerPrev
                )

                when {
                    triggerNext -> {
                        if (!passedHorizontalDetent) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        onSkipNext()
                    }
                    triggerPrev -> {
                        if (!passedHorizontalDetent) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        onSkipPrevious()
                    }
                    triggerExpand -> {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onExpand()
                    }
                    isCleanTap -> {
                        onExpand()
                    }
                }
                break
            }

            velocityTracker.addPosition(change.uptimeMillis, change.position)
            val delta = change.positionChange()
            totalDx += delta.x
            totalDy += delta.y

            if (lockedAxis == 0 && (abs(totalDx) > 12f || abs(totalDy) > 12f)) {
                lockedAxis = if (abs(totalDx) >= abs(totalDy)) 1 else 2
            }

            when (lockedAxis) {
                1 -> {
                    change.consume()
                    physics.onHorizontalDragDelta(delta.x, change.position.x)
                    val crossed = abs(physics.offsetX.value) >= MiniPlayerPillPhysicsState.SKIP_DETENT_PX
                    if (crossed && !passedHorizontalDetent) {
                        passedHorizontalDetent = true
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    } else if (!crossed) {
                        passedHorizontalDetent = false
                    }
                }
                2 -> {
                    change.consume()
                    physics.onVerticalDragDelta(delta.y)
                    val crossed = physics.offsetY.value <= -MiniPlayerPillPhysicsState.EXPAND_DETENT_PX
                    if (crossed && !passedVerticalDetent) {
                        passedVerticalDetent = true
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    } else if (!crossed) {
                        passedVerticalDetent = false
                    }
                }
            }
        }
    }
}
