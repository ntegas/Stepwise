package com.stepwise.feature.goals

import com.stepwise.core.model.Goal
import com.stepwise.core.model.Milestone

/**
 * `:feature:goals`'s own Goal Detail state. [NotFound] and [Error] are both parameterless —
 * neither carries a raw [com.stepwise.core.common.AppError] message; `GoalDetailScreen` resolves
 * its own fixed, localized string for each instead (DEV-016 design review, HIGH — a
 * non-English-locale user otherwise sees untranslated developer-facing English text), the same
 * UI-agnostic-signal convention `:feature:onboarding-auth`'s `SignInUiState` already uses. A Goal
 * that genuinely doesn't exist and one that failed to load are kept as distinct states even
 * though they render identically today, in case a future task wants to tell them apart (e.g. a
 * retry action only makes sense for [Error]).
 *
 * [Content.actionFailed] covers a lifecycle-action/milestone-toggle/progress-update failure (or a
 * mid-session sign-out) that happens outside the Add Milestone dialog; [Content.milestoneHasError]
 * is scoped to that dialog's own save failure - kept separate so dismissing one never silently
 * clears a message that belongs to the other.
 */
sealed interface GoalDetailUiState {
    data object Loading : GoalDetailUiState

    data object NotFound : GoalDetailUiState

    data object Error : GoalDetailUiState

    data class Content(
        val goal: Goal,
        val milestones: List<Milestone>,
        val actionFailed: Boolean = false,
        val milestoneDialogVisible: Boolean = false,
        val milestoneTitle: String = "",
        val milestoneWeight: String = "",
        val milestoneIsBinary: Boolean = true,
        val milestoneTitleError: Boolean = false,
        val milestoneWeightError: Boolean = false,
        val milestoneSaving: Boolean = false,
        val milestoneHasError: Boolean = false,
    ) : GoalDetailUiState
}
