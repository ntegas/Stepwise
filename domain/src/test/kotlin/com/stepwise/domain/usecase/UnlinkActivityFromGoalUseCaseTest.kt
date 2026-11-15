package com.stepwise.domain.usecase

import com.stepwise.core.common.AppError
import com.stepwise.core.common.AppResult
import com.stepwise.core.common.FakeClock
import com.stepwise.core.model.Goal
import com.stepwise.core.model.GoalActivityLink
import com.stepwise.core.model.GoalActivityLinkId
import com.stepwise.core.model.GoalId
import com.stepwise.core.model.GoalProgressMode
import com.stepwise.core.model.GoalType
import com.stepwise.core.model.LifecycleStatus
import com.stepwise.core.model.MetricId
import com.stepwise.core.model.ProgressSourceKind
import com.stepwise.core.model.SyncMetadata
import com.stepwise.core.model.UserId
import com.stepwise.domain.fake.FakeGoalActivityLinkRepository
import com.stepwise.domain.fake.FakeGoalRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Instant

private val NOW = Instant.fromEpochMilliseconds(1_000_000)
private val OWNER = UserId("owner-1")
private val OTHER_USER = UserId("other-1")

private fun metricGoal(userId: UserId = OWNER): Goal =
    Goal(
        id = GoalId("goal-1"),
        userId = userId,
        lifeAreaId = null,
        projectId = null,
        title = "Run a marathon",
        type = GoalType.DISTANCE,
        progressMode = GoalProgressMode.METRIC,
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

private fun link(): GoalActivityLink =
    GoalActivityLink(
        id = GoalActivityLinkId("link-1"),
        sourceType = ProgressSourceKind.ACTIVITY,
        sourceId = "activity-1",
        goalId = GoalId("goal-1"),
        metricId = MetricId("metric-1"),
        sync = SyncMetadata(updatedAt = NOW, version = 1, deletedAt = null),
    )

class UnlinkActivityFromGoalUseCaseTest {
    private val goalRepository = FakeGoalRepository()
    private val linkRepository = FakeGoalActivityLinkRepository()
    private val useCase = UnlinkActivityFromGoalUseCase(goalRepository, linkRepository, FakeClock(NOW))

    @Test
    fun `unlinking an owned link tombstones it`() =
        runTest {
            goalRepository.seed(metricGoal())
            linkRepository.seed(link())

            val result = useCase.execute(OWNER, GoalActivityLinkId("link-1"))

            check(result is AppResult.Success)
            assertEquals(true, linkRepository.get(GoalActivityLinkId("link-1"))?.sync?.isDeleted)
        }

    @Test
    fun `unlinking a link whose Goal belongs to a different user is rejected`() =
        runTest {
            goalRepository.seed(metricGoal(userId = OTHER_USER))
            linkRepository.seed(link())

            val result = useCase.execute(OWNER, GoalActivityLinkId("link-1"))

            check(result is AppResult.Failure)
            assertTrue(result.error is AppError.Auth)
        }

    @Test
    fun `unlinking a non-existent link is rejected`() =
        runTest {
            val result = useCase.execute(OWNER, GoalActivityLinkId("missing"))

            check(result is AppResult.Failure)
            assertTrue(result.error is AppError.Validation)
        }
}
