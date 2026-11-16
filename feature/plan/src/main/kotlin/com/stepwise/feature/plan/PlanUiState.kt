package com.stepwise.feature.plan

import com.stepwise.core.model.PlanAgenda
import com.stepwise.domain.usecase.CompletionTarget

/**
 * `:feature:plan`'s own state (`/ROADMAP.md` DEV-017). First pass is Week-only — `Day` is
 * already `:feature:today`'s own screen, and `Month`/`Year` are read-mostly zoom levels this
 * task defers rather than building all four granularities at once; disclosed here, not silently
 * narrowed, the same "narrower than the roadmap line reads" pattern several prior DEV tasks
 * already named explicitly rather than assumed.
 */
sealed interface PlanUiState {
    data object Loading : PlanUiState

    data object SignedOut : PlanUiState

    data object Error : PlanUiState

    data class Content(
        val agenda: PlanAgenda,
        val rescheduleTarget: PlanRescheduleTarget? = null,
        val actionFailed: Boolean = false,
    ) : PlanUiState
}

/**
 * The one item currently being rescheduled, i.e. the date picker is open for it. [completionTarget]
 * is computed once, from the actual [com.stepwise.core.model.TodayItem] row, at the moment the
 * user taps reschedule ([PlanViewModel.onRescheduleClicked]) via
 * [com.stepwise.domain.usecase.toCompletionTarget] - stored ready-to-use rather than
 * reconstructed from scratch once a date is actually picked.
 */
data class PlanRescheduleTarget(
    val title: String,
    val completionTarget: CompletionTarget,
)
