package com.stepwise.feature.activities

import com.stepwise.core.model.Goal
import com.stepwise.core.model.GoalActivityLink
import com.stepwise.core.model.Habit
import com.stepwise.core.model.Metric
import com.stepwise.core.model.Session

/** `:feature:activities`'s own Habit Detail state (`/ROADMAP.md` DEV-018) - mirrors [ActivityDetailUiState] exactly. */
sealed interface HabitDetailUiState {
    data object Loading : HabitDetailUiState

    data object NotFound : HabitDetailUiState

    data object Error : HabitDetailUiState

    data class Content(
        val habit: Habit,
        val goalLinks: List<GoalActivityLink>,
        val goals: List<Goal>,
        val metrics: List<Metric>,
        val sessions: List<Session>,
        val actionFailed: Boolean = false,
        val linkGoalDialogVisible: Boolean = false,
        val linkGoalDialog: LinkGoalDialogState = LinkGoalDialogState(),
        val manualEntryDialogVisible: Boolean = false,
        val manualEntryDialog: ManualEntryDialogState? = null,
    ) : HabitDetailUiState
}
