package com.stepwise.feature.goals

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stepwise.core.common.AppResult
import com.stepwise.core.model.Goal
import com.stepwise.core.model.GoalId
import com.stepwise.core.model.LifecycleStatus
import com.stepwise.core.model.Milestone
import com.stepwise.domain.repository.AuthRepository
import com.stepwise.domain.usecase.AddMilestoneCommand
import com.stepwise.domain.usecase.AddMilestoneUseCase
import com.stepwise.domain.usecase.EditMilestoneCommand
import com.stepwise.domain.usecase.EditMilestoneUseCase
import com.stepwise.domain.usecase.GetGoalsUseCase
import com.stepwise.domain.usecase.GetMilestonesUseCase
import com.stepwise.domain.usecase.UpdateGoalStatusUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val KEY_GOAL_ID = "goalId"

/**
 * Goal Detail (`/ROADMAP.md` DEV-016) — Goal metadata, lifecycle actions, and the Milestones
 * checklist. Deliberately reuses [GetGoalsUseCase] (the same reactive Flow [GoalsListViewModel]
 * observes) rather than a one-shot `getById` lookup: [GoalRepository][com.stepwise.domain.repository.GoalRepository]
 * exposes no `observeById`, and re-deriving the single Goal from the already-reactive
 * `observeAll` Flow keeps this screen's own "no manual re-fetch" guarantee after a lifecycle
 * action or edit, the same reactivity [GoalsListViewModel]'s own KDoc already relies on, instead
 * of adding a second, narrower reactive repository method for one caller.
 */
@HiltViewModel
class GoalDetailViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val authRepository: AuthRepository,
        private val getGoalsUseCase: GetGoalsUseCase,
        private val getMilestonesUseCase: GetMilestonesUseCase,
        private val updateGoalStatusUseCase: UpdateGoalStatusUseCase,
        private val addMilestoneUseCase: AddMilestoneUseCase,
        private val editMilestoneUseCase: EditMilestoneUseCase,
    ) : ViewModel() {
        private val goalId: GoalId = GoalId(checkNotNull(savedStateHandle.get<String>(KEY_GOAL_ID)))

        private val _state = MutableStateFlow<GoalDetailUiState>(GoalDetailUiState.Loading)
        val state: StateFlow<GoalDetailUiState> = _state.asStateFlow()

        init {
            viewModelScope.launch {
                authRepository
                    .observeUserId()
                    .flatMapLatest { userId ->
                        if (userId == null) {
                            flowOf<AppResult<Goal?>>(AppResult.Success(null))
                        } else {
                            getGoalsUseCase.execute(userId).map { it.findGoal() }
                        }
                    }
                    .combine(getMilestonesUseCase.execute(goalId)) { goalResult, milestonesResult ->
                        goalResult to milestonesResult
                    }
                    .collect { (goalResult, milestonesResult) -> applyLoaded(goalResult, milestonesResult) }
            }
        }

        private fun AppResult<List<Goal>>.findGoal(): AppResult<Goal?> =
            when (this) {
                is AppResult.Success -> AppResult.Success(value.find { it.id == goalId })
                is AppResult.Failure -> this
            }

        private fun applyLoaded(
            goalResult: AppResult<Goal?>,
            milestonesResult: AppResult<List<Milestone>>,
        ) {
            if (goalResult is AppResult.Failure) {
                _state.value = GoalDetailUiState.Error(goalResult.error.message)
                return
            }
            if (milestonesResult is AppResult.Failure) {
                _state.value = GoalDetailUiState.Error(milestonesResult.error.message)
                return
            }
            val goal = (goalResult as AppResult.Success).value
            if (goal == null) {
                _state.value = GoalDetailUiState.NotFound
                return
            }
            val milestones = (milestonesResult as AppResult.Success).value.sortedBy { it.sortOrder }
            val current = _state.value
            _state.value =
                if (current is GoalDetailUiState.Content) {
                    current.copy(goal = goal, milestones = milestones)
                } else {
                    GoalDetailUiState.Content(goal = goal, milestones = milestones)
                }
        }

        fun pause() = updateStatus(LifecycleStatus.PAUSED)

        fun resume() = updateStatus(LifecycleStatus.ACTIVE)

        fun complete() = updateStatus(LifecycleStatus.COMPLETED)

        fun archive() = updateStatus(LifecycleStatus.ARCHIVED)

        fun reopen() = updateStatus(LifecycleStatus.ACTIVE)

        private fun updateStatus(status: LifecycleStatus) {
            viewModelScope.launch {
                val userId = authRepository.observeUserId().first() ?: return@launch
                updateGoalStatusUseCase.execute(userId, goalId, status)
            }
        }

        fun onAddMilestoneClicked() {
            updateContent {
                it.copy(
                    milestoneDialogVisible = true,
                    milestoneTitle = "",
                    milestoneWeight = "",
                    milestoneIsBinary = true,
                    milestoneTitleError = false,
                    milestoneWeightError = false,
                    milestoneError = null,
                )
            }
        }

        fun onMilestoneDialogDismissed() {
            updateContent { it.copy(milestoneDialogVisible = false) }
        }

        fun onMilestoneTitleChanged(value: String) {
            updateContent { it.copy(milestoneTitle = value, milestoneTitleError = false, milestoneError = null) }
        }

        fun onMilestoneWeightChanged(value: String) {
            updateContent { it.copy(milestoneWeight = value, milestoneWeightError = false, milestoneError = null) }
        }

        fun onMilestoneIsBinaryChanged(isBinary: Boolean) {
            updateContent { it.copy(milestoneIsBinary = isBinary) }
        }

        fun saveMilestone() {
            val content = _state.value as? GoalDetailUiState.Content ?: return
            if (content.milestoneSaving) return
            val title = content.milestoneTitle.trim()
            val weight = content.milestoneWeight.trim().toDoubleOrNull()
            val titleInvalid = title.isBlank()
            val weightInvalid = weight == null || !weight.isFinite() || weight <= 0.0
            if (titleInvalid || weightInvalid) {
                updateContent { it.copy(milestoneTitleError = titleInvalid, milestoneWeightError = weightInvalid) }
                return
            }
            updateContent { it.copy(milestoneSaving = true, milestoneError = null) }
            viewModelScope.launch {
                val userId = authRepository.observeUserId().first()
                if (userId == null) {
                    updateContent { it.copy(milestoneSaving = false) }
                    return@launch
                }
                val command =
                    AddMilestoneCommand(
                        userId = userId,
                        goalId = goalId,
                        title = title,
                        sortOrder = content.milestones.size,
                        weight = checkNotNull(weight),
                        isBinary = content.milestoneIsBinary,
                    )
                when (val result = addMilestoneUseCase.execute(command)) {
                    is AppResult.Success -> {
                        updateContent { it.copy(milestoneSaving = false, milestoneDialogVisible = false) }
                    }
                    is AppResult.Failure -> {
                        updateContent { it.copy(milestoneSaving = false, milestoneError = result.error.message) }
                    }
                }
            }
        }

        fun toggleMilestone(milestone: Milestone) {
            if (!milestone.isBinary) return
            viewModelScope.launch {
                val userId = authRepository.observeUserId().first() ?: return@launch
                val command =
                    EditMilestoneCommand(
                        userId = userId,
                        goalId = goalId,
                        milestoneId = milestone.id,
                        completed = !milestone.completed,
                    )
                reportMilestoneFailure(editMilestoneUseCase.execute(command))
            }
        }

        fun updateMilestoneProgress(
            milestone: Milestone,
            progressPercent: Double,
        ) {
            if (milestone.isBinary) return
            viewModelScope.launch {
                val userId = authRepository.observeUserId().first() ?: return@launch
                val command =
                    EditMilestoneCommand(
                        userId = userId,
                        goalId = goalId,
                        milestoneId = milestone.id,
                        progressPercent = progressPercent,
                    )
                reportMilestoneFailure(editMilestoneUseCase.execute(command))
            }
        }

        private fun reportMilestoneFailure(result: AppResult<Milestone>) {
            if (result is AppResult.Failure) {
                updateContent { it.copy(milestoneError = result.error.message) }
            }
        }

        private fun updateContent(transform: (GoalDetailUiState.Content) -> GoalDetailUiState.Content) {
            val current = _state.value
            if (current is GoalDetailUiState.Content) {
                _state.value = transform(current)
            }
        }
    }
