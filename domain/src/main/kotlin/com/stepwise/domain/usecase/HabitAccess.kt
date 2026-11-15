package com.stepwise.domain.usecase

import com.stepwise.core.common.AppError
import com.stepwise.core.common.AppResult
import com.stepwise.core.model.Habit
import com.stepwise.core.model.HabitId
import com.stepwise.core.model.UserId
import com.stepwise.domain.repository.HabitRepository

/**
 * Shared ownership check for any Use Case that needs to load a [Habit] and verify the caller
 * owns it — the same `*Access` pattern [ActivityAccess]/[GoalAccess]/[RecurrenceRuleAccess]
 * already established. Extracted at DEV-012 (architecture review) once a second consumer
 * ([LinkActivityToGoalUseCase]) needed the identical check [MetricResolver.habitDefaultMetrics]
 * (DEV-009) already performed inline — the two had already drifted (the inline DEV-009 check
 * never verified [Habit.sync]'s tombstone state), exactly the risk centralizing this early avoids
 * going forward (`/DEVELOPMENT_PROTOCOL.md`'s anti-duplication rule).
 *
 * [userId] is trusted input, same disclosure as [GoalAccess]/[ActivityAccess]'s own KDoc.
 */
object HabitAccess {
    suspend fun verifyOwnedHabit(
        repository: HabitRepository,
        userId: UserId,
        habitId: HabitId,
    ): AppResult<Habit> =
        repository.getById(habitId).andThen { habit ->
            when {
                habit == null -> AppResult.Failure(AppError.Validation("Habit not found", field = "habitId"))
                habit.userId != userId -> AppResult.Failure(AppError.Auth("Not authorized for this Habit"))
                habit.sync.isDeleted -> AppResult.Failure(AppError.Domain("Cannot modify a deleted Habit"))
                else -> AppResult.Success(habit)
            }
        }
}
