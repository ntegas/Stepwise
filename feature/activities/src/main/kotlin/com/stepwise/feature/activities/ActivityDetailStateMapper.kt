package com.stepwise.feature.activities

import com.stepwise.core.common.AppResult

/**
 * Maps a freshly-loaded (Activity, GoalActivityLinks, Goals, Metrics, Sessions) tuple onto the
 * current [ActivityDetailUiState] - extracted out of [ActivityDetailViewModel], the same
 * `goalDetailStateFrom` (DEV-016) pattern applied once a ViewModel's own `applyLoaded` would
 * otherwise cross detekt's TooManyFunctions/CyclomaticComplexMethod thresholds.
 */
internal fun activityDetailStateFrom(
    current: ActivityDetailUiState,
    loaded: ActivityDetailLoaded,
): ActivityDetailUiState {
    val activity = (loaded.activity as? AppResult.Success)?.value
    val anyFailure =
        loaded.activity is AppResult.Failure ||
            loaded.links is AppResult.Failure ||
            loaded.goals is AppResult.Failure ||
            loaded.metrics is AppResult.Failure ||
            loaded.sessions is AppResult.Failure
    val links = (loaded.links as? AppResult.Success)?.value.orEmpty()
    val goals = (loaded.goals as? AppResult.Success)?.value.orEmpty()
    val metrics = (loaded.metrics as? AppResult.Success)?.value.orEmpty()
    val sessions = (loaded.sessions as? AppResult.Success)?.value.orEmpty()
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
