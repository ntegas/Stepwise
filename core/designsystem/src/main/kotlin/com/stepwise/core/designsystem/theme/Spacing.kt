package com.stepwise.core.designsystem.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Material3 has no built-in spacing scale — this is Stepwise's, so no screen
 * hardcodes a raw `.dp` padding/gap value (DEVELOPMENT_PROTOCOL.md rule 20).
 */
object StepwiseSpacing {
    val none: Dp = 0.dp
    val extraSmall: Dp = 4.dp
    val small: Dp = 8.dp
    val medium: Dp = 16.dp
    val large: Dp = 24.dp
    val extraLarge: Dp = 32.dp
    val extraExtraLarge: Dp = 48.dp
}
