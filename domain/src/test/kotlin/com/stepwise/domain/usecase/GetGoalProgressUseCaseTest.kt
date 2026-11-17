package com.stepwise.domain.usecase

import com.stepwise.core.calculation.PeriodRange
import com.stepwise.core.common.AppError
import com.stepwise.core.common.AppResult
import com.stepwise.core.common.minusDays
import com.stepwise.core.model.ExecutableKind
import com.stepwise.core.model.ExecutionStatus
import com.stepwise.core.model.Goal
import com.stepwise.core.model.GoalActivityLink
import com.stepwise.core.model.GoalActivityLinkId
import com.stepwise.core.model.GoalId
import com.stepwise.core.model.GoalProgressMode
import com.stepwise.core.model.GoalType
import com.stepwise.core.model.LifecycleStatus
import com.stepwise.core.model.MetricId
import com.stepwise.core.model.Milestone
import com.stepwise.core.model.MilestoneId
import com.stepwise.core.model.ProgressSourceKind
import com.stepwise.core.model.Session
import com.stepwise.core.model.SessionId
import com.stepwise.core.model.SessionMetricValue
import com.stepwise.core.model.SessionMetricValueId
import com.stepwise.core.model.SyncMetadata
import com.stepwise.core.model.Task
import com.stepwise.core.model.TaskId
import com.stepwise.core.model.UserId
import com.stepwise.domain.fake.FakeGoalActivityLinkRepository
import com.stepwise.domain.fake.FakeGoalRepository
import com.stepwise.domain.fake.FakeMilestoneRepository
import com.stepwise.domain.fake.FakeSessionRepository
import com.stepwise.domain.fake.FakeTaskRepository
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Instant

private val NOW = Instant.fromEpochMilliseconds(1_000_000)
private val USER = UserId("user-1")
private val OTHER_USER = UserId("user-2")
private val SYNC = SyncMetadata(updatedAt = NOW, version = 1, deletedAt = null)
private val GOAL_ID = GoalId("goal-1")
private val DURATION_METRIC = MetricId("duration_minutes")
private val DISTANCE_METRIC = MetricId("distance_km")
private val DATE = LocalDate(2026, 11, 17)

private fun goal(
    progressMode: GoalProgressMode,
    targetValue: Double? = 100.0,
    owner: UserId = USER,
): Goal =
    Goal(
        id = GOAL_ID,
        userId = owner,
        lifeAreaId = null,
        projectId = null,
        title = "Read 100 books",
        type = GoalType.CUMULATIVE,
        progressMode = progressMode,
        targetValue = targetValue,
        unit = "books",
        deadline = null,
        priority = null,
        category = null,
        description = null,
        status = LifecycleStatus.ACTIVE,
        createdAt = NOW,
        sync = SYNC,
    )

class GetGoalProgressUseCaseTest {
    private val goalRepository = FakeGoalRepository()
    private val milestoneRepository = FakeMilestoneRepository()
    private val goalActivityLinkRepository = FakeGoalActivityLinkRepository()
    private val sessionRepository = FakeSessionRepository()
    private val taskRepository = FakeTaskRepository()
    private val useCase =
        GetGoalProgressUseCase(
            goalRepository,
            milestoneRepository,
            goalActivityLinkRepository,
            sessionRepository,
            taskRepository,
        )

    private fun seedLink(
        metricId: MetricId = DURATION_METRIC,
        linkId: String = "link-1",
    ): GoalActivityLink {
        val link =
            GoalActivityLink(
                id = GoalActivityLinkId(linkId),
                sourceType = ProgressSourceKind.ACTIVITY,
                sourceId = "activity-1",
                goalId = GOAL_ID,
                metricId = metricId,
                sync = SYNC,
            )
        goalActivityLinkRepository.seed(link)
        return link
    }

    private var nextSessionSuffix = 0

    private suspend fun seedSessionWithMetric(
        date: LocalDate,
        actualValue: Double,
        status: ExecutionStatus = ExecutionStatus.DONE,
        metricId: MetricId = DURATION_METRIC,
    ) = seedSessionWithMetrics(date, status, metricId to actualValue)

    private suspend fun seedSessionWithMetrics(
        date: LocalDate,
        status: ExecutionStatus = ExecutionStatus.DONE,
        vararg metricValues: Pair<MetricId, Double>,
    ) {
        val sessionId = SessionId("session-${nextSessionSuffix++}")
        sessionRepository.upsertSessionWithMetricValues(
            Session(
                id = sessionId,
                userId = USER,
                sourceType = ExecutableKind.ACTIVITY,
                sourceId = "activity-1",
                date = date,
                status = status,
                clientEventId = sessionId.value,
                notes = null,
                sync = SYNC,
            ),
            metricValues.mapIndexed { index, (metricId, actualValue) ->
                SessionMetricValue(
                    id = SessionMetricValueId("${sessionId.value}-metric-$index"),
                    sessionId = sessionId,
                    metricId = metricId,
                    plannedValue = null,
                    actualValue = actualValue,
                    sync = SYNC,
                )
            },
        )
    }

    @Test
    fun `a Goal owned by a different user is rejected`() =
        runTest {
            goalRepository.seed(goal(GoalProgressMode.METRIC, owner = OTHER_USER))

            val result = useCase.execute(USER, GOAL_ID, PeriodRange.allTime(DATE))

            check(result is AppResult.Failure)
            assertTrue(result.error is AppError.Auth)
        }

    @Test
    fun `MANUAL mode has no stored value to read and fails explicitly, not a guessed zero`() =
        runTest {
            goalRepository.seed(goal(GoalProgressMode.MANUAL, targetValue = null))

            val result = useCase.execute(USER, GOAL_ID, PeriodRange.allTime(DATE))

            check(result is AppResult.Failure)
            assertTrue(result.error is AppError.Domain)
        }

    @Test
    fun `METRIC mode sums every linked actual value`() =
        runTest {
            goalRepository.seed(goal(GoalProgressMode.METRIC, targetValue = 100.0))
            seedLink()
            seedSessionWithMetric(DATE, actualValue = 40.0)
            seedSessionWithMetric(DATE, actualValue = 30.0)

            val result = useCase.execute(USER, GOAL_ID, PeriodRange.allTime(DATE))

            check(result is AppResult.Success)
            assertEquals(70.0, result.value.currentValue)
            assertEquals(70.0, result.value.percent)
        }

    @Test
    fun `METRIC mode counts a Partial session's real actual in full, per PRODUCT_CANON §27`() =
        runTest {
            goalRepository.seed(goal(GoalProgressMode.METRIC, targetValue = 100.0))
            seedLink()
            seedSessionWithMetric(DATE, actualValue = 40.0, status = ExecutionStatus.PARTIAL)

            val result = useCase.execute(USER, GOAL_ID, PeriodRange.allTime(DATE))

            check(result is AppResult.Success)
            assertEquals(40.0, result.value.currentValue)
        }

    @Test
    fun `TARGET_VALUE mode reads only the most recent linked value, not a sum`() =
        runTest {
            goalRepository.seed(goal(GoalProgressMode.TARGET_VALUE, targetValue = 70.0))
            seedLink()
            seedSessionWithMetric(DATE.minusDays(1), actualValue = 80.0)
            seedSessionWithMetric(DATE, actualValue = 63.0)

            val result = useCase.execute(USER, GOAL_ID, PeriodRange.allTime(DATE))

            check(result is AppResult.Success)
            assertEquals(63.0, result.value.currentValue)
        }

    @Test
    fun `TARGET_VALUE mode with no reading yet is null, not a false zero`() =
        runTest {
            goalRepository.seed(goal(GoalProgressMode.TARGET_VALUE, targetValue = 70.0))

            val result = useCase.execute(USER, GOAL_ID, PeriodRange.allTime(DATE))

            check(result is AppResult.Success)
            assertNull(result.value.currentValue)
        }

    @Test
    fun `FREQUENCY mode counts only qualifying sessions inside the given period`() =
        runTest {
            goalRepository.seed(goal(GoalProgressMode.FREQUENCY, targetValue = 3.0))
            seedLink()
            seedSessionWithMetric(DATE, actualValue = 1.0)
            seedSessionWithMetric(DATE.minusDays(10), actualValue = 1.0)
            seedSessionWithMetric(DATE, actualValue = 1.0, status = ExecutionStatus.CANCELLED)

            val result = useCase.execute(USER, GOAL_ID, PeriodRange.week(DATE))

            check(result is AppResult.Success)
            assertEquals(1.0, result.value.currentValue)
        }

    @Test
    fun `FREQUENCY mode counts a PARTIAL session, not only DONE`() =
        runTest {
            goalRepository.seed(goal(GoalProgressMode.FREQUENCY, targetValue = 3.0))
            seedLink()
            seedSessionWithMetric(DATE, actualValue = 1.0, status = ExecutionStatus.PARTIAL)

            val result = useCase.execute(USER, GOAL_ID, PeriodRange.week(DATE))

            check(result is AppResult.Success)
            assertEquals(1.0, result.value.currentValue)
        }

    @Test
    fun `FREQUENCY mode excludes RESCHEDULED, MISSED, and IN_PROGRESS, not only CANCELLED`() =
        runTest {
            goalRepository.seed(goal(GoalProgressMode.FREQUENCY, targetValue = 3.0))
            seedLink()
            seedSessionWithMetric(DATE, actualValue = 1.0, status = ExecutionStatus.RESCHEDULED)
            seedSessionWithMetric(DATE, actualValue = 1.0, status = ExecutionStatus.MISSED)
            seedSessionWithMetric(DATE, actualValue = 1.0, status = ExecutionStatus.IN_PROGRESS)

            val result = useCase.execute(USER, GOAL_ID, PeriodRange.week(DATE))

            check(result is AppResult.Success)
            assertEquals(0.0, result.value.currentValue)
        }

    @Test
    fun `FREQUENCY mode counts a session reachable through two links to the same source once`() =
        runTest {
            // A Goal can have two GoalActivityLinks to the same source - one per linked Metric
            // (goal_activity_links' own uniqueness is on sourceType+sourceId+goalId+metricId, not
            // sourceType+sourceId+goalId) - and a session linked via both must still count once,
            // not once per link.
            goalRepository.seed(goal(GoalProgressMode.FREQUENCY, targetValue = 3.0))
            seedLink(metricId = DURATION_METRIC, linkId = "link-duration")
            seedLink(metricId = DISTANCE_METRIC, linkId = "link-distance")
            seedSessionWithMetrics(DATE, ExecutionStatus.DONE, DURATION_METRIC to 30.0, DISTANCE_METRIC to 5.0)

            val result = useCase.execute(USER, GOAL_ID, PeriodRange.week(DATE))

            check(result is AppResult.Success)
            assertEquals(1.0, result.value.currentValue)
        }

    @Test
    fun `MILESTONES mode weights binary and gradable milestones together`() =
        runTest {
            goalRepository.seed(goal(GoalProgressMode.MILESTONES, targetValue = null))
            milestoneRepository.seed(
                Milestone(
                    id = MilestoneId("m1"),
                    goalId = GOAL_ID,
                    title = "Phase 1",
                    sortOrder = 0,
                    weight = 1.0,
                    isBinary = true,
                    completed = true,
                    progressPercent = null,
                    sync = SYNC,
                ),
            )

            val result = useCase.execute(USER, GOAL_ID, PeriodRange.allTime(DATE))

            check(result is AppResult.Success)
            assertEquals(100.0, result.value.percent)
        }

    @Test
    fun `TASKS mode is equal-weighted completed over total linked tasks`() =
        runTest {
            goalRepository.seed(goal(GoalProgressMode.TASKS, targetValue = null))
            val doneTaskId = TaskId("task-done")
            val openTaskId = TaskId("task-open")
            taskRepository.seed(task(doneTaskId))
            taskRepository.seed(task(openTaskId))
            val sessionId = SessionId("done-session")
            sessionRepository.upsertSessionWithMetricValues(
                Session(
                    id = sessionId,
                    userId = USER,
                    sourceType = ExecutableKind.TASK,
                    sourceId = doneTaskId.value,
                    date = DATE,
                    status = ExecutionStatus.DONE,
                    clientEventId = sessionId.value,
                    notes = null,
                    sync = SYNC,
                ),
                emptyList(),
            )

            val result = useCase.execute(USER, GOAL_ID, PeriodRange.allTime(DATE))

            check(result is AppResult.Success)
            assertEquals(50.0, result.value.percent)
        }

    private fun task(id: TaskId): Task =
        Task(
            id = id,
            userId = USER,
            goalId = GOAL_ID,
            projectId = null,
            lifeAreaId = null,
            title = "Sub-task",
            date = null,
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
            sync = SYNC,
        )
}
