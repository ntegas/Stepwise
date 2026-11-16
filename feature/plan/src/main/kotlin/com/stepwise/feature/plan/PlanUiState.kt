package com.stepwise.feature.plan

import com.stepwise.core.model.PlanAgenda
import com.stepwise.domain.usecase.CompletionTarget
import kotlinx.datetime.LocalDate

/**
 * `:feature:plan`'s own state (`/ROADMAP.md` DEV-017). First pass is Week-only — `Day` is
 * already `:feature:today`'s own screen, and `Month`/`Year` are read-mostly zoom levels this
 * task defers rather than building all four granularities at once; disclosed here, not silently
 * narrowed, the same "narrower than the roadmap line reads" pattern several prior DEV tasks
 * already named explicitly rather than assumed.
 *
 * [Content.today] lets [PlanScreen] highlight the current day without reading platform time
 * itself (`/DEVELOPMENT_PROTOCOL.md` rule 11's "no raw system time API outside Clock" — the
 * ViewModel is the one place that reads [com.stepwise.core.common.Clock], same as
 * `TodayViewModel`'s own `TimeZone.currentSystemDefault()` boundary).
 *
 * A failed reschedule is reported through [PlanEvent.RescheduleFailed] (a one-shot event,
 * `:feature:today`'s own `TodayEvent.CompletionFailed` pattern), not a persisted field here —
 * review found DEV-017's first draft used a persisted `actionFailed: Boolean` that never reset
 * on week navigation, so a failure from one week stayed visible after paging away to an
 * unrelated week (DEV-017 review, user-flow reviewer, HIGH).
 */
sealed interface PlanUiState {
    data object Loading : PlanUiState

    data object SignedOut : PlanUiState

    data object Error : PlanUiState

    data class Content(
        val agenda: PlanAgenda,
        val today: LocalDate,
        val rescheduleTarget: PlanRescheduleTarget? = null,
    ) : PlanUiState
}

/** A one-off, non-persisted signal for `PlanScreen` to show (a Snackbar) and then forget. */
sealed interface PlanEvent {
    data object RescheduleFailed : PlanEvent
}

/**
 * The one item currently being rescheduled, i.e. the date picker is open for it. [completionTarget]
 * is computed once, from the actual [com.stepwise.core.model.TodayItem] row, at the moment the
 * user taps reschedule ([PlanViewModel.onRescheduleClicked]) via
 * [com.stepwise.domain.usecase.toCompletionTarget] - stored ready-to-use rather than
 * reconstructed from scratch once a date is actually picked.
 *
 * [isRecurring] tells [RescheduleDialog] whether this moves one occurrence of a recurring series
 * (the rest of the series stays on its normal pattern) or a plain one-off Task's own date, so the
 * dialog can say which (DEV-017 review, user-flow reviewer, MEDIUM — the first draft's dialog
 * never distinguished the two, risking a user believing they just moved a whole recurring series).
 */
data class PlanRescheduleTarget(
    val title: String,
    val completionTarget: CompletionTarget,
    val isRecurring: Boolean,
)
