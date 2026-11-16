package com.stepwise.feature.activities

import com.stepwise.core.common.AppResult
import com.stepwise.core.model.Activity
import com.stepwise.core.model.Goal
import com.stepwise.core.model.GoalActivityLink
import com.stepwise.core.model.Metric
import com.stepwise.core.model.Session

/**
 * Maps a freshly-loaded (Activity, GoalActivityLinks, Goals, Metrics, Sessions) tuple onto the
 * current [ActivityDetailUiState] - extracted out of [ActivityDetailViewModel], the same
 * `goalDetailStateFrom` (DEV-016) pattern applied once a ViewModel's own `applyLoaded` would
 * otherwise cross detekt's TooManyFunctions/CyclomaticComplexMethod thresholds.
 */
internal fun activityDetailStateFrom(
    current: ActivityDetailUiState,
    activityResult: AppResult<Activity?>,
    linksResult: AppResult<List<GoalActivityLink>>,
    goalsResult: AppResult<List<Goal>>,
    metricsResult: AppResult<List<Metric>>,
    sessionsResult: AppResult<List<Session>>,
): ActivityDetailUiState {
    val activity = (activityResult as? AppResult.Success)?.value
    val anyFailure =
        activityResult is AppResult.Failure ||
            linksResult is AppResult.Failure ||
            goalsResult is AppResult.Failure ||
            metricsResult is AppResult.Failure ||
            sessionsResult is AppResult.Failure
    val links = (linksResult as? AppResult.Success)?.value.orEmpty()
    val goals = (goalsResult as? AppResult.Success)?.value.orEmpty()
    val metrics = (metricsResult as? AppResult.Success)?.value.orEmpty()
    val sessions = (sessionsResult as? AppResult.Success)?.value.orEmpty()
    return when {
        anyFailure -> ActivityDetailUiState.Error
        activity == null -> ActivityDetailUiState.NotFound
        current is ActivityDetailUiState.Content ->
            current.copy(activity = activity, goalLinks = links, goals = goals, metrics = metrics, sessions = sessions)
        else ->
            ActivityDetailUiState.Content(
                activity = activity,
                goalLinks = links,
                goals = goals,
                metrics = metrics,
                sessions = sessions,
            )
    }
}
