package com.stepwise.domain.usecase

import com.stepwise.core.common.AppError
import com.stepwise.core.common.AppResult
import com.stepwise.core.model.Habit
import com.stepwise.core.model.HabitId
import com.stepwise.core.model.UserId
import com.stepwise.domain.repository.HabitRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/** The Habits UI's (DEV-018) own read-side Use Cases - mirrors [ActivityReadUseCases.kt] exactly. */
class GetHabitsUseCase(
    private val habitRepository: HabitRepository,
) {
    fun execute(userId: UserId): Flow<AppResult<List<Habit>>> =
        habitRepository
            .observeAll(userId)
            .map<List<Habit>, AppResult<List<Habit>>> { AppResult.Success(it) }
            .catch { cause ->
                emit(AppResult.Failure(AppError.Unexpected(cause.message ?: "Failed to load habits", cause)))
            }
}

/** Habit Detail's own reactive single-Habit read - mirrors [ObserveActivityUseCase] exactly. */
class ObserveHabitUseCase(
    private val habitRepository: HabitRepository,
) {
    fun execute(
        userId: UserId,
        habitId: HabitId,
    ): Flow<AppResult<Habit?>> =
        flow {
            val ownership = HabitAccess.verifyOwnedHabit(habitRepository, userId, habitId)
            if (ownership is AppResult.Failure) {
                emit(AppResult.Success<Habit?>(null))
                return@flow
            }
            emitAll(
                habitRepository.observeById(habitId).map<Habit?, AppResult<Habit?>> { habit ->
                    AppResult.Success(habit?.takeIf { !it.sync.isDeleted })
                },
            )
        }.catch { cause ->
            emit(AppResult.Failure(AppError.Unexpected(cause.message ?: "Failed to load habit", cause)))
        }
}
