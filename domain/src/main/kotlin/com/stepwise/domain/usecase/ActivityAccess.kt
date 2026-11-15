package com.stepwise.domain.usecase

import com.stepwise.core.common.AppError
import com.stepwise.core.common.AppResult
import com.stepwise.core.model.Activity
import com.stepwise.core.model.ActivityId
import com.stepwise.core.model.UserId
import com.stepwise.domain.repository.ActivityRepository

/**
 * Shared ownership check for any Use Case that needs to load an [Activity] and verify the
 * caller owns it before acting on it — the same `*Access` pattern [GoalAccess] (DEV-011) and
 * [RecurrenceRuleAccess] (DEV-010) already established, extracted here because
 * [EditActivityUseCase] and [LinkActivityToGoalUseCase] both need the identical check.
 *
 * [userId] is trusted input, same disclosure as [GoalAccess]'s own KDoc.
 */
object ActivityAccess {
    suspend fun verifyOwnedActivity(
        repository: ActivityRepository,
        userId: UserId,
        activityId: ActivityId,
    ): AppResult<Activity> =
        repository.getById(activityId).andThen { activity ->
            when {
                activity == null -> AppResult.Failure(AppError.Validation("Activity not found", field = "activityId"))
                activity.userId != userId -> AppResult.Failure(AppError.Auth("Not authorized for this Activity"))
                activity.sync.isDeleted -> AppResult.Failure(AppError.Domain("Cannot modify a deleted Activity"))
                else -> AppResult.Success(activity)
            }
        }
}
