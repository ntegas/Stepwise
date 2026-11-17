package com.stepwise.domain.usecase

import com.stepwise.core.calculation.PeriodRange
import com.stepwise.core.common.AppResult
import com.stepwise.core.model.Activity
import com.stepwise.core.model.ActivityId
import com.stepwise.core.model.ExecutableKind
import com.stepwise.core.model.ExecutionStatus
import com.stepwise.core.model.MetricId
import com.stepwise.core.model.Session
import com.stepwise.core.model.SessionId
import com.stepwise.core.model.SessionMetricValue
import com.stepwise.core.model.SessionMetricValueId
import com.stepwise.core.model.SyncMetadata
import com.stepwise.core.model.UserId
import com.stepwise.domain.fake.FakeActivityRepository
import com.stepwise.domain.fake.FakeHabitRepository
import com.stepwise.domain.fake.FakeSessionRepository
import com.stepwise.domain.fake.FakeTaskRepository
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import kotlin.time.Instant

private val NOW = Instant.fromEpochMilliseconds(1_000_000)
private val USER = UserId("user-1")
private val SYNC = SyncMetadata(updatedAt = NOW, version = 1, deletedAt = null)
private val ACTIVITY_ID = ActivityId("activity-1")
private val DURATION_METRIC = MetricId("duration_minutes")
// A Tuesday, so this week is 2026-11-16..2026-11-22 and last week is 2026-11-09..2026-11-15.
private val THIS_WEEK_DATE = LocalDate(2026, 11, 17)
private val LAST_WEEK_DATE = LocalDate(2026, 11, 10)

class GetActivityStatsUseCaseTest {
    private val taskRepository = FakeTaskRepository()
    private val activityRepository = FakeActivityRepository()
    private val habitRepository = FakeHabitRepository()
    private val sessionRepository = FakeSessionRepository()
    private val getSessionHistoryUseCase =
        GetSessionHistoryUseCase(taskRepository, activityRepository, habitRepository, sessionRepository)
    private val useCase = GetActivityStatsUseCase(getSessionHistoryUseCase, sessionRepository)

    private var nextSessionSuffix = 0

    private suspend fun seedSession(
        date: LocalDate,
        status: ExecutionStatus,
        plannedValue: Double? = null,
        actualValue: Double? = null,
    ) {
        val sessionId = SessionId("session-${nextSessionSuffix++}")
        val metricValues =
            if (actualValue == null) {
                emptyList()
            } else {
                listOf(
                    SessionMetricValue(
                        id = SessionMetricValueId("${sessionId.value}-metric"),
                        sessionId = sessionId,
                        metricId = DURATION_METRIC,
                        plannedValue = plannedValue,
                        actualValue = actualValue,
                        sync = SYNC,
                    ),
                )
            }
        sessionRepository.upsertSessionWithMetricValues(
            Session(
                id = sessionId,
                userId = USER,
                sourceType = ExecutableKind.ACTIVITY,
                sourceId = ACTIVITY_ID.value,
                date = date,
                status = status,
                clientEventId = sessionId.value,
                notes = null,
                sync = SYNC,
            ),
            metricValues,
        )
    }

    @Test
    fun `planned and actual totals sum this period's qualifying sessions only`() =
        runTest {
            activityRepository.seed(activity())
            seedSession(THIS_WEEK_DATE, ExecutionStatus.DONE, plannedValue = 30.0, actualValue = 30.0)
            seedSession(THIS_WEEK_DATE, ExecutionStatus.PARTIAL, plannedValue = 60.0, actualValue = 40.0)
            seedSession(LAST_WEEK_DATE, ExecutionStatus.DONE, plannedValue = 100.0, actualValue = 100.0)

            val result =
                useCase.execute(USER, ExecutableKind.ACTIVITY, ACTIVITY_ID.value, PeriodRange.week(THIS_WEEK_DATE))

            check(result is AppResult.Success)
            assertEquals(90.0, result.value.plannedTotal)
            assertEquals(70.0, result.value.actualTotal)
        }

    @Test
    fun `trend compares this period's Execution Rate against the immediately preceding period`() =
        runTest {
            activityRepository.seed(activity())
            seedSession(THIS_WEEK_DATE, ExecutionStatus.DONE)
            seedSession(LAST_WEEK_DATE, ExecutionStatus.MISSED)

            val result =
                useCase.execute(USER, ExecutableKind.ACTIVITY, ACTIVITY_ID.value, PeriodRange.week(THIS_WEEK_DATE))

            check(result is AppResult.Success)
            assertEquals(100.0, result.value.executionRate)
            assertEquals(100.0, result.value.trend)
        }

    @Test
    fun `trend is null when the previous period has no qualifying sessions`() =
        runTest {
            activityRepository.seed(activity())
            seedSession(THIS_WEEK_DATE, ExecutionStatus.DONE)

            val result =
                useCase.execute(USER, ExecutableKind.ACTIVITY, ACTIVITY_ID.value, PeriodRange.week(THIS_WEEK_DATE))

            check(result is AppResult.Success)
            assertNull(result.value.trend)
        }

    @Test
    fun `a nonexistent source yields zero stats, the same non-disclosure convention GetSessionHistoryUseCase uses`() =
        runTest {
            // No Activity seeded at all - GetSessionHistoryUseCase's own ownership check fails
            // and it deliberately returns Success(emptyList()) rather than a Failure (its own
            // "not found and not owned read back identically" convention), so this use case's
            // result is a real Success carrying zero/null stats, never a Failure to propagate.
            val result =
                useCase.execute(USER, ExecutableKind.ACTIVITY, ACTIVITY_ID.value, PeriodRange.week(THIS_WEEK_DATE))

            check(result is AppResult.Success)
            assertEquals(0, result.value.sessionCount)
            assertNull(result.value.executionRate)
        }

    private fun activity(): Activity =
        Activity(
            id = ACTIVITY_ID,
            userId = USER,
            lifeAreaId = null,
            title = "Boxing",
            icon = null,
            archived = false,
            sync = SYNC,
        )
}
