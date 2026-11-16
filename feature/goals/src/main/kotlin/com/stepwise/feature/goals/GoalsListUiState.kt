package com.stepwise.feature.goals

import com.stepwise.core.model.Goal

/**
 * `:feature:goals`'s own list-screen state — a loading/error/signed-out moment is always explicit.
 * [Error] carries no message: `GoalsListScreen` resolves its own fixed, localized
 * `goals_error_generic` string instead of surfacing a raw [com.stepwise.core.common.AppError]
 * message (DEV-016 design review, HIGH — a non-English-locale user otherwise sees untranslated
 * developer-facing English text on a load failure).
 */
sealed interface GoalsListUiState {
    data object Loading : GoalsListUiState

    data object SignedOut : GoalsListUiState

    data class Content(
        val active: List<Goal>,
        val completed: List<Goal>,
        val archived: List<Goal>,
    ) : GoalsListUiState

    data object Error : GoalsListUiState
}
