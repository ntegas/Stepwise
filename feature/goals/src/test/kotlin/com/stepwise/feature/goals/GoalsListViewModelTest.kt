package com.stepwise.feature.goals

import com.stepwise.core.model.Goal
import com.stepwise.core.model.GoalId
import com.stepwise.core.model.GoalProgressMode
import com.stepwise.core.model.GoalType
import com.stepwise.core.model.LifecycleStatus
import com.stepwise.core.model.SyncMetadata
import com.stepwise.core.model.UserId
import com.stepwise.domain.fake.FakeAuthRepository
import com.stepwise.domain.fake.FakeGoalRepository
import com.stepwise.domain.usecase.GetGoalsUseCase
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

private fun goal(
    id: String,
    title: String,
    status: LifecycleStatus,
): Goal =
    Goal(
        id = GoalId(id),
        userId = OWNER,
        lifeAreaId = null,
        projectId = null,
        title = title,
        type = GoalType.CUMULATIVE,
        progressMode = GoalProgressMode.MILESTONES,
        targetValue = null,
        unit = null,
        deadline = null,
        priority = null,
        category = null,
        description = null,
        status = status,
        createdAt = NOW,
        sync = SyncMetadata(updatedAt = NOW, version = 1, deletedAt = null),
    )

class GoalsListViewModelTest {
    private val goalRepository = FakeGoalRepository()
    private val authRepository = FakeAuthRepository(initialUserId = OWNER)
    private val getGoalsUseCase = GetGoalsUseCase(goalRepository)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = GoalsListViewModel(authRepository, getGoalsUseCase)

    @Test
    fun `goals are grouped into active, completed, and archived, with paused folded into active`() =
        runTest {
            goalRepository.seed(goal("g1", "Active goal", LifecycleStatus.ACTIVE))
            goalRepository.seed(goal("g2", "Paused goal", LifecycleStatus.PAUSED))
            goalRepository.seed(goal("g3", "Done goal", LifecycleStatus.COMPLETED))
            goalRepository.seed(goal("g4", "Old goal", LifecycleStatus.ARCHIVED))

            val state = viewModel().state.value

            check(state is GoalsListUiState.Content)
            assertEquals(2, state.active.size)
            assertEquals(1, state.completed.size)
            assertEquals(1, state.archived.size)
        }

    @Test
    fun `goals within a group are sorted by title`() =
        runTest {
            goalRepository.seed(goal("g1", "Zebra", LifecycleStatus.ACTIVE))
            goalRepository.seed(goal("g2", "Apple", LifecycleStatus.ACTIVE))

            val state = viewModel().state.value

            check(state is GoalsListUiState.Content)
            assertEquals(listOf("Apple", "Zebra"), state.active.map { it.title })
        }

    @Test
    fun `a signed-out user sees SignedOut, not Content or an error`() =
        runTest {
            authRepository.setUserId(null)

            val state = viewModel().state.value

            assertEquals(GoalsListUiState.SignedOut, state)
        }
}
