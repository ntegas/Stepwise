package com.stepwise.feature.activities

import androidx.lifecycle.SavedStateHandle
import com.stepwise.core.common.FakeClock
import com.stepwise.core.common.FakeIdGenerator
import com.stepwise.core.model.Activity
import com.stepwise.core.model.ActivityId
import com.stepwise.core.model.SyncMetadata
import com.stepwise.core.model.UserId
import com.stepwise.domain.fake.FakeActivityRepository
import com.stepwise.domain.fake.FakeAuthRepository
import com.stepwise.domain.fake.FakeLifeAreaRepository
import com.stepwise.domain.usecase.CreateActivityUseCase
import com.stepwise.domain.usecase.EditActivityUseCase
import com.stepwise.domain.usecase.GetLifeAreasUseCase
import com.stepwise.domain.usecase.ObserveActivityUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

private val NOW = LocalDateTime(2026, 11, 15, 12, 0).toInstant(TimeZone.UTC)
private val OWNER = UserId("owner-1")

class ActivityFormViewModelTest {
    private val activityRepository = FakeActivityRepository()
    private val lifeAreaRepository = FakeLifeAreaRepository()
    private val authRepository = FakeAuthRepository(initialUserId = OWNER)
    private val getLifeAreasUseCase = GetLifeAreasUseCase(lifeAreaRepository)
    private val observeActivityUseCase = ObserveActivityUseCase(activityRepository)
    private val createActivityUseCase =
        CreateActivityUseCase(activityRepository, lifeAreaRepository, FakeIdGenerator(), FakeClock(NOW))
    private val editActivityUseCase = EditActivityUseCase(activityRepository, lifeAreaRepository, FakeClock(NOW))

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(activityId: ActivityId? = null) =
        ActivityFormViewModel(
            SavedStateHandle(activityId?.let { mapOf("activityId" to it.value) } ?: emptyMap()),
            authRepository,
            getLifeAreasUseCase,
            observeActivityUseCase,
            createActivityUseCase,
            editActivityUseCase,
        )

    @Test
    fun `creating an activity with a valid title succeeds and marks the form saved`() =
        runTest {
            val vm = viewModel()
            vm.onTitleChanged("Boxing")

            vm.save()

            assertTrue(vm.state.value.saved)
            val created = activityRepository.get(ActivityId("id-1"))
            assertEquals("Boxing", created?.title)
        }

    @Test
    fun `creating an activity with a blank title shows an inline error and does not save`() =
        runTest {
            val vm = viewModel()

            vm.save()

            val state = vm.state.value
            assertTrue(state.titleError)
            assertEquals(false, state.saved)
        }

    @Test
    fun `editing an existing activity loads its title into the form`() =
        runTest {
            val existing =
                Activity(
                    id = ActivityId("a1"),
                    userId = OWNER,
                    lifeAreaId = null,
                    title = "Boxing",
                    icon = null,
                    archived = false,
                    sync = SyncMetadata(updatedAt = NOW, version = 1, deletedAt = null),
                )
            activityRepository.seed(existing)

            val state = viewModel(ActivityId("a1")).state.value

            assertEquals("Boxing", state.title)
        }
}
