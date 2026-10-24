package com.abhishek.ame.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.abhishek.ame.ui.glass.backdrop.Backdrop
import com.abhishek.ame.ui.glass.backdrop.drawBackdrop
import com.abhishek.ame.ui.glass.backdrop.effects.blur
import com.abhishek.ame.ui.theme.pageGround

enum class ProgressiveBlurPosition {
    Top,
    Bottom,
}

/**
 * Jetpack Compose implementation of Magic UI's `@magicui/progressive-blur`.
 *
 * Replicates stepped backdrop-filter layers masked by progressive gradient stops:
 * - Position [ProgressiveBlurPosition.Top]: Top edge is deeply blurred and solid ground,
 *   progressively dissolving downwards into transparency.
 * - Multi-tier blur levels (32px, 16px, 6px) stacked with offset gradient masks.
 * - Deep progressive scrim ensures text / chips in the top navbar remain completely
 *   legible without underlying scroll content bleeding through.
 */
@Composable
fun ProgressiveBlur(
    height: Dp,
    modifier: Modifier = Modifier,
    position: ProgressiveBlurPosition = ProgressiveBlurPosition.Top,
    backdrop: Backdrop? = null,
    tintColor: Color = pageGround(),
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
    ) {
        if (backdrop != null) {
            // Level 1: Deep blur (32px) for the upper core
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { RectangleShape },
                        effects = { blur(32f) },
                        highlight = null,
                        shadow = null,
                    )
                    .drawWithContent {
                        drawContent()
                        val mask = when (position) {
                            ProgressiveBlurPosition.Top -> Brush.verticalGradient(
                                0.0f to Color.Black,
                                0.55f to Color.Black,
                                0.78f to Color.Black.copy(alpha = 0.5f),
                                1.0f to Color.Transparent,
                            )
                            ProgressiveBlurPosition.Bottom -> Brush.verticalGradient(
                                0.0f to Color.Transparent,
                                0.22f to Color.Black.copy(alpha = 0.5f),
                                0.45f to Color.Black,
                                1.0f to Color.Black,
                            )
                        }
                        drawRect(brush = mask, blendMode = BlendMode.DstIn)
                    },
            )

            // Level 2: Mid blur (16px) bridging mid to lower transition
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { RectangleShape },
                        effects = { blur(16f) },
                        highlight = null,
                        shadow = null,
                    )
                    .drawWithContent {
                        drawContent()
                        val mask = when (position) {
                            ProgressiveBlurPosition.Top -> Brush.verticalGradient(
                                0.0f to Color.Black.copy(alpha = 0.9f),
                                0.65f to Color.Black.copy(alpha = 0.7f),
                                0.88f to Color.Black.copy(alpha = 0.25f),
                                1.0f to Color.Transparent,
                            )
                            ProgressiveBlurPosition.Bottom -> Brush.verticalGradient(
                                0.0f to Color.Transparent,
                                0.12f to Color.Black.copy(alpha = 0.25f),
                                0.35f to Color.Black.copy(alpha = 0.7f),
                                1.0f to Color.Black.copy(alpha = 0.9f),
                            )
                        }
                        drawRect(brush = mask, blendMode = BlendMode.DstIn)
                    },
            )

            // Level 3: Soft blur (6px) feathering the outer edge
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { RectangleShape },
                        effects = { blur(6f) },
                        highlight = null,
                        shadow = null,
                    )
                    .drawWithContent {
                        drawContent()
                        val mask = when (position) {
                            ProgressiveBlurPosition.Top -> Brush.verticalGradient(
                                0.0f to Color.Transparent,
                                0.50f to Color.Black.copy(alpha = 0.3f),
                                0.82f to Color.Black.copy(alpha = 0.6f),
                                1.0f to Color.Transparent,
                            )
                            ProgressiveBlurPosition.Bottom -> Brush.verticalGradient(
                                0.0f to Color.Transparent,
                                0.18f to Color.Black.copy(alpha = 0.6f),
                                0.50f to Color.Black.copy(alpha = 0.3f),
                                1.0f to Color.Transparent,
                            )
                        }
                        drawRect(brush = mask, blendMode = BlendMode.DstIn)
                    },
            )
        }

        // Deep progressive dark scrim:
        // Top 55% is solid to protect header readability, then progressively dissolves downward
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = when (position) {
                        ProgressiveBlurPosition.Top -> Brush.verticalGradient(
                            0.0f to tintColor,
                            0.55f to tintColor,
                            0.75f to tintColor.copy(alpha = 0.85f),
                            0.90f to tintColor.copy(alpha = 0.35f),
                            1.0f to Color.Transparent,
                        )
                        ProgressiveBlurPosition.Bottom -> Brush.verticalGradient(
                            0.0f to Color.Transparent,
                            0.10f to tintColor.copy(alpha = 0.35f),
                            0.25f to tintColor.copy(alpha = 0.85f),
                            0.45f to tintColor,
                            1.0f to tintColor,
                        )
                    },
                ),
        )
    }
}
