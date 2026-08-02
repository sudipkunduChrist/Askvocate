package com.example.askvocate.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.askvocate.ui.components.RoleSelectionCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Role Selection Screen — "Choose Your Experience".
 *
 * Matches choose_your_experience design reference:
 * - Step indicator: "Step 3 of 3"
 * - Headline + subtitle
 * - Two role cards (User / Lawyer)
 * - Premium abstract path illustration
 * - Footer text
 */

enum class UserRole {
    USER, LAWYER, NONE
}

@Composable
fun RoleSelectionScreen(
    onRoleSelected: (UserRole) -> Unit,
    onBack: () -> Unit
) {
    var selectedRole by remember { mutableStateOf(UserRole.NONE) }
    val scrollState = rememberScrollState()

    // Animations
    val contentAlpha = remember { Animatable(0f) }
    val cardsAlpha = remember { Animatable(0f) }
    val illustrationAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch { contentAlpha.animateTo(1f, tween(500)) }
        delay(150)
        launch { cardsAlpha.animateTo(1f, tween(600)) }
        delay(150)
        launch { illustrationAlpha.animateTo(1f, tween(600)) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Top App Bar ───────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 32.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.padding(start = 0.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "Askvocate",
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
                Box(modifier = Modifier.padding(24.dp)) // Spacer for centering
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(contentAlpha.value),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ── Step Indicator ────────────────────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 48.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Step 3 of 3",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Finalizing Profile",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // Progress bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.secondary,
                                            MaterialTheme.colorScheme.tertiary
                                        )
                                    )
                                )
                        )
                    }
                }

                // ── Headers ───────────────────────────────────────────────
                Text(
                    text = "Choose Your\nExperience",
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Continue as a client seeking legal assistance or as a verified legal professional.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(48.dp))
            }

            // ── Selection Cards ───────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(cardsAlpha.value),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                RoleSelectionCard(
                    icon = Icons.Filled.Person,
                    iconTint = MaterialTheme.colorScheme.secondary,
                    iconBackground = MaterialTheme.colorScheme.secondaryContainer,
                    title = "Continue as User",
                    description = "Find top-tier lawyers, consult with our AI legal assistant, and seamlessly book appointments to resolve your legal matters.",
                    buttonText = "Continue as User",
                    isSelected = selectedRole == UserRole.USER,
                    isPrimary = true,
                    onClick = { onRoleSelected(UserRole.USER) }
                )

                RoleSelectionCard(
                    icon = Icons.Filled.Gavel,
                    iconTint = MaterialTheme.colorScheme.tertiary,
                    iconBackground = MaterialTheme.colorScheme.tertiaryContainer,
                    title = "Continue as Lawyer",
                    description = "Create a verified professional profile, receive high-quality client requests, and manage communications efficiently.",
                    buttonText = "Continue as Lawyer",
                    isSelected = selectedRole == UserRole.LAWYER,
                    isPrimary = false,
                    onClick = { onRoleSelected(UserRole.LAWYER) }
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            // ── Abstract Illustration ─────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f)
                    .alpha(illustrationAlpha.value)
                    .shadow(
                        elevation = 8.dp,
                        shape = RoundedCornerShape(24.dp),
                        ambientColor = Color(0x0D000000),
                        spotColor = Color(0x0D000000)
                    )
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            ) {
                // Gradient overlays simulating the paths
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f),
                                    Color.Transparent,
                                    MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.2f)
                                )
                            )
                        )
                )

                AsyncImage(
                    model = "https://lh3.googleusercontent.com/aida-public/AB6AXuCQr15GmyiPlGvVDEM2y2I4-1-ohNl2GlUPX-XKHHcNyiTUc6iMowYRWlwVumWZ9yXp3ns_ZJWJ-VpQphrvHGNJc6MebDoGwQHf5eMk_TQSj05L7gZmrF2BivWrchwTjVneQcmu5EjvlZMwH30DWVAifXwMqW2GPTvdh3C2ynqBzDgGRzJBcVTd9fT68Rqe9YFfchcHw72UEsdxgcMsB5IZO1xw64noFmSsM-i4hkwPvazW0lRD6F1C",
                    contentDescription = "Abstract 3D paths",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(0.8f)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // ── Footer ────────────────────────────────────────────────
            Text(
                text = "You can switch roles later from Settings.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(illustrationAlpha.value)
            )
        }
    }
}