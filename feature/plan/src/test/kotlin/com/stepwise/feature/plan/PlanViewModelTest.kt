package com.stepwise.feature.plan

import com.stepwise.core.common.FakeClock
import com.stepwise.core.common.FakeIdGenerator
import com.stepwise.core.model.ExecutableKind
import com.stepwise.core.model.SyncMetadata
import com.stepwise.core.model.Task
import com.stepwise.core.model.TaskId
import com.stepwise.core.model.UserId
import com.stepwise.domain.fake.FakeActivityRepository
import com.stepwise.domain.fake.FakeAuthRepository
import com.stepwise.domain.fake.FakeHabitRepository
import com.stepwise.domain.fake.FakeRecurrenceOccurrenceRepository
import com.stepwise.domain.fake.FakeRecurrenceRuleRepository
import com.stepwise.domain.fake.FakeSessionRepository
import com.stepwise.domain.fake.FakeTaskRepository
import com.stepwise.domain.usecase.CompleteOccurrenceUseCase
import com.stepwise.domain.usecase.GetPlanRangeUseCase
import com.stepwise.domain.usecase.MetricResolver
import com.stepwise.domain.usecase.ResolveOccurrencesUseCase
import com.stepwise.domain.usecase.toCompletionTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

// A Sunday - deliberately not a Monday, so a wrong day-of-week-to-Monday calculation would be
// caught by the "today's own Task appears in the initial week" test below.
private val TODAY = LocalDate(2026, 11, 15)
private val MONDAY_OF_TODAY = LocalDate(2026, 11, 9)
private val NOW = LocalDateTime(2026, 11, 15, 12, 0).toInstant(TimeZone.UTC)
private val OWNER = UserId("owner-1")
private val SYNC = SyncMetadata(updatedAt = NOW, version = 1, deletedAt = null)

private fun task(date: LocalDate): Task =
    Task(
        id = TaskId("task-1"),
        userId = OWNER,
        goalId = null,
        projectId = null,
        lifeAreaId = null,
        title = "Read",
        date = date,
        time = null,
        deadline = null,
        durationMinutes = null,
        priority = null,
        reminderId = null,
        metricId = null,
        plannedResult = null,
        goalImpactScore = null,
        archived = false,
        createdAt = NOW,
        sync = SYNC,
    )

class PlanViewModelTest {
    private val taskRepository = FakeTaskRepository()
    private val activityRepository = FakeActivityRepository()
    private val habitRepository = FakeHabitRepository()
    private val ruleRepository = FakeRecurrenceRuleRepository()
    private val occurrenceRepository = FakeRecurrenceOccurrenceRepository()
    private val sessionRepository = FakeSessionRepository()
    private val authRepository = FakeAuthRepository(initialUserId = OWNER)
    private val resolveOccurrencesUseCase = ResolveOccurrencesUseCase(ruleRepository, occurrenceRepository)
    private val getPlanRangeUseCase =
        GetPlanRangeUseCase(
            taskRepository,
            activityRepository,
            habitRepository,
            ruleRepository,
            sessionRepository,
            resolveOccurrencesUseCase,
        )
    private val completeOccurrenceUseCase =
        CompleteOccurrenceUseCase(
            metricResolver = MetricResolver(taskRepository, activityRepository, habitRepository),
            recurrenceRuleRepository = ruleRepository,
            recurrenceOccurrenceRepository = occurrenceRepository,
            sessionRepository = sessionRepository,
            taskRepository = taskRepository,
            idGenerator = FakeIdGenerator(),
            clock = FakeClock(NOW),
        )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() =
        PlanViewModel(authRepository, getPlanRangeUseCase, completeOccurrenceUseCase, FakeClock(NOW))

    @Test
    fun `a signed-out user sees SignedOut, not Content or an error`() =
        runTest {
            authRepository.setUserId(null)

            val state = viewModel().state.value

            assertEquals(PlanUiState.SignedOut, state)
        }

    @Test
    fun `the initial week starts on the Monday of today's own date`() =
        runTest {
            taskRepository.seed(task(TODAY))

            val state = viewModel().state.value

            check(state is PlanUiState.Content)
            assertEquals(MONDAY_OF_TODAY, state.agenda.range.start)
            assertEquals(1, state.agenda.days.single { it.date == TODAY }.items.size)
        }

    @Test
    fun `onNextWeek then onPreviousWeek returns to the original week`() =
        runTest {
            val vm = viewModel()
            val originalStart = (vm.state.value as PlanUiState.Content).agenda.range.start

            vm.onNextWeek()
            val afterNext = (vm.state.value as PlanUiState.Content).agenda.range.start

            vm.onPreviousWeek()
            val afterPrevious = (vm.state.value as PlanUiState.Content).agenda.range.start

            assertEquals(originalStart.toEpochDays() + 7, afterNext.toEpochDays())
            assertEquals(originalStart, afterPrevious)
        }

    @Test
    fun `rescheduling a one-off Task moves it to the new date and clears the dialog target`() =
        runTest {
            val seeded = task(TODAY)
            taskRepository.seed(seeded)
            val vm = viewModel()
            val item = (vm.state.value as PlanUiState.Content).agenda.days.single { it.date == TODAY }.items.single()
            vm.onRescheduleClicked(item)
            check((vm.state.value as PlanUiState.Content).rescheduleTarget != null)

            vm.onRescheduleDateSelected(TODAY.plusDays(2))

            val state = vm.state.value
            check(state is PlanUiState.Content)
            assertNull(state.rescheduleTarget)
            val updated = taskRepository.getById(TaskId("task-1"))
            check(updated is com.stepwise.core.common.AppResult.Success)
            assertEquals(TODAY.plusDays(2), updated.value?.date)
        }

    private fun LocalDate.plusDays(days: Int): LocalDate = LocalDate.fromEpochDays(toEpochDays() + days)
}
