package com.example.askvocate.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.example.askvocate.ui.theme.GoldLight
import com.example.askvocate.ui.theme.OutlineVariant
import com.example.askvocate.ui.theme.Secondary

/**
 * Progress Timeline — Vertical progress line with a glowing dot.
 *
 * - Displays on the right side of the onboarding journey screen
 * - Tracks scroll position with a Royal Blue → Gold gradient fill
 * - The glowing dot pulses with an infinite animation
 */
@Composable
fun ProgressTimeline(
    scrollProgress: Float, // 0f..1f
    modifier: Modifier = Modifier
) {
    // Glow pulse animation for the dot
    val infiniteTransition = rememberInfiniteTransition(label = "progressPulse")
    val glowRadius by infiniteTransition.animateFloat(
        initialValue = 8f,
        targetValue = 14f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowRadius"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    Box(
        modifier = modifier
            .width(24.dp)
            .fillMaxHeight()
            .padding(vertical = 32.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Canvas(
            modifier = Modifier
                .width(24.dp)
                .fillMaxHeight()
        ) {
            val lineX = size.width / 2f
            val lineTop = 0f
            val lineBottom = size.height

            // Background track line
            drawLine(
                color = OutlineVariant,
                start = Offset(lineX, lineTop),
                end = Offset(lineX, lineBottom),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )

            // Filled progress line (gradient)
            val progressY = lineTop + (lineBottom - lineTop) * scrollProgress.coerceIn(0f, 1f)
            if (scrollProgress > 0f) {
                drawLine(
                    brush = Brush.verticalGradient(
                        colors = listOf(Secondary, GoldLight),
                        startY = lineTop,
                        endY = progressY
                    ),
                    start = Offset(lineX, lineTop),
                    end = Offset(lineX, progressY),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // Glowing dot at current position
            val dotCenter = Offset(lineX, progressY)

            // Outer glow
            drawCircle(
                color = Secondary.copy(alpha = glowAlpha),
                radius = glowRadius.dp.toPx(),
                center = dotCenter
            )

            // Inner solid dot
            drawCircle(
                color = Secondary,
                radius = 6.dp.toPx(),
                center = dotCenter
            )

            // White center highlight
            drawCircle(
                color = Color.White,
                radius = 2.dp.toPx(),
                center = dotCenter
            )
        }
    }
}
