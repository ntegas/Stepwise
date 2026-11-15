package com.stepwise.domain.usecase

import com.stepwise.core.common.AppResult
import com.stepwise.core.common.Clock
import com.stepwise.core.model.Habit
import com.stepwise.domain.repository.ActivityRepository
import com.stepwise.domain.repository.HabitRepository
import com.stepwise.domain.repository.MetricRepository

/**
 * Edits an existing [Habit]'s own fields (`/ROADMAP.md` DEV-013), including toggling
 * [Habit.archived]. Re-verifies [HabitValidation] and [activityId][EditHabitCommand.activityId]
 * ownership on the resulting Habit, since an edit can change `targetValue`/`metricId`/`activityId`.
 */
class EditHabitUseCase(
    private val habitRepository: HabitRepository,
    private val activityRepository: ActivityRepository,
    private val metricRepository: MetricRepository,
    private val clock: Clock,
) {
    suspend fun execute(command: EditHabitCommand): AppResult<Habit> =
        HabitAccess.verifyOwnedHabit(habitRepository, command.userId, command.habitId).andThen { existing ->
            writeEdit(existing, command)
        }

    private suspend fun writeEdit(
        existing: Habit,
        command: EditHabitCommand,
    ): AppResult<Habit> {
        val updated = applyEdits(existing, command)
        return HabitValidation.validateTargetValue(updated.targetValue).andThen {
            HabitValidation.validateMetricExists(metricRepository, updated.metricId).andThen {
                CrossEntityOwnership
                    .verifyActivity(activityRepository, updated.userId, updated.activityId)
                    .andThen {
                        habitRepository.upsert(updated).map { updated }
                    }
            }
        }
    }

    private fun applyEdits(
        existing: Habit,
        command: EditHabitCommand,
    ): Habit =
        existing.copy(
            title = command.title ?: existing.title,
            targetValue = command.targetValue ?: existing.targetValue,
            metricId = command.metricId ?: existing.metricId,
            activityId = command.activityId ?: existing.activityId,
            archived = command.archived ?: existing.archived,
            sync = existing.sync.copy(updatedAt = clock.now()),
        )
}
