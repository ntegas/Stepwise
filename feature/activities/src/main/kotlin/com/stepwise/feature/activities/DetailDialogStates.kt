package com.stepwise.feature.activities

import com.stepwise.core.model.ExecutionStatus
import com.stepwise.core.model.GoalId
import com.stepwise.core.model.MetricId

/**
 * The [ExecutionStatus] values a manual Session-log entry (`/ROADMAP.md` DEV-018) offers -
 * deliberately narrower than the full enum: RESCHEDULED needs a target date a manual log has no
 * use for (nothing to reschedule - this *creates* the record), and IN_PROGRESS is Timer's own
 * state (`/ROADMAP.md` DEV-026, not yet built) - [CompleteOccurrenceUseCase] itself still accepts
 * either, this is only this screen's own menu of sensible choices.
 */
val MANUAL_ENTRY_STATUSES =
    listOf(ExecutionStatus.DONE, ExecutionStatus.PARTIAL, ExecutionStatus.MISSED, ExecutionStatus.CANCELLED)

/** One metric's input row inside [ManualEntryDialogState] - [label] is pre-resolved (Metric.key + unit). */
data class MetricInputState(
    val metricId: MetricId,
    val label: String,
    val value: String = "",
    val error: Boolean = false,
)

/**
 * Shared manual-entry dialog state (`/ROADMAP.md` DEV-018's "Session history/manual entry") -
 * used identically by Activity Detail and Habit Detail, each populating [metricInputs]
 * differently ([MetricResolver]'s own rule: a Habit always has exactly its own metric; an
 * Activity has none of its own, only whatever its [com.stepwise.core.model.GoalActivityLink] rows
 * imply) but driving the same dialog shape.
 */
data class ManualEntryDialogState(
    val date: String,
    val status: ExecutionStatus = ExecutionStatus.DONE,
    val metricInputs: List<MetricInputState> = emptyList(),
    val isSubmitting: Boolean = false,
    val hasError: Boolean = false,
)

/** Shared "link this source to a Goal" dialog state - used identically by both Detail screens. */
data class LinkGoalDialogState(
    val selectedGoalId: GoalId? = null,
    val selectedMetricId: MetricId? = null,
    val isSubmitting: Boolean = false,
    val hasError: Boolean = false,
)
