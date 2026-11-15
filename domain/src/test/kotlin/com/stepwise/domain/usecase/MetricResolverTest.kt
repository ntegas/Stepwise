package com.stepwise.domain.usecase

import com.stepwise.core.common.AppError
import com.stepwise.core.common.AppResult
import com.stepwise.core.model.Activity
import com.stepwise.core.model.ActivityId
import com.stepwise.core.model.ExecutableKind
import com.stepwise.core.model.ExecutionStatus
import com.stepwise.core.model.Habit
import com.stepwise.core.model.HabitId
import com.stepwise.core.model.MetricId
import com.stepwise.core.model.SyncMetadata
import com.stepwise.core.model.Task
import com.stepwise.core.model.TaskId
import com.stepwise.core.model.UserId
import com.stepwise.domain.fake.FakeActivityRepository
import com.stepwise.domain.fake.FakeHabitRepository
import com.stepwise.domain.fake.FakeTaskRepository
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Instant

private val NOW = Instant.fromEpochMilliseconds(1_000_000)
private val USER = UserId("user-1")
private val OTHER_USER = UserId("user-2")
private val DATE = LocalDate(2026, 11, 14)
private val SYNC = SyncMetadata(updatedAt = NOW, version = 1, deletedAt = null)

class MetricResolverTest {
    private val taskRepository = FakeTaskRepository()
    private val activityRepository = FakeActivityRepository()
    private val habitRepository = FakeHabitRepository()
    private val resolver = MetricResolver(taskRepository, activityRepository, habitRepository)

    @Test
    fun `a Task with no metricId at all resolves to an empty metric list`() =
        runTest {
            taskRepository.seed(task(metricId = null, plannedResult = null))
            val target = CompletionTarget.OneOff(ExecutableKind.TASK, "task-1", DATE)

            val result = resolver.resolve(USER, target, ExecutionStatus.DONE, overrides = emptyList())

            check(result is AppResult.Success)
            assertTrue(result.value.isEmpty())
        }

    @Test
    fun `completing an unknown Activity id is rejected`() =
        runTest {
            val target = CompletionTarget.OneOff(ExecutableKind.ACTIVITY, "no-such-activity", DATE)

            val result = resolver.resolve(USER, target, ExecutionStatus.DONE, overrides = emptyList())

            check(result is AppResult.Failure)
            assertTrue(result.error is AppError.Validation)
        }

    @Test
    fun `completing a Task owned by a different user is rejected`() =
        runTest {
            taskRepository.seed(task(metricId = null, plannedResult = null, userId = OTHER_USER))
            val target = CompletionTarget.OneOff(ExecutableKind.TASK, "task-1", DATE)

            val result = resolver.resolve(USER, target, ExecutionStatus.DONE, overrides = emptyList())

            check(result is AppResult.Failure)
            assertTrue(result.error is AppError.Auth)
        }

    @Test
    fun `completing a tombstoned Task is rejected`() =
        runTest {
            val deletedSync = SyncMetadata(updatedAt = NOW, version = 1, deletedAt = NOW)
            taskRepository.seed(task(metricId = null, plannedResult = null, sync = deletedSync))
            val target = CompletionTarget.OneOff(ExecutableKind.TASK, "task-1", DATE)

            val result = resolver.resolve(USER, target, ExecutionStatus.DONE, overrides = emptyList())

            check(result is AppResult.Failure)
            assertTrue(result.error is AppError.Domain)
        }

    @Test
    fun `completing an Activity owned by a different user is rejected`() =
        runTest {
            activityRepository.seed(
                Activity(
                    id = ActivityId("activity-1"),
                    userId = OTHER_USER,
                    lifeAreaId = null,
                    title = "Running",
                    icon = null,
                    archived = false,
                    sync = SYNC,
                ),
            )
            val target = CompletionTarget.OneOff(ExecutableKind.ACTIVITY, "activity-1", DATE)

            val result = resolver.resolve(USER, target, ExecutionStatus.DONE, overrides = emptyList())

            check(result is AppResult.Failure)
            assertTrue(result.error is AppError.Auth)
        }

    @Test
    fun `completing a Habit owned by a different user is rejected`() =
        runTest {
            val metricId = MetricId("count")
            habitRepository.seed(
                Habit(
                    id = HabitId("habit-1"),
                    userId = OTHER_USER,
                    activityId = null,
                    title = "Stretch",
                    targetValue = 1.0,
                    metricId = metricId,
                    archived = false,
                    sync = SYNC,
                ),
            )
            val target = CompletionTarget.OneOff(ExecutableKind.HABIT, "habit-1", DATE)

            val result = resolver.resolve(USER, target, ExecutionStatus.DONE, overrides = emptyList())

            check(result is AppResult.Failure)
            assertTrue(result.error is AppError.Auth)
        }

    @Test
    fun `a caller override replaces the entity's own default for the same metric`() =
        runTest {
            val metricId = MetricId("duration_minutes")
            taskRepository.seed(task(metricId = metricId, plannedResult = 30.0))
            val target = CompletionTarget.OneOff(ExecutableKind.TASK, "task-1", DATE)

            val result =
                resolver.resolve(
                    USER,
                    target,
                    ExecutionStatus.DONE,
                    overrides = listOf(MetricEntry(metricId, plannedValue = 30.0, actualValue = 45.0)),
                )

            check(result is AppResult.Success)
            assertEquals(45.0, result.value.single().actualValue)
        }

    private fun task(
        metricId: MetricId?,
        plannedResult: Double?,
        userId: UserId = USER,
        sync: SyncMetadata = SYNC,
    ): Task =
        Task(
            id = TaskId("task-1"),
            userId = userId,
            goalId = null,
            projectId = null,
            lifeAreaId = null,
            title = "Read",
            date = DATE,
            time = null,
            deadline = null,
            durationMinutes = null,
            priority = null,
            reminderId = null,
            metricId = metricId,
            plannedResult = plannedResult,
            goalImpactScore = null,
            archived = false,
            createdAt = NOW,
            sync = sync,
        )
}
