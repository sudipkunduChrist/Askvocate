package com.example.askvocate.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Askvocate Shape System
 *
 * 16px radius for interactive elements (buttons, inputs).
 * 24px radius for containers (cards, sections).
 * Matches the design specification for a sophisticated, approachable feel.
 */
val AskvocateShapes = Shapes(
    // Small — chips, tags (8dp)
    small = RoundedCornerShape(8.dp),
    // Medium — buttons, inputs (16dp)
    medium = RoundedCornerShape(16.dp),
    // Large — cards, containers (24dp)
    large = RoundedCornerShape(24.dp),
    // Extra Large — hero sections (32dp)
    extraLarge = RoundedCornerShape(32.dp)
)
