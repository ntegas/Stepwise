package com.stepwise.domain.usecase

import com.stepwise.core.common.AppError
import com.stepwise.core.common.AppResult
import com.stepwise.core.model.Task
import com.stepwise.core.model.TaskId
import com.stepwise.core.model.UserId
import com.stepwise.domain.repository.TaskRepository

/**
 * Shared ownership check for any Use Case that needs to load a [Task] and verify the caller
 * owns it — the same `*Access` pattern [ActivityAccess]/[HabitAccess]/[GoalAccess] already
 * established. Extracted at DEV-013 the same way [HabitAccess] was at DEV-012: a second
 * consumer ([EditTaskUseCase]) needed the identical check [MetricResolver.taskDefaultMetrics]
 * (DEV-009) already performed inline — the inline DEV-009 check never verified [Task.sync]'s
 * tombstone state either, closed here the same way DEV-012 closed it for Habit.
 *
 * [userId] is trusted input, same disclosure as every other `*Access` object's own KDoc.
 */
object TaskAccess {
    suspend fun verifyOwnedTask(
        repository: TaskRepository,
        userId: UserId,
        taskId: TaskId,
    ): AppResult<Task> =
        repository.getById(taskId).andThen { task ->
            when {
                task == null -> AppResult.Failure(AppError.Validation("Task not found", field = "taskId"))
                task.userId != userId -> AppResult.Failure(AppError.Auth("Not authorized for this Task"))
                task.sync.isDeleted -> AppResult.Failure(AppError.Domain("Cannot modify a deleted Task"))
                else -> AppResult.Success(task)
            }
        }
}
