package com.abhishek.ame.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import com.abhishek.ame.ui.glass.backdrop.Backdrop
import com.abhishek.ame.ui.glass.backdrop.backdrops.emptyBackdrop
import com.abhishek.ame.ui.glass.backdrop.backdrops.layerBackdrop
import com.abhishek.ame.ui.glass.backdrop.backdrops.rememberBackdrop
import com.abhishek.ame.ui.glass.backdrop.backdrops.rememberCombinedBackdrop
import com.abhishek.ame.ui.glass.backdrop.backdrops.rememberLayerBackdrop
import com.abhishek.ame.ui.glass.backdrop.drawBackdrop
import com.abhishek.ame.ui.glass.backdrop.effects.lens
import com.abhishek.ame.ui.glass.backdrop.highlight.Highlight
import com.abhishek.ame.ui.glass.backdrop.shadow.InnerShadow
import com.abhishek.ame.ui.glass.backdrop.shadow.Shadow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The playback position progress bar with authentic Liquid Glass refraction scrub thumb.
 *
 * Normal playback displays a clean minimal capsule track without visual clutter (no resting thumb).
 * When touched or dragged with a finger, a liquid glass orb thumb appears at the touch
 * position with spherical lens refraction, chromatic dispersion along the rim, and ambient
 * specular reflection, magnifying the underlying track and ambient backdrop.
 * Fast scrubbing triggers fluid inertia squish-and-stretch with underdamped spring bounce.
 */
@Composable
fun GlassProgressBar(
    positionMs: State<Long>,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    accentColor: Color,
    trackColor: Color,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
    onScrubbing: ((Long?) -> Unit)? = null,
    isBuffering: Boolean = false,
) {
    var scrubbing by remember { mutableStateOf<Float?>(null) }
    var committedScrub by remember { mutableStateOf<Float?>(null) }

    val latestOnSeek by rememberUpdatedState(onSeek)
    val latestOnScrubbing by rememberUpdatedState(onScrubbing)

    // Clear committed scrub once engine position catches up or track changes
    LaunchedEffect(positionMs.value, durationMs) {
        if (committedScrub != null) {
            val targetMs = (committedScrub!! * durationMs).toLong()
            if (abs(positionMs.value - targetMs) < 1200L || positionMs.value >= targetMs) {
                committedScrub = null
            }
        }
    }

    val isDragging = scrubbing != null
    val progress = scrubbing ?: committedScrub ?: progressOf(positionMs.value, durationMs)

    // Liquid glass thumb orb animates in with a tactile spring on drag, and dissolves away on release
    val dragProgress by animateFloatAsState(
        targetValue = if (isDragging) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "liquidGlassOrbDrag",
    )

    // Fluid velocity tracking for the "giggly" squish & stretch inertia effect
    val velocityAnim = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()
    val velocityTracker = remember { VelocityTracker() }

    val resolvedBackdrop = backdrop ?: emptyBackdrop()
    val trackBackdrop = rememberLayerBackdrop()
    val combinedBackdrop = rememberCombinedBackdrop(
        resolvedBackdrop,
        rememberBackdrop(trackBackdrop) { drawBackdrop ->
            val scaleX = lerp(0.72f, 1f, dragProgress)
            val scaleY = lerp(0f, 1f, dragProgress)
            scale(scaleX, scaleY) {
                drawBackdrop()
            }
        }
    )

    // Dynamic loading shimmer alpha: fades in when buffering starts, dissolves cleanly when loaded
    val shimmerAlpha by animateFloatAsState(
        targetValue = if (isBuffering) 1f else 0f,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "bufferingShimmerAlpha",
    )

    // Thumb visibility: hidden while glowing loading shimmer is active; gracefully springs into view once loaded
    val thumbVisibility by animateFloatAsState(
        targetValue = if (isBuffering) 0f else 1f,
        animationSpec = if (isBuffering) {
            tween(durationMillis = 200, easing = FastOutSlowInEasing)
        } else {
            spring(dampingRatio = 0.72f, stiffness = 420f)
        },
        label = "bufferingThumbVisibility",
    )

    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val thumbSize = 28.dp

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(36.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        val trackWidth = constraints.maxWidth
        if (trackWidth <= 0) return@BoxWithConstraints

        // Track layer recorded for refraction sampling beneath the liquid glass orb
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .layerBackdrop(trackBackdrop),
            contentAlignment = Alignment.CenterStart,
        ) {
            // Inactive background track
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(50))
                    .background(trackColor)
            )
            // Active elapsed track
            Box(
                Modifier
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50))
                    .background(accentColor)
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val clampedProgress = progress.coerceIn(0f, 1f)
                        val w = (constraints.maxWidth * clampedProgress).roundToInt()
                        layout(w, placeable.height) { placeable.place(0, 0) }
                    }
            )

            // Dynamic loading shimmer pulse along the time bar during song loading/buffering
            if (shimmerAlpha > 0.005f) {
                val infiniteTransition = rememberInfiniteTransition(label = "timeBarBuffering")
                val shimmerPhase by infiniteTransition.animateFloat(
                    initialValue = -0.3f,
                    targetValue = 1.3f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1200, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart,
                    ),
                    label = "bufferingPulse",
                )
                val shimmerBrush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0f),
                        Color.White.copy(alpha = 0.50f * shimmerAlpha),
                        Color.White.copy(alpha = 0.90f * shimmerAlpha),
                        Color.White.copy(alpha = 0.50f * shimmerAlpha),
                        Color.White.copy(alpha = 0f),
                    ),
                    startX = (shimmerPhase - 0.25f) * trackWidth,
                    endX = (shimmerPhase + 0.25f) * trackWidth,
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(50))
                        .background(shimmerBrush)
                )
            }
        }

        // Unified Thumb: Sleek round white circle during resting playback, smoothly morphing into
        // the refined compact Liquid Glass lens orb (28dp) when touched/dragged, with bouncy fluid jiggle.
        val thumbSizePx = with(density) { thumbSize.toPx() }
        val visualProgress = if (isLtr) progress else (1f - progress)
        val thumbCenterX = (visualProgress * trackWidth).coerceIn(0f, trackWidth.toFloat())
        val thumbLeft = (thumbCenterX - thumbSizePx / 2f)
            .coerceIn(-thumbSizePx * 0.25f, trackWidth - thumbSizePx * 0.75f)

        // Fluid velocity deformation:
        // Calm & steady during gentle scrubbing; lively, volume-conserving squish & stretch on fast flick
        val v = velocityAnim.value
        val absV = abs(v)
        val stretchX = 1f + (absV * 0.55f).coerceAtMost(0.26f)
        val squishY = 1f / (1f + (absV * 0.40f).coerceAtMost(0.18f))
        val tiltDegrees = (v * 9f).coerceIn(-9f, 9f)

        Box(
            modifier = Modifier
                .graphicsLayer {
                    translationX = thumbLeft
                }
                .size(thumbSize),
            contentAlignment = Alignment.Center,
        ) {
            // 1. Resting state: pristine round white circle thumb (clean white, zero dark circle artifact)
            // Hidden during loading shimmer; animates back in once song has loaded
            val restingAlpha = ((1f - dragProgress * 2.2f).coerceIn(0f, 1f)) * thumbVisibility
            if (restingAlpha > 0.005f) {
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            alpha = restingAlpha
                            scaleX = (1f - dragProgress * 0.2f) * thumbVisibility
                            scaleY = (1f - dragProgress * 0.2f) * thumbVisibility
                        }
                        .size(13.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                )
            }

            // 2. Dragging state: authentic compact Liquid Glass lens orb with fluid refraction and caustics
            if (dragProgress > 0.005f) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            rotationZ = tiltDegrees
                            scaleX = (0.38f + 0.62f * dragProgress) * stretchX
                            scaleY = (0.38f + 0.62f * dragProgress) * squishY
                            alpha = dragProgress.coerceIn(0f, 1f)
                        }
                        .drawBackdrop(
                            backdrop = combinedBackdrop,
                            shape = { CircleShape },
                            effects = {
                                lens(
                                    refractionHeight = 12f.dp.toPx() * dragProgress,
                                    refractionAmount = 16f.dp.toPx() * dragProgress,
                                    depthEffect = true,
                                    chromaticAberration = true,
                                )
                            },
                            highlight = {
                                Highlight.Ambient.copy(
                                    width = 1.2.dp,
                                    blurRadius = 0.8.dp,
                                    alpha = dragProgress * 0.90f,
                                )
                            },
                            shadow = {
                                Shadow(
                                    radius = 8.dp,
                                    color = Color.Black.copy(alpha = 0.24f * dragProgress),
                                )
                            },
                            innerShadow = {
                                InnerShadow(
                                    radius = 5.dp * dragProgress,
                                    alpha = 0.48f * dragProgress,
                                )
                            },
                            onDrawSurface = {
                                // Radial top-left light source reflection
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = 0.35f * dragProgress),
                                            Color.White.copy(alpha = 0.10f * dragProgress),
                                            Color.Transparent,
                                        ),
                                        center = Offset(size.width * 0.35f, size.height * 0.30f),
                                        radius = size.width * 0.55f,
                                    )
                                )
                                // Refracting sweep rim caustics
                                drawCircle(
                                    brush = Brush.sweepGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = 0.72f * dragProgress),
                                            Color.White.copy(alpha = 0.18f * dragProgress),
                                            Color.White.copy(alpha = 0.62f * dragProgress),
                                            Color.White.copy(alpha = 0.16f * dragProgress),
                                            Color.White.copy(alpha = 0.72f * dragProgress),
                                        ),
                                        center = Offset(size.width / 2f, size.height / 2f),
                                    ),
                                    radius = (size.width / 2f) - 0.75f,
                                    style = Stroke(width = 1.3f),
                                )
                            }
                        )
                )
            }
        }

        // Unified responsive gesture detector with instant down capture and velocity tracking
        Box(
            Modifier
                .matchParentSize()
                .pointerInput(durationMs, trackWidth) {
                    fun scrubTo(x: Float) {
                        if (durationMs <= 0 || trackWidth <= 0) return
                        val raw = (x / trackWidth.toFloat()).coerceIn(0f, 1f)
                        val frac = if (isLtr) raw else (1f - raw)
                        scrubbing = frac
                        latestOnScrubbing?.invoke((frac * durationMs).toLong())
                    }

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        velocityTracker.resetTracking()
                        velocityTracker.addPosition(down.uptimeMillis, down.position)
                        coroutineScope.launch {
                            velocityAnim.snapTo(0f)
                        }
                        scrubTo(down.position.x)

                        try {
                            while (true) {
                                val change = awaitPointerEvent().changes
                                    .firstOrNull { it.id == down.id }
                                    ?: break
                                if (!change.pressed) break
                                if (change.positionChanged()) {
                                    change.consume()
                                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                                    val rawVx = velocityTracker.calculateVelocity().x
                                    // Deadzone: steady/gentle sliding (< 650 px/sec) remains standard and steady.
                                    // Intentional fast scrub or quick flick activates elastic fluid jiggle.
                                    val fastThreshold = 650f
                                    val excess = (abs(rawVx) - fastThreshold).coerceAtLeast(0f)
                                    val targetVx = if (excess > 0f) {
                                        val sign = kotlin.math.sign(rawVx)
                                        val intensity = (excess / 2200f).coerceIn(0f, 0.35f)
                                        sign * intensity
                                    } else {
                                        0f
                                    }
                                    coroutineScope.launch {
                                        velocityAnim.animateTo(
                                            targetValue = targetVx,
                                            animationSpec = spring(
                                                dampingRatio = 0.52f, // Bouncy fluid inertia
                                                stiffness = 380f,
                                            ),
                                        )
                                    }
                                    scrubTo(change.position.x)
                                }
                            }
                        } finally {
                            val finalScrub = scrubbing
                            if (finalScrub != null && durationMs > 0) {
                                committedScrub = finalScrub
                                latestOnSeek((finalScrub * durationMs).toLong())
                            }
                            scrubbing = null
                            latestOnScrubbing?.invoke(null)
                            coroutineScope.launch {
                                velocityAnim.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(
                                        dampingRatio = 0.58f, // Fluid settling bounce
                                        stiffness = 360f,
                                    ),
                                )
                            }
                        }
                    }
                }
                .zIndex(2f),
        )
    }
}
