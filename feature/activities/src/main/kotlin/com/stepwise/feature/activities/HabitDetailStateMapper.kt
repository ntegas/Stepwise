package com.stepwise.feature.activities

import com.stepwise.core.common.AppResult
import com.stepwise.core.model.Goal
import com.stepwise.core.model.GoalActivityLink
import com.stepwise.core.model.Habit
import com.stepwise.core.model.Metric
import com.stepwise.core.model.Session

/** Mirrors [activityDetailStateFrom] exactly, for [HabitDetailUiState]. */
@Suppress("LongParameterList") // One AppResult per independent reactive source this screen's own combine() merges.
internal fun habitDetailStateFrom(
    current: HabitDetailUiState,
    habitResult: AppResult<Habit?>,
    linksResult: AppResult<List<GoalActivityLink>>,
    goalsResult: AppResult<List<Goal>>,
    metricsResult: AppResult<List<Metric>>,
    sessionsResult: AppResult<List<Session>>,
): HabitDetailUiState {
    val habit = (habitResult as? AppResult.Success)?.value
    val anyFailure =
        habitResult is AppResult.Failure ||
            linksResult is AppResult.Failure ||
            goalsResult is AppResult.Failure ||
            metricsResult is AppResult.Failure ||
            sessionsResult is AppResult.Failure
    val links = (linksResult as? AppResult.Success)?.value.orEmpty()
    val goals = (goalsResult as? AppResult.Success)?.value.orEmpty()
    val metrics = (metricsResult as? AppResult.Success)?.value.orEmpty()
    val sessions = (sessionsResult as? AppResult.Success)?.value.orEmpty()
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
