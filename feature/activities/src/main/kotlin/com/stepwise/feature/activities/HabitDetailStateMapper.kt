package com.stepwise.feature.activities

import com.stepwise.core.common.AppResult

/** Mirrors [activityDetailStateFrom] exactly, for [HabitDetailUiState]. */
internal fun habitDetailStateFrom(
    current: HabitDetailUiState,
    loaded: HabitDetailLoaded,
): HabitDetailUiState {
    val habit = (loaded.habit as? AppResult.Success)?.value
    val anyFailure =
        loaded.habit is AppResult.Failure ||
            loaded.links is AppResult.Failure ||
            loaded.goals is AppResult.Failure ||
            loaded.metrics is AppResult.Failure ||
            loaded.sessions is AppResult.Failure
    val links = (loaded.links as? AppResult.Success)?.value.orEmpty()
    val goals = (loaded.goals as? AppResult.Success)?.value.orEmpty()
    val metrics = (loaded.metrics as? AppResult.Success)?.value.orEmpty()
    val sessions = (loaded.sessions as? AppResult.Success)?.value.orEmpty()
    return when {
        anyFailure -> HabitDetailUiState.Error
        habit == null -> HabitDetailUiState.NotFound
        current is HabitDetailUiState.Content ->
            current.copy(habit = habit, goalLinks = links, goals = goals, metrics = metrics, sessions = sessions)
        else ->
            HabitDetailUiState.Content(
                habit = habit,
                goalLinks = links,
                goals = goals,
                metrics = metrics,
                sessions = sessions,
            )
    }
}
