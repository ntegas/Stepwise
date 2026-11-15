package com.stepwise.domain.usecase

import com.stepwise.core.common.AppError
import com.stepwise.core.common.AppResult
import com.stepwise.core.common.FakeClock
import com.stepwise.core.model.Activity
import com.stepwise.core.model.ActivityId
import com.stepwise.core.model.LifeArea
import com.stepwise.core.model.LifeAreaId
import com.stepwise.core.model.SyncMetadata
import com.stepwise.core.model.UserId
import com.stepwise.domain.fake.FakeActivityRepository
import com.stepwise.domain.fake.FakeLifeAreaRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Instant

private val NOW = Instant.fromEpochMilliseconds(1_000_000)
private val OWNER = UserId("owner-1")
private val OTHER_USER = UserId("other-1")

private fun activity(): Activity =
    Activity(
        id = ActivityId("activity-1"),
        userId = OWNER,
        lifeAreaId = null,
        title = "Running",
        icon = null,
        archived = false,
        sync = SyncMetadata(updatedAt = NOW, version = 1, deletedAt = null),
    )

class EditActivityUseCaseTest {
    private val activityRepository = FakeActivityRepository()
    private val lifeAreaRepository = FakeLifeAreaRepository()
    private val useCase = EditActivityUseCase(activityRepository, lifeAreaRepository, FakeClock(NOW))

    @Test
    fun `editing title succeeds`() =
        runTest {
            activityRepository.seed(activity())
            val command = EditActivityCommand(userId = OWNER, activityId = ActivityId("activity-1"), title = "Jogging")

            val result = useCase.execute(command)

            check(result is AppResult.Success)
            assertEquals("Jogging", result.value.title)
        }

    @Test
    fun `archiving an Activity succeeds`() =
        runTest {
            activityRepository.seed(activity())
            val command = EditActivityCommand(userId = OWNER, activityId = ActivityId("activity-1"), archived = true)

            val result = useCase.execute(command)

            check(result is AppResult.Success)
            assertEquals(true, result.value.archived)
        }

    @Test
    fun `an Activity owned by a different user cannot be edited`() =
        runTest {
            activityRepository.seed(activity())
            val command =
                EditActivityCommand(userId = OTHER_USER, activityId = ActivityId("activity-1"), title = "Hijacked")

            val result = useCase.execute(command)

            check(result is AppResult.Failure)
            assertTrue(result.error is AppError.Auth)
        }

    @Test
    fun `editing a non-existent Activity is rejected`() =
        runTest {
            val command = EditActivityCommand(userId = OWNER, activityId = ActivityId("missing"), title = "Ghost")

            val result = useCase.execute(command)

            check(result is AppResult.Failure)
            assertTrue(result.error is AppError.Validation)
        }

    @Test
    fun `attaching a lifeAreaId owned by a different user is rejected via edit`() =
        runTest {
            activityRepository.seed(activity())
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
            val command =
                EditActivityCommand(
                    userId = OWNER,
                    activityId = ActivityId("activity-1"),
                    lifeAreaId = LifeAreaId("area-1"),
                )

            val result = useCase.execute(command)

            check(result is AppResult.Failure)
            assertTrue(result.error is AppError.Auth)
        }

    @Test
    fun `editing a tombstoned Activity is rejected`() =
        runTest {
            val deletedSync = SyncMetadata(updatedAt = NOW, version = 1, deletedAt = NOW)
            activityRepository.seed(activity().copy(sync = deletedSync))
            val command = EditActivityCommand(userId = OWNER, activityId = ActivityId("activity-1"), title = "Ghost")

            val result = useCase.execute(command)

            check(result is AppResult.Failure)
            assertTrue(result.error is AppError.Domain)
        }
}
