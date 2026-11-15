package com.stepwise.domain.usecase

import com.stepwise.core.common.AppError
import com.stepwise.core.common.AppResult
import com.stepwise.core.common.FakeClock
import com.stepwise.core.model.Activity
import com.stepwise.core.model.ActivityId
import com.stepwise.core.model.Habit
import com.stepwise.core.model.HabitId
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

private fun activity(userId: UserId): Activity =
    Activity(
        id = ActivityId("activity-1"),
        userId = userId,
        lifeAreaId = null,
        title = "Running",
        icon = null,
        archived = false,
        sync = SyncMetadata(updatedAt = NOW, version = 1, deletedAt = null),
    )

private fun habit(): Habit =
    Habit(
        id = HabitId("habit-1"),
        userId = OWNER,
        activityId = null,
        title = "Run 3x/week",
        targetValue = 3.0,
        metricId = MetricId("metric-1"),
        archived = false,
        sync = SyncMetadata(updatedAt = NOW, version = 1, deletedAt = null),
    )

class EditHabitUseCaseTest {
    private val habitRepository = FakeHabitRepository()
    private val activityRepository = FakeActivityRepository()
    private val metricRepository = FakeMetricRepository()
    private val useCase = EditHabitUseCase(habitRepository, activityRepository, metricRepository, FakeClock(NOW))

    @Test
    fun `editing targetValue succeeds`() =
        runTest {
            metricRepository.seed(metric())
            habitRepository.seed(habit())
            val command = EditHabitCommand(userId = OWNER, habitId = HabitId("habit-1"), targetValue = 5.0)

            val result = useCase.execute(command)

            check(result is AppResult.Success)
            assertEquals(5.0, result.value.targetValue)
        }

    @Test
    fun `a Habit owned by a different user cannot be edited`() =
        runTest {
            metricRepository.seed(metric())
            habitRepository.seed(habit())
            val command = EditHabitCommand(userId = OTHER_USER, habitId = HabitId("habit-1"), title = "Hijacked")

            val result = useCase.execute(command)

            check(result is AppResult.Failure)
            assertTrue(result.error is AppError.Auth)
        }

    @Test
    fun `setting a non-positive targetValue via edit is rejected`() =
        runTest {
            metricRepository.seed(metric())
            habitRepository.seed(habit())
            val command = EditHabitCommand(userId = OWNER, habitId = HabitId("habit-1"), targetValue = -1.0)

            val result = useCase.execute(command)

            check(result is AppResult.Failure)
            assertTrue(result.error is AppError.Validation)
        }

    @Test
    fun `setting a non-existent metricId via edit is rejected`() =
        runTest {
            metricRepository.seed(metric())
            habitRepository.seed(habit())
            val command = EditHabitCommand(userId = OWNER, habitId = HabitId("habit-1"), metricId = MetricId("missing"))

            val result = useCase.execute(command)

            check(result is AppResult.Failure)
            assertTrue(result.error is AppError.Validation)
        }

    @Test
    fun `attaching an activityId owned by a different user is rejected via edit`() =
        runTest {
            metricRepository.seed(metric())
            habitRepository.seed(habit())
            activityRepository.seed(activity(userId = OTHER_USER))
            val command =
                EditHabitCommand(userId = OWNER, habitId = HabitId("habit-1"), activityId = ActivityId("activity-1"))

            val result = useCase.execute(command)

            check(result is AppResult.Failure)
            assertTrue(result.error is AppError.Auth)
        }

    @Test
    fun `editing a tombstoned Habit is rejected`() =
        runTest {
            val deletedSync = SyncMetadata(updatedAt = NOW, version = 1, deletedAt = NOW)
            metricRepository.seed(metric())
            habitRepository.seed(habit().copy(sync = deletedSync))
            val command = EditHabitCommand(userId = OWNER, habitId = HabitId("habit-1"), title = "Ghost")

            val result = useCase.execute(command)

            check(result is AppResult.Failure)
            assertTrue(result.error is AppError.Domain)
        }
}
