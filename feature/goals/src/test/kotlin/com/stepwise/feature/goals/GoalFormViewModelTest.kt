package com.stepwise.feature.goals

import androidx.lifecycle.SavedStateHandle
import com.stepwise.core.common.FakeClock
import com.stepwise.core.common.FakeIdGenerator
import com.stepwise.core.model.Goal
import com.stepwise.core.model.GoalId
import com.stepwise.core.model.GoalProgressMode
import com.stepwise.core.model.GoalType
import com.stepwise.core.model.LifecycleStatus
import com.stepwise.core.model.SyncMetadata
import com.stepwise.core.model.UserId
import com.stepwise.domain.fake.FakeAuthRepository
import com.stepwise.domain.fake.FakeGoalRepository
import com.stepwise.domain.fake.FakeLifeAreaRepository
import com.stepwise.domain.fake.FakeProjectRepository
import com.stepwise.domain.usecase.CreateGoalUseCase
import com.stepwise.domain.usecase.EditGoalUseCase
import com.stepwise.domain.usecase.GetGoalByIdUseCase
import com.stepwise.domain.usecase.GetLifeAreasUseCase
import com.stepwise.domain.usecase.GetProjectsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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

class GoalFormViewModelTest {
    private val goalRepository = FakeGoalRepository()
    private val lifeAreaRepository = FakeLifeAreaRepository()
    private val projectRepository = FakeProjectRepository()
    private val authRepository = FakeAuthRepository(initialUserId = OWNER)
    private val idGenerator = FakeIdGenerator()
    private val clock = FakeClock(NOW)
    private val createGoalUseCase =
        CreateGoalUseCase(goalRepository, lifeAreaRepository, projectRepository, idGenerator, clock)
    private val editGoalUseCase = EditGoalUseCase(goalRepository, lifeAreaRepository, projectRepository, clock)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(savedStateHandle: SavedStateHandle = SavedStateHandle()) =
        GoalFormViewModel(
            savedStateHandle,
            authRepository,
            GetLifeAreasUseCase(lifeAreaRepository),
            GetProjectsUseCase(projectRepository),
            GetGoalByIdUseCase(goalRepository),
            createGoalUseCase,
            editGoalUseCase,
        )

    private fun existingGoal(): Goal =
        Goal(
            id = GoalId("goal-1"),
            userId = OWNER,
            lifeAreaId = null,
            projectId = null,
            title = "100 Hours Boxing",
            type = GoalType.QUANTITY,
            progressMode = GoalProgressMode.TARGET_VALUE,
            targetValue = 100.0,
            unit = "hours",
            deadline = null,
            priority = null,
            category = null,
            description = null,
            status = LifecycleStatus.ACTIVE,
            createdAt = NOW,
            sync = SyncMetadata(updatedAt = NOW, version = 1, deletedAt = null),
        )

    @Test
    fun `saving with a blank title shows an inline error and does not persist a Goal`() =
        runTest {
            val vm = viewModel()

            vm.save()

            assertTrue(vm.state.value.titleError)
            assertTrue(goalRepository.observeAll(OWNER).first().isEmpty())
        }

    @Test
    fun `saving with a title, target, and unit creates a TARGET_VALUE QUANTITY Goal`() =
        runTest {
            val vm = viewModel()
            vm.onTitleChanged("100 Hours Boxing")
            vm.onTargetValueChanged("100")
            vm.onUnitChanged("hours")

            vm.save()

            assertTrue(vm.state.value.saved)
            val created = goalRepository.get(GoalId("id-1"))
            check(created != null)
            assertEquals(GoalProgressMode.TARGET_VALUE, created.progressMode)
            assertEquals(GoalType.QUANTITY, created.type)
            assertEquals(100.0, created.targetValue)
            assertEquals("hours", created.unit)
        }

    @Test
    fun `saving with only a title defaults to a MILESTONES CUMULATIVE Goal`() =
        runTest {
            val vm = viewModel()
            vm.onTitleChanged("Learn Kotlin")

            vm.save()

            assertTrue(vm.state.value.saved)
            val created = goalRepository.get(GoalId("id-1"))
            check(created != null)
            assertEquals(GoalProgressMode.MILESTONES, created.progressMode)
            assertEquals(GoalType.CUMULATIVE, created.type)
        }

    @Test
    fun `an invalid deadline shows an inline error and does not persist a Goal`() =
        runTest {
            val vm = viewModel()
            vm.onTitleChanged("Learn Kotlin")
            vm.onDeadlineChanged("not-a-date")

            vm.save()

            assertTrue(vm.state.value.deadlineError)
            assertFalse(vm.state.value.saved)
        }

    @Test
    fun `edit mode pre-fills fields from the existing Goal`() =
        runTest {
            goalRepository.seed(existingGoal())
            val savedStateHandle = SavedStateHandle(mapOf("goalId" to "goal-1"))

            val state = viewModel(savedStateHandle).state.value

            assertEquals("100 Hours Boxing", state.title)
            assertEquals("100.0", state.targetValue)
            assertEquals("hours", state.unit)
        }

    @Test
    fun `editing an existing Goal's title persists the change`() =
        runTest {
            goalRepository.seed(existingGoal())
            val savedStateHandle = SavedStateHandle(mapOf("goalId" to "goal-1"))
            val vm = viewModel(savedStateHandle)

            vm.onTitleChanged("200 Hours Boxing")
            vm.save()

            assertTrue(vm.state.value.saved)
            assertEquals("200 Hours Boxing", goalRepository.get(GoalId("goal-1"))?.title)
        }
}
