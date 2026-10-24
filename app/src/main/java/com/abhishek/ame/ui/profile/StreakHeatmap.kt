package com.abhishek.ame.ui.profile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abhishek.ame.R
import com.abhishek.ame.ui.theme.Ink
import com.abhishek.ame.ui.theme.InkDim
import java.time.LocalDate

/**
 * Spotify & GitHub-style listening activity heatmap.
 * Renders a hardware-accelerated Canvas with larger, clear activity cells
 * and smooth gradient edge fades on the left and right scrolling horizons.
 */
@Composable
fun StreakHeatmap(
    dailyListening: Map<LocalDate, Int>,
    accentColor: Color,
    modifier: Modifier = Modifier,
    today: LocalDate = remember { LocalDate.now() },
    cellSize: Dp = 15.dp,
    cellSpacing: Dp = 3.5.dp,
) {
    val density = LocalDensity.current
    val cellPx = with(density) { cellSize.toPx() }
    val spacingPx = with(density) { cellSpacing.toPx() }
    val stepPx = cellPx + spacingPx
    val cornerRadiusPx = with(density) { 3.dp.toPx() }

    val weeksCount = 52
    val totalWidthDp = (cellSize + cellSpacing) * weeksCount + 36.dp
    val totalHeightDp = (cellSize + cellSpacing) * 7 + 10.dp

    val scrollState = rememberScrollState()

    // Automatically scroll to the latest week on first layout
    LaunchedEffect(Unit) {
        scrollState.scrollTo(scrollState.maxValue)
    }

    val cardBg = Color.White.copy(alpha = 0.05f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(cardBg)
            .border(0.75.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
            .padding(16.dp),
    ) {
        // Heatmap Section Header & Legend
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.streak_heatmap_title).uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                ),
                color = InkDim,
            )

            // Legend
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = "Less",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = InkDim,
                    modifier = Modifier.padding(end = 2.dp),
                )
                Box(Modifier.size(9.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.08f)))
                Box(Modifier.size(9.dp).clip(RoundedCornerShape(2.dp)).background(accentColor.copy(alpha = 0.35f)))
                Box(Modifier.size(9.dp).clip(RoundedCornerShape(2.dp)).background(accentColor.copy(alpha = 0.65f)))
                Box(Modifier.size(9.dp).clip(RoundedCornerShape(2.dp)).background(accentColor))
                Text(
                    text = "More",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = InkDim,
                    modifier = Modifier.padding(start = 2.dp),
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // Scrollable Heatmap Grid Container with Left & Right Gradient Horizon Fades
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(totalHeightDp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState),
            ) {
                Canvas(
                    modifier = Modifier
                        .width(totalWidthDp)
                        .height(totalHeightDp)
                        .padding(vertical = 4.dp, horizontal = 12.dp),
                ) {
                    // Calculate date for the top-left-most cell (51 weeks ago, Sunday)
                    val currentDayOfWeek = today.dayOfWeek.value % 7 // Sunday = 0
                    val startDate = today.minusWeeks(51).minusDays(currentDayOfWeek.toLong())

                    var walkDate = startDate
                    for (col in 0 until weeksCount) {
                        for (row in 0..6) {
                            if (!walkDate.isAfter(today)) {
                                val count = dailyListening[walkDate] ?: 0
                                val cellColor = when {
                                    count == 0 -> Color.White.copy(alpha = 0.08f)
                                    count in 1..2 -> accentColor.copy(alpha = 0.35f)
                                    count in 3..6 -> accentColor.copy(alpha = 0.65f)
                                    else -> accentColor
                                }

                                val x = col * stepPx
                                val y = row * stepPx

                                drawRoundRect(
                                    color = cellColor,
                                    topLeft = Offset(x, y),
                                    size = Size(cellPx, cellPx),
                                    cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                                )
                            }
                            walkDate = walkDate.plusDays(1)
                        }
                    }
                }
            }

            // Left Side Horizon Fade
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(24.dp)
                    .fillMaxHeight()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFF161618),
                                Color.Transparent,
                            ),
                        ),
                    ),
            )

            // Right Side Horizon Fade
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(24.dp)
                    .fillMaxHeight()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0xFF161618),
                            ),
                        ),
                    ),
            )
        }
    }
}
