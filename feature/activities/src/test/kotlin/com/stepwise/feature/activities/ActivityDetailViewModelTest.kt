package com.stepwise.feature.activities

import androidx.lifecycle.SavedStateHandle
import com.stepwise.core.common.AppResult
import com.stepwise.core.common.FakeClock
import com.stepwise.core.common.FakeIdGenerator
import com.stepwise.core.model.Activity
import com.stepwise.core.model.ActivityId
import com.stepwise.core.model.ExecutableKind
import com.stepwise.core.model.Goal
import com.stepwise.core.model.GoalId
import com.stepwise.core.model.GoalProgressMode
import com.stepwise.core.model.GoalType
import com.stepwise.core.model.LifecycleStatus
import com.stepwise.core.model.Metric
import com.stepwise.core.model.MetricId
import com.stepwise.core.model.ProgressSourceKind
import com.stepwise.core.model.SyncMetadata
import com.stepwise.core.model.UserId
import com.stepwise.domain.fake.FakeActivityRepository
import com.stepwise.domain.fake.FakeAuthRepository
import com.stepwise.domain.fake.FakeGoalActivityLinkRepository
import com.stepwise.domain.fake.FakeGoalRepository
import com.stepwise.domain.fake.FakeHabitRepository
import com.stepwise.domain.fake.FakeLifeAreaRepository
import com.stepwise.domain.fake.FakeMetricRepository
import com.stepwise.domain.fake.FakeRecurrenceOccurrenceRepository
import com.stepwise.domain.fake.FakeRecurrenceRuleRepository
import com.stepwise.domain.fake.FakeSessionRepository
import com.stepwise.domain.fake.FakeTaskRepository
import com.stepwise.domain.usecase.CompleteOccurrenceUseCase
import com.stepwise.domain.usecase.EditActivityUseCase
import com.stepwise.domain.usecase.GetGoalLinksForSourceUseCase
import com.stepwise.domain.usecase.GetGoalsUseCase
import com.stepwise.domain.usecase.GetMetricsUseCase
import com.stepwise.domain.usecase.GetSessionHistoryUseCase
import com.stepwise.domain.usecase.LinkActivityToGoalUseCase
import com.stepwise.domain.usecase.MetricResolver
import com.stepwise.domain.usecase.ObserveActivityUseCase
import com.stepwise.domain.usecase.UnlinkActivityFromGoalUseCase
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

private val NOW = LocalDateTime(2026, 11, 15, 12, 0).toInstant(TimeZone.UTC)
private val OWNER = UserId("owner-1")
private val ACTIVITY_ID = ActivityId("activity-1")
private val GOAL_ID = GoalId("goal-1")
private val COUNT_METRIC = Metric(id = MetricId("count"), key = "count", canonicalUnit = "x")

private fun activity(): Activity =
    Activity(
        id = ACTIVITY_ID,
        userId = OWNER,
        lifeAreaId = null,
        title = "Boxing",
        icon = null,
        archived = false,
        sync = SyncMetadata(updatedAt = NOW, version = 1, deletedAt = null),
    )

private fun goal(): Goal =
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
        status = LifecycleStatus.ACTIVE,
        createdAt = NOW,
        sync = SyncMetadata(updatedAt = NOW, version = 1, deletedAt = null),
    )

class ActivityDetailViewModelTest {
    private val activityRepository = FakeActivityRepository()
    private val habitRepository = FakeHabitRepository()
    private val taskRepository = FakeTaskRepository()
    private val lifeAreaRepository = FakeLifeAreaRepository()
    private val goalRepository = FakeGoalRepository()
    private val goalActivityLinkRepository = FakeGoalActivityLinkRepository()
    private val metricRepository = FakeMetricRepository()
    private val sessionRepository = FakeSessionRepository()
    private val ruleRepository = FakeRecurrenceRuleRepository()
    private val occurrenceRepository = FakeRecurrenceOccurrenceRepository()
    private val authRepository = FakeAuthRepository(initialUserId = OWNER)

    private val observeActivityUseCase = ObserveActivityUseCase(activityRepository)
    private val getGoalLinksForSourceUseCase =
        GetGoalLinksForSourceUseCase(activityRepository, habitRepository, goalActivityLinkRepository)
    private val getGoalsUseCase = GetGoalsUseCase(goalRepository)
    private val getMetricsUseCase = GetMetricsUseCase(metricRepository)
    private val getSessionHistoryUseCase =
        GetSessionHistoryUseCase(taskRepository, activityRepository, habitRepository, sessionRepository)
    private val editActivityUseCase = EditActivityUseCase(activityRepository, lifeAreaRepository, FakeClock(NOW))
    private val linkActivityToGoalUseCase =
        LinkActivityToGoalUseCase(
            activityRepository,
            habitRepository,
            goalRepository,
            metricRepository,
            goalActivityLinkRepository,
            FakeIdGenerator(),
            FakeClock(NOW),
        )
    private val unlinkActivityFromGoalUseCase =
        UnlinkActivityFromGoalUseCase(goalRepository, goalActivityLinkRepository, FakeClock(NOW))
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
        metricRepository.seed(COUNT_METRIC)
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(activityId: ActivityId = ACTIVITY_ID) =
        ActivityDetailViewModel(
            SavedStateHandle(mapOf("activityId" to activityId.value)),
            authRepository,
            observeActivityUseCase,
            getGoalLinksForSourceUseCase,
            getGoalsUseCase,
            getMetricsUseCase,
            getSessionHistoryUseCase,
            editActivityUseCase,
            linkActivityToGoalUseCase,
            unlinkActivityFromGoalUseCase,
            completeOccurrenceUseCase,
        )

    @Test
    fun `an activity that doesn't exist yields NotFound`() =
        runTest {
            val state = viewModel(ActivityId("no-such-activity")).state.value

            assertEquals(ActivityDetailUiState.NotFound, state)
        }

    @Test
    fun `toggling archived flips the flag reactively`() =
        runTest {
            activityRepository.seed(activity())
            val vm = viewModel()

            vm.toggleArchived()

            val state = vm.state.value
            check(state is ActivityDetailUiState.Content)
            assertTrue(state.activity.archived)
        }

    @Test
    fun `logging a manual session writes a real Session through the canonical use case`() =
        runTest {
            activityRepository.seed(activity())
            val vm = viewModel()

            vm.onLogSessionClicked()
            vm.onManualEntryDateChanged("2026-11-15")
            vm.onConfirmManualEntry()

            assertEquals(1, sessionRepository.sessionCount)
            val sessions = sessionRepository.observeForSource(ExecutableKind.ACTIVITY, ACTIVITY_ID.value).first()
            assertEquals(1, sessions.size)
            val state = vm.state.value
            check(state is ActivityDetailUiState.Content)
            assertEquals(false, state.manualEntryDialogVisible)
        }

    @Test
    fun `linking to a goal creates a real GoalActivityLink`() =
        runTest {
            activityRepository.seed(activity())
            goalRepository.seed(goal())
            val vm = viewModel()

            vm.onLinkGoalClicked()
            vm.onLinkGoalSelected(GOAL_ID)
            vm.onLinkMetricSelected(COUNT_METRIC.id)
            vm.onConfirmLinkGoal()

            val state = vm.state.value
            check(state is ActivityDetailUiState.Content)
            assertEquals(false, state.linkGoalDialogVisible)
            val linksResult =
                getGoalLinksForSourceUseCase.execute(OWNER, ProgressSourceKind.ACTIVITY, ACTIVITY_ID.value).first()
            check(linksResult is AppResult.Success)
            assertEquals(1, linksResult.value.size)
        }
}
