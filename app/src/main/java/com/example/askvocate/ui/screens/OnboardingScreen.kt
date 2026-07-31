package com.example.askvocate.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.askvocate.ui.components.OnboardingPageIndicator
import com.example.askvocate.ui.components.OnboardingSticker
import com.example.askvocate.ui.components.PrimaryButton
import com.example.askvocate.ui.theme.InkBlack
import com.example.askvocate.ui.theme.SunYellow
import com.example.askvocate.ui.theme.SunYellowLight
import kotlinx.coroutines.launch

/**
 * Onboarding Screen — 6-page swipeable introduction.
 *
 * Each page shows a placeholder "sticker" illustration in the middle
 * (see OnboardingSticker.kt — swap in a real GIF/Lottie asset there later)
 * with a headline and short description below it. A "Skip" button in the
 * top bar jumps straight to the final page; the final page swaps the round
 * "Next" arrow for a full-width black "Get Started" button that leads into
 * role selection.
 */

private data class OnboardingPageData(
    val icon: ImageVector,
    val iconTint: Color,
    val stickerBackground: Color,
    val title: String,
    val description: String
)

private val onboardingPages = listOf(
    OnboardingPageData(
        icon = Icons.Filled.Gavel,
        iconTint = InkBlack,
        stickerBackground = SunYellowLight,
        title = "Legal Help Shouldn't Feel Like a Maze",
        description = "Askvocate connects you with real lawyers and smart AI — no confusing legal jargon required."
    ),
    OnboardingPageData(
        icon = Icons.Filled.Lock,
        iconTint = SunYellow,
        stickerBackground = InkBlack,
        title = "Ask Anything, Privately",
        description = "Describe your situation in a secure, encrypted space. Your story stays yours until you choose to share it."
    ),
    OnboardingPageData(
        icon = Icons.Filled.Psychology,
        iconTint = InkBlack,
        stickerBackground = SunYellowLight,
        title = "AI That Actually Gets It",
        description = "Our legal AI breaks down your case, spots what matters, and turns it into something a lawyer can act on fast."
    ),
    OnboardingPageData(
        icon = Icons.Filled.PersonSearch,
        iconTint = SunYellow,
        stickerBackground = InkBlack,
        title = "Matched With the Right Lawyer",
        description = "Skip the guesswork. Get matched with vetted, specialized lawyers who've handled cases like yours."
    ),
    OnboardingPageData(
        icon = Icons.Filled.Forum,
        iconTint = InkBlack,
        stickerBackground = SunYellowLight,
        title = "Stay in the Loop, Always",
        description = "Chat, share files, and track every update in one secure thread — no more chasing emails."
    ),
    OnboardingPageData(
        icon = Icons.Filled.TaskAlt,
        iconTint = SunYellow,
        stickerBackground = InkBlack,
        title = "Ready When You Are",
        description = "Join as someone who needs help, or as a lawyer ready to offer it. Either way, we've got you."
    )
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    onNavigateToRoleSelection: () -> Unit,
    onBack: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { onboardingPages.size })
    val coroutineScope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == onboardingPages.lastIndex

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // ── Top bar: back arrow + Skip ─────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!isLastPage) {
                    TextButton(
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(onboardingPages.lastIndex)
                            }
                        }
                    ) {
                        Text(
                            text = "Skip",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(64.dp))
                }
            }

            // ── Swipeable page content ─────────────────────────────────
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                val data = onboardingPages[page]
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    OnboardingSticker(
                        icon = data.icon,
                        tint = data.iconTint,
                        background = data.stickerBackground,
                        modifier = Modifier.fillMaxWidth(0.7f)
                    )

                    Spacer(modifier = Modifier.height(40.dp))

                    Text(
                        text = data.title,
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = data.description,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // ── Bottom: dots + Next / Get Started ──────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                OnboardingPageIndicator(
                    pageCount = onboardingPages.size,
                    currentPage = pagerState.currentPage
                )

                Spacer(modifier = Modifier.height(24.dp))

                if (isLastPage) {
                    PrimaryButton(
                        text = "Get Started",
                        onClick = onNavigateToRoleSelection,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        },
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Next",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }
}
