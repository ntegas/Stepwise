package com.stepwise.feature.activities

import com.stepwise.core.model.ExecutionStatus
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
 *
 * **[MetricInputState.plannedValue] is threaded through into the built [MetricEntry] rather than
 * hardcoded to `null`** (DEV-018 review, logic reviewer, HIGH) - the first draft always built
 * `MetricEntry(plannedValue = null, ...)`, which [MetricResolver.mergeMetricEntries]'s own
 * replace-not-merge rule means silently overwrites a Habit's own known `targetValue` with `null`
 * in the stored `session_metric_values.planned_value` column the moment a user types any actual
 * value - permanent data loss on a canonical column DEV-019's calculations will read verbatim.
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

internal data class MetricInputsValidationResult(
    val metricValues: List<MetricEntry>,
    val invalidMetricIds: Set<MetricId>,
)

/**
 * The per-metric half of validation, shared between a brand-new manual entry
 * ([validateManualEntry]) and correcting an existing Session's already-logged values
 * ([EditSessionDialogState] in [ActivityDetailViewModel]/[HabitDetailViewModel]) - neither needs
 * its own copy of "blank is skipped, non-numeric text is an error, a parsed value carries its
 * own [MetricInputState.plannedValue] forward."
 */
internal fun validateMetricInputs(inputs: List<MetricInputState>): MetricInputsValidationResult {
    val invalidMetricIds = mutableSetOf<MetricId>()
    val metricValues = mutableListOf<MetricEntry>()
    for (input in inputs) {
        val text = input.value.trim()
        if (text.isBlank()) continue
        val value = text.toDoubleOrNull()
        if (value == null) {
            invalidMetricIds += input.metricId
        } else {
            metricValues +=
                MetricEntry(metricId = input.metricId, plannedValue = input.plannedValue, actualValue = value)
        }
    }
    return MetricInputsValidationResult(metricValues, invalidMetricIds)
}

/**
 * [ExecutionStatus.MISSED]/[ExecutionStatus.CANCELLED] never resolve any metric
 * ([MetricResolver.resolveMerged]'s own rule), so metric inputs are excluded entirely for those
 * statuses, not merely optional - a value a user types while one of those is selected is never
 * sent to the domain layer at all (DEV-018 review, design reviewer, HIGH: the first draft kept
 * rendering the metric fields regardless of status, so a typed value was silently dropped on
 * save with zero indication this would happen).
 */
internal fun statusAcceptsMetrics(status: ExecutionStatus): Boolean =
    status == ExecutionStatus.DONE || status == ExecutionStatus.PARTIAL

internal fun validateManualEntry(state: ManualEntryDialogState): ManualEntryValidationResult {
    val date = runCatching { LocalDate.parse(state.date.trim()) }.getOrNull()
    val dateInvalid = date == null
    val metricResult = if (statusAcceptsMetrics(state.status)) validateMetricInputs(state.metricInputs) else null
    val invalidMetricIds = metricResult?.invalidMetricIds.orEmpty()
    val validated =
        if (dateInvalid || invalidMetricIds.isNotEmpty()) {
            null
        } else {
            ValidatedManualEntry(date = checkNotNull(date), metricValues = metricResult?.metricValues.orEmpty())
        }
    return ManualEntryValidationResult(validated, dateInvalid, invalidMetricIds)
}
