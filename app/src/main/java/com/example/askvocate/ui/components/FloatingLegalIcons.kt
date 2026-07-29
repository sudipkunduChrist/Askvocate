package com.example.askvocate.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.example.askvocate.ui.theme.Secondary
import com.example.askvocate.ui.theme.TertiaryFixedDim
import com.example.askvocate.ui.theme.Primary

/**
 * Floating Legal Icons — Decorative animated icons for the welcome screen.
 *
 * Icons: Balance (Scales of Justice), Shield, Description (Law Book),
 * Gavel, Account Balance (Court Building)
 *
 * Each icon floats with a translateY + rotate infinite animation
 * with staggered delays for a natural, organic feel.
 */

private data class FloatingIconData(
    val icon: ImageVector,
    val xFraction: Float,  // 0..1 position fraction of screen width
    val yFraction: Float,  // 0..1 position fraction of screen height
    val size: Int,          // dp size
    val alpha: Float,       // opacity
    val durationMs: Int,    // animation cycle duration
    val delayMs: Int,       // stagger delay within the cycle
    val floatAmplitude: Float // how far it moves in dp
)

private val floatingIcons = listOf(
    FloatingIconData(Icons.Filled.AccountBalance, 0.15f, 0.18f, 56, 0.15f, 6000, 0, 20f),
    FloatingIconData(Icons.Filled.Shield, 0.75f, 0.12f, 48, 0.12f, 7000, 1000, 15f),
    FloatingIconData(Icons.Filled.Description, 0.8f, 0.35f, 64, 0.10f, 5500, 2000, 25f),
    FloatingIconData(Icons.Filled.Gavel, 0.2f, 0.45f, 44, 0.12f, 6500, 1500, 18f),
    FloatingIconData(Icons.Filled.AccountBalance, 0.6f, 0.55f, 52, 0.08f, 8000, 500, 22f)
)

@Composable
fun FloatingLegalIcons(
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp
    val screenHeight = configuration.screenHeightDp

    Box(modifier = modifier.fillMaxSize()) {
        floatingIcons.forEach { iconData ->
            FloatingIcon(
                data = iconData,
                screenWidth = screenWidth,
                screenHeight = screenHeight
            )
        }
    }
}

@Composable
private fun FloatingIcon(
    data: FloatingIconData,
    screenWidth: Int,
    screenHeight: Int
) {
    val infiniteTransition = rememberInfiniteTransition(
        label = "float_${data.icon.name}"
    )

    // Vertical float animation
    val translateY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -data.floatAmplitude,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = data.durationMs,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatY_${data.icon.name}"
    )

    // Subtle rotation
    val rotation by infiniteTransition.animateFloat(
        initialValue = -3f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = data.durationMs,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatRotate_${data.icon.name}"
    )

    val xOffset = (screenWidth * data.xFraction).dp
    val yOffset = (screenHeight * data.yFraction).dp

    Icon(
        imageVector = data.icon,
        contentDescription = null, // Decorative
        tint = Secondary,
        modifier = Modifier
            .offset(x = xOffset, y = yOffset + translateY.dp)
            .size(data.size.dp)
            .alpha(data.alpha)
            .rotate(rotation)
    )
}
