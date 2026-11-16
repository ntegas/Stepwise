package com.stepwise.feature.activities

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stepwise.core.common.AppResult
import com.stepwise.core.model.Habit
import com.stepwise.domain.repository.AuthRepository
import com.stepwise.domain.usecase.GetHabitsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The Habits list (`/ROADMAP.md` DEV-018) - mirrors [ActivitiesListViewModel] exactly. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HabitsListViewModel
    @Inject
    constructor(
        private val authRepository: AuthRepository,
        private val getHabitsUseCase: GetHabitsUseCase,
    ) : ViewModel() {
        private val _state = MutableStateFlow<HabitsListUiState>(HabitsListUiState.Loading)
        val state: StateFlow<HabitsListUiState> = _state.asStateFlow()

        init {
            viewModelScope.launch {
                authRepository
                    .observeUserId()
                    .flatMapLatest { userId ->
                        if (userId == null) {
                            flowOf<AppResult<List<Habit>>?>(null)
                        } else {
                            getHabitsUseCase.execute(userId)
                        }
                    }.collect { result -> _state.value = result.toUiState() }
            }
        }

        private fun AppResult<List<Habit>>?.toUiState(): HabitsListUiState =
            when (this) {
                null -> HabitsListUiState.SignedOut
                is AppResult.Success -> value.toContentState()
                is AppResult.Failure -> HabitsListUiState.Error
            }

        private fun List<Habit>.toContentState(): HabitsListUiState.Content =
            HabitsListUiState.Content(
                active = filter { !it.archived }.sortedBy { it.title },
                archived = filter { it.archived }.sortedBy { it.title },
            )
    }
