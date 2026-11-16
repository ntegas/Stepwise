package com.stepwise.feature.activities

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stepwise.core.common.AppResult
import com.stepwise.core.model.ActivityId
import com.stepwise.core.model.HabitId
import com.stepwise.core.model.MetricId
import com.stepwise.core.model.UserId
import com.stepwise.domain.repository.AuthRepository
import com.stepwise.domain.usecase.CreateHabitCommand
import com.stepwise.domain.usecase.CreateHabitUseCase
import com.stepwise.domain.usecase.EditHabitCommand
import com.stepwise.domain.usecase.EditHabitUseCase
import com.stepwise.domain.usecase.GetActivitiesUseCase
import com.stepwise.domain.usecase.GetMetricsUseCase
import com.stepwise.domain.usecase.ObserveHabitUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val KEY_HABIT_ID = "habitId"

/** Create/Edit Habit form (`/ROADMAP.md` DEV-018) - mirrors [ActivityFormViewModel] for Habit's own field set. */
@Suppress("TooManyFunctions", "LongParameterList")
// One on*Changed handler per field, plus load/save; one dependency per thing this form loads or saves to.
@HiltViewModel
class HabitFormViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val authRepository: AuthRepository,
        private val getMetricsUseCase: GetMetricsUseCase,
        private val getActivitiesUseCase: GetActivitiesUseCase,
        private val observeHabitUseCase: ObserveHabitUseCase,
        private val createHabitUseCase: CreateHabitUseCase,
        private val editHabitUseCase: EditHabitUseCase,
    ) : ViewModel() {
        private val editingHabitId: HabitId? = savedStateHandle.get<String>(KEY_HABIT_ID)?.let { HabitId(it) }
        private val initialMode: HabitFormMode = editingHabitId?.let { HabitFormMode.Edit(it) } ?: HabitFormMode.Create

        private val _state = MutableStateFlow(HabitFormUiState(mode = initialMode))
        val state: StateFlow<HabitFormUiState> = _state.asStateFlow()

        init {
            viewModelScope.launch {
                val userId = authRepository.observeUserId().first() ?: return@launch
                loadPickerOptions(userId)
                editingHabitId?.let { loadExistingHabit(it, userId) }
            }
        }

        private suspend fun loadPickerOptions(userId: UserId) {
            val metrics = (getMetricsUseCase.execute().first() as? AppResult.Success)?.value.orEmpty()
            val activities = (getActivitiesUseCase.execute(userId).first() as? AppResult.Success)?.value.orEmpty()
            _state.value = _state.value.copy(metrics = metrics, activities = activities)
        }

        private suspend fun loadExistingHabit(
            habitId: HabitId,
            userId: UserId,
        ) {
            when (val result = observeHabitUseCase.execute(userId, habitId).first()) {
                is AppResult.Success -> {
                    val habit = result.value
                    _state.value =
                        if (habit == null) _state.value.copy(hasError = true) else _state.value.copyFrom(habit)
                }
                is AppResult.Failure -> _state.value = _state.value.copy(hasError = true)
            }
        }

        fun onTitleChanged(value: String) {
            _state.value = _state.value.copy(title = value, titleError = false, hasError = false)
        }

        fun onTargetValueChanged(value: String) {
            _state.value = _state.value.copy(targetValue = value, targetValueError = false, hasError = false)
        }

        fun onMetricSelected(id: MetricId) {
            _state.value = _state.value.copy(metricId = id, metricError = false, hasError = false)
        }

        fun onActivitySelected(id: ActivityId?) {
            _state.value = _state.value.copy(activityId = id)
        }

        fun save() {
            val current = _state.value
            if (current.isSubmitting) return
            val title = current.title.trim()
            val targetValue = current.targetValue.trim().toDoubleOrNull()
            val titleInvalid = title.isBlank()
            val targetValueInvalid = targetValue == null || !targetValue.isFinite() || targetValue <= 0.0
            val metricId = current.metricId
            val metricInvalid = metricId == null
            if (titleInvalid || targetValueInvalid || metricInvalid) {
                _state.value =
                    current.copy(
                        titleError = titleInvalid,
                        targetValueError = targetValueInvalid,
                        metricError = metricInvalid,
                    )
                return
            }
            _state.value = current.copy(isSubmitting = true, hasError = false)
            viewModelScope.launch {
                saveHabit(current, title, checkNotNull(targetValue), checkNotNull(metricId))
            }
        }

        private suspend fun saveHabit(
            current: HabitFormUiState,
            title: String,
            targetValue: Double,
            metricId: MetricId,
        ) {
            val userId = authRepository.observeUserId().first()
            if (userId == null) {
                _state.value = _state.value.copy(isSubmitting = false, hasError = true)
                return
            }
            val saveResult =
                when (val mode = current.mode) {
                    is HabitFormMode.Create ->
                        createHabitUseCase.execute(
                            CreateHabitCommand(
                                userId = userId,
                                title = title,
                                targetValue = targetValue,
                                metricId = metricId,
                                activityId = current.activityId,
                            ),
                        )
                    is HabitFormMode.Edit ->
                        editHabitUseCase.execute(
                            EditHabitCommand(
                                userId = userId,
                                habitId = mode.habitId,
                                title = title,
                                targetValue = targetValue,
                                metricId = metricId,
                                activityId = current.activityId,
                            ),
                        )
                }
            _state.value =
                when (saveResult) {
                    is AppResult.Success -> _state.value.copy(isSubmitting = false, saved = true)
                    is AppResult.Failure -> _state.value.copy(isSubmitting = false, hasError = true)
                }
        }
    }
