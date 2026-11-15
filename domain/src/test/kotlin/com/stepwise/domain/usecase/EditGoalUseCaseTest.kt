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
import com.stepwise.core.model.SyncMetadata
import com.stepwise.core.model.UserId
import com.stepwise.domain.fake.FakeGoalRepository
import com.stepwise.domain.fake.FakeLifeAreaRepository
import com.stepwise.domain.fake.FakeProjectRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Instant

private val NOW = Instant.fromEpochMilliseconds(1_000_000)
private val OWNER = UserId("owner-1")
private val OTHER_USER = UserId("other-1")

private fun targetValueGoal(): Goal =
    Goal(
        id = GoalId("goal-1"),
        userId = OWNER,
        lifeAreaId = null,
        projectId = null,
        title = "Save money",
        type = GoalType.FINANCIAL,
        progressMode = GoalProgressMode.TARGET_VALUE,
        targetValue = 1000.0,
        unit = "$",
        deadline = null,
        priority = null,
        category = null,
        description = null,
        status = LifecycleStatus.ACTIVE,
        createdAt = NOW,
        sync = SyncMetadata(updatedAt = NOW, version = 1, deletedAt = null),
    )

// EditGoalUseCase's own constructor takes no SessionRepository at all - "doesn't damage
// Sessions" holds by construction here, not by a runtime call-count assertion.
class EditGoalUseCaseTest {
    private val goalRepository = FakeGoalRepository()
    private val lifeAreaRepository = FakeLifeAreaRepository()
    private val projectRepository = FakeProjectRepository()
    private val useCase = EditGoalUseCase(goalRepository, lifeAreaRepository, projectRepository, FakeClock(NOW))

    @Test
    fun `editing targetValue on a TARGET_VALUE Goal succeeds`() =
        runTest {
            goalRepository.seed(targetValueGoal())
            val command = EditGoalCommand(userId = OWNER, goalId = GoalId("goal-1"), targetValue = 2000.0)

            val result = useCase.execute(command)

            check(result is AppResult.Success)
            assertEquals(2000.0, result.value.targetValue)
        }

    @Test
    fun `switching progressMode into MANUAL clears targetValue and unit`() =
        runTest {
            goalRepository.seed(targetValueGoal())
            val command =
                EditGoalCommand(userId = OWNER, goalId = GoalId("goal-1"), progressMode = GoalProgressMode.MANUAL)

            val result = useCase.execute(command)

            check(result is AppResult.Success)
            assertNull(result.value.targetValue)
            assertNull(result.value.unit)
        }

    @Test
    fun `a Goal owned by a different user cannot be edited`() =
        runTest {
            goalRepository.seed(targetValueGoal())
            val command = EditGoalCommand(userId = OTHER_USER, goalId = GoalId("goal-1"), title = "Hijacked")

            val result = useCase.execute(command)

            check(result is AppResult.Failure)
            assertTrue(result.error is AppError.Auth)
        }

    @Test
    fun `editing a non-existent Goal is rejected`() =
        runTest {
            val command = EditGoalCommand(userId = OWNER, goalId = GoalId("missing"), title = "Ghost")

            val result = useCase.execute(command)

            check(result is AppResult.Failure)
            assertTrue(result.error is AppError.Validation)
        }

    @Test
    fun `switching progressMode into TARGET_VALUE without a targetValue is rejected via edit`() =
        runTest {
            val manualGoal =
                targetValueGoal().copy(progressMode = GoalProgressMode.MANUAL, targetValue = null, unit = null)
            goalRepository.seed(manualGoal)
            val command =
                EditGoalCommand(userId = OWNER, goalId = GoalId("goal-1"), progressMode = GoalProgressMode.TARGET_VALUE)

            val result = useCase.execute(command)

            check(result is AppResult.Failure)
            assertTrue(result.error is AppError.Validation)
        }

    @Test
    fun `setting a targetValue with no unit is rejected via edit`() =
        runTest {
            // unit=null on the command means "leave unchanged" (EditGoalCommand's own
            // convention), so this must start from a Goal whose existing unit is already null -
            // otherwise the existing unit survives the edit and validation sees a paired value.
            // METRIC (not in TARGET_FREE_MODES/TARGET_REQUIRED_MODES) isolates the unit-pairing
            // rule from the mode-vs-targetValue rules checked earlier in GoalValidation.validate.
            val metricGoal =
                targetValueGoal().copy(progressMode = GoalProgressMode.METRIC, targetValue = null, unit = null)
            goalRepository.seed(metricGoal)
            val command =
                EditGoalCommand(userId = OWNER, goalId = GoalId("goal-1"), targetValue = 500.0)

            val result = useCase.execute(command)

            check(result is AppResult.Failure)
            assertTrue(result.error is AppError.Validation)
        }

    @Test
    fun `attaching a lifeAreaId owned by a different user is rejected via edit`() =
        runTest {
            goalRepository.seed(targetValueGoal())
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
            val command = EditGoalCommand(userId = OWNER, goalId = GoalId("goal-1"), lifeAreaId = LifeAreaId("area-1"))

            val result = useCase.execute(command)

            check(result is AppResult.Failure)
            assertTrue(result.error is AppError.Auth)
        }
}
