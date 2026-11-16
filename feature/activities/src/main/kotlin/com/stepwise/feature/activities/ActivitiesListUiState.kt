package com.stepwise.feature.activities

import com.stepwise.core.model.Activity

/**
 * `:feature:activities`'s own Activities-list state (`/ROADMAP.md` DEV-018) - the same
 * explicit-loading/error/signed-out shape `GoalsListUiState` (DEV-016) established. [Error]
 * carries no message - `ActivitiesListScreen` resolves its own fixed, localized string instead,
 * the same non-English-locale-safe convention every prior DEV task's list screen already uses.
 */
sealed interface ActivitiesListUiState {
    data object Loading : ActivitiesListUiState

    data object SignedOut : ActivitiesListUiState

    data object Error : ActivitiesListUiState

    data class Content(
        val active: List<Activity>,
        val archived: List<Activity>,
    ) : ActivitiesListUiState
}
