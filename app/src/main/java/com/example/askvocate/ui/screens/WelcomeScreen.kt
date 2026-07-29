package com.example.askvocate.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.askvocate.ui.components.FloatingLegalIcons
import com.example.askvocate.ui.components.GradientBackground
import com.example.askvocate.ui.components.PrimaryButton
import com.example.askvocate.ui.components.SecondaryButton
import kotlinx.coroutines.launch

/**
 * Welcome Screen — First interactive screen after splash.
 *
 * Matches the welcome_to_lexconnect design reference:
 * - Header: "Askvocate" title + "Log In" button
 * - Glassmorphic text panel: "Legal Help, Made Simple." headline
 * - Hero illustration
 * - Floating legal icons overlay
 * - "Get Started" + "Skip" buttons
 */

private const val HERO_IMAGE_URL =
    "https://lh3.googleusercontent.com/aida-public/AB6AXuAMsSPWb8lYimwJXV5oeGY_36y9UwVJw6RF9hQ1LvigEVYZQyUIesMmtJgWXufOl8ldymPpj82B4pDphs55klNCzPYMcOwRgQsPIrn0uBkdmvvoIaOS2vh60-hDLjLylJbO4l9C3ZQKG9FCPHwiP4ErgEHx4VtRZn1PJ9EL3dNevukBxpqnZlepJ6mmLIn2-u4BN2VR8irT7YR96VazabzL77h2lzIyfzbBGfJES6XounPlKUXSG6bi"

@Composable
fun WelcomeScreen(
    onGetStarted: () -> Unit,
    onSkip: () -> Unit,
    onLogin: () -> Unit
) {
    // Entrance animations
    val contentAlpha = remember { Animatable(0f) }
    val heroAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch { contentAlpha.animateTo(1f, tween(600)) }
        kotlinx.coroutines.delay(200)
        launch { heroAlpha.animateTo(1f, tween(800)) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Animated gradient background
        GradientBackground(
            modifier = Modifier.fillMaxSize(),
            alpha = 0.6f
        )

        // Floating decorative icons
        FloatingLegalIcons()

        // Main content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // ── Top App Bar ───────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(contentAlpha.value),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Askvocate",
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
                TextButton(onClick = onLogin) {
                    Text(
                        text = "Log In",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            // ── Hero Section ──────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Glassmorphic text panel
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(contentAlpha.value)
                        .shadow(
                            elevation = 4.dp,
                            shape = RoundedCornerShape(24.dp),
                            ambientColor = Color(0x0D0B1F3A),
                            spotColor = Color(0x0D0B1F3A)
                        )
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.White.copy(alpha = 0.8f))
                        .padding(32.dp)
                ) {
                    Text(
                        text = "Legal Help,\nMade Simple.",
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Connect with verified lawyers, understand your legal options through AI, and securely manage your legal journey.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Hero illustration
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .alpha(heroAlpha.value)
                ) {
                    AsyncImage(
                        model = HERO_IMAGE_URL,
                        contentDescription = "Professional lawyer consulting with AI elements",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                    )
                    // Fade gradient overlay at bottom
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                                    )
                                )
                            )
                    )
                }
            }

            // ── Bottom Actions ────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(contentAlpha.value)
                    .padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                PrimaryButton(
                    text = "Get Started",
                    onClick = onGetStarted,
                    modifier = Modifier.width(320.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                SecondaryButton(
                    text = "Skip",
                    onClick = onSkip,
                    modifier = Modifier.width(320.dp)
                )
            }
        }
    }
}
