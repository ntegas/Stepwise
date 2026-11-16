package com.stepwise.feature.activities

import androidx.lifecycle.SavedStateHandle
import com.stepwise.core.common.FakeClock
import com.stepwise.core.common.FakeIdGenerator
import com.stepwise.core.model.Metric
import com.stepwise.core.model.MetricId
import com.stepwise.core.model.UserId
import com.stepwise.domain.fake.FakeActivityRepository
import com.stepwise.domain.fake.FakeAuthRepository
import com.stepwise.domain.fake.FakeHabitRepository
import com.stepwise.domain.fake.FakeMetricRepository
import com.stepwise.domain.usecase.CreateHabitUseCase
import com.stepwise.domain.usecase.EditHabitUseCase
import com.stepwise.domain.usecase.GetActivitiesUseCase
import com.stepwise.domain.usecase.GetMetricsUseCase
import com.stepwise.domain.usecase.ObserveHabitUseCase
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
private val COUNT_METRIC = Metric(id = MetricId("count"), key = "count", canonicalUnit = "x")

class HabitFormViewModelTest {
    private val habitRepository = FakeHabitRepository()
    private val activityRepository = FakeActivityRepository()
    private val metricRepository = FakeMetricRepository()
    private val authRepository = FakeAuthRepository(initialUserId = OWNER)
    private val getMetricsUseCase = GetMetricsUseCase(metricRepository)
    private val getActivitiesUseCase = GetActivitiesUseCase(activityRepository)
    private val observeHabitUseCase = ObserveHabitUseCase(habitRepository)
    private val createHabitUseCase =
        CreateHabitUseCase(habitRepository, activityRepository, metricRepository, FakeIdGenerator(), FakeClock(NOW))
    private val editHabitUseCase = EditHabitUseCase(habitRepository, activityRepository, metricRepository, FakeClock(NOW))

    @BeforeEach
    fun setUp() {
        metricRepository.seed(COUNT_METRIC)
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() =
        HabitFormViewModel(
            SavedStateHandle(),
            authRepository,
            getMetricsUseCase,
            getActivitiesUseCase,
            observeHabitUseCase,
            createHabitUseCase,
            editHabitUseCase,
        )

    @Test
    fun `creating a habit with a valid title, target, and metric succeeds`() =
        runTest {
            val vm = viewModel()
            vm.onTitleChanged("3x/week boxing")
            vm.onTargetValueChanged("3")
            vm.onMetricSelected(COUNT_METRIC.id)

            vm.save()

            assertTrue(vm.state.value.saved)
        }

    @Test
    fun `creating a habit with no metric selected shows an inline error and does not save`() =
        runTest {
            val vm = viewModel()
            vm.onTitleChanged("3x/week boxing")
            vm.onTargetValueChanged("3")

            vm.save()

            val state = vm.state.value
            assertTrue(state.metricError)
            assertEquals(false, state.saved)
        }

    @Test
    fun `creating a habit with a non-positive target shows an inline error`() =
        runTest {
            val vm = viewModel()
            vm.onTitleChanged("3x/week boxing")
            vm.onTargetValueChanged("0")
            vm.onMetricSelected(COUNT_METRIC.id)

            vm.save()

            val state = vm.state.value
            assertTrue(state.targetValueError)
            assertEquals(false, state.saved)
        }
}
