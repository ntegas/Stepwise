package com.stepwise.core.designsystem.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing

/**
 * Centralized animation timing (DESIGN_SYSTEM.md's "Animation" token category).
 * No animated component exists yet — these are the values a future one reads,
 * rather than a screen picking its own duration. Reduced-motion support (skipping
 * or shortening these) is applied at the call site once a real animation exists;
 * not built speculatively here.
 */
object StepwiseMotion {
    const val DURATION_SHORT_MS = 100
    const val DURATION_MEDIUM_MS = 300
    const val DURATION_LONG_MS = 500

    val StandardEasing: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val EmphasizedEasing: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
}
