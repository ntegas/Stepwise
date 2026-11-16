package com.stepwise.domain.usecase

import com.stepwise.core.common.AppError
import com.stepwise.core.common.AppResult
import com.stepwise.core.model.ActivityId
import com.stepwise.core.model.GoalActivityLink
import com.stepwise.core.model.HabitId
import com.stepwise.core.model.Metric
import com.stepwise.core.model.ProgressSourceKind
import com.stepwise.core.model.UserId
import com.stepwise.domain.repository.ActivityRepository
import com.stepwise.domain.repository.GoalActivityLinkRepository
import com.stepwise.domain.repository.HabitRepository
import com.stepwise.domain.repository.MetricRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * Activity/Habit Detail's own reactive read of "which Goals is this source linked to, via which
 * metric" (DEV-018) - [GoalActivityLinkRepository.observeForSource] is already generic over
 * [ProgressSourceKind], so one Use Case serves both Detail screens rather than two near-identical
 * copies, the same "share when truly identical" reasoning DEV-017's `resolveScheduledItems`
 * extraction already applied. Ownership is verified against the *source* (Activity or Habit)
 * before streaming, the same dispatch [LinkActivityToGoalUseCase.verifySource] already performs -
 * duplicated here rather than extracted from that already-reviewed DEV-012 file, since this task
 * has no need to touch it (a small, disclosed duplication, not a new shared abstraction forced
 * onto code nothing else requires changing).
 */
class GetGoalLinksForSourceUseCase(
    private val activityRepository: ActivityRepository,
    private val habitRepository: HabitRepository,
    private val goalActivityLinkRepository: GoalActivityLinkRepository,
) {
    fun execute(
        userId: UserId,
        sourceType: ProgressSourceKind,
        sourceId: String,
    ): Flow<AppResult<List<GoalActivityLink>>> =
        flow {
            val ownership = verifySource(userId, sourceType, sourceId)
            if (ownership is AppResult.Failure) {
                emit(AppResult.Success<List<GoalActivityLink>>(emptyList()))
                return@flow
            }
            emitAll(
                goalActivityLinkRepository
                    .observeForSource(sourceType, sourceId)
                    .map<List<GoalActivityLink>, AppResult<List<GoalActivityLink>>> { AppResult.Success(it) },
            )
        }.catch { cause ->
            emit(AppResult.Failure(AppError.Unexpected(cause.message ?: "Failed to load goal links", cause)))
        }

    private suspend fun verifySource(
        userId: UserId,
        sourceType: ProgressSourceKind,
        sourceId: String,
    ): AppResult<Unit> =
        when (sourceType) {
            ProgressSourceKind.ACTIVITY ->
                ActivityAccess.verifyOwnedActivity(activityRepository, userId, ActivityId(sourceId)).map { }
            ProgressSourceKind.HABIT ->
                HabitAccess.verifyOwnedHabit(habitRepository, userId, HabitId(sourceId)).map { }
        }
}

/**
 * The global [Metric] catalog read (`:core:model`'s own KDoc: no [UserId]/ownership applies, a
 * shared reference table) - needed by Activity/Habit Detail's manual-entry form to label each
 * metric input with its [Metric.key]/[Metric.canonicalUnit] rather than a bare id, and by the
 * "link to a Goal" picker's own metric choice.
 */
class GetMetricsUseCase(
    private val metricRepository: MetricRepository,
) {
    fun execute(): Flow<AppResult<List<Metric>>> =
        metricRepository
            .observeAll()
            .map<List<Metric>, AppResult<List<Metric>>> { AppResult.Success(it) }
            .catch { cause ->
                emit(AppResult.Failure(AppError.Unexpected(cause.message ?: "Failed to load metrics", cause)))
            }
}
