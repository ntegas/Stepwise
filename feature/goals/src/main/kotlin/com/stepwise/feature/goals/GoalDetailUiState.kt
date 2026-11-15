package com.stepwise.feature.goals

import com.stepwise.core.model.Goal
import com.stepwise.core.model.Milestone

/**
 * `:feature:goals`'s own Goal Detail state. [NotFound] is distinct from [Error] — a missing Goal
 * (deleted, or a stale deep link) is not itself an [com.stepwise.core.common.AppError], so it
 * carries no raw message; [GoalDetailScreen] resolves its own fixed string for it, the same
 * UI-agnostic-signal convention `:feature:onboarding-auth`'s `SignInUiState` already uses.
 */
sealed interface GoalDetailUiState {
    data object Loading : GoalDetailUiState

    data object NotFound : GoalDetailUiState

    data class Error(
        val message: String,
    ) : GoalDetailUiState

    data class Content(
        val goal: Goal,
        val milestones: List<Milestone>,
        val milestoneDialogVisible: Boolean = false,
        val milestoneTitle: String = "",
        val milestoneWeight: String = "",
        val milestoneIsBinary: Boolean = true,
        val milestoneTitleError: Boolean = false,
        val milestoneWeightError: Boolean = false,
        val milestoneSaving: Boolean = false,
        val milestoneError: String? = null,
    ) : GoalDetailUiState
}
