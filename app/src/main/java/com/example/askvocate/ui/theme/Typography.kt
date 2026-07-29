package com.example.askvocate.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Askvocate Typography System
 *
 * Uses the system default sans-serif which maps to Inter/Roboto on most devices.
 * All styles match the exact sizes from DESIGN.md.
 * Tight tracking for headlines, expanded for labels.
 */

val AskvocateFontFamily = FontFamily.SansSerif

val AskvocateTypography = Typography(
    // Display Large — 48sp, Bold, -0.02em tracking
    displayLarge = TextStyle(
        fontFamily = AskvocateFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 48.sp,
        lineHeight = 56.sp,
        letterSpacing = (-0.96).sp // -0.02em at 48sp
    ),
    // Display Medium — 36sp, Bold, -0.02em tracking (mobile display)
    displayMedium = TextStyle(
        fontFamily = AskvocateFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.72).sp // -0.02em at 36sp
    ),
    // Display Small — 32sp, SemiBold, -0.01em tracking
    displaySmall = TextStyle(
        fontFamily = AskvocateFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.32).sp // -0.01em at 32sp
    ),
    // Headline Large — 32sp, SemiBold
    headlineLarge = TextStyle(
        fontFamily = AskvocateFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.32).sp
    ),
    // Headline Medium — 28sp, SemiBold
    headlineMedium = TextStyle(
        fontFamily = AskvocateFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),
    // Headline Small — 24sp, SemiBold
    headlineSmall = TextStyle(
        fontFamily = AskvocateFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),
    // Title Large
    titleLarge = TextStyle(
        fontFamily = AskvocateFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    // Title Medium
    titleMedium = TextStyle(
        fontFamily = AskvocateFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    // Title Small
    titleSmall = TextStyle(
        fontFamily = AskvocateFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    // Body Large — 18sp, Regular
    bodyLarge = TextStyle(
        fontFamily = AskvocateFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    // Body Medium — 16sp, Regular
    bodyMedium = TextStyle(
        fontFamily = AskvocateFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    ),
    // Body Small
    bodySmall = TextStyle(
        fontFamily = AskvocateFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp
    ),
    // Label Large — 14sp, Medium, 0.05em tracking
    labelLarge = TextStyle(
        fontFamily = AskvocateFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.7.sp // 0.05em at 14sp
    ),
    // Label Medium
    labelMedium = TextStyle(
        fontFamily = AskvocateFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    // Label Small — 12sp, SemiBold
    labelSmall = TextStyle(
        fontFamily = AskvocateFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp
    )
)
