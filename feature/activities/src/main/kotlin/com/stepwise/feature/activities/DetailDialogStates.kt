package com.stepwise.feature.activities

import com.stepwise.core.model.ExecutionStatus
import com.stepwise.core.model.GoalId
import com.stepwise.core.model.MetricId
import com.stepwise.core.model.SessionId

/**
 * The [ExecutionStatus] values a manual Session-log entry (`/ROADMAP.md` DEV-018) offers -
 * deliberately narrower than the full enum: RESCHEDULED needs a target date a manual log has no
 * use for (nothing to reschedule - this *creates* the record), and IN_PROGRESS is Timer's own
 * state (`/ROADMAP.md` DEV-026, not yet built) - [CompleteOccurrenceUseCase] itself still accepts
 * either, this is only this screen's own menu of sensible choices.
 */
val MANUAL_ENTRY_STATUSES =
    listOf(ExecutionStatus.DONE, ExecutionStatus.PARTIAL, ExecutionStatus.MISSED, ExecutionStatus.CANCELLED)

/**
 * One metric's input row, shared by a brand-new manual entry and correcting an existing Session's
 * logged values - [label] is pre-resolved (`Metric.key` + unit, via [displayLabel]).
 * [plannedValue] carries the metric's own known planned amount (a Habit's `targetValue`; an
 * existing Session's own stored `plannedValue`; `null` for an Activity metric, which has none) so
 * [validateMetricInputs] can build a [com.stepwise.domain.usecase.MetricEntry] that preserves it
 * rather than silently zeroing a canonical column - see [validateMetricInputs]'s own KDoc.
 */
data class MetricInputState(
    val metricId: MetricId,
    val label: String,
    val plannedValue: Double? = null,
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

/**
 * Correcting an already-logged Session's metric values (`/docs/dev-plan-detailed.md` DEV-018's
 * own "edit/delete recompute" completion check, DEV-018 review, HIGH - the first draft had no way
 * to reach [EditSessionUseCase]/[DeleteSessionUseCase] at all, both already built since DEV-009).
 * [metricInputs] starts empty and [isLoading] while the existing [com.stepwise.core.model.SessionMetricValue]
 * rows are fetched ([GetSessionMetricValuesUseCase]) - editing is a correction of what's already
 * there, never a fresh "Done defaults to planned" guess, so nothing is pre-filled until the real
 * stored values are in hand.
 */
data class EditSessionDialogState(
    val sessionId: SessionId,
    val metricInputs: List<MetricInputState> = emptyList(),
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val hasError: Boolean = false,
)

/** A one-off, non-persisted signal for Activity/Habit Detail to show (a Snackbar) and then forget. */
sealed interface DetailActionEvent {
    data object ActionFailed : DetailActionEvent
}
