package com.stepwise.domain.usecase

import com.stepwise.core.common.AppError
import com.stepwise.core.common.AppResult
import com.stepwise.core.common.FakeClock
import com.stepwise.core.common.FakeIdGenerator
import com.stepwise.core.model.Activity
import com.stepwise.core.model.ActivityId
import com.stepwise.core.model.Metric
import com.stepwise.core.model.MetricId
import com.stepwise.core.model.SyncMetadata
import com.stepwise.core.model.UserId
import com.stepwise.domain.fake.FakeActivityRepository
import com.stepwise.domain.fake.FakeHabitRepository
import com.stepwise.domain.fake.FakeMetricRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Instant

private val NOW = Instant.fromEpochMilliseconds(1_000_000)
private val OWNER = UserId("owner-1")
private val OTHER_USER = UserId("other-1")

private fun metric(): Metric = Metric(id = MetricId("metric-1"), key = "count", canonicalUnit = "count")

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

class CreateHabitUseCaseTest {
    private val habitRepository = FakeHabitRepository()
    private val activityRepository = FakeActivityRepository()
    private val metricRepository = FakeMetricRepository()
    private val useCase =
        CreateHabitUseCase(habitRepository, activityRepository, metricRepository, FakeIdGenerator(), FakeClock(NOW))

    @Test
    fun `creating a Habit with a valid targetValue and metric succeeds`() =
        runTest {
            metricRepository.seed(metric())
            val command =
                CreateHabitCommand(
                    userId = OWNER,
                    title = "Run 3x/week",
                    targetValue = 3.0,
                    metricId = MetricId("metric-1"),
                )

            val result = useCase.execute(command)

            check(result is AppResult.Success)
            assertEquals(false, result.value.archived)
        }

    @Test
    fun `a non-positive targetValue is rejected`() =
        runTest {
            metricRepository.seed(metric())
            val command =
                CreateHabitCommand(
                    userId = OWNER,
                    title = "Run 3x/week",
                    targetValue = 0.0,
                    metricId = MetricId("metric-1"),
                )

            val result = useCase.execute(command)

            check(result is AppResult.Failure)
            assertTrue(result.error is AppError.Validation)
        }

    @Test
    fun `a non-existent metric is rejected`() =
        runTest {
            val command =
                CreateHabitCommand(
                    userId = OWNER,
                    title = "Run 3x/week",
                    targetValue = 3.0,
                    metricId = MetricId("missing"),
                )

            val result = useCase.execute(command)

            check(result is AppResult.Failure)
            assertTrue(result.error is AppError.Validation)
        }

    @Test
    fun `attaching an activityId owned by a different user is rejected`() =
        runTest {
            metricRepository.seed(metric())
            activityRepository.seed(activity(userId = OTHER_USER))
            val command =
                CreateHabitCommand(
                    userId = OWNER,
                    title = "Run 3x/week",
                    targetValue = 3.0,
                    metricId = MetricId("metric-1"),
                    activityId = ActivityId("activity-1"),
                )

            val result = useCase.execute(command)

            check(result is AppResult.Failure)
            assertTrue(result.error is AppError.Auth)
        }
}
