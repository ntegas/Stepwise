package com.stepwise.feature.goals

import androidx.lifecycle.SavedStateHandle
import com.stepwise.core.common.FakeClock
import com.stepwise.core.common.FakeIdGenerator
import com.stepwise.core.model.Goal
import com.stepwise.core.model.GoalId
import com.stepwise.core.model.GoalProgressMode
import com.stepwise.core.model.GoalType
import com.stepwise.core.model.LifecycleStatus
import com.stepwise.core.model.Milestone
import com.stepwise.core.model.MilestoneId
import com.stepwise.core.model.SyncMetadata
import com.stepwise.core.model.UserId
import com.stepwise.domain.fake.FakeAuthRepository
import com.stepwise.domain.fake.FakeGoalRepository
import com.stepwise.domain.fake.FakeMilestoneRepository
import com.stepwise.domain.usecase.AddMilestoneUseCase
import com.stepwise.domain.usecase.EditMilestoneUseCase
import com.stepwise.domain.usecase.GetGoalsUseCase
import com.stepwise.domain.usecase.GetMilestonesUseCase
import com.stepwise.domain.usecase.UpdateGoalStatusUseCase
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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

private val NOW = LocalDateTime(2026, 11, 15, 12, 0).toInstant(TimeZone.UTC)
private val OWNER = UserId("owner-1")
private val GOAL_ID = GoalId("goal-1")

private fun goal(status: LifecycleStatus = LifecycleStatus.ACTIVE): Goal =
    Goal(
        id = GOAL_ID,
        userId = OWNER,
        lifeAreaId = null,
        projectId = null,
        title = "100 Hours Boxing",
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

class GoalDetailViewModelTest {
    private val goalRepository = FakeGoalRepository()
    private val milestoneRepository = FakeMilestoneRepository()
    private val authRepository = FakeAuthRepository(initialUserId = OWNER)
    private val idGenerator = FakeIdGenerator()
    private val clock = FakeClock(NOW)
    private val getGoalsUseCase = GetGoalsUseCase(goalRepository)
    private val getMilestonesUseCase = GetMilestonesUseCase(milestoneRepository)
    private val updateGoalStatusUseCase = UpdateGoalStatusUseCase(goalRepository, clock)
    private val addMilestoneUseCase = AddMilestoneUseCase(goalRepository, milestoneRepository, idGenerator, clock)
    private val editMilestoneUseCase = EditMilestoneUseCase(goalRepository, milestoneRepository, clock)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(goalId: GoalId = GOAL_ID) =
        GoalDetailViewModel(
            SavedStateHandle(mapOf("goalId" to goalId.value)),
            authRepository,
            getGoalsUseCase,
            getMilestonesUseCase,
            updateGoalStatusUseCase,
            addMilestoneUseCase,
            editMilestoneUseCase,
        )

    @Test
    fun `a goal that doesn't exist yields NotFound`() =
        runTest {
            val state = viewModel(GoalId("no-such-goal")).state.value

            assertEquals(GoalDetailUiState.NotFound, state)
        }

    @Test
    fun `pausing an Active goal transitions it to Paused reactively`() =
        runTest {
            goalRepository.seed(goal(LifecycleStatus.ACTIVE))
            val vm = viewModel()

            vm.pause()

            val state = vm.state.value
            check(state is GoalDetailUiState.Content)
            assertEquals(LifecycleStatus.PAUSED, state.goal.status)
        }

    @Test
    fun `adding a milestone with valid fields appends it to the Milestones list`() =
        runTest {
            goalRepository.seed(goal())
            val vm = viewModel()
            vm.onAddMilestoneClicked()
            vm.onMilestoneTitleChanged("Specification")
            vm.onMilestoneWeightChanged("1")

            vm.saveMilestone()

            val state = vm.state.value
            check(state is GoalDetailUiState.Content)
            assertEquals(1, state.milestones.size)
            assertEquals("Specification", state.milestones.single().title)
            assertFalse(state.milestoneDialogVisible)
        }

    @Test
    fun `adding a milestone with a blank title shows an inline error`() =
        runTest {
            goalRepository.seed(goal())
            val vm = viewModel()
            vm.onAddMilestoneClicked()
            vm.onMilestoneWeightChanged("1")

            vm.saveMilestone()

            val state = vm.state.value
            check(state is GoalDetailUiState.Content)
            assertTrue(state.milestoneTitleError)
            assertEquals(0, state.milestones.size)
        }

    @Test
    fun `toggling a binary milestone flips its completed flag`() =
        runTest {
            goalRepository.seed(goal())
            val milestone =
                Milestone(
                    id = MilestoneId("m1"),
                    goalId = GOAL_ID,
                    title = "Specification",
                    sortOrder = 0,
                    weight = 1.0,
                    isBinary = true,
                    completed = false,
                    progressPercent = null,
                    sync = SyncMetadata(updatedAt = NOW, version = 1, deletedAt = null),
                )
            milestoneRepository.seed(milestone)
            val vm = viewModel()

            vm.toggleMilestone(milestone)

            val state = vm.state.value
            check(state is GoalDetailUiState.Content)
            assertTrue(state.milestones.single().completed)
        }
}
