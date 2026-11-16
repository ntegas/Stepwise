package com.stepwise.feature.activities

import com.stepwise.core.model.Metric

/**
 * The one place this module formats a [Metric] for display - `"${key} (${canonicalUnit})"` was
 * independently duplicated in four call sites before this extraction (DEV-018 review,
 * architecture reviewer, MEDIUM; `/DEVELOPMENT_PROTOCOL.md` rule 12's "computed once, not
 * per-screen"). Not a display-name system (no localization of the metric's own `key`) - just the
 * one formula every call site already agreed on, centralized.
 */
internal fun Metric.displayLabel(): String = "$key ($canonicalUnit)"
