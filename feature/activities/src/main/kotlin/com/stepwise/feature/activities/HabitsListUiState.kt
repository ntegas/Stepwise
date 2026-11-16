package com.stepwise.feature.activities

import com.stepwise.core.model.Habit

/** `:feature:activities`'s own Habits-list state (`/ROADMAP.md` DEV-018) - mirrors [ActivitiesListUiState] exactly. */
sealed interface HabitsListUiState {
    data object Loading : HabitsListUiState

    data object SignedOut : HabitsListUiState

    data object Error : HabitsListUiState

    data class Content(
        val active: List<Habit>,
        val archived: List<Habit>,
    ) : HabitsListUiState
}
