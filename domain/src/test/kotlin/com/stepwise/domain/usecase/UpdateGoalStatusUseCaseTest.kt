package com.stepwise.domain.usecase

import com.stepwise.core.common.AppError
import com.stepwise.core.common.AppResult
import com.stepwise.core.common.FakeClock
import com.stepwise.core.model.Goal
import com.stepwise.core.model.GoalId
import com.stepwise.core.model.GoalProgressMode
import com.stepwise.core.model.GoalType
import com.stepwise.core.model.LifecycleStatus
import com.stepwise.core.model.SyncMetadata
import com.stepwise.core.model.UserId
import com.stepwise.domain.fake.FakeGoalRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Instant

private val NOW = Instant.fromEpochMilliseconds(1_000_000)
private val OWNER = UserId("owner-1")
private val OTHER_USER = UserId("other-1")

private fun activeGoal(): Goal =
    Goal(
        id = GoalId("goal-1"),
        userId = OWNER,
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

class UpdateGoalStatusUseCaseTest {
    private val goalRepository = FakeGoalRepository()
    private val useCase = UpdateGoalStatusUseCase(goalRepository, FakeClock(NOW))

    @Test
    fun `ACTIVE to PAUSED succeeds`() =
        runTest {
            goalRepository.seed(activeGoal())

            val result = useCase.execute(OWNER, GoalId("goal-1"), LifecycleStatus.PAUSED)

            check(result is AppResult.Success)
            assertEquals(LifecycleStatus.PAUSED, result.value.status)
        }

    @Test
    fun `ACTIVE to COMPLETED succeeds`() =
        runTest {
            goalRepository.seed(activeGoal())

            val result = useCase.execute(OWNER, GoalId("goal-1"), LifecycleStatus.COMPLETED)

            check(result is AppResult.Success)
            assertEquals(LifecycleStatus.COMPLETED, result.value.status)
        }

    @Test
    fun `COMPLETED back to ACTIVE is accepted, no transition is forbidden`() =
        runTest {
            goalRepository.seed(activeGoal().copy(status = LifecycleStatus.COMPLETED))

            val result = useCase.execute(OWNER, GoalId("goal-1"), LifecycleStatus.ACTIVE)

            check(result is AppResult.Success)
            assertEquals(LifecycleStatus.ACTIVE, result.value.status)
        }

    @Test
    fun `a no-op transition to the same status is accepted as ordinary success`() =
        runTest {
            goalRepository.seed(activeGoal())

            val result = useCase.execute(OWNER, GoalId("goal-1"), LifecycleStatus.ACTIVE)

            check(result is AppResult.Success)
            assertEquals(LifecycleStatus.ACTIVE, result.value.status)
        }

    @Test
    fun `ARCHIVED is reachable from any other status`() =
        runTest {
            goalRepository.seed(activeGoal().copy(status = LifecycleStatus.PAUSED))

            val result = useCase.execute(OWNER, GoalId("goal-1"), LifecycleStatus.ARCHIVED)

            check(result is AppResult.Success)
            assertEquals(LifecycleStatus.ARCHIVED, result.value.status)
        }

    @Test
    fun `a Goal owned by a different user cannot have its status changed`() =
        runTest {
            goalRepository.seed(activeGoal())

            val result = useCase.execute(OTHER_USER, GoalId("goal-1"), LifecycleStatus.ARCHIVED)

            check(result is AppResult.Failure)
            assertTrue(result.error is AppError.Auth)
        }

    @Test
    fun `a non-existent Goal cannot have its status changed`() =
        runTest {
            val result = useCase.execute(OWNER, GoalId("missing"), LifecycleStatus.ARCHIVED)

            check(result is AppResult.Failure)
            assertTrue(result.error is AppError.Validation)
        }
}
