package com.stepwise.feature.activities

import com.stepwise.core.model.Habit
import com.stepwise.core.model.HabitId
import com.stepwise.core.model.MetricId
import com.stepwise.core.model.SyncMetadata
import com.stepwise.core.model.UserId
import com.stepwise.domain.fake.FakeAuthRepository
import com.stepwise.domain.fake.FakeHabitRepository
import com.stepwise.domain.usecase.GetHabitsUseCase
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

private fun habit(
    id: String,
    title: String,
    archived: Boolean = false,
): Habit =
    Habit(
        id = HabitId(id),
        userId = OWNER,
        activityId = null,
        title = title,
        targetValue = 3.0,
        metricId = MetricId("count"),
        archived = archived,
        sync = SyncMetadata(updatedAt = NOW, version = 1, deletedAt = null),
    )

class HabitsListViewModelTest {
    private val habitRepository = FakeHabitRepository()
    private val authRepository = FakeAuthRepository(initialUserId = OWNER)
    private val getHabitsUseCase = GetHabitsUseCase(habitRepository)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = HabitsListViewModel(authRepository, getHabitsUseCase)

    @Test
    fun `habits are grouped into active and archived, sorted by title`() =
        runTest {
            habitRepository.seed(habit("h1", "Zebra"))
            habitRepository.seed(habit("h2", "Apple"))
            habitRepository.seed(habit("h3", "Old one", archived = true))

            val state = viewModel().state.value

            check(state is HabitsListUiState.Content)
            assertEquals(listOf("Apple", "Zebra"), state.active.map { it.title })
            assertEquals(1, state.archived.size)
        }

    @Test
    fun `a signed-out user sees SignedOut, not Content or an error`() =
        runTest {
            authRepository.setUserId(null)

            val state = viewModel().state.value

            assertEquals(HabitsListUiState.SignedOut, state)
        }
}
