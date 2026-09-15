package com.stepwise.core.designsystem.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Matches Material3's tonal elevation scale, named for use at call sites instead of raw `.dp`. */
object StepwiseElevation {
    val level0: Dp = 0.dp
    val level1: Dp = 1.dp
    val level2: Dp = 3.dp
    val level3: Dp = 6.dp
    val level4: Dp = 8.dp
    val level5: Dp = 12.dp
}

/** Icon sizing (DESIGN_SYSTEM.md's "Icon sizing" token category). */
object StepwiseIconSize {
    val small: Dp = 16.dp
    val medium: Dp = 24.dp
    val large: Dp = 32.dp
    val extraLarge: Dp = 48.dp
}
