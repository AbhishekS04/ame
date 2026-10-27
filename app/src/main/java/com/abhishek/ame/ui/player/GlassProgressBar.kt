package com.abhishek.ame.ui.player

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
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
import kotlin.math.roundToInt

/**
 * The playback position progress bar with authentic Liquid Glass refraction scrub thumb.
 *
 * Normal playback displays a clean minimal capsule track without visual clutter.
 * When touched or dragged with a finger, a liquid glass orb thumb appears at the touch
 * position with spherical lens refraction, chromatic dispersion along the rim, and ambient
 * specular reflection, magnifying the underlying track and ambient backdrop.
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
) {
    var scrubbing by remember { mutableStateOf<Float?>(null) }
    var committedScrub by remember { mutableStateOf<Float?>(null) }

    val latestOnSeek by rememberUpdatedState(onSeek)
    val latestOnScrubbing by rememberUpdatedState(onScrubbing)

    // Clear committed scrub once engine position catches up or track changes
    LaunchedEffect(positionMs.value, durationMs) {
        if (committedScrub != null) {
            val targetMs = (committedScrub!! * durationMs).toLong()
            if (kotlin.math.abs(positionMs.value - targetMs) < 1200L || positionMs.value >= targetMs) {
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

    val resolvedBackdrop = backdrop ?: emptyBackdrop()
    val trackBackdrop = rememberLayerBackdrop()
    val combinedBackdrop = rememberCombinedBackdrop(
        resolvedBackdrop,
        rememberBackdrop(trackBackdrop) { drawBackdrop ->
            drawBackdrop()
        }
    )

    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val thumbSize = 34.dp

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
        }

        // Liquid glass orb thumb: rendered when touch down / dragging
        if (dragProgress > 0.005f) {
            val thumbSizePx = with(density) { thumbSize.toPx() }
            val visualProgress = if (isLtr) progress else (1f - progress)
            val thumbCenterX = (visualProgress * trackWidth).coerceIn(0f, trackWidth.toFloat())
            val thumbLeft = (thumbCenterX - thumbSizePx / 2f)
                .coerceIn(-thumbSizePx * 0.25f, trackWidth - thumbSizePx * 0.75f)

            Box(
                Modifier
                    .graphicsLayer {
                        translationX = thumbLeft
                        scaleX = 0.45f + 0.55f * dragProgress
                        scaleY = 0.45f + 0.55f * dragProgress
                        alpha = dragProgress.coerceIn(0f, 1f)
                    }
                    .drawBackdrop(
                        backdrop = combinedBackdrop,
                        shape = { CircleShape },
                        effects = {
                            lens(
                                refractionHeight = 15f.dp.toPx() * dragProgress,
                                refractionAmount = 18f.dp.toPx() * dragProgress,
                                depthEffect = true,
                                chromaticAberration = true,
                            )
                        },
                        highlight = {
                            Highlight.Ambient.copy(
                                width = 1.dp,
                                blurRadius = 0.8.dp,
                                alpha = dragProgress * 0.9f,
                            )
                        },
                        shadow = {
                            Shadow(
                                radius = 6.dp,
                                color = Color.Black.copy(alpha = 0.25f * dragProgress),
                            )
                        },
                        innerShadow = {
                            InnerShadow(
                                radius = 4.dp * dragProgress,
                                alpha = 0.5f * dragProgress,
                            )
                        },
                        onDrawSurface = {
                            // Subtle radial top-left light source reflection
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.28f * dragProgress),
                                        Color.White.copy(alpha = 0.08f * dragProgress),
                                        Color.Transparent,
                                    ),
                                    center = Offset(size.width * 0.35f, size.height * 0.3f),
                                    radius = size.width * 0.55f,
                                )
                            )
                            // Refracting sweep rim highlight
                            drawCircle(
                                brush = Brush.sweepGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.65f * dragProgress),
                                        Color.White.copy(alpha = 0.2f * dragProgress),
                                        Color.White.copy(alpha = 0.55f * dragProgress),
                                        Color.White.copy(alpha = 0.15f * dragProgress),
                                        Color.White.copy(alpha = 0.65f * dragProgress),
                                    ),
                                    center = Offset(size.width / 2f, size.height / 2f),
                                ),
                                radius = (size.width / 2f) - 0.75f,
                                style = Stroke(width = 1.2f),
                            )
                        }
                    )
                    .size(thumbSize)
            )
        }

        // Unified responsive gesture detector: captures first down immediately without slop delay
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
                        scrubTo(down.position.x)

                        try {
                            while (true) {
                                val change = awaitPointerEvent().changes
                                    .firstOrNull { it.id == down.id }
                                    ?: break
                                if (!change.pressed) break
                                if (change.positionChanged()) {
                                    change.consume()
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
                        }
                    }
                }
                .zIndex(2f),
        )
    }
}
