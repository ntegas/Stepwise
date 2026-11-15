package com.stepwise.domain.usecase

import com.stepwise.core.common.AppResult
import com.stepwise.core.common.Clock
import com.stepwise.core.common.IdGenerator
import com.stepwise.core.model.Habit
import com.stepwise.core.model.HabitId
import com.stepwise.core.model.SyncMetadata
import com.stepwise.domain.repository.ActivityRepository
import com.stepwise.domain.repository.HabitRepository
import com.stepwise.domain.repository.MetricRepository

/**
 * Creates a new [Habit] (`/ROADMAP.md` DEV-013) — verifies [metricId][CreateHabitCommand.metricId]
 * exists and [targetValue][CreateHabitCommand.targetValue] is sane ([HabitValidation]), and that
 * an attached [activityId][CreateHabitCommand.activityId] belongs to the same caller
 * ([CrossEntityOwnership.verifyActivity]).
 */
class CreateHabitUseCase(
    private val habitRepository: HabitRepository,
    private val activityRepository: ActivityRepository,
    private val metricRepository: MetricRepository,
    private val idGenerator: IdGenerator,
    private val clock: Clock,
) {
    suspend fun execute(command: CreateHabitCommand): AppResult<Habit> =
        HabitValidation.validateTargetValue(command.targetValue).andThen {
            HabitValidation.validateMetricExists(metricRepository, command.metricId).andThen {
                CrossEntityOwnership.verifyActivity(activityRepository, command.userId, command.activityId).andThen {
                    val habit = buildHabit(command)
                    habitRepository.upsert(habit).map { habit }
                }
            }
        }

    private fun buildHabit(command: CreateHabitCommand): Habit =
        Habit(
            id = HabitId(idGenerator.newId()),
            userId = command.userId,
            activityId = command.activityId,
            title = command.title,
            targetValue = command.targetValue,
            metricId = command.metricId,
            archived = false,
            sync = SyncMetadata(updatedAt = clock.now(), version = 1, deletedAt = null),
        )
}
