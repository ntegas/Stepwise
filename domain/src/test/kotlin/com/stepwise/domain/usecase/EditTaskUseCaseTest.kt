package com.stepwise.domain.usecase

import com.stepwise.core.common.AppError
import com.stepwise.core.common.AppResult
import com.stepwise.core.common.FakeClock
import com.stepwise.core.model.Goal
import com.stepwise.core.model.GoalId
import com.stepwise.core.model.GoalProgressMode
import com.stepwise.core.model.GoalType
import com.stepwise.core.model.LifeArea
import com.stepwise.core.model.LifeAreaId
import com.stepwise.core.model.LifecycleStatus
import com.stepwise.core.model.Project
import com.stepwise.core.model.ProjectId
import com.stepwise.core.model.SyncMetadata
import com.stepwise.core.model.Task
import com.stepwise.core.model.TaskId
import com.stepwise.core.model.UserId
import com.stepwise.domain.fake.FakeGoalRepository
import com.stepwise.domain.fake.FakeLifeAreaRepository
import com.stepwise.domain.fake.FakeProjectRepository
import com.stepwise.domain.fake.FakeTaskRepository
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Instant

private val NOW = Instant.fromEpochMilliseconds(1_000_000)
private val OWNER = UserId("owner-1")
private val OTHER_USER = UserId("other-1")

private fun task(): Task =
    Task(
        id = TaskId("task-1"),
        userId = OWNER,
        goalId = null,
        projectId = null,
        lifeAreaId = null,
        title = "Read a chapter",
        date = LocalDate(2026, 11, 14),
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

private fun manualGoal(userId: UserId): Goal =
    Goal(
        id = GoalId("goal-1"),
        userId = userId,
        lifeAreaId = null,
        projectId = null,
        title = "Learn Spanish",
        type = GoalType.PERCENTAGE,
        progressMode = GoalProgressMode.MANUAL,
        targetValue = null,
        unit = null,
        deadline = null,
        priority = null,
        category = null,
        description = null,
        status = LifecycleStatus.ACTIVE,
        createdAt = NOW,
        sync = SyncMetadata(updatedAt = NOW, version = 1, deletedAt = null),
    )

private fun project(userId: UserId): Project =
    Project(
        id = ProjectId("project-1"),
        userId = userId,
        goalId = null,
        lifeAreaId = null,
        title = "Side project",
        archived = false,
        sync = SyncMetadata(updatedAt = NOW, version = 1, deletedAt = null),
    )

private fun lifeArea(userId: UserId): LifeArea =
    LifeArea(
        id = LifeAreaId("area-1"),
        userId = userId,
        name = "Health",
        icon = null,
        category = null,
        sortOrder = 0,
        archived = false,
        sync = SyncMetadata(updatedAt = NOW, version = 1, deletedAt = null),
    )

class EditTaskUseCaseTest {
    private val taskRepository = FakeTaskRepository()
    private val goalRepository = FakeGoalRepository()
    private val projectRepository = FakeProjectRepository()
    private val lifeAreaRepository = FakeLifeAreaRepository()
    private val useCase =
        EditTaskUseCase(taskRepository, goalRepository, projectRepository, lifeAreaRepository, FakeClock(NOW))

    @Test
    fun `editing title succeeds`() =
        runTest {
            taskRepository.seed(task())
            val command = EditTaskCommand(userId = OWNER, taskId = TaskId("task-1"), title = "Read two chapters")

            val result = useCase.execute(command)

            check(result is AppResult.Success)
            assertEquals("Read two chapters", result.value.title)
        }

    @Test
    fun `archiving a Task succeeds`() =
        runTest {
            taskRepository.seed(task())
            val command = EditTaskCommand(userId = OWNER, taskId = TaskId("task-1"), archived = true)

            val result = useCase.execute(command)

            check(result is AppResult.Success)
            assertEquals(true, result.value.archived)
        }

    @Test
    fun `a Task owned by a different user cannot be edited`() =
        runTest {
            taskRepository.seed(task())
            val command = EditTaskCommand(userId = OTHER_USER, taskId = TaskId("task-1"), title = "Hijacked")

            val result = useCase.execute(command)

            check(result is AppResult.Failure)
            assertTrue(result.error is AppError.Auth)
        }

    @Test
    fun `editing a non-existent Task is rejected`() =
        runTest {
            val command = EditTaskCommand(userId = OWNER, taskId = TaskId("missing"), title = "Ghost")

            val result = useCase.execute(command)

            check(result is AppResult.Failure)
            assertTrue(result.error is AppError.Validation)
        }

    @Test
    fun `setting a negative plannedResult via edit is rejected`() =
        runTest {
            taskRepository.seed(task())
            val command = EditTaskCommand(userId = OWNER, taskId = TaskId("task-1"), plannedResult = -1.0)

            val result = useCase.execute(command)

            check(result is AppResult.Failure)
            assertTrue(result.error is AppError.Validation)
        }

    @Test
    fun `clearing date moves a scheduled Task back to the Inbox`() =
        runTest {
            taskRepository.seed(task())
            val command = EditTaskCommand(userId = OWNER, taskId = TaskId("task-1"), clearDate = true)

            val result = useCase.execute(command)

            check(result is AppResult.Success)
            assertNull(result.value.date)
        }

    @Test
    fun `attaching a goalId owned by a different user is rejected via edit`() =
        runTest {
            taskRepository.seed(task())
            goalRepository.seed(manualGoal(userId = OTHER_USER))
            val command = EditTaskCommand(userId = OWNER, taskId = TaskId("task-1"), goalId = GoalId("goal-1"))

            val result = useCase.execute(command)

            check(result is AppResult.Failure)
            assertTrue(result.error is AppError.Auth)
        }

    @Test
    fun `attaching a projectId owned by a different user is rejected via edit`() =
        runTest {
            taskRepository.seed(task())
            projectRepository.seed(project(userId = OTHER_USER))
            val command = EditTaskCommand(userId = OWNER, taskId = TaskId("task-1"), projectId = ProjectId("project-1"))

            val result = useCase.execute(command)

            check(result is AppResult.Failure)
            assertTrue(result.error is AppError.Auth)
        }

    @Test
    fun `attaching a lifeAreaId owned by a different user is rejected via edit`() =
        runTest {
            taskRepository.seed(task())
            lifeAreaRepository.seed(lifeArea(userId = OTHER_USER))
            val command =
                EditTaskCommand(userId = OWNER, taskId = TaskId("task-1"), lifeAreaId = LifeAreaId("area-1"))

            val result = useCase.execute(command)

            check(result is AppResult.Failure)
            assertTrue(result.error is AppError.Auth)
        }

    @Test
    fun `editing a tombstoned Task is rejected`() =
        runTest {
            val deletedSync = SyncMetadata(updatedAt = NOW, version = 1, deletedAt = NOW)
            taskRepository.seed(task().copy(sync = deletedSync))
            val command = EditTaskCommand(userId = OWNER, taskId = TaskId("task-1"), title = "Ghost")

            val result = useCase.execute(command)

            check(result is AppResult.Failure)
            assertTrue(result.error is AppError.Domain)
        }
}
