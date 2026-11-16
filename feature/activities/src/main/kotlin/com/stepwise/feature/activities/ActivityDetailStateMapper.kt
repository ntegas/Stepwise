package com.stepwise.feature.activities

import com.stepwise.core.common.AppResult
import com.stepwise.core.model.Activity
import com.stepwise.core.model.Goal
import com.stepwise.core.model.GoalActivityLink
import com.stepwise.core.model.Metric
import com.stepwise.core.model.Session

/**
 * Bundles [ActivityDetailViewModel]'s own `combine()` of five independent reactive sources into
 * one value, so [activityDetailStateFrom] takes `(current, loaded)` rather than six separate
 * parameters - DEV-018 review, architecture reviewer, MEDIUM: the first draft suppressed
 * detekt's `LongParameterList` on the mapper instead of bundling, even though every one of these
 * five results was already available as one object (the ViewModel's own private `Loaded` class)
 * at every call site; promoting that bundle out of `private` removes the suppression entirely
 * rather than justifying it.
 */
internal data class ActivityDetailLoaded(
    val activity: AppResult<Activity?>,
    val links: AppResult<List<GoalActivityLink>>,
    val goals: AppResult<List<Goal>>,
    val metrics: AppResult<List<Metric>>,
    val sessions: AppResult<List<Session>>,
)

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
