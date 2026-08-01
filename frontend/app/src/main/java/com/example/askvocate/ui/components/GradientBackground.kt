package com.example.askvocate.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.cos
import kotlin.math.sin

/**
 * Animated Gradient Background — Replicates the WebGL shader from the design reference.
 *
 * Creates a slowly flowing gradient between Ink Black and Charcoal
 * with a Sun Yellow accent glow that moves in a circular path.
 * Uses Compose Canvas with infiniteTransition for smooth animation.
 */
@Composable
fun GradientBackground(
    modifier: Modifier = Modifier,
    alpha: Float = 0.6f
) {
    val infiniteTransition = rememberInfiniteTransition(label = "gradientBg")

    // Slow rotation for gradient shift
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.2832f, // 2π
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "gradientTime"
    )

    // Secondary wave for depth
    val wave by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.2832f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 15000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "gradientWave"
    )

    val deepNavy = Color(0xFF161616) // Ink Black
    val royalBlue = Color(0xFF3D3D3D) // Charcoal
    val lightGray = Color(0xFFFFF8EC) // Cream
    val goldAccent = Color(0xFFFFC93C) // Sun Yellow

    Canvas(
        modifier = modifier.fillMaxSize()
    ) {
        val w = size.width
        val h = size.height

        // Primary gradient: diagonal sweep
        val gradientCenterX = w * 0.5f + w * 0.3f * cos(time * 0.5f)
        val gradientCenterY = h * 0.5f + h * 0.3f * sin(time * 0.3f)

        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    royalBlue.copy(alpha = alpha),
                    deepNavy.copy(alpha = alpha),
                    deepNavy.copy(alpha = alpha * 0.8f)
                ),
                center = Offset(gradientCenterX, gradientCenterY),
                radius = maxOf(w, h) * 0.8f
            )
        )

        // Secondary layer: light wash from top
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    lightGray.copy(alpha = alpha * 0.15f),
                    Color.Transparent
                ),
                startY = 0f,
                endY = h * 0.4f
            )
        )

        // Gold accent glow — orbiting point
        val glowX = w * 0.5f + w * 0.3f * cos(time * 0.2f)
        val glowY = h * 0.5f + h * 0.3f * sin(time * 0.2f)

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    goldAccent.copy(alpha = alpha * 0.15f),
                    Color.Transparent
                ),
                center = Offset(glowX, glowY),
                radius = minOf(w, h) * 0.25f
            ),
            center = Offset(glowX, glowY),
            radius = minOf(w, h) * 0.25f
        )

        // Secondary glow — subtle wave
        val glow2X = w * 0.3f + w * 0.2f * sin(wave)
        val glow2Y = h * 0.7f + h * 0.15f * cos(wave * 0.7f)

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    royalBlue.copy(alpha = alpha * 0.1f),
                    Color.Transparent
                ),
                center = Offset(glow2X, glow2Y),
                radius = minOf(w, h) * 0.3f
            ),
            center = Offset(glow2X, glow2Y),
            radius = minOf(w, h) * 0.3f
        )
    }
}