package com.example.askvocate.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.askvocate.ui.components.GradientBackground
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Splash Screen — Entry point of the Askvocate app.
 *
 * - Animated blue gradient background (GradientBackground)
 * - Logo: "Askvocate" text centered, fades in + scales 0.7→1.0 over 900ms
 * - Tagline: "Ask Smart. Connect Right." fades in after logo
 * - Auto-navigates to Welcome after 2 seconds
 */
@Composable
fun SplashScreen(
    onNavigateToWelcome: () -> Unit
) {
    // Animation states
    val logoAlpha = remember { Animatable(0f) }
    val logoScale = remember { Animatable(0.7f) }
    val taglineAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // Logo animation: fade in + scale up over 900ms
        launch { logoAlpha.animateTo(1f, tween(900)) }
        launch { logoScale.animateTo(1f, tween(900)) }

        // Tagline appears after logo animation starts
        delay(600)
        launch { taglineAlpha.animateTo(1f, tween(600)) }

        // Navigate to Welcome after 2 seconds total
        delay(1400) // 600 + 1400 = 2000ms total
        onNavigateToWelcome()
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Animated gradient background
        GradientBackground(
            modifier = Modifier.fillMaxSize(),
            alpha = 0.85f
        )

        // Content
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            // Logo text
            Text(
                text = "Askvocate",
                style = MaterialTheme.typography.displayLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 42.sp,
                    letterSpacing = (-1).sp
                ),
                color = Color.White,
                modifier = Modifier
                    .alpha(logoAlpha.value)
                    .scale(logoScale.value)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Tagline
            Text(
                text = "Ask Smart. Connect Right.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(taglineAlpha.value)
            )
        }
    }
}
