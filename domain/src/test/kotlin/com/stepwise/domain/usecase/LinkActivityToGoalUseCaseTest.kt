package com.stepwise.domain.usecase

import com.stepwise.core.common.AppError
import com.stepwise.core.common.AppResult
import com.stepwise.core.common.FakeClock
import com.stepwise.core.common.FakeIdGenerator
import com.stepwise.core.model.Activity
import com.stepwise.core.model.ActivityId
import com.stepwise.core.model.Goal
import com.stepwise.core.model.GoalId
import com.stepwise.core.model.GoalProgressMode
import com.stepwise.core.model.GoalType
import com.stepwise.core.model.Habit
import com.stepwise.core.model.HabitId
import com.stepwise.core.model.LifecycleStatus
import com.stepwise.core.model.Metric
import com.stepwise.core.model.MetricId
import com.stepwise.core.model.ProgressSourceKind
import com.stepwise.core.model.SyncMetadata
import com.stepwise.core.model.UserId
import com.stepwise.domain.fake.FakeActivityRepository
import com.stepwise.domain.fake.FakeGoalActivityLinkRepository
import com.stepwise.domain.fake.FakeGoalRepository
import com.stepwise.domain.fake.FakeHabitRepository
import com.stepwise.domain.fake.FakeMetricRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Instant

private val NOW = Instant.fromEpochMilliseconds(1_000_000)
private val OWNER = UserId("owner-1")
private val OTHER_USER = UserId("other-1")

private fun metricGoal(): Goal =
    Goal(
        id = GoalId("goal-1"),
        userId = OWNER,
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

private fun activity(userId: UserId = OWNER): Activity =
    Activity(
        id = ActivityId("activity-1"),
        userId = userId,
        lifeAreaId = null,
        title = "Running",
        icon = null,
        archived = false,
        sync = SyncMetadata(updatedAt = NOW, version = 1, deletedAt = null),
    )

private fun habit(userId: UserId = OWNER): Habit =
    Habit(
        id = HabitId("habit-1"),
        userId = userId,
        activityId = null,
        title = "Run 3x/week",
        targetValue = 3.0,
        metricId = MetricId("metric-1"),
        archived = false,
        sync = SyncMetadata(updatedAt = NOW, version = 1, deletedAt = null),
    )

private fun metric(): Metric = Metric(id = MetricId("metric-1"), key = "distance_km", canonicalUnit = "km")

class LinkActivityToGoalUseCaseTest {
    private val activityRepository = FakeActivityRepository()
    private val habitRepository = FakeHabitRepository()
    private val goalRepository = FakeGoalRepository()
    private val metricRepository = FakeMetricRepository()
    private val linkRepository = FakeGoalActivityLinkRepository()
    private val useCase =
        LinkActivityToGoalUseCase(
            activityRepository,
            habitRepository,
            goalRepository,
            metricRepository,
            linkRepository,
            FakeIdGenerator(),
            FakeClock(NOW),
        )

    @Test
    fun `linking an owned Activity to an owned Goal succeeds`() =
        runTest {
            activityRepository.seed(activity())
            goalRepository.seed(metricGoal())
            metricRepository.seed(metric())
            val command =
                LinkActivityToGoalCommand(
                    userId = OWNER,
                    sourceType = ProgressSourceKind.ACTIVITY,
                    sourceId = "activity-1",
                    goalId = GoalId("goal-1"),
                    metricId = MetricId("metric-1"),
                )

            val result = useCase.execute(command)

            check(result is AppResult.Success)
        }

    @Test
    fun `linking an owned Habit to an owned Goal succeeds`() =
        runTest {
            habitRepository.seed(habit())
            goalRepository.seed(metricGoal())
            metricRepository.seed(metric())
            val command =
                LinkActivityToGoalCommand(
                    userId = OWNER,
                    sourceType = ProgressSourceKind.HABIT,
                    sourceId = "habit-1",
                    goalId = GoalId("goal-1"),
                    metricId = MetricId("metric-1"),
                )

            val result = useCase.execute(command)

            check(result is AppResult.Success)
        }

    @Test
    fun `linking an Activity owned by a different user is rejected`() =
        runTest {
            activityRepository.seed(activity(userId = OTHER_USER))
            goalRepository.seed(metricGoal())
            metricRepository.seed(metric())
            val command =
                LinkActivityToGoalCommand(
                    userId = OWNER,
                    sourceType = ProgressSourceKind.ACTIVITY,
                    sourceId = "activity-1",
                    goalId = GoalId("goal-1"),
                    metricId = MetricId("metric-1"),
                )

            val result = useCase.execute(command)

            check(result is AppResult.Failure)
            assertTrue(result.error is AppError.Auth)
        }

    @Test
    fun `linking to a Goal owned by a different user is rejected`() =
        runTest {
            activityRepository.seed(activity())
            goalRepository.seed(metricGoal().copy(userId = OTHER_USER))
            metricRepository.seed(metric())
            val command =
                LinkActivityToGoalCommand(
                    userId = OWNER,
                    sourceType = ProgressSourceKind.ACTIVITY,
                    sourceId = "activity-1",
                    goalId = GoalId("goal-1"),
                    metricId = MetricId("metric-1"),
                )

            val result = useCase.execute(command)

            check(result is AppResult.Failure)
            assertTrue(result.error is AppError.Auth)
        }

    @Test
    fun `linking with a non-existent metric is rejected`() =
        runTest {
            activityRepository.seed(activity())
            goalRepository.seed(metricGoal())
            val command =
                LinkActivityToGoalCommand(
                    userId = OWNER,
                    sourceType = ProgressSourceKind.ACTIVITY,
                    sourceId = "activity-1",
                    goalId = GoalId("goal-1"),
                    metricId = MetricId("missing"),
                )

            val result = useCase.execute(command)

            check(result is AppResult.Failure)
            assertTrue(result.error is AppError.Validation)
        }

    @Test
    fun `an exact-duplicate link is rejected`() =
        runTest {
            activityRepository.seed(activity())
            goalRepository.seed(metricGoal())
            metricRepository.seed(metric())
            val command =
                LinkActivityToGoalCommand(
                    userId = OWNER,
                    sourceType = ProgressSourceKind.ACTIVITY,
                    sourceId = "activity-1",
                    goalId = GoalId("goal-1"),
                    metricId = MetricId("metric-1"),
                )

            val firstResult = useCase.execute(command)
            val secondResult = useCase.execute(command)

            check(firstResult is AppResult.Success)
            check(secondResult is AppResult.Failure)
            assertTrue(secondResult.error is AppError.Validation)
        }

    @Test
    fun `linking a Habit owned by a different user is rejected`() =
        runTest {
            habitRepository.seed(habit(userId = OTHER_USER))
            goalRepository.seed(metricGoal())
            metricRepository.seed(metric())
            val command =
                LinkActivityToGoalCommand(
                    userId = OWNER,
                    sourceType = ProgressSourceKind.HABIT,
                    sourceId = "habit-1",
                    goalId = GoalId("goal-1"),
                    metricId = MetricId("metric-1"),
                )

            val result = useCase.execute(command)

            check(result is AppResult.Failure)
            assertTrue(result.error is AppError.Auth)
        }

    @Test
    fun `linking a non-existent Activity is rejected`() =
        runTest {
            goalRepository.seed(metricGoal())
            metricRepository.seed(metric())
            val command =
                LinkActivityToGoalCommand(
                    userId = OWNER,
                    sourceType = ProgressSourceKind.ACTIVITY,
                    sourceId = "missing",
                    goalId = GoalId("goal-1"),
                    metricId = MetricId("metric-1"),
                )

            val result = useCase.execute(command)

            check(result is AppResult.Failure)
            assertTrue(result.error is AppError.Validation)
        }

    @Test
    fun `linking a non-existent Habit is rejected`() =
        runTest {
            goalRepository.seed(metricGoal())
            metricRepository.seed(metric())
            val command =
                LinkActivityToGoalCommand(
                    userId = OWNER,
                    sourceType = ProgressSourceKind.HABIT,
                    sourceId = "missing",
                    goalId = GoalId("goal-1"),
                    metricId = MetricId("metric-1"),
                )

            val result = useCase.execute(command)

            check(result is AppResult.Failure)
            assertTrue(result.error is AppError.Validation)
        }

    @Test
    fun `linking a tombstoned Activity is rejected`() =
        runTest {
            val deletedSync = SyncMetadata(updatedAt = NOW, version = 1, deletedAt = NOW)
            activityRepository.seed(activity().copy(sync = deletedSync))
            goalRepository.seed(metricGoal())
            metricRepository.seed(metric())
            val command =
                LinkActivityToGoalCommand(
                    userId = OWNER,
                    sourceType = ProgressSourceKind.ACTIVITY,
                    sourceId = "activity-1",
                    goalId = GoalId("goal-1"),
                    metricId = MetricId("metric-1"),
                )

            val result = useCase.execute(command)

            check(result is AppResult.Failure)
            assertTrue(result.error is AppError.Domain)
        }

    @Test
    fun `linking to a tombstoned Goal is rejected`() =
        runTest {
            val deletedSync = SyncMetadata(updatedAt = NOW, version = 1, deletedAt = NOW)
            activityRepository.seed(activity())
            goalRepository.seed(metricGoal().copy(sync = deletedSync))
            metricRepository.seed(metric())
            val command =
                LinkActivityToGoalCommand(
                    userId = OWNER,
                    sourceType = ProgressSourceKind.ACTIVITY,
                    sourceId = "activity-1",
                    goalId = GoalId("goal-1"),
                    metricId = MetricId("metric-1"),
                )

            val result = useCase.execute(command)

            check(result is AppResult.Failure)
            assertTrue(result.error is AppError.Domain)
        }

    @Test
    fun `re-linking after an unlink succeeds, a tombstoned link is not treated as a duplicate`() =
        runTest {
            activityRepository.seed(activity())
            goalRepository.seed(metricGoal())
            metricRepository.seed(metric())
            val command =
                LinkActivityToGoalCommand(
                    userId = OWNER,
                    sourceType = ProgressSourceKind.ACTIVITY,
                    sourceId = "activity-1",
                    goalId = GoalId("goal-1"),
                    metricId = MetricId("metric-1"),
                )
            val firstLink = useCase.execute(command)
            check(firstLink is AppResult.Success)
            val tombstoned = firstLink.value.copy(sync = firstLink.value.sync.copy(deletedAt = NOW))
            linkRepository.seed(tombstoned)

            val result = useCase.execute(command)

            check(result is AppResult.Success)
        }
}
