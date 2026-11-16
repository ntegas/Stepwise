package com.stepwise.feature.activities

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stepwise.core.common.AppResult
import com.stepwise.core.model.Activity
import com.stepwise.core.model.ActivityId
import com.stepwise.core.model.ExecutableKind
import com.stepwise.core.model.ExecutionStatus
import com.stepwise.core.model.Goal
import com.stepwise.core.model.GoalActivityLink
import com.stepwise.core.model.GoalActivityLinkId
import com.stepwise.core.model.GoalId
import com.stepwise.core.model.Metric
import com.stepwise.core.model.MetricId
import com.stepwise.core.model.ProgressSourceKind
import com.stepwise.core.model.Session
import com.stepwise.core.model.UserId
import com.stepwise.domain.repository.AuthRepository
import com.stepwise.domain.usecase.CompleteOccurrenceCommand
import com.stepwise.domain.usecase.CompleteOccurrenceUseCase
import com.stepwise.domain.usecase.CompletionTarget
import com.stepwise.domain.usecase.EditActivityCommand
import com.stepwise.domain.usecase.EditActivityUseCase
import com.stepwise.domain.usecase.GetGoalLinksForSourceUseCase
import com.stepwise.domain.usecase.GetGoalsUseCase
import com.stepwise.domain.usecase.GetMetricsUseCase
import com.stepwise.domain.usecase.GetSessionHistoryUseCase
import com.stepwise.domain.usecase.LinkActivityToGoalCommand
import com.stepwise.domain.usecase.LinkActivityToGoalUseCase
import com.stepwise.domain.usecase.ObserveActivityUseCase
import com.stepwise.domain.usecase.UnlinkActivityFromGoalUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val KEY_ACTIVITY_ID = "activityId"

/**
 * Activity Detail (`/ROADMAP.md` DEV-018) - metadata, archive toggle, linked Goals
 * ([GetGoalLinksForSourceUseCase]), Session history ([GetSessionHistoryUseCase]), and the two
 * dialogs (link-to-goal, manual entry). Manual entry is the one write this screen makes besides
 * the canonical Use Cases above, and it too only ever calls [CompleteOccurrenceUseCase]
 * (`/ROADMAP.md`'s "reschedule/complete только через canonical use cases" rule, extended here to
 * "log только через") - the exact same engine Today/Plan already use, so a manually-logged
 * Session can never disagree with one logged any other way.
 */
@Suppress("TooManyFunctions") // One handler per dialog field/lifecycle action - ActivityDetailScreen's genuine surface.
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ActivityDetailViewModel
    @Inject
    @Suppress("LongParameterList") // One repository/Use Case per thing this screen loads, acts on, or saves to.
    constructor(
        savedStateHandle: SavedStateHandle,
        private val authRepository: AuthRepository,
        private val observeActivityUseCase: ObserveActivityUseCase,
        private val getGoalLinksForSourceUseCase: GetGoalLinksForSourceUseCase,
        private val getGoalsUseCase: GetGoalsUseCase,
        private val getMetricsUseCase: GetMetricsUseCase,
        private val getSessionHistoryUseCase: GetSessionHistoryUseCase,
        private val editActivityUseCase: EditActivityUseCase,
        private val linkActivityToGoalUseCase: LinkActivityToGoalUseCase,
        private val unlinkActivityFromGoalUseCase: UnlinkActivityFromGoalUseCase,
        private val completeOccurrenceUseCase: CompleteOccurrenceUseCase,
    ) : ViewModel() {
        private val activityId: ActivityId = ActivityId(checkNotNull(savedStateHandle.get<String>(KEY_ACTIVITY_ID)))

        private val _state = MutableStateFlow<ActivityDetailUiState>(ActivityDetailUiState.Loading)
        val state: StateFlow<ActivityDetailUiState> = _state.asStateFlow()

        private var currentUserId: UserId? = null

        init {
            viewModelScope.launch {
                authRepository
                    .observeUserId()
                    .flatMapLatest { userId ->
                        currentUserId = userId
                        if (userId == null) {
                            flowOf(null)
                        } else {
                            loadedFlow(userId)
                        }
                    }.collect { loaded -> applyLoaded(loaded) }
            }
        }

        private fun loadedFlow(userId: UserId) =
            combine(
                observeActivityUseCase.execute(userId, activityId),
                getGoalLinksForSourceUseCase.execute(userId, ProgressSourceKind.ACTIVITY, activityId.value),
                getGoalsUseCase.execute(userId),
                getMetricsUseCase.execute(),
                getSessionHistoryUseCase.execute(userId, ExecutableKind.ACTIVITY, activityId.value),
            ) { activity, links, goals, metrics, sessions ->
                Loaded(activity, links, goals, metrics, sessions)
            }

        private fun applyLoaded(loaded: Loaded?) {
            _state.value =
                if (loaded == null) {
                    ActivityDetailUiState.NotFound
                } else {
                    activityDetailStateFrom(
                        _state.value,
                        loaded.activity,
                        loaded.links,
                        loaded.goals,
                        loaded.metrics,
                        loaded.sessions,
                    )
                }
        }

        fun toggleArchived() {
            val content = _state.value as? ActivityDetailUiState.Content ?: return
            updateContent { it.copy(actionFailed = false) }
            viewModelScope.launch {
                val userId = currentUserId ?: return@launch
                val command =
                    EditActivityCommand(userId = userId, activityId = activityId, archived = !content.activity.archived)
                if (editActivityUseCase.execute(command) is AppResult.Failure) {
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
            val content = _state.value as? ActivityDetailUiState.Content ?: return
            val goalId = content.linkGoalDialog.selectedGoalId
            val metricId = content.linkGoalDialog.selectedMetricId
            if (goalId == null || metricId == null) return
            updateContent { it.copy(linkGoalDialog = it.linkGoalDialog.copy(isSubmitting = true, hasError = false)) }
            viewModelScope.launch {
                val userId = currentUserId ?: return@launch
                val command =
                    LinkActivityToGoalCommand(
                        userId = userId,
                        sourceType = ProgressSourceKind.ACTIVITY,
                        sourceId = activityId.value,
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
            val content = _state.value as? ActivityDetailUiState.Content ?: return
            val metricInputs =
                content.goalLinks.map { it.metricId }.distinct().map { metricId ->
                    val metric = content.metrics.firstOrNull { it.id == metricId }
                    val label = metric?.let { "${it.key} (${it.canonicalUnit})" } ?: metricId.value
                    MetricInputState(metricId = metricId, label = label)
                }
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
            val content = _state.value as? ActivityDetailUiState.Content
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
                        target = CompletionTarget.OneOff(ExecutableKind.ACTIVITY, activityId.value, validated.date),
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

        private fun updateContent(transform: (ActivityDetailUiState.Content) -> ActivityDetailUiState.Content) {
            val current = _state.value
            if (current is ActivityDetailUiState.Content) {
                _state.value = transform(current)
            }
        }

        private data class Loaded(
            val activity: AppResult<Activity?>,
            val links: AppResult<List<GoalActivityLink>>,
            val goals: AppResult<List<Goal>>,
            val metrics: AppResult<List<Metric>>,
            val sessions: AppResult<List<Session>>,
        )
    }
