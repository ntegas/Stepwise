package com.stepwise.feature.goals

import com.stepwise.core.common.AppResult
import com.stepwise.core.model.Goal
import com.stepwise.core.model.Milestone

/**
 * Maps a freshly-loaded (Goal, Milestones) pair onto the current [GoalDetailUiState] - extracted
 * out of [GoalDetailViewModel]'s own `applyLoaded` once that class crossed detekt's
 * TooManyFunctions threshold. A single `when` expression (rather than the original's sequence of
 * early returns) also keeps this under detekt's ReturnCount threshold.
 */
internal fun goalDetailStateFrom(
    current: GoalDetailUiState,
    goalResult: AppResult<Goal?>,
    milestonesResult: AppResult<List<Milestone>>,
): GoalDetailUiState {
    val goal = (goalResult as? AppResult.Success)?.value
    val milestones = (milestonesResult as? AppResult.Success)?.value?.sortedBy { it.sortOrder }
    return when {
        goalResult is AppResult.Failure || milestonesResult is AppResult.Failure -> GoalDetailUiState.Error
        goal == null -> GoalDetailUiState.NotFound
        current is GoalDetailUiState.Content -> current.copy(goal = goal, milestones = milestones.orEmpty())
        else -> GoalDetailUiState.Content(goal = goal, milestones = milestones.orEmpty())
    }
}
