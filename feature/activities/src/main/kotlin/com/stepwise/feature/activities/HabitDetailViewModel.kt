package com.stepwise.feature.activities

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stepwise.core.common.AppResult
import com.stepwise.core.model.ExecutableKind
import com.stepwise.core.model.ExecutionStatus
import com.stepwise.core.model.Goal
import com.stepwise.core.model.GoalActivityLink
import com.stepwise.core.model.GoalActivityLinkId
import com.stepwise.core.model.GoalId
import com.stepwise.core.model.Habit
import com.stepwise.core.model.HabitId
import com.stepwise.core.model.Metric
import com.stepwise.core.model.MetricId
import com.stepwise.core.model.ProgressSourceKind
import com.stepwise.core.model.Session
import com.stepwise.core.model.UserId
import com.stepwise.domain.repository.AuthRepository
import com.stepwise.domain.usecase.CompleteOccurrenceCommand
import com.stepwise.domain.usecase.CompleteOccurrenceUseCase
import com.stepwise.domain.usecase.CompletionTarget
import com.stepwise.domain.usecase.EditHabitCommand
import com.stepwise.domain.usecase.EditHabitUseCase
import com.stepwise.domain.usecase.GetGoalLinksForSourceUseCase
import com.stepwise.domain.usecase.GetGoalsUseCase
import com.stepwise.domain.usecase.GetMetricsUseCase
import com.stepwise.domain.usecase.GetSessionHistoryUseCase
import com.stepwise.domain.usecase.LinkActivityToGoalCommand
import com.stepwise.domain.usecase.LinkActivityToGoalUseCase
import com.stepwise.domain.usecase.ObserveHabitUseCase
import com.stepwise.domain.usecase.UnlinkActivityFromGoalUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val KEY_HABIT_ID = "habitId"

/** Habit Detail (`/ROADMAP.md` DEV-018) - mirrors [ActivityDetailViewModel] exactly, for [Habit]'s own field set. */
@Suppress("TooManyFunctions") // One handler per dialog field/lifecycle action - HabitDetailScreen's genuine surface.
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HabitDetailViewModel
    @Inject
    @Suppress("LongParameterList") // One repository/Use Case per thing this screen loads, acts on, or saves to.
    constructor(
        savedStateHandle: SavedStateHandle,
        private val authRepository: AuthRepository,
        private val observeHabitUseCase: ObserveHabitUseCase,
        private val getGoalLinksForSourceUseCase: GetGoalLinksForSourceUseCase,
        private val getGoalsUseCase: GetGoalsUseCase,
        private val getMetricsUseCase: GetMetricsUseCase,
        private val getSessionHistoryUseCase: GetSessionHistoryUseCase,
        private val editHabitUseCase: EditHabitUseCase,
        private val linkActivityToGoalUseCase: LinkActivityToGoalUseCase,
        private val unlinkActivityFromGoalUseCase: UnlinkActivityFromGoalUseCase,
        private val completeOccurrenceUseCase: CompleteOccurrenceUseCase,
    ) : ViewModel() {
        private val habitId: HabitId = HabitId(checkNotNull(savedStateHandle.get<String>(KEY_HABIT_ID)))

        private val _state = MutableStateFlow<HabitDetailUiState>(HabitDetailUiState.Loading)
        val state: StateFlow<HabitDetailUiState> = _state.asStateFlow()

        private var currentUserId: UserId? = null

        init {
            viewModelScope.launch {
                authRepository
                    .observeUserId()
                    .flatMapLatest { userId ->
                        currentUserId = userId
                        if (userId == null) flowOf(null) else loadedFlow(userId)
                    }.collect { loaded -> applyLoaded(loaded) }
            }
        }

        private fun loadedFlow(userId: UserId) =
            combine(
                observeHabitUseCase.execute(userId, habitId),
                getGoalLinksForSourceUseCase.execute(userId, ProgressSourceKind.HABIT, habitId.value),
                getGoalsUseCase.execute(userId),
                getMetricsUseCase.execute(),
                getSessionHistoryUseCase.execute(userId, ExecutableKind.HABIT, habitId.value),
            ) { habit, links, goals, metrics, sessions ->
                Loaded(habit, links, goals, metrics, sessions)
            }

        private fun applyLoaded(loaded: Loaded?) {
            _state.value =
                if (loaded == null) {
                    HabitDetailUiState.NotFound
                } else {
                    habitDetailStateFrom(
                        _state.value,
                        loaded.habit,
                        loaded.links,
                        loaded.goals,
                        loaded.metrics,
                        loaded.sessions,
                    )
                }
        }

        fun toggleArchived() {
            val content = _state.value as? HabitDetailUiState.Content ?: return
            updateContent { it.copy(actionFailed = false) }
            viewModelScope.launch {
                val userId = currentUserId ?: return@launch
                val command = EditHabitCommand(userId = userId, habitId = habitId, archived = !content.habit.archived)
                if (editHabitUseCase.execute(command) is AppResult.Failure) {
                    updateContent { it.copy(actionFailed = true) }
                }
            }
        }

        fun onLinkGoalClicked() {
            updateContent { it.copy(linkGoalDialogVisible = true, linkGoalDialog = LinkGoalDialogState()) }
        }

        fun onLinkGoalDialogDismissed() {
            updateContent { it.copy(linkGoalDialogVisible = false) }
        }

        fun onLinkGoalSelected(goalId: GoalId) {
            updateContent {
                it.copy(linkGoalDialog = it.linkGoalDialog.copy(selectedGoalId = goalId, hasError = false))
            }
        }

        fun onLinkMetricSelected(metricId: MetricId) {
            updateContent {
                it.copy(linkGoalDialog = it.linkGoalDialog.copy(selectedMetricId = metricId, hasError = false))
            }
        }

        fun onConfirmLinkGoal() {
            val content = _state.value as? HabitDetailUiState.Content ?: return
            val goalId = content.linkGoalDialog.selectedGoalId
            val metricId = content.linkGoalDialog.selectedMetricId
            if (goalId == null || metricId == null) return
            updateContent { it.copy(linkGoalDialog = it.linkGoalDialog.copy(isSubmitting = true, hasError = false)) }
            viewModelScope.launch {
                val userId = currentUserId ?: return@launch
                val command =
                    LinkActivityToGoalCommand(
                        userId = userId,
                        sourceType = ProgressSourceKind.HABIT,
                        sourceId = habitId.value,
                        goalId = goalId,
                        metricId = metricId,
                    )
                when (linkActivityToGoalUseCase.execute(command)) {
                    is AppResult.Success -> updateContent { it.copy(linkGoalDialogVisible = false) }
                    is AppResult.Failure ->
                        updateContent {
                            it.copy(linkGoalDialog = it.linkGoalDialog.copy(isSubmitting = false, hasError = true))
                        }
                }
            }
        }

        fun onUnlinkClicked(linkId: GoalActivityLinkId) {
            updateContent { it.copy(actionFailed = false) }
            viewModelScope.launch {
                val userId = currentUserId ?: return@launch
                if (unlinkActivityFromGoalUseCase.execute(userId, linkId) is AppResult.Failure) {
                    updateContent { it.copy(actionFailed = true) }
                }
            }
        }

        fun onLogSessionClicked() {
            val content = _state.value as? HabitDetailUiState.Content ?: return
            val metric = content.metrics.firstOrNull { it.id == content.habit.metricId }
            val label = metric?.let { "${it.key} (${it.canonicalUnit})" } ?: content.habit.metricId.value
            val metricInputs = listOf(MetricInputState(metricId = content.habit.metricId, label = label))
            updateContent {
                it.copy(
                    manualEntryDialogVisible = true,
                    manualEntryDialog = ManualEntryDialogState(date = "", metricInputs = metricInputs),
                )
            }
        }

        fun onManualEntryDialogDismissed() {
            updateContent { it.copy(manualEntryDialogVisible = false) }
        }

        fun onManualEntryDateChanged(value: String) {
            updateManualEntry { it.copy(date = value, hasError = false) }
        }

        fun onManualEntryStatusSelected(status: ExecutionStatus) {
            updateManualEntry { it.copy(status = status, hasError = false) }
        }

        fun onManualEntryMetricValueChanged(
            metricId: MetricId,
            value: String,
        ) {
            updateManualEntry { dialog ->
                dialog.copy(
                    metricInputs =
                        dialog.metricInputs.map { input ->
                            if (input.metricId == metricId) input.copy(value = value, error = false) else input
                        },
                    hasError = false,
                )
            }
        }

        fun onConfirmManualEntry() {
            val content = _state.value as? HabitDetailUiState.Content
            val dialog = content?.manualEntryDialog
            if (content == null || dialog == null) return
            val result = validateManualEntry(dialog)
            val validated = result.validated
            if (validated == null) {
                updateManualEntry {
                    it.copy(
                        hasError = result.dateInvalid,
                        metricInputs =
                            it.metricInputs.map { input ->
                                input.copy(error = input.metricId in result.invalidMetricIds)
                            },
                    )
                }
                return
            }
            updateManualEntry { it.copy(isSubmitting = true, hasError = false) }
            viewModelScope.launch {
                val userId = currentUserId ?: return@launch
                val command =
                    CompleteOccurrenceCommand(
                        userId = userId,
                        target = CompletionTarget.OneOff(ExecutableKind.HABIT, habitId.value, validated.date),
                        status = dialog.status,
                        metricValues = validated.metricValues,
                    )
                when (completeOccurrenceUseCase.execute(command)) {
                    is AppResult.Success -> updateContent { it.copy(manualEntryDialogVisible = false) }
                    is AppResult.Failure -> updateManualEntry { it.copy(isSubmitting = false, hasError = true) }
                }
            }
        }

        private fun updateManualEntry(transform: (ManualEntryDialogState) -> ManualEntryDialogState) {
            updateContent { content ->
                val dialog = content.manualEntryDialog ?: return@updateContent content
                content.copy(manualEntryDialog = transform(dialog))
            }
        }

        private fun updateContent(transform: (HabitDetailUiState.Content) -> HabitDetailUiState.Content) {
            val current = _state.value
            if (current is HabitDetailUiState.Content) {
                _state.value = transform(current)
            }
        }

        private data class Loaded(
            val habit: AppResult<Habit?>,
            val links: AppResult<List<GoalActivityLink>>,
            val goals: AppResult<List<Goal>>,
            val metrics: AppResult<List<Metric>>,
            val sessions: AppResult<List<Session>>,
        )
    }
