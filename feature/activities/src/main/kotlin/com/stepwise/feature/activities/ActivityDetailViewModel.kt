package com.stepwise.feature.activities

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stepwise.core.common.AppResult
import com.stepwise.core.common.Clock
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
import com.stepwise.core.model.SessionId
import com.stepwise.core.model.UserId
import com.stepwise.domain.repository.AuthRepository
import com.stepwise.domain.usecase.CompleteOccurrenceCommand
import com.stepwise.domain.usecase.CompleteOccurrenceUseCase
import com.stepwise.domain.usecase.CompletionTarget
import com.stepwise.domain.usecase.DeleteSessionUseCase
import com.stepwise.domain.usecase.EditActivityCommand
import com.stepwise.domain.usecase.EditActivityUseCase
import com.stepwise.domain.usecase.EditSessionCommand
import com.stepwise.domain.usecase.EditSessionUseCase
import com.stepwise.domain.usecase.GetGoalLinksForSourceUseCase
import com.stepwise.domain.usecase.GetGoalsUseCase
import com.stepwise.domain.usecase.GetMetricsUseCase
import com.stepwise.domain.usecase.GetSessionHistoryUseCase
import com.stepwise.domain.usecase.GetSessionMetricValuesUseCase
import com.stepwise.domain.usecase.LinkActivityToGoalCommand
import com.stepwise.domain.usecase.LinkActivityToGoalUseCase
import com.stepwise.domain.usecase.ObserveActivityUseCase
import com.stepwise.domain.usecase.UnlinkActivityFromGoalUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import javax.inject.Inject

private const val KEY_ACTIVITY_ID = "activityId"

/**
 * Activity Detail (`/ROADMAP.md` DEV-018) - metadata, archive toggle, linked Goals
 * ([GetGoalLinksForSourceUseCase]), Session history ([GetSessionHistoryUseCase]), and the
 * dialogs: link-to-goal, unlink-confirm, manual entry, edit-session, delete-session-confirm.
 * Every write this screen makes goes through a canonical Use Case - manual entry and Session
 * correction both only ever call [CompleteOccurrenceUseCase]/[EditSessionUseCase]
 * (`/ROADMAP.md`'s "только через canonical use cases" rule) - the exact same engines Today/Plan
 * already use, so a manually-logged or later-corrected Session can never disagree with one
 * logged any other way.
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
        private val getSessionMetricValuesUseCase: GetSessionMetricValuesUseCase,
        private val editActivityUseCase: EditActivityUseCase,
        private val linkActivityToGoalUseCase: LinkActivityToGoalUseCase,
        private val unlinkActivityFromGoalUseCase: UnlinkActivityFromGoalUseCase,
        private val completeOccurrenceUseCase: CompleteOccurrenceUseCase,
        private val editSessionUseCase: EditSessionUseCase,
        private val deleteSessionUseCase: DeleteSessionUseCase,
        private val clock: Clock,
    ) : ViewModel() {
        private val activityId: ActivityId = ActivityId(checkNotNull(savedStateHandle.get<String>(KEY_ACTIVITY_ID)))

        private val _state = MutableStateFlow<ActivityDetailUiState>(ActivityDetailUiState.Loading)
        val state: StateFlow<ActivityDetailUiState> = _state.asStateFlow()

        private val _events = MutableSharedFlow<DetailActionEvent>(extraBufferCapacity = 1)
        val events: SharedFlow<DetailActionEvent> = _events.asSharedFlow()

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
                ActivityDetailLoaded(activity, links, goals, metrics, sessions)
            }

        private fun applyLoaded(loaded: ActivityDetailLoaded?) {
            _state.value =
                if (loaded == null) ActivityDetailUiState.NotFound else activityDetailStateFrom(_state.value, loaded)
        }

        private fun today() = clock.todayIn(TimeZone.currentSystemDefault())

        fun toggleArchived() {
            val content = _state.value as? ActivityDetailUiState.Content ?: return
            viewModelScope.launch {
                val userId = currentUserId ?: return@launch
                val command =
                    EditActivityCommand(userId = userId, activityId = activityId, archived = !content.activity.archived)
                if (editActivityUseCase.execute(command) is AppResult.Failure) {
                    _events.emit(DetailActionEvent.ActionFailed)
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

        /** Opens a confirmation, rather than unlinking immediately on tap (DEV-018 review, HIGH). */
        fun onUnlinkClicked(linkId: GoalActivityLinkId) {
            updateContent { it.copy(unlinkConfirmLinkId = linkId) }
        }

        fun onUnlinkDialogDismissed() {
            updateContent { it.copy(unlinkConfirmLinkId = null) }
        }

        fun onConfirmUnlink() {
            val content = _state.value as? ActivityDetailUiState.Content ?: return
            val linkId = content.unlinkConfirmLinkId ?: return
            updateContent { it.copy(unlinkConfirmLinkId = null) }
            viewModelScope.launch {
                val userId = currentUserId ?: return@launch
                if (unlinkActivityFromGoalUseCase.execute(userId, linkId) is AppResult.Failure) {
                    _events.emit(DetailActionEvent.ActionFailed)
                }
            }
        }

        fun onLogSessionClicked() {
            val content = _state.value as? ActivityDetailUiState.Content ?: return
            val metricInputs =
                content.goalLinks.map { it.metricId }.distinct().map { metricId ->
                    val metric = content.metrics.firstOrNull { it.id == metricId }
                    val label = metric?.displayLabel() ?: metricId.value
                    MetricInputState(metricId = metricId, label = label)
                }
            updateContent {
                it.copy(
                    manualEntryDialogVisible = true,
                    manualEntryDialog = ManualEntryDialogState(date = today().toString(), metricInputs = metricInputs),
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

        fun onEditSessionClicked(sessionId: SessionId) {
            updateContent { it.copy(editSessionDialog = EditSessionDialogState(sessionId = sessionId)) }
            viewModelScope.launch {
                val userId = currentUserId ?: return@launch
                val content = _state.value as? ActivityDetailUiState.Content ?: return@launch
                when (val result = getSessionMetricValuesUseCase.execute(userId, sessionId)) {
                    is AppResult.Success -> {
                        val inputs =
                            result.value.map { metricValue ->
                                val metric = content.metrics.firstOrNull { it.id == metricValue.metricId }
                                MetricInputState(
                                    metricId = metricValue.metricId,
                                    label = metric?.displayLabel() ?: metricValue.metricId.value,
                                    plannedValue = metricValue.plannedValue,
                                    value = metricValue.actualValue.toString(),
                                )
                            }
                        updateEditSession { it.copy(metricInputs = inputs, isLoading = false) }
                    }
                    is AppResult.Failure -> updateEditSession { it.copy(isLoading = false, hasError = true) }
                }
            }
        }

        fun onEditSessionDialogDismissed() {
            updateContent { it.copy(editSessionDialog = null) }
        }

        fun onEditSessionMetricValueChanged(
            metricId: MetricId,
            value: String,
        ) {
            updateEditSession { dialog ->
                dialog.copy(
                    metricInputs =
                        dialog.metricInputs.map { input ->
                            if (input.metricId == metricId) input.copy(value = value, error = false) else input
                        },
                    hasError = false,
                )
            }
        }

        fun onConfirmEditSession() {
            val dialog = (_state.value as? ActivityDetailUiState.Content)?.editSessionDialog ?: return
            val result = validateMetricInputs(dialog.metricInputs)
            if (result.invalidMetricIds.isNotEmpty()) {
                updateEditSession { dialogState ->
                    dialogState.copy(
                        metricInputs =
                            dialogState.metricInputs.map { input ->
                                input.copy(error = input.metricId in result.invalidMetricIds)
                            },
                    )
                }
                return
            }
            updateEditSession { it.copy(isSubmitting = true, hasError = false) }
            viewModelScope.launch {
                val userId = currentUserId ?: return@launch
                val command =
                    EditSessionCommand(
                        userId = userId,
                        sessionId = dialog.sessionId,
                        metricValues = result.metricValues,
                    )
                when (editSessionUseCase.execute(command)) {
                    is AppResult.Success -> updateContent { it.copy(editSessionDialog = null) }
                    is AppResult.Failure -> updateEditSession { it.copy(isSubmitting = false, hasError = true) }
                }
            }
        }

        fun onDeleteSessionClicked(sessionId: SessionId) {
            updateContent { it.copy(deleteConfirmSessionId = sessionId) }
        }

        fun onDeleteSessionDialogDismissed() {
            updateContent { it.copy(deleteConfirmSessionId = null) }
        }

        fun onConfirmDeleteSession() {
            val content = _state.value as? ActivityDetailUiState.Content ?: return
            val sessionId = content.deleteConfirmSessionId ?: return
            updateContent { it.copy(deleteConfirmSessionId = null) }
            viewModelScope.launch {
                val userId = currentUserId ?: return@launch
                if (deleteSessionUseCase.execute(userId, sessionId) is AppResult.Failure) {
                    _events.emit(DetailActionEvent.ActionFailed)
                }
            }
        }

        private fun updateManualEntry(transform: (ManualEntryDialogState) -> ManualEntryDialogState) {
            updateContent { content ->
                val dialog = content.manualEntryDialog ?: return@updateContent content
                content.copy(manualEntryDialog = transform(dialog))
            }
        }

        private fun updateEditSession(transform: (EditSessionDialogState) -> EditSessionDialogState) {
            updateContent { content ->
                val dialog = content.editSessionDialog ?: return@updateContent content
                content.copy(editSessionDialog = transform(dialog))
            }
        }

        private fun updateContent(transform: (ActivityDetailUiState.Content) -> ActivityDetailUiState.Content) {
            val current = _state.value
            if (current is ActivityDetailUiState.Content) {
                _state.value = transform(current)
            }
        }
    }

/**
 * Bundles [ActivityDetailViewModel]'s own `combine()` of five independent reactive sources into
 * one value, so [activityDetailStateFrom] takes `(current, loaded)` rather than six separate
 * parameters - DEV-018 review, architecture reviewer, MEDIUM: the first draft suppressed
 * detekt's `LongParameterList` on the mapper instead of bundling, even though every one of these
 * five results was already available as one object (the ViewModel's own private `Loaded` class)
 * at every call site; promoting that bundle out of `private` removes the suppression entirely
 * rather than justifying it. Defined here, not in `ActivityDetailStateMapper.kt`, so that file
 * keeps exactly one top-level class-like declaration and detekt's `MatchingDeclarationName`
 * doesn't force it to be renamed away from its own established naming convention.
 */
internal data class ActivityDetailLoaded(
    val activity: AppResult<Activity?>,
    val links: AppResult<List<GoalActivityLink>>,
    val goals: AppResult<List<Goal>>,
    val metrics: AppResult<List<Metric>>,
    val sessions: AppResult<List<Session>>,
)
