package com.stepwise.feature.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stepwise.core.common.AppResult
import com.stepwise.core.model.Goal
import com.stepwise.core.model.LifecycleStatus
import com.stepwise.domain.repository.AuthRepository
import com.stepwise.domain.usecase.GetGoalsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The Goals list (`/ROADMAP.md` DEV-016) — reactively driven by [GetGoalsUseCase], the same
 * "no manual re-fetch" reactivity [com.stepwise.domain.usecase.GetTodayAgendaUseCase] already
 * gives Today (DEV-014/015): any write through [com.stepwise.domain.usecase.CreateGoalUseCase]/
 * [com.stepwise.domain.usecase.EditGoalUseCase]/
 * [com.stepwise.domain.usecase.UpdateGoalStatusUseCase] reaches the same Room database this
 * screen already observes, so [state] updates itself.
 *
 * **Grouped Active/Completed/Archived, with Paused folded into Active** — `/PRODUCT_CANON.md`'s
 * own Goal lifecycle section states Paused is a first-class state, not a hidden one, so a paused
 * Goal stays visible alongside active ones (marked with a chip in [GoalsListScreen]) rather than
 * disappearing into its own rarely-checked section.
 */
@HiltViewModel
class GoalsListViewModel
    @Inject
    constructor(
        private val authRepository: AuthRepository,
        private val getGoalsUseCase: GetGoalsUseCase,
    ) : ViewModel() {
        private val _state = MutableStateFlow<GoalsListUiState>(GoalsListUiState.Loading)
        val state: StateFlow<GoalsListUiState> = _state.asStateFlow()

        init {
            viewModelScope.launch {
                authRepository
                    .observeUserId()
                    .flatMapLatest { userId ->
                        if (userId == null) {
                            flowOf<AppResult<List<Goal>>?>(null)
                        } else {
                            getGoalsUseCase.execute(userId)
                        }
                    }.collect { result -> _state.value = result.toUiState() }
            }
        }

        private fun AppResult<List<Goal>>?.toUiState(): GoalsListUiState =
            when (this) {
                null -> GoalsListUiState.SignedOut
                is AppResult.Success -> value.toContentState()
                is AppResult.Failure -> GoalsListUiState.Error(error.message)
            }

        private fun List<Goal>.toContentState(): GoalsListUiState.Content =
            GoalsListUiState.Content(
                active = byStatus(LifecycleStatus.ACTIVE, LifecycleStatus.PAUSED),
                completed = byStatus(LifecycleStatus.COMPLETED),
                archived = byStatus(LifecycleStatus.ARCHIVED),
            )

        private fun List<Goal>.byStatus(vararg statuses: LifecycleStatus): List<Goal> =
            filter { it.status in statuses }.sortedBy { it.title }
    }
