package com.stepwise.domain.usecase

import com.stepwise.core.common.AppError
import com.stepwise.core.common.AppResult
import com.stepwise.core.common.FakeClock
import com.stepwise.core.common.FakeIdGenerator
import com.stepwise.core.model.Activity
import com.stepwise.core.model.ActivityId
import com.stepwise.core.model.ExecutableKind
import com.stepwise.core.model.RecurrencePattern
import com.stepwise.core.model.SyncMetadata
import com.stepwise.core.model.Task
import com.stepwise.core.model.TaskId
import com.stepwise.core.model.UserId
import com.stepwise.domain.fake.FakeActivityRepository
import com.stepwise.domain.fake.FakeHabitRepository
import com.stepwise.domain.fake.FakeRecurrenceRuleRepository
import com.stepwise.domain.fake.FakeTaskRepository
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Instant

private val NOW = Instant.fromEpochMilliseconds(1_000_000)
private val OWNER = UserId("owner-1")
private val OTHER_USER = UserId("other-1")
private val STARTS_ON = LocalDate(2026, 11, 14)

private fun task(userId: UserId = OWNER): Task =
    Task(
        id = TaskId("task-1"),
        userId = userId,
        goalId = null,
        projectId = null,
        lifeAreaId = null,
        title = "Read a chapter",
        date = null,
        time = null,
        deadline = null,
        durationMinutes = null,
        priority = null,
        reminderId = null,
        metricId = null,
        plannedResult = null,
        goalImpactScore = null,
        archived = false,
        createdAt = NOW,
        sync = SyncMetadata(updatedAt = NOW, version = 1, deletedAt = null),
    )

private fun activity(): Activity =
    Activity(
        id = ActivityId("activity-1"),
        userId = OWNER,
        lifeAreaId = null,
        title = "Running",
        icon = null,
        archived = false,
        sync = SyncMetadata(updatedAt = NOW, version = 1, deletedAt = null),
    )

class CreateRecurrenceRuleUseCaseTest {
    private val taskRepository = FakeTaskRepository()
    private val activityRepository = FakeActivityRepository()
    private val habitRepository = FakeHabitRepository()
    private val ruleRepository = FakeRecurrenceRuleRepository()
    private val useCase =
        CreateRecurrenceRuleUseCase(
            taskRepository,
            activityRepository,
            habitRepository,
            ruleRepository,
            FakeIdGenerator(),
            FakeClock(NOW),
        )

    @Test
    fun `creating a Daily rule for an owned Task succeeds`() =
        runTest {
            taskRepository.seed(task())
            val command =
                CreateRecurrenceRuleCommand(
                    userId = OWNER,
                    ownerType = ExecutableKind.TASK,
                    ownerId = "task-1",
                    pattern = RecurrencePattern.Daily,
                    startsOn = STARTS_ON,
                )

            val result = useCase.execute(command)

            check(result is AppResult.Success)
        }

    @Test
    fun `creating a rule for an owned Activity succeeds`() =
        runTest {
            activityRepository.seed(activity())
            val command =
                CreateRecurrenceRuleCommand(
                    userId = OWNER,
                    ownerType = ExecutableKind.ACTIVITY,
                    ownerId = "activity-1",
                    pattern = RecurrencePattern.Daily,
                    startsOn = STARTS_ON,
                )

            val result = useCase.execute(command)

            check(result is AppResult.Success)
        }

    @Test
    fun `creating a rule for a Task owned by a different user is rejected`() =
        runTest {
            taskRepository.seed(task(userId = OTHER_USER))
            val command =
                CreateRecurrenceRuleCommand(
                    userId = OWNER,
                    ownerType = ExecutableKind.TASK,
                    ownerId = "task-1",
                    pattern = RecurrencePattern.Daily,
                    startsOn = STARTS_ON,
                )

            val result = useCase.execute(command)

            check(result is AppResult.Failure)
            assertTrue(result.error is AppError.Auth)
        }

    @Test
    fun `creating a rule for a non-existent Task is rejected`() =
        runTest {
            val command =
                CreateRecurrenceRuleCommand(
                    userId = OWNER,
                    ownerType = ExecutableKind.TASK,
                    ownerId = "missing",
                    pattern = RecurrencePattern.Daily,
                    startsOn = STARTS_ON,
                )

            val result = useCase.execute(command)

            check(result is AppResult.Failure)
            assertTrue(result.error is AppError.Validation)
        }

    @Test
    fun `a non-positive EveryNDays intervalDays is rejected eagerly, not left for later resolution`() =
        runTest {
            taskRepository.seed(task())
            val command =
                CreateRecurrenceRuleCommand(
                    userId = OWNER,
                    ownerType = ExecutableKind.TASK,
                    ownerId = "task-1",
                    pattern = RecurrencePattern.EveryNDays(intervalDays = 0),
                    startsOn = STARTS_ON,
                )

            val result = useCase.execute(command)

            check(result is AppResult.Failure)
            assertTrue(result.error is AppError.Validation)
        }

    @Test
    fun `an invalid Custom rrule is rejected eagerly`() =
        runTest {
            taskRepository.seed(task())
            val command =
                CreateRecurrenceRuleCommand(
                    userId = OWNER,
                    ownerType = ExecutableKind.TASK,
                    ownerId = "task-1",
                    pattern = RecurrencePattern.Custom(rrule = "FREQ=YEARLY;BYMONTH=13;BYMONTHDAY=1"),
                    startsOn = STARTS_ON,
                )

            val result = useCase.execute(command)

            check(result is AppResult.Failure)
            assertTrue(result.error is AppError.Validation)
        }

    @Test
    fun `a Weekly rule with an empty daysOfWeek is rejected eagerly`() =
        runTest {
            taskRepository.seed(task())
            val command =
                CreateRecurrenceRuleCommand(
                    userId = OWNER,
                    ownerType = ExecutableKind.TASK,
                    ownerId = "task-1",
                    pattern = RecurrencePattern.Weekly(emptySet()),
                    startsOn = STARTS_ON,
                )

            val result = useCase.execute(command)

            check(result is AppResult.Failure)
            assertTrue(result.error is AppError.Validation)
        }

    @Test
    fun `an endsOn before startsOn is rejected`() =
        runTest {
            taskRepository.seed(task())
            val command =
                CreateRecurrenceRuleCommand(
                    userId = OWNER,
                    ownerType = ExecutableKind.TASK,
                    ownerId = "task-1",
                    pattern = RecurrencePattern.Daily,
                    startsOn = STARTS_ON,
                    endsOn = LocalDate(2026, 1, 1),
                )

            val result = useCase.execute(command)

            check(result is AppResult.Failure)
            assertTrue(result.error is AppError.Validation)
        }
}
