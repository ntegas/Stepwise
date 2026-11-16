package com.stepwise.domain.usecase

import com.stepwise.core.common.AppError
import com.stepwise.core.common.AppResult
import com.stepwise.core.model.ActivityId
import com.stepwise.core.model.ExecutableKind
import com.stepwise.core.model.HabitId
import com.stepwise.core.model.Session
import com.stepwise.core.model.TaskId
import com.stepwise.core.model.UserId
import com.stepwise.domain.repository.ActivityRepository
import com.stepwise.domain.repository.HabitRepository
import com.stepwise.domain.repository.SessionRepository
import com.stepwise.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * Activity/Habit Detail's own "Session history for this source" read (`/ROADMAP.md` DEV-018's
 * "Session history/manual entry") - [SessionRepository.observeForSource] is already generic over
 * [ExecutableKind], covering all three source kinds, so this one Use Case works for Task's own
 * (currently unused) future Detail screen too, not only Activity/Habit. Ownership of the *source*
 * is verified before streaming, the same pattern [GetGoalLinksForSourceUseCase] uses for the
 * narrower [com.stepwise.core.model.ProgressSourceKind] case.
 */
class GetSessionHistoryUseCase(
    private val taskRepository: TaskRepository,
    private val activityRepository: ActivityRepository,
    private val habitRepository: HabitRepository,
    private val sessionRepository: SessionRepository,
) {
    fun execute(
        userId: UserId,
        sourceType: ExecutableKind,
        sourceId: String,
    ): Flow<AppResult<List<Session>>> =
        flow {
            val ownership = verifySource(userId, sourceType, sourceId)
            if (ownership is AppResult.Failure) {
                emit(AppResult.Success<List<Session>>(emptyList()))
                return@flow
            }
            emitAll(
                sessionRepository
                    .observeForSource(sourceType, sourceId)
                    .map<List<Session>, AppResult<List<Session>>> { AppResult.Success(it) },
            )
        }.catch { cause ->
            emit(AppResult.Failure(AppError.Unexpected(cause.message ?: "Failed to load session history", cause)))
        }

    private suspend fun verifySource(
        userId: UserId,
        sourceType: ExecutableKind,
        sourceId: String,
    ): AppResult<Unit> =
        when (sourceType) {
            ExecutableKind.TASK -> TaskAccess.verifyOwnedTask(taskRepository, userId, TaskId(sourceId)).map { }
            ExecutableKind.ACTIVITY ->
                ActivityAccess.verifyOwnedActivity(activityRepository, userId, ActivityId(sourceId)).map { }
            ExecutableKind.HABIT -> HabitAccess.verifyOwnedHabit(habitRepository, userId, HabitId(sourceId)).map { }
        }
}
