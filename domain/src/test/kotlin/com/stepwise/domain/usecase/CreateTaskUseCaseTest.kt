package com.stepwise.domain.usecase

import com.stepwise.core.common.AppError
import com.stepwise.core.common.AppResult
import com.stepwise.core.common.FakeClock
import com.stepwise.core.common.FakeIdGenerator
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
import com.stepwise.core.model.UserId
import com.stepwise.domain.fake.FakeGoalRepository
import com.stepwise.domain.fake.FakeLifeAreaRepository
import com.stepwise.domain.fake.FakeProjectRepository
import com.stepwise.domain.fake.FakeTaskRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Instant

private val NOW = Instant.fromEpochMilliseconds(1_000_000)
private val OWNER = UserId("owner-1")
private val OTHER_USER = UserId("other-1")

private fun manualGoal(userId: UserId = OWNER): Goal =
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

class CreateTaskUseCaseTest {
    private val taskRepository = FakeTaskRepository()
    private val goalRepository = FakeGoalRepository()
    private val projectRepository = FakeProjectRepository()
    private val lifeAreaRepository = FakeLifeAreaRepository()
    private val useCase =
        CreateTaskUseCase(
            taskRepository,
            goalRepository,
            projectRepository,
            lifeAreaRepository,
            FakeIdGenerator(),
            FakeClock(NOW),
        )

    @Test
    fun `creating a Task with only a title succeeds, not archived by default`() =
        runTest {
            val command = CreateTaskCommand(userId = OWNER, title = "Read a chapter")

            val result = useCase.execute(command)

            check(result is AppResult.Success)
            assertEquals(false, result.value.archived)
        }

    @Test
    fun `attaching a goalId owned by a different user is rejected`() =
        runTest {
            goalRepository.seed(manualGoal(userId = OTHER_USER))
            val command = CreateTaskCommand(userId = OWNER, title = "Read a chapter", goalId = GoalId("goal-1"))

            val result = useCase.execute(command)

            check(result is AppResult.Failure)
            assertTrue(result.error is AppError.Auth)
        }

    @Test
    fun `attaching a projectId owned by a different user is rejected`() =
        runTest {
            projectRepository.seed(project(userId = OTHER_USER))
            val command =
                CreateTaskCommand(userId = OWNER, title = "Read a chapter", projectId = ProjectId("project-1"))

            val result = useCase.execute(command)

            check(result is AppResult.Failure)
            assertTrue(result.error is AppError.Auth)
        }

    @Test
    fun `attaching a lifeAreaId owned by a different user is rejected`() =
        runTest {
            lifeAreaRepository.seed(lifeArea(userId = OTHER_USER))
            val command =
                CreateTaskCommand(userId = OWNER, title = "Read a chapter", lifeAreaId = LifeAreaId("area-1"))

            val result = useCase.execute(command)

            check(result is AppResult.Failure)
            assertTrue(result.error is AppError.Auth)
        }

    @Test
    fun `a negative plannedResult is rejected`() =
        runTest {
            val command = CreateTaskCommand(userId = OWNER, title = "Run", plannedResult = -5.0)

            val result = useCase.execute(command)

            check(result is AppResult.Failure)
            assertTrue(result.error is AppError.Validation)
        }

    @Test
    fun `a NaN plannedResult is rejected`() =
        runTest {
            val command = CreateTaskCommand(userId = OWNER, title = "Run", plannedResult = Double.NaN)

            val result = useCase.execute(command)

            check(result is AppResult.Failure)
            assertTrue(result.error is AppError.Validation)
        }
}
