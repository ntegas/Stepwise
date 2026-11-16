package com.stepwise.feature.activities

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stepwise.core.common.AppResult
import com.stepwise.core.model.Activity
import com.stepwise.domain.repository.AuthRepository
import com.stepwise.domain.usecase.GetActivitiesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The Activities list (`/ROADMAP.md` DEV-018) - reactively driven by [GetActivitiesUseCase], the
 * same "no manual re-fetch" reactivity [GoalsListViewModel] (DEV-016) already established: any
 * write through [com.stepwise.domain.usecase.CreateActivityUseCase]/
 * [com.stepwise.domain.usecase.EditActivityUseCase] reaches the same Room database this screen
 * already observes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ActivitiesListViewModel
    @Inject
    constructor(
        private val authRepository: AuthRepository,
        private val getActivitiesUseCase: GetActivitiesUseCase,
    ) : ViewModel() {
        private val _state = MutableStateFlow<ActivitiesListUiState>(ActivitiesListUiState.Loading)
        val state: StateFlow<ActivitiesListUiState> = _state.asStateFlow()

        init {
            viewModelScope.launch {
                authRepository
                    .observeUserId()
                    .flatMapLatest { userId ->
                        if (userId == null) {
                            flowOf<AppResult<List<Activity>>?>(null)
                        } else {
                            getActivitiesUseCase.execute(userId)
                        }
                    }.collect { result -> _state.value = result.toUiState() }
            }
        }

        private fun AppResult<List<Activity>>?.toUiState(): ActivitiesListUiState =
            when (this) {
                null -> ActivitiesListUiState.SignedOut
                is AppResult.Success -> value.toContentState()
                is AppResult.Failure -> ActivitiesListUiState.Error
            }

        private fun List<Activity>.toContentState(): ActivitiesListUiState.Content =
            ActivitiesListUiState.Content(
                active = filter { !it.archived }.sortedBy { it.title },
                archived = filter { it.archived }.sortedBy { it.title },
            )
    }
