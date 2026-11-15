package com.stepwise.domain.usecase

import com.stepwise.core.common.AppError
import com.stepwise.core.common.AppResult
import com.stepwise.core.common.FakeClock
import com.stepwise.core.common.FakeIdGenerator
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
private val USER = UserId("user-1")
private val OTHER_USER = UserId("other-1")

class CreateActivityUseCaseTest {
    private val activityRepository = FakeActivityRepository()
    private val lifeAreaRepository = FakeLifeAreaRepository()
    private val useCase =
        CreateActivityUseCase(activityRepository, lifeAreaRepository, FakeIdGenerator(), FakeClock(NOW))

    @Test
    fun `creating an Activity with only a title succeeds, not archived by default`() =
        runTest {
            val command = CreateActivityCommand(userId = USER, title = "Running")

            val result = useCase.execute(command)

            check(result is AppResult.Success)
            assertEquals(false, result.value.archived)
            assertEquals(result.value, activityRepository.get(result.value.id))
        }

    @Test
    fun `attaching a lifeAreaId owned by a different user is rejected`() =
        runTest {
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
            val command = CreateActivityCommand(userId = USER, title = "Running", lifeAreaId = LifeAreaId("area-1"))

            val result = useCase.execute(command)

            check(result is AppResult.Failure)
            assertTrue(result.error is AppError.Auth)
        }

    @Test
    fun `attaching a tombstoned lifeAreaId is rejected`() =
        runTest {
            lifeAreaRepository.seed(
                LifeArea(
                    id = LifeAreaId("area-1"),
                    userId = USER,
                    name = "Health",
                    icon = null,
                    category = null,
                    sortOrder = 0,
                    archived = false,
                    sync = SyncMetadata(updatedAt = NOW, version = 1, deletedAt = NOW),
                ),
            )
            val command = CreateActivityCommand(userId = USER, title = "Running", lifeAreaId = LifeAreaId("area-1"))

            val result = useCase.execute(command)

            check(result is AppResult.Failure)
            assertTrue(result.error is AppError.Domain)
        }
}
