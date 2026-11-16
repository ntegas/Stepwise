package com.stepwise.feature.today

import com.stepwise.core.common.FakeClock
import com.stepwise.core.common.FakeIdGenerator
import com.stepwise.core.model.ExecutableKind
import com.stepwise.core.model.Habit
import com.stepwise.core.model.HabitId
import com.stepwise.core.model.MetricId
import com.stepwise.core.model.RecurrencePattern
import com.stepwise.core.model.RecurrenceRule
import com.stepwise.core.model.RecurrenceRuleId
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
import com.stepwise.domain.usecase.GetTodayAgendaUseCase
import com.stepwise.domain.usecase.MetricResolver
import com.stepwise.domain.usecase.ResolveOccurrencesUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

private val TODAY = LocalDate(2026, 11, 15)

// Derived from TODAY, not an independently-chosen literal - DEV-014's own test-fixture pitfall
// (two unrelated NOW/TODAY constants that are supposed to represent the same calendar day, with
// nothing enforcing that relationship) recurred verbatim in this sibling file, confirmed by a
// real CI run that discovered these tests for the first time (DEV-015, missing useJUnitPlatform()
// wiring) and failed 5 of 6 of them with NoSuchElementException/AssertionFailedError.
private val NOW = LocalDateTime(TODAY, LocalTime(12, 0)).toInstant(TimeZone.UTC)
private val OWNER = UserId("owner-1")

private fun task(): Task =
    Task(
        id = TaskId("task-1"),
        userId = OWNER,
        goalId = null,
        projectId = null,
        lifeAreaId = null,
        title = "Read",
        date = TODAY,
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
        sync = SyncMetadata(updatedAt = NOW, version = 1, deletedAt = null),
    )

class TodayViewModelTest {
    private val taskRepository = FakeTaskRepository()
    private val activityRepository = FakeActivityRepository()
    private val habitRepository = FakeHabitRepository()
    private val ruleRepository = FakeRecurrenceRuleRepository()
    private val occurrenceRepository = FakeRecurrenceOccurrenceRepository()
    private val sessionRepository = FakeSessionRepository()
    private val authRepository = FakeAuthRepository(initialUserId = OWNER)
    private val clock = FakeClock(NOW)
    private val resolveOccurrencesUseCase = ResolveOccurrencesUseCase(ruleRepository, occurrenceRepository)
    private val getTodayAgendaUseCase =
        GetTodayAgendaUseCase(
            taskRepository,
            activityRepository,
            habitRepository,
            ruleRepository,
            sessionRepository,
            resolveOccurrencesUseCase,
            clock,
        )
    private val completeOccurrenceUseCase =
        CompleteOccurrenceUseCase(
            MetricResolver(taskRepository, activityRepository, habitRepository),
            ruleRepository,
            occurrenceRepository,
            sessionRepository,
            taskRepository,
            FakeIdGenerator(),
            clock,
        )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = TodayViewModel(authRepository, getTodayAgendaUseCase, completeOccurrenceUseCase)

    @Test
    fun `a signed-in user with a scheduled Task sees it as Content`() =
        runTest {
            taskRepository.seed(task())

            val state = viewModel().state.value

            check(state is TodayUiState.Content)
            assertEquals(1, state.agenda.items.size)
        }

    @Test
    fun `a signed-out user sees SignedOut, not Content or an error`() =
        runTest {
            authRepository.setUserId(null)

            val state = viewModel().state.value

            assertEquals(TodayUiState.SignedOut, state)
        }

    @Test
    fun `quick-done on a one-off Task writes a Session and the agenda reflects it reactively`() =
        runTest {
            taskRepository.seed(task())
            val vm = viewModel()
            val before = vm.state.value
            check(before is TodayUiState.Content)
            val item = before.agenda.items.single()

            vm.onQuickDone(item)

            val after = vm.state.value
            check(after is TodayUiState.Content)
            val completedItem = after.agenda.items.single()
            assertTrue(completedItem.isCompleted)
        }

    @Test
    fun `quick-done on a recurring item completes via the recurring CompletionTarget`() =
        runTest {
            habitRepository.seed(
                Habit(
                    id = HabitId("habit-1"),
                    userId = OWNER,
                    activityId = null,
                    title = "Stretch",
                    targetValue = 1.0,
                    metricId = MetricId("count"),
                    archived = false,
                    sync = SyncMetadata(updatedAt = NOW, version = 1, deletedAt = null),
                ),
            )
            ruleRepository.seed(
                RecurrenceRule(
                    id = RecurrenceRuleId("rule-1"),
                    userId = OWNER,
                    ownerType = ExecutableKind.HABIT,
                    ownerId = "habit-1",
                    pattern = RecurrencePattern.Daily,
                    time = null,
                    startsOn = TODAY,
                    endsOn = null,
                    sync = SyncMetadata(updatedAt = NOW, version = 1, deletedAt = null),
                ),
            )
            val vm = viewModel()
            val before = vm.state.value
            check(before is TodayUiState.Content)
            val item = before.agenda.items.single()

            vm.onQuickDone(item)

            val after = vm.state.value
            check(after is TodayUiState.Content)
            val completedItem = after.agenda.items.single()
            assertTrue(completedItem.isCompleted)
        }

    @Test
    fun `a completion that fails ownership verification emits a CompletionFailed event`() =
        // Not the bare runTest { } every other test here uses: runTest's own default dispatcher
        // (StandardTestDispatcher) only QUEUES a bare launch { } rather than running it, unlike
        // viewModelScope's (Dispatchers.Main, overridden to UnconfinedTestDispatcher in setUp) -
        // runCurrent() right after starting the collector job below did not fix this (confirmed
        // by a real CI run still losing the emission identically), so the test body itself now
        // runs on an UnconfinedTestDispatcher too, matching viewModelScope's eager semantics
        // exactly rather than trying to interleave two different dispatchers' scheduling models.
        runTest(UnconfinedTestDispatcher()) {
            taskRepository.seed(task())
            val vm = viewModel()
            val before = vm.state.value
            check(before is TodayUiState.Content)
            // A TodayItem pointing at a Task that doesn't exist - MetricResolver's own
            // ownership check rejects it, exercising the failure path onQuickDone used to discard
            // entirely (DEV-015 review, user-flow reviewer, HIGH).
            val existingItem = before.agenda.items.single()
            val missingTaskItem = existingItem.copy(sourceId = "no-such-task")

            var receivedEvent: TodayEvent? = null
            val job = launch { receivedEvent = vm.events.first() }
            vm.onQuickDone(missingTaskItem)

            assertEquals(TodayEvent.CompletionFailed, receivedEvent)
            job.cancel()
        }

    @Test
    fun `quick-done does not write a Session twice for one tap`() =
        runTest {
            taskRepository.seed(task())
            val vm = viewModel()
            val before = vm.state.value
            check(before is TodayUiState.Content)
            val item = before.agenda.items.single()

            vm.onQuickDone(item)

            assertEquals(1, sessionRepository.sessionCount)
        }
}
