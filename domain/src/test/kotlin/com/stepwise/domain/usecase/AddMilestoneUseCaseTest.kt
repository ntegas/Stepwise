package com.stepwise.domain.usecase

import com.stepwise.core.common.AppError
import com.stepwise.core.common.AppResult
import com.stepwise.core.common.FakeClock
import com.stepwise.core.common.FakeIdGenerator
import com.stepwise.core.model.Goal
import com.stepwise.core.model.GoalId
import com.stepwise.core.model.GoalProgressMode
import com.stepwise.core.model.GoalType
import com.stepwise.core.model.LifecycleStatus
import com.stepwise.core.model.SyncMetadata
import com.stepwise.core.model.UserId
import com.stepwise.domain.fake.FakeGoalRepository
import com.stepwise.domain.fake.FakeMilestoneRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Instant

private val NOW = Instant.fromEpochMilliseconds(1_000_000)
private val OWNER = UserId("owner-1")
private val OTHER_USER = UserId("other-1")

private fun milestonesGoal(): Goal =
    Goal(
        id = GoalId("goal-1"),
        userId = OWNER,
        lifeAreaId = null,
        projectId = null,
        title = "Launch a product",
        type = GoalType.PERCENTAGE,
        progressMode = GoalProgressMode.MILESTONES,
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

class AddMilestoneUseCaseTest {
    private val goalRepository = FakeGoalRepository()
    private val milestoneRepository = FakeMilestoneRepository()
    private val useCase = AddMilestoneUseCase(goalRepository, milestoneRepository, FakeIdGenerator(), FakeClock(NOW))

    @Test
    fun `adding a binary Milestone starts not completed with a null progressPercent`() =
        runTest {
            goalRepository.seed(milestonesGoal())
            val command =
                AddMilestoneCommand(
                    userId = OWNER,
                    goalId = GoalId("goal-1"),
                    title = "Design done",
                    sortOrder = 0,
                    weight = 50.0,
                    isBinary = true,
                )

            val result = useCase.execute(command)

            check(result is AppResult.Success)
            assertEquals(false, result.value.completed)
            assertNull(result.value.progressPercent)
        }

    @Test
    fun `adding a gradable Milestone starts at 0 progressPercent`() =
        runTest {
            goalRepository.seed(milestonesGoal())
            val command =
                AddMilestoneCommand(
                    userId = OWNER,
                    goalId = GoalId("goal-1"),
                    title = "Beta rollout",
                    sortOrder = 1,
                    weight = 30.0,
                    isBinary = false,
                )

            val result = useCase.execute(command)

            check(result is AppResult.Success)
            assertEquals(0.0, result.value.progressPercent)
        }

    @Test
    fun `Milestone weights need not sum to 100`() =
        runTest {
            goalRepository.seed(milestonesGoal())
            val first =
                AddMilestoneCommand(
                    userId = OWNER,
                    goalId = GoalId("goal-1"),
                    title = "A",
                    sortOrder = 0,
                    weight = 5.0,
                    isBinary = true,
                )
            val second =
                AddMilestoneCommand(
                    userId = OWNER,
                    goalId = GoalId("goal-1"),
                    title = "B",
                    sortOrder = 1,
                    weight = 5.0,
                    isBinary = true,
                )

            val firstResult = useCase.execute(first)
            val secondResult = useCase.execute(second)

            check(firstResult is AppResult.Success)
            check(secondResult is AppResult.Success)
        }

    @Test
    fun `a Goal owned by a different user cannot gain a Milestone`() =
        runTest {
            goalRepository.seed(milestonesGoal())
            val command =
                AddMilestoneCommand(
                    userId = OTHER_USER,
                    goalId = GoalId("goal-1"),
                    title = "Intrusion",
                    sortOrder = 0,
                    weight = 1.0,
                    isBinary = true,
                )

            val result = useCase.execute(command)

            check(result is AppResult.Failure)
            assertTrue(result.error is AppError.Auth)
        }

    @Test
    fun `a non-existent Goal cannot gain a Milestone`() =
        runTest {
            val command =
                AddMilestoneCommand(
                    userId = OWNER,
                    goalId = GoalId("missing"),
                    title = "Ghost",
                    sortOrder = 0,
                    weight = 1.0,
                    isBinary = true,
                )

            val result = useCase.execute(command)

            check(result is AppResult.Failure)
            assertTrue(result.error is AppError.Validation)
        }

    @Test
    fun `a negative weight is rejected`() =
        runTest {
            goalRepository.seed(milestonesGoal())
            val command =
                AddMilestoneCommand(
                    userId = OWNER,
                    goalId = GoalId("goal-1"),
                    title = "Bad weight",
                    sortOrder = 0,
                    weight = -5.0,
                    isBinary = true,
                )

            val result = useCase.execute(command)

            check(result is AppResult.Failure)
            assertTrue(result.error is AppError.Validation)
        }

    @Test
    fun `a NaN weight is rejected`() =
        runTest {
            goalRepository.seed(milestonesGoal())
            val command =
                AddMilestoneCommand(
                    userId = OWNER,
                    goalId = GoalId("goal-1"),
                    title = "Bad weight",
                    sortOrder = 0,
                    weight = Double.NaN,
                    isBinary = true,
                )

            val result = useCase.execute(command)

            check(result is AppResult.Failure)
            assertTrue(result.error is AppError.Validation)
        }
}
