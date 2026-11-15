package com.stepwise.domain.usecase

import com.stepwise.core.common.AppError
import com.stepwise.core.common.AppResult
import com.stepwise.core.common.FakeClock
import com.stepwise.core.common.FakeIdGenerator
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
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Instant

private val NOW = Instant.fromEpochMilliseconds(1_000_000)
private val USER = UserId("user-1")
private val OTHER_USER = UserId("other-1")

class CreateGoalUseCaseTest {
    private val goalRepository = FakeGoalRepository()
    private val lifeAreaRepository = FakeLifeAreaRepository()
    private val projectRepository = FakeProjectRepository()
    private val useCase =
        CreateGoalUseCase(goalRepository, lifeAreaRepository, projectRepository, FakeIdGenerator(), FakeClock(NOW))

    @Test
    fun `creating a Goal with only the required fields succeeds, ACTIVE by default`() =
        runTest {
            val command =
                CreateGoalCommand(
                    userId = USER,
                    title = "Run a marathon",
                    type = GoalType.DISTANCE,
                    progressMode = GoalProgressMode.METRIC,
                )

            val result = useCase.execute(command)

            check(result is AppResult.Success)
            assertEquals(LifecycleStatus.ACTIVE, result.value.status)
            assertEquals(result.value, goalRepository.get(result.value.id))
        }

    @Test
    fun `FREQUENCY mode without a targetValue is rejected`() =
        runTest {
            val command =
                CreateGoalCommand(
                    userId = USER,
                    title = "Go to the gym",
                    type = GoalType.FREQUENCY,
                    progressMode = GoalProgressMode.FREQUENCY,
                )

            val result = useCase.execute(command)

            check(result is AppResult.Failure)
            assertTrue(result.error is AppError.Validation)
        }

    @Test
    fun `MILESTONES mode with a non-null targetValue is rejected`() =
        runTest {
            val command =
                CreateGoalCommand(
                    userId = USER,
                    title = "Launch a product",
                    type = GoalType.PERCENTAGE,
                    progressMode = GoalProgressMode.MILESTONES,
                    targetValue = 100.0,
                )

            val result = useCase.execute(command)

            check(result is AppResult.Failure)
            assertTrue(result.error is AppError.Validation)
        }

    @Test
    fun `MANUAL mode with a non-null targetValue is rejected`() =
        runTest {
            val command =
                CreateGoalCommand(
                    userId = USER,
                    title = "Learn Spanish",
                    type = GoalType.PERCENTAGE,
                    progressMode = GoalProgressMode.MANUAL,
                    targetValue = 50.0,
                )

            val result = useCase.execute(command)

            check(result is AppResult.Failure)
            assertTrue(result.error is AppError.Validation)
        }

    @Test
    fun `a targetValue without a paired unit is rejected`() =
        runTest {
            val command =
                CreateGoalCommand(
                    userId = USER,
                    title = "Save money",
                    type = GoalType.FINANCIAL,
                    progressMode = GoalProgressMode.TARGET_VALUE,
                    targetValue = 1000.0,
                )

            val result = useCase.execute(command)

            check(result is AppResult.Failure)
            assertTrue(result.error is AppError.Validation)
        }

    @Test
    fun `TARGET_VALUE with a targetValue and unit succeeds`() =
        runTest {
            val command =
                CreateGoalCommand(
                    userId = USER,
                    title = "Save money",
                    type = GoalType.FINANCIAL,
                    progressMode = GoalProgressMode.TARGET_VALUE,
                    targetValue = 1000.0,
                    unit = "$",
                )

            val result = useCase.execute(command)

            check(result is AppResult.Success)
            assertEquals(1000.0, result.value.targetValue)
        }

    @Test
    fun `an atypical type-progressMode pairing is accepted, not rejected`() =
        runTest {
            // ARCHITECTURE.md's own B6: "an unusual pairing is still representable" - type and
            // progressMode are independent axes, never a locked combination.
            val command =
                CreateGoalCommand(
                    userId = USER,
                    title = "Odd pairing",
                    type = GoalType.CUMULATIVE,
                    progressMode = GoalProgressMode.MANUAL,
                )

            val result = useCase.execute(command)

            check(result is AppResult.Success)
        }

    @Test
    fun `a NaN targetValue is rejected`() =
        runTest {
            val command =
                CreateGoalCommand(
                    userId = USER,
                    title = "Save money",
                    type = GoalType.FINANCIAL,
                    progressMode = GoalProgressMode.TARGET_VALUE,
                    targetValue = Double.NaN,
                    unit = "$",
                )

            val result = useCase.execute(command)

            check(result is AppResult.Failure)
            assertTrue(result.error is AppError.Validation)
        }

    @Test
    fun `a negative targetValue is rejected`() =
        runTest {
            val command =
                CreateGoalCommand(
                    userId = USER,
                    title = "Save money",
                    type = GoalType.FINANCIAL,
                    progressMode = GoalProgressMode.TARGET_VALUE,
                    targetValue = -500.0,
                    unit = "$",
                )

            val result = useCase.execute(command)

            check(result is AppResult.Failure)
            assertTrue(result.error is AppError.Validation)
        }

    @Test
    fun `attaching a lifeAreaId owned by a different user is rejected`() =
        runTest {
            lifeAreaRepository.seed(
                LifeArea(
                    id = LifeAreaId("area-1"),
                    userId = OTHER_USER,
                    name = "Health",
                    icon = null,
                    category = null,
                    sortOrder = 0,
                    archived = false,
                    sync = SyncMetadata(updatedAt = NOW, version = 1, deletedAt = null),
                ),
            )
            val command =
                CreateGoalCommand(
                    userId = USER,
                    title = "Run a marathon",
                    type = GoalType.DISTANCE,
                    progressMode = GoalProgressMode.METRIC,
                    lifeAreaId = LifeAreaId("area-1"),
                )

            val result = useCase.execute(command)

            check(result is AppResult.Failure)
            assertTrue(result.error is AppError.Auth)
        }

    @Test
    fun `attaching a projectId owned by a different user is rejected`() =
        runTest {
            projectRepository.seed(
                Project(
                    id = ProjectId("project-1"),
                    userId = OTHER_USER,
                    goalId = null,
                    lifeAreaId = null,
                    title = "Someone else's project",
                    archived = false,
                    sync = SyncMetadata(updatedAt = NOW, version = 1, deletedAt = null),
                ),
            )
            val command =
                CreateGoalCommand(
                    userId = USER,
                    title = "Run a marathon",
                    type = GoalType.DISTANCE,
                    progressMode = GoalProgressMode.METRIC,
                    projectId = ProjectId("project-1"),
                )

            val result = useCase.execute(command)

            check(result is AppResult.Failure)
            assertTrue(result.error is AppError.Auth)
        }
}
