package com.stepwise.feature.goals

import com.stepwise.core.model.Goal

/** `:feature:goals`'s own list-screen state — a loading/error/signed-out moment is always explicit. */
sealed interface GoalsListUiState {
    data object Loading : GoalsListUiState

    data object SignedOut : GoalsListUiState

    data class Content(
        val active: List<Goal>,
        val completed: List<Goal>,
        val archived: List<Goal>,
    ) : GoalsListUiState

    data class Error(
        val message: String,
    ) : GoalsListUiState
}
