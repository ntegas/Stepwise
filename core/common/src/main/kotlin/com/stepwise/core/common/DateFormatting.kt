package com.stepwise.core.common

import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Locale-aware display formatting for a [LocalDate] — e.g. "31 December 2026" in English, the
 * same shape `/PRODUCT_CANON.md`'s own Goal-creation example uses, rather than the raw ISO
 * `toString()` (DEV-016 design review, HIGH) every Goals UI screen was otherwise showing a user
 * directly. The formatter is built fresh per call (not cached as a top-level val) so a runtime
 * locale change is picked up without requiring a process restart.
 */
fun LocalDate.toDisplayString(): String {
    val formatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.getDefault())
    return toJavaLocalDate().format(formatter)
}
