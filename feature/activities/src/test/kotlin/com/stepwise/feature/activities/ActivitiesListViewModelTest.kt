package com.stepwise.feature.activities

import com.stepwise.core.model.Activity
import com.stepwise.core.model.ActivityId
import com.stepwise.core.model.SyncMetadata
import com.stepwise.core.model.UserId
import com.stepwise.domain.fake.FakeActivityRepository
import com.stepwise.domain.fake.FakeAuthRepository
import com.stepwise.domain.usecase.GetActivitiesUseCase
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

private val NOW = LocalDateTime(2026, 11, 15, 12, 0).toInstant(TimeZone.UTC)
private val OWNER = UserId("owner-1")

private fun activity(
    id: String,
    title: String,
    archived: Boolean = false,
): Activity =
    Activity(
        id = ActivityId(id),
        userId = OWNER,
        lifeAreaId = null,
        title = title,
        icon = null,
        archived = archived,
        sync = SyncMetadata(updatedAt = NOW, version = 1, deletedAt = null),
    )

class ActivitiesListViewModelTest {
    private val activityRepository = FakeActivityRepository()
    private val authRepository = FakeAuthRepository(initialUserId = OWNER)
    private val getActivitiesUseCase = GetActivitiesUseCase(activityRepository)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = ActivitiesListViewModel(authRepository, getActivitiesUseCase)

    @Test
    fun `activities are grouped into active and archived, sorted by title`() =
        runTest {
            activityRepository.seed(activity("a1", "Zebra"))
            activityRepository.seed(activity("a2", "Apple"))
            activityRepository.seed(activity("a3", "Old one", archived = true))

            val state = viewModel().state.value

            check(state is ActivitiesListUiState.Content)
            assertEquals(listOf("Apple", "Zebra"), state.active.map { it.title })
            assertEquals(1, state.archived.size)
        }

    @Test
    fun `a signed-out user sees SignedOut, not Content or an error`() =
        runTest {
            authRepository.setUserId(null)

            val state = viewModel().state.value

            assertEquals(ActivitiesListUiState.SignedOut, state)
        }
}
