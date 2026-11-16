package com.stepwise.feature.goals

/**
 * `:feature:goals`'s own shared display formatting for a [com.stepwise.core.model.Goal]'s
 * `targetValue` — strips a trailing `.0` for whole numbers ("100" rather than "100.0") while
 * keeping real decimals ("100.5"), used by both the list row and Goal Detail so the two screens
 * can't drift on this one formatting rule (DEVELOPMENT_PROTOCOL.md's no-duplicated-logic rule).
 */
fun formatTargetValue(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
