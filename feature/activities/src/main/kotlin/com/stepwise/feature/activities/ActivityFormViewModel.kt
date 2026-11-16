package com.stepwise.feature.activities

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stepwise.core.common.AppResult
import com.stepwise.core.model.ActivityId
import com.stepwise.core.model.LifeAreaId
import com.stepwise.core.model.UserId
import com.stepwise.domain.repository.AuthRepository
import com.stepwise.domain.usecase.CreateActivityCommand
import com.stepwise.domain.usecase.CreateActivityUseCase
import com.stepwise.domain.usecase.EditActivityCommand
import com.stepwise.domain.usecase.EditActivityUseCase
import com.stepwise.domain.usecase.GetLifeAreasUseCase
import com.stepwise.domain.usecase.ObserveActivityUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val KEY_ACTIVITY_ID = "activityId"

/**
 * Create/Edit Activity form (`/ROADMAP.md` DEV-018) - the same shape `GoalFormViewModel`
 * (DEV-016) established, narrowed to Activity's own, much smaller field set (no Target/Unit/
 * Deadline/Project/Category/Description/Priority - an Activity has only title and an optional
 * Life Area, `/core/model/.../Activity.kt`'s own KDoc).
 */
@Suppress("LongParameterList") // One repository/Use Case per thing this form loads, picks from, or saves to.
@HiltViewModel
class ActivityFormViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val authRepository: AuthRepository,
        private val getLifeAreasUseCase: GetLifeAreasUseCase,
        private val observeActivityUseCase: ObserveActivityUseCase,
        private val createActivityUseCase: CreateActivityUseCase,
        private val editActivityUseCase: EditActivityUseCase,
    ) : ViewModel() {
        private val editingActivityId: ActivityId? =
            savedStateHandle.get<String>(KEY_ACTIVITY_ID)?.let { ActivityId(it) }

        private val initialMode: ActivityFormMode =
            editingActivityId?.let { ActivityFormMode.Edit(it) } ?: ActivityFormMode.Create
        private val _state = MutableStateFlow(ActivityFormUiState(mode = initialMode))
        val state: StateFlow<ActivityFormUiState> = _state.asStateFlow()

        init {
            viewModelScope.launch {
                val userId = authRepository.observeUserId().first() ?: return@launch
                val lifeAreas = (getLifeAreasUseCase.execute(userId).first() as? AppResult.Success)?.value.orEmpty()
                _state.value = _state.value.copy(lifeAreas = lifeAreas)
                editingActivityId?.let { loadExistingActivity(it, userId) }
            }
        }

        private suspend fun loadExistingActivity(
            activityId: ActivityId,
            userId: UserId,
        ) {
            when (val result = observeActivityUseCase.execute(userId, activityId).first()) {
                is AppResult.Success -> {
                    val activity = result.value
                    if (activity == null) {
                        _state.value = _state.value.copy(hasError = true)
                    } else {
                        _state.value = _state.value.copyFrom(activity)
                    }
                }
                is AppResult.Failure -> _state.value = _state.value.copy(hasError = true)
            }
        }

        fun onTitleChanged(value: String) {
            _state.value = _state.value.copy(title = value, titleError = false, hasError = false)
        }

        fun onLifeAreaSelected(id: LifeAreaId?) {
            _state.value = _state.value.copy(lifeAreaId = id)
        }

        fun save() {
            val current = _state.value
            if (current.isSubmitting) return
            val title = current.title.trim()
            if (title.isBlank()) {
                _state.value = current.copy(titleError = true)
                return
            }
            _state.value = current.copy(isSubmitting = true, hasError = false, titleError = false)
            viewModelScope.launch {
                val userId = authRepository.observeUserId().first()
                if (userId == null) {
                    _state.value = _state.value.copy(isSubmitting = false, hasError = true)
                    return@launch
                }
                val saveResult =
                    when (val mode = current.mode) {
                        is ActivityFormMode.Create ->
                            createActivityUseCase.execute(
                                CreateActivityCommand(userId = userId, title = title, lifeAreaId = current.lifeAreaId),
                            )
                        is ActivityFormMode.Edit ->
                            editActivityUseCase.execute(
                                EditActivityCommand(
                                    userId = userId,
                                    activityId = mode.activityId,
                                    title = title,
                                    lifeAreaId = current.lifeAreaId,
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
    }
