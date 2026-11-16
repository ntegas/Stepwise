package com.stepwise.feature.activities

import com.stepwise.core.model.MetricId
import com.stepwise.domain.usecase.MetricEntry
import kotlinx.datetime.LocalDate

/**
 * Pure manual-entry form validation, shared by Activity Detail and Habit Detail - extracted so
 * neither ViewModel re-derives the same date-parsing/per-metric-numeric-parsing rules. A blank
 * metric input is skipped entirely (no [MetricEntry] built for it), not treated as a `0` or an
 * error - [com.stepwise.domain.usecase.MetricResolver]'s own rule is "the caller's overrides fully
 * determine what this session logs" for an Activity (no entity-level default to fall back to), so
 * leaving a field blank here means genuinely not logging that metric this time, not a mistake.
 */
internal data class ValidatedManualEntry(
    val date: LocalDate,
    val metricValues: List<MetricEntry>,
)

internal data class ManualEntryValidationResult(
    val validated: ValidatedManualEntry?,
    val dateInvalid: Boolean,
    val invalidMetricIds: Set<MetricId>,
)

internal fun validateManualEntry(state: ManualEntryDialogState): ManualEntryValidationResult {
    val date = runCatching { LocalDate.parse(state.date.trim()) }.getOrNull()
    val dateInvalid = date == null
    val invalidMetricIds = mutableSetOf<MetricId>()
    val metricValues = mutableListOf<MetricEntry>()
    for (input in state.metricInputs) {
        val text = input.value.trim()
        if (text.isBlank()) continue
        val value = text.toDoubleOrNull()
        if (value == null) {
            invalidMetricIds += input.metricId
        } else {
            metricValues += MetricEntry(metricId = input.metricId, plannedValue = null, actualValue = value)
        }
    }
    val validated =
        if (dateInvalid || invalidMetricIds.isNotEmpty()) {
            null
        } else {
            ValidatedManualEntry(date = checkNotNull(date), metricValues = metricValues)
        }
    return ManualEntryValidationResult(validated, dateInvalid, invalidMetricIds)
}
