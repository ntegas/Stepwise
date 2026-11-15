package com.stepwise.domain.usecase

import com.stepwise.core.common.AppError
import com.stepwise.core.common.AppResult
import com.stepwise.core.common.Clock
import com.stepwise.core.common.IdGenerator
import com.stepwise.core.model.ActivityId
import com.stepwise.core.model.ExecutableKind
import com.stepwise.core.model.HabitId
import com.stepwise.core.model.RecurrenceRule
import com.stepwise.core.model.RecurrenceRuleId
import com.stepwise.core.model.SyncMetadata
import com.stepwise.core.model.TaskId
import com.stepwise.core.model.UserId
import com.stepwise.core.model.occurrenceDatesIn
import com.stepwise.domain.repository.ActivityRepository
import com.stepwise.domain.repository.HabitRepository
import com.stepwise.domain.repository.RecurrenceRuleRepository
import com.stepwise.domain.repository.TaskRepository

/**
 * Creates a [RecurrenceRule] for a Task, Activity, or Habit (`/ROADMAP.md` DEV-013: "recurrence
 * integration") — one engine for all three owner kinds (`/ARCHITECTURE.md` §13), dispatching the
 * ownership check per [ExecutableKind] to [TaskAccess]/[ActivityAccess]/[HabitAccess] rather than
 * three separate rule types or three copies of the same check.
 *
 * **Validates the pattern eagerly, by actually generating one trivial day of dates from it**,
 * rather than re-deriving `RecurrenceDateGenerator`'s own range checks a second time: a rule
 * saved with an invalid `Custom` rrule or a non-positive `EveryNDays.intervalDays` would
 * otherwise persist successfully and only fail much later, the first time
 * [ResolveOccurrencesUseCase] (DEV-010) tries to resolve it — too late to give the caller a
 * useful error. Reusing [RecurrenceRule.occurrenceDatesIn] here means this Use Case can never
 * drift from what the generator actually accepts, since it *is* the generator.
 */
class CreateRecurrenceRuleUseCase(
    private val taskRepository: TaskRepository,
    private val activityRepository: ActivityRepository,
    private val habitRepository: HabitRepository,
    private val recurrenceRuleRepository: RecurrenceRuleRepository,
    private val idGenerator: IdGenerator,
    private val clock: Clock,
) {
    suspend fun execute(command: CreateRecurrenceRuleCommand): AppResult<RecurrenceRule> =
        verifyOwner(command.userId, command.ownerType, command.ownerId).andThen {
            val rule = buildRule(command)
            validatePattern(rule).andThen {
                recurrenceRuleRepository.upsert(rule).map { rule }
            }
        }

    private suspend fun verifyOwner(
        userId: UserId,
        ownerType: ExecutableKind,
        ownerId: String,
    ): AppResult<Unit> =
        when (ownerType) {
            ExecutableKind.TASK -> TaskAccess.verifyOwnedTask(taskRepository, userId, TaskId(ownerId)).map { }
            ExecutableKind.ACTIVITY ->
                ActivityAccess.verifyOwnedActivity(activityRepository, userId, ActivityId(ownerId)).map { }
            ExecutableKind.HABIT -> HabitAccess.verifyOwnedHabit(habitRepository, userId, HabitId(ownerId)).map { }
        }

    private fun validatePattern(rule: RecurrenceRule): AppResult<Unit> {
        if (rule.endsOn != null && rule.endsOn < rule.startsOn) {
            return AppResult.Failure(AppError.Validation("endsOn must not be before startsOn", field = "endsOn"))
        }
        return try {
            rule.occurrenceDatesIn(rule.startsOn..rule.startsOn)
            AppResult.Success(Unit)
        } catch (e: IllegalArgumentException) {
            AppResult.Failure(AppError.Validation(e.message ?: "Invalid recurrence pattern", field = "pattern"))
        }
    }

    private fun buildRule(command: CreateRecurrenceRuleCommand): RecurrenceRule =
        RecurrenceRule(
            id = RecurrenceRuleId(idGenerator.newId()),
            userId = command.userId,
            ownerType = command.ownerType,
            ownerId = command.ownerId,
            pattern = command.pattern,
            time = command.time,
            startsOn = command.startsOn,
            endsOn = command.endsOn,
            sync = SyncMetadata(updatedAt = clock.now(), version = 1, deletedAt = null),
        )
}
