package com.stepwise.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * The single entry point for Stepwise's visual identity (DEVELOPMENT_PROTOCOL.md
 * rule 20). Every screen wraps its content in this, never in a bare `MaterialTheme`
 * with inline colors/typography/shapes — that's exactly the per-screen drift this
 * module exists to prevent.
 */
@Composable
fun StepwiseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) StepwiseDarkColorScheme else StepwiseLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = StepwiseTypography,
        shapes = StepwiseShapes,
        content = content,
    )
}
