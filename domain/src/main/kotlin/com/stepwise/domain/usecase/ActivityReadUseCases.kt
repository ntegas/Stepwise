package com.stepwise.domain.usecase

import com.stepwise.core.common.AppError
import com.stepwise.core.common.AppResult
import com.stepwise.core.model.Activity
import com.stepwise.core.model.ActivityId
import com.stepwise.core.model.UserId
import com.stepwise.domain.repository.ActivityRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * The Activities UI's (DEV-018) own read-side Use Cases - the same thin-pass-through-over-
 * [ActivityRepository] shape [GoalReadUseCases.kt]'s `GetGoalsUseCase`/`ObserveGoalUseCase`
 * already established at DEV-016, including the same ownership-centralization and
 * not-found/not-owned non-disclosure this codebase's every other read Use Case already gives.
 */
class GetActivitiesUseCase(
    private val activityRepository: ActivityRepository,
) {
    fun execute(userId: UserId): Flow<AppResult<List<Activity>>> =
        activityRepository
            .observeAll(userId)
            .map<List<Activity>, AppResult<List<Activity>>> { AppResult.Success(it) }
            .catch { cause ->
                emit(AppResult.Failure(AppError.Unexpected(cause.message ?: "Failed to load activities", cause)))
            }
}

/**
 * Activity Detail's own reactive single-Activity read, [ActivityRepository.observeById]-backed
 * (added at this task rather than filtering [GetActivitiesUseCase]'s entire per-user list) -
 * the same [ObserveGoalUseCase] pattern, proactively applied here instead of waiting to
 * rediscover the DEV-016 architecture-review finding that motivated it.
 */
class ObserveActivityUseCase(
    private val activityRepository: ActivityRepository,
) {
    fun execute(
        userId: UserId,
        activityId: ActivityId,
    ): Flow<AppResult<Activity?>> =
        flow {
            val ownership = ActivityAccess.verifyOwnedActivity(activityRepository, userId, activityId)
            if (ownership is AppResult.Failure) {
                // Not found and not owned read back identically here - see ObserveGoalUseCase's
                // own KDoc for why.
                emit(AppResult.Success<Activity?>(null))
                return@flow
            }
            emitAll(
                activityRepository.observeById(activityId).map<Activity?, AppResult<Activity?>> { activity ->
                    AppResult.Success(activity?.takeIf { !it.sync.isDeleted })
                },
            )
        }.catch { cause ->
            emit(AppResult.Failure(AppError.Unexpected(cause.message ?: "Failed to load activity", cause)))
        }
}
