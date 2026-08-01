package com.example.askvocate.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import kotlinx.coroutines.launch

/**
 * Animated Illustration — Image loader with glassmorphic frame.
 *
 * - Loads images via Coil with fade-in + scale entrance animation
 * - Glassmorphic card wrapper (24dp radius, semi-transparent bg, shadow)
 * - Shows loading indicator while image downloads
 */
@Composable
fun AnimatedIllustration(
    imageUrl: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    aspectRatio: Float = 1f,
    animationDelay: Int = 0
) {
    val alpha = remember { Animatable(0f) }
    val imgScale = remember { Animatable(0.92f) }

    LaunchedEffect(imageUrl) {
        kotlinx.coroutines.delay(animationDelay.toLong())
        launch { alpha.animateTo(1f, tween(600)) }
        launch { imgScale.animateTo(1f, tween(600)) }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .alpha(alpha.value)
            .scale(imgScale.value)
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(24.dp),
                ambientColor = Color(0x0D000000),
                spotColor = Color(0x0D000000)
            )
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.8f))
            .padding(12.dp)
    ) {
        SubcomposeAsyncImage(
            model = imageUrl,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio)
                .clip(RoundedCornerShape(16.dp)),
            loading = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(aspectRatio),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.secondary,
                        strokeWidth = 2.dp
                    )
                }
            },
            error = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(aspectRatio)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            color = MaterialTheme.colorScheme.surfaceContainerLow
                        )
                )
            }
        )
    }
}