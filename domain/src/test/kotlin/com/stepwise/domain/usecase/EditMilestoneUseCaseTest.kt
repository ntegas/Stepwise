package com.stepwise.domain.usecase

import com.stepwise.core.common.AppError
import com.stepwise.core.common.AppResult
import com.stepwise.core.common.FakeClock
import com.stepwise.core.model.Goal
import com.stepwise.core.model.GoalId
import com.stepwise.core.model.GoalProgressMode
import com.stepwise.core.model.GoalType
import com.stepwise.core.model.LifecycleStatus
import com.stepwise.core.model.Milestone
import com.stepwise.core.model.MilestoneId
import com.stepwise.core.model.SyncMetadata
import com.stepwise.core.model.UserId
import com.stepwise.domain.fake.FakeGoalRepository
import com.stepwise.domain.fake.FakeMilestoneRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Instant

private val NOW = Instant.fromEpochMilliseconds(1_000_000)
private val OWNER = UserId("owner-1")
private val OTHER_USER = UserId("other-1")
private val GOAL_ID = GoalId("goal-1")
private val OTHER_GOAL_ID = GoalId("goal-2")

private fun milestonesGoal(id: GoalId = GOAL_ID): Goal =
    Goal(
        id = id,
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

private fun milestone(goalId: GoalId = GOAL_ID): Milestone =
    Milestone(
        id = MilestoneId("milestone-1"),
        goalId = goalId,
        title = "Design done",
        sortOrder = 0,
        weight = 50.0,
        isBinary = true,
        completed = false,
        progressPercent = null,
        sync = SyncMetadata(updatedAt = NOW, version = 1, deletedAt = null),
    )

private fun gradableMilestone(): Milestone =
    milestone().copy(isBinary = false, completed = false, progressPercent = 0.0)

class EditMilestoneUseCaseTest {
    private val goalRepository = FakeGoalRepository()
    private val milestoneRepository = FakeMilestoneRepository()
    private val useCase = EditMilestoneUseCase(goalRepository, milestoneRepository, FakeClock(NOW))

    @Test
    fun `editing completed on a binary Milestone succeeds`() =
        runTest {
            goalRepository.seed(milestonesGoal())
            milestoneRepository.seed(milestone())
            val command =
                EditMilestoneCommand(
                    userId = OWNER,
                    goalId = GOAL_ID,
                    milestoneId = MilestoneId("milestone-1"),
                    completed = true,
                )

            val result = useCase.execute(command)

            check(result is AppResult.Success)
            assertEquals(true, result.value.completed)
        }

    @Test
    fun `editing progressPercent on a binary Milestone is ignored, not persisted`() =
        runTest {
            goalRepository.seed(milestonesGoal())
            milestoneRepository.seed(milestone())
            val command =
                EditMilestoneCommand(
                    userId = OWNER,
                    goalId = GOAL_ID,
                    milestoneId = MilestoneId("milestone-1"),
                    progressPercent = 42.0,
                )

            val result = useCase.execute(command)

            check(result is AppResult.Success)
            assertEquals(null, result.value.progressPercent)
        }

    @Test
    fun `editing completed on a gradable Milestone is ignored, not persisted`() =
        runTest {
            goalRepository.seed(milestonesGoal())
            milestoneRepository.seed(gradableMilestone())
            val command =
                EditMilestoneCommand(
                    userId = OWNER,
                    goalId = GOAL_ID,
                    milestoneId = MilestoneId("milestone-1"),
                    completed = true,
                )

            val result = useCase.execute(command)

            check(result is AppResult.Success)
            assertEquals(false, result.value.completed)
        }

    @Test
    fun `editing progressPercent on a gradable Milestone succeeds`() =
        runTest {
            goalRepository.seed(milestonesGoal())
            milestoneRepository.seed(gradableMilestone())
            val command =
                EditMilestoneCommand(
                    userId = OWNER,
                    goalId = GOAL_ID,
                    milestoneId = MilestoneId("milestone-1"),
                    progressPercent = 75.0,
                )

            val result = useCase.execute(command)

            check(result is AppResult.Success)
            assertEquals(75.0, result.value.progressPercent)
        }

    @Test
    fun `a Goal owned by a different user cannot have its Milestone edited`() =
        runTest {
            goalRepository.seed(milestonesGoal())
            milestoneRepository.seed(milestone())
            val command =
                EditMilestoneCommand(
                    userId = OTHER_USER,
                    goalId = GOAL_ID,
                    milestoneId = MilestoneId("milestone-1"),
                    title = "Hijacked",
                )

            val result = useCase.execute(command)

            check(result is AppResult.Failure)
            assertTrue(result.error is AppError.Auth)
        }

    @Test
    fun `a Milestone that belongs to a different Goal is rejected`() =
        runTest {
            goalRepository.seed(milestonesGoal())
            goalRepository.seed(milestonesGoal(id = OTHER_GOAL_ID))
            milestoneRepository.seed(milestone(goalId = OTHER_GOAL_ID))
            val command =
                EditMilestoneCommand(
                    userId = OWNER,
                    goalId = GOAL_ID,
                    milestoneId = MilestoneId("milestone-1"),
                    title = "Mismatch",
                )

            val result = useCase.execute(command)

            check(result is AppResult.Failure)
            assertTrue(result.error is AppError.Validation)
        }

    @Test
    fun `editing a non-existent Milestone is rejected`() =
        runTest {
            goalRepository.seed(milestonesGoal())
            val command =
                EditMilestoneCommand(
                    userId = OWNER,
                    goalId = GOAL_ID,
                    milestoneId = MilestoneId("missing"),
                    title = "Ghost",
                )

            val result = useCase.execute(command)

            check(result is AppResult.Failure)
            assertTrue(result.error is AppError.Validation)
        }

    @Test
    fun `a progressPercent above 100 is rejected`() =
        runTest {
            goalRepository.seed(milestonesGoal())
            milestoneRepository.seed(gradableMilestone())
            val command =
                EditMilestoneCommand(
                    userId = OWNER,
                    goalId = GOAL_ID,
                    milestoneId = MilestoneId("milestone-1"),
                    progressPercent = 150.0,
                )

            val result = useCase.execute(command)

            check(result is AppResult.Failure)
            assertTrue(result.error is AppError.Validation)
        }

    @Test
    fun `a negative weight is rejected`() =
        runTest {
            goalRepository.seed(milestonesGoal())
            milestoneRepository.seed(milestone())
            val command =
                EditMilestoneCommand(
                    userId = OWNER,
                    goalId = GOAL_ID,
                    milestoneId = MilestoneId("milestone-1"),
                    weight = -10.0,
                )

            val result = useCase.execute(command)

            check(result is AppResult.Failure)
            assertTrue(result.error is AppError.Validation)
        }
}
