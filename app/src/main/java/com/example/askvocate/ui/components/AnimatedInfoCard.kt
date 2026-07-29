package com.example.askvocate.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Animated Info Card — Glassmorphic card for onboarding story sections.
 *
 * Features:
 * - Fade in + slide up + scale animation on visibility
 * - Icon badge with tinted background
 * - Title and description text
 * - Optional composable content slot (for images)
 */
@Composable
fun AnimatedInfoCard(
    icon: ImageVector,
    iconTint: Color,
    iconBackground: Color,
    title: String,
    description: String,
    isVisible: Boolean,
    animationDelay: Int = 0,
    modifier: Modifier = Modifier,
    content: (@Composable () -> Unit)? = null
) {
    // Animation state
    val alpha = remember { Animatable(0f) }
    val offsetY = remember { Animatable(30f) }
    val cardScale = remember { Animatable(0.9f) }

    LaunchedEffect(isVisible) {
        if (isVisible) {
            delay(animationDelay.toLong())
            // Run animations in parallel
            launch {
                alpha.animateTo(1f, animationSpec = tween(500))
            }
            launch {
                offsetY.animateTo(0f, animationSpec = tween(500))
            }
            launch {
                cardScale.animateTo(1f, animationSpec = tween(500))
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .alpha(alpha.value)
            .scale(cardScale.value)
            .padding(vertical = 8.dp)
    ) {
        // Icon badge
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(iconBackground.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = iconTint,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Title
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Description
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
        )

        // Optional content (image)
        if (content != null) {
            Spacer(modifier = Modifier.height(16.dp))
            content()
        }
    }
}
