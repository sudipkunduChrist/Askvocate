package com.example.askvocate.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.askvocate.ui.components.AnimatedIllustration
import com.example.askvocate.ui.components.AnimatedInfoCard
import com.example.askvocate.ui.components.PrimaryButton
import com.example.askvocate.ui.components.ProgressTimeline
import com.example.askvocate.ui.components.SecondaryButton
import com.example.askvocate.ui.theme.Secondary
import com.example.askvocate.ui.theme.SecondaryContainer
import com.example.askvocate.ui.theme.TertiaryFixedDim
import com.example.askvocate.ui.theme.PrimaryFixedDim

/**
 * Onboarding Screen — Vertical storytelling journey.
 *
 * Matches the_lexconnect_journey design reference:
 * - Fixed top bar with back arrow + "Askvocate" + profile
 * - Header: "Your Legal Journey" + subtitle
 * - 6 story sections with alternating layouts
 * - Scroll-driven ProgressTimeline on the right
 * - Bottom CTA: "Start Your Case" + "Learn More"
 */

// ── Section Data ──────────────────────────────────────────────────────────────
private data class OnboardingSection(
    val icon: ImageVector,
    val iconTint: Color,
    val iconBackground: Color,
    val title: String,
    val description: String,
    val imageUrl: String?,
    val isReversed: Boolean
)

private val onboardingSections = listOf(
    OnboardingSection(
        icon = Icons.Filled.Lock,
        iconTint = Secondary,
        iconBackground = SecondaryContainer,
        title = "Discuss Privately",
        description = "Start by detailing your situation in a completely secure, encrypted environment. Your information remains strictly confidential, acting as the foundation for your case strategy without compromising privacy.",
        imageUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuDmTgESSDK7ogIbQpIJrn5OJ5WFZEdmoUStqV3-HLi0TTXqiCsz3TYMGWJivAx5Jai2FlzxqRfPQrHQqXFEKWk0JepdlIH5w1SVWBeknX54rr86Scepmszcg8SGBcDPCTJBV3WLHhD8D2fWIznAe6csKUFqTmM0Mav0uu2mW1gYhw99yVDQwvLCArwVQZkP9ULYGtjXJJVzxG3x794bRltZ0O-l7wKmoobjPIGvo_FzGm0-w7nqG1XA",
        isReversed = false
    ),
    OnboardingSection(
        icon = Icons.Filled.Psychology,
        iconTint = TertiaryFixedDim,
        iconBackground = TertiaryFixedDim,
        title = "AI Understands Your Case",
        description = "Our advanced Legal AI analyzes your input instantly. It extracts key facts, identifies relevant legal precedents, and structures your narrative into a professional brief ready for attorney review.",
        imageUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuA0q_iPbYqdMakwTD8wJ6pB3WxPiEitkGmzMZOrQjANWMa9_anLLG0PN0kPjBINnw4HYToPFIeeealoLrGbij-UOoXHld4zOgkppdvl9mP8drcrfFlzll4p2u0qkfTAGfZ6iNrujsqVZ_8LwQj98TeIclQeGmC2PiOT2N8fE6ceykYzJDVci6esRl_E-T88CauOPyLXXUO8atf_aeoV5c4PbpAVqGr4RLNLy5voJTv0xMOh5DO6UxIo",
        isReversed = true
    ),
    OnboardingSection(
        icon = Icons.Filled.PersonSearch,
        iconTint = Secondary,
        iconBackground = SecondaryContainer,
        title = "Find the Right Lawyer",
        description = "Based on the AI's analysis, you are matched with highly vetted, specialized attorneys whose expertise aligns perfectly with your specific legal needs. Review profiles, ratings, and track records.",
        imageUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuCVBy-fRr5xMf9itM4IPAyp6nGox-CpdtMy_mlWHTr5nhyAgUbXRGqsRsfU1vwXUVRapwooVRqGv1LiPdUd45feB3uVvmCtWvoSJA--CRV59l9qXMCY_XjRm7D2US-pI8x2yj9rfhEAb-535mrboyeOe7vPIjvrBwC9AOcrZKDIs1fjLSj_vAeU-0vno2jRz3yQOlVAS5sSuvh8rvXSgvnonsHkxxoDrAlnA_r3MQowNMSLJU0AmE8L",
        isReversed = false
    ),
    OnboardingSection(
        icon = Icons.Filled.Gavel,
        iconTint = PrimaryFixedDim,
        iconBackground = PrimaryFixedDim,
        title = "Lawyers Receive Your Request",
        description = "Attorneys review the AI-generated summaries and express interest. You receive curated proposals, allowing you to select the counsel that best fits your expectations and budget.",
        imageUrl = null, // This section uses a custom card layout
        isReversed = true
    ),
    OnboardingSection(
        icon = Icons.Filled.Forum,
        iconTint = Secondary,
        iconBackground = SecondaryContainer,
        title = "Secure Communication",
        description = "Once engaged, all communication, document sharing, and strategy discussions happen within our encrypted vault. Maintain a clear, documented history of your entire legal engagement.",
        imageUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuDe7PSTWk6vXUn78nmjlhYBq7HmtVbq6mBX3lMDNymg1Jhzdg012dNoNVkv4C8EoPNBQiRi1zgdOYk2l6zgp7bcsCt50vWc2VgvO4HnB2OH3iOmUrpJbwDjHLpf0_f9WO7c2MCrWYtKzqS2vEeDzmfDGBDn39-jRjdEcpfSBYgHbLcvqKjj0rDwWoOYY3FiK03wU2sXu1jmW3cvoFvftR_LV9guSEp6Yj-n4G2rcXODI43vQnhRkE8U",
        isReversed = false
    ),
    OnboardingSection(
        icon = Icons.Filled.TaskAlt,
        iconTint = TertiaryFixedDim,
        iconBackground = TertiaryFixedDim,
        title = "Track Your Legal Journey",
        description = "From initial query to final resolution, experience a structured, transparent, and efficient process powered by AI and human expertise.",
        imageUrl = null, // Final CTA section
        isReversed = false
    )
)

@Composable
fun OnboardingScreen(
    onNavigateToRoleSelection: () -> Unit,
    onBack: () -> Unit
) {
    val scrollState = rememberScrollState()

    // Calculate scroll progress for the timeline
    val scrollProgress by remember {
        derivedStateOf {
            if (scrollState.maxValue > 0) {
                scrollState.value.toFloat() / scrollState.maxValue.toFloat()
            } else 0f
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                )
            )
    ) {
        // Main scrollable content
        Row(modifier = Modifier.fillMaxSize()) {
            // Content column
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(scrollState)
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp)
                    .navigationBarsPadding()
            ) {
                // ── Top App Bar (Scrollable) ─────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Go back",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = "Askvocate",
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 28.sp,
                            letterSpacing = (-0.5).sp
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(onClick = { /* Profile */ }) {
                        Icon(
                            imageVector = Icons.Filled.AccountCircle,
                            contentDescription = "Profile",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // ── Header Section ────────────────────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp, bottom = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Your Legal Journey",
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Experience a seamless, secure, and intelligent path to resolving your legal matters.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                // ── Story Sections ────────────────────────────────────────
                onboardingSections.forEachIndexed { index, section ->
                    if (index == onboardingSections.lastIndex) {
                        // Final section — centered CTA
                        FinalSection(
                            section = section,
                            onStartCase = onNavigateToRoleSelection,
                            animationDelay = index * 100
                        )
                    } else if (section.imageUrl == null && index == 3) {
                        // Section 4 — custom notification card
                        NotificationSection(
                            section = section,
                            animationDelay = index * 100
                        )
                    } else {
                        // Standard section with image
                        StorySection(
                            section = section,
                            animationDelay = index * 100
                        )
                    }

                    if (index < onboardingSections.lastIndex) {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }

                Spacer(modifier = Modifier.height(48.dp))
            }

            // ── Progress Timeline (right side) ────────────────────────────
            ProgressTimeline(
                scrollProgress = scrollProgress,
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(end = 8.dp)
            )
        }
    }
}

// ── Story Section Composable ──────────────────────────────────────────────────
@Composable
private fun StorySection(
    section: OnboardingSection,
    animationDelay: Int
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        AnimatedInfoCard(
            icon = section.icon,
            iconTint = section.iconTint,
            iconBackground = section.iconBackground,
            title = section.title,
            description = section.description,
            isVisible = true,
            animationDelay = animationDelay
        )

        if (section.imageUrl != null) {
            Spacer(modifier = Modifier.height(16.dp))
            AnimatedIllustration(
                imageUrl = section.imageUrl,
                contentDescription = section.title,
                aspectRatio = 4f / 3f,
                animationDelay = animationDelay + 200
            )
        }
    }
}

// ── Notification Card Section (Section 4) ─────────────────────────────────────
@Composable
private fun NotificationSection(
    section: OnboardingSection,
    animationDelay: Int
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        AnimatedInfoCard(
            icon = section.icon,
            iconTint = section.iconTint,
            iconBackground = section.iconBackground,
            title = section.title,
            description = section.description,
            isVisible = true,
            animationDelay = animationDelay
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Custom notification card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 4.dp,
                    shape = RoundedCornerShape(24.dp),
                    ambientColor = Color(0x0D0B1F3A),
                    spotColor = Color(0x0D0B1F3A)
                )
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Bell icon
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.NotificationsActive,
                    contentDescription = "Notification",
                    tint = Secondary,
                    modifier = Modifier.size(40.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "New Proposal Received",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "A top-rated attorney has reviewed your brief.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            PrimaryButton(
                text = "Review Proposal",
                onClick = { }
            )
        }
    }
}

// ── Final CTA Section (Section 6) ────────────────────────────────────────────
@Composable
private fun FinalSection(
    section: OnboardingSection,
    onStartCase: () -> Unit,
    animationDelay: Int
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Icon
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(section.iconBackground.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = section.icon,
                contentDescription = section.title,
                tint = section.iconTint,
                modifier = Modifier.size(32.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Complete Your Legal Journey",
            style = MaterialTheme.typography.displayMedium.copy(
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = section.description,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            PrimaryButton(
                text = "Start Your Case",
                onClick = onStartCase,
                modifier = Modifier.weight(1f)
            )
            SecondaryButton(
                text = "Learn More",
                onClick = { },
                modifier = Modifier.weight(1f),
                outlined = true
            )
        }
    }
}
