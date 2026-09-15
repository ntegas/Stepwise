package com.stepwise.core.designsystem.component

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Generic chrome only — the actual destination list (Today/Goals/Plan/Progress,
 * concept §3) is owned by the navigation layer (DEVELOPMENT_PROTOCOL.md rule 21),
 * not hardcoded into the design system.
 */
data class StepwiseNavigationItem(
    val label: String,
    val icon: ImageVector,
    val iconContentDescription: String,
    val selected: Boolean,
    val onClick: () -> Unit,
)
