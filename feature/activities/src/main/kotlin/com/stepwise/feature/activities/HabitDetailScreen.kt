@file:OptIn(ExperimentalMaterial3Api::class)

package com.stepwise.feature.activities

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stepwise.core.designsystem.component.StepwiseErrorState
import com.stepwise.core.designsystem.component.StepwiseLoadingState
import com.stepwise.core.designsystem.component.StepwiseOutlinedButton
import com.stepwise.core.designsystem.component.StepwiseTopBar
import com.stepwise.core.designsystem.theme.StepwiseSpacing
import com.stepwise.core.model.ExecutionStatus
import com.stepwise.core.model.GoalActivityLinkId
import com.stepwise.core.model.GoalId
import com.stepwise.core.model.MetricId
import com.stepwise.core.model.SessionId

@Composable
fun HabitDetailRoute(
    onBack: () -> Unit,
    onEditClicked: () -> Unit,
    viewModel: HabitDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val actionFailedMessage = stringResource(R.string.activity_detail_action_error)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is DetailActionEvent.ActionFailed -> snackbarHostState.showSnackbar(actionFailedMessage)
            }
        }
    }

    Scaffold(
        topBar = {
            StepwiseTopBar(
                title = (state as? HabitDetailUiState.Content)?.habit?.title ?: stringResource(R.string.habits_title),
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                navigationIconContentDescription = stringResource(R.string.activity_form_back_content_description),
                onNavigationClick = onBack,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        HabitDetailContent(
            modifier = Modifier.padding(padding),
            state = state,
            onEditClicked = onEditClicked,
            onToggleArchived = viewModel::toggleArchived,
            onLinkGoalClicked = viewModel::onLinkGoalClicked,
            onLinkGoalDialogDismissed = viewModel::onLinkGoalDialogDismissed,
            onLinkGoalSelected = viewModel::onLinkGoalSelected,
            onLinkMetricSelected = viewModel::onLinkMetricSelected,
            onConfirmLinkGoal = viewModel::onConfirmLinkGoal,
            onUnlinkClicked = viewModel::onUnlinkClicked,
            onUnlinkDialogDismissed = viewModel::onUnlinkDialogDismissed,
            onConfirmUnlink = viewModel::onConfirmUnlink,
            onLogSessionClicked = viewModel::onLogSessionClicked,
            onManualEntryDialogDismissed = viewModel::onManualEntryDialogDismissed,
            onManualEntryDateChanged = viewModel::onManualEntryDateChanged,
            onManualEntryStatusSelected = viewModel::onManualEntryStatusSelected,
            onManualEntryMetricValueChanged = viewModel::onManualEntryMetricValueChanged,
            onConfirmManualEntry = viewModel::onConfirmManualEntry,
            onEditSessionClicked = viewModel::onEditSessionClicked,
            onEditSessionDialogDismissed = viewModel::onEditSessionDialogDismissed,
            onEditSessionMetricValueChanged = viewModel::onEditSessionMetricValueChanged,
            onConfirmEditSession = viewModel::onConfirmEditSession,
            onDeleteSessionClicked = viewModel::onDeleteSessionClicked,
            onDeleteSessionDialogDismissed = viewModel::onDeleteSessionDialogDismissed,
            onConfirmDeleteSession = viewModel::onConfirmDeleteSession,
        )
    }
}

@Suppress("LongParameterList") // One callback per lifecycle action/dialog field - this screen's genuine surface.
@Composable
private fun HabitDetailContent(
    state: HabitDetailUiState,
    onEditClicked: () -> Unit,
    onToggleArchived: () -> Unit,
    onLinkGoalClicked: () -> Unit,
    onLinkGoalDialogDismissed: () -> Unit,
    onLinkGoalSelected: (GoalId) -> Unit,
    onLinkMetricSelected: (MetricId) -> Unit,
    onConfirmLinkGoal: () -> Unit,
    onUnlinkClicked: (GoalActivityLinkId) -> Unit,
    onUnlinkDialogDismissed: () -> Unit,
    onConfirmUnlink: () -> Unit,
    onLogSessionClicked: () -> Unit,
    onManualEntryDialogDismissed: () -> Unit,
    onManualEntryDateChanged: (String) -> Unit,
    onManualEntryStatusSelected: (ExecutionStatus) -> Unit,
    onManualEntryMetricValueChanged: (MetricId, String) -> Unit,
    onConfirmManualEntry: () -> Unit,
    onEditSessionClicked: (SessionId) -> Unit,
    onEditSessionDialogDismissed: () -> Unit,
    onEditSessionMetricValueChanged: (MetricId, String) -> Unit,
    onConfirmEditSession: () -> Unit,
    onDeleteSessionClicked: (SessionId) -> Unit,
    onDeleteSessionDialogDismissed: () -> Unit,
    onConfirmDeleteSession: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        is HabitDetailUiState.Loading -> StepwiseLoadingState(modifier = modifier.fillMaxSize())
        is HabitDetailUiState.NotFound, is HabitDetailUiState.Error -> {
            StepwiseErrorState(
                message = stringResource(R.string.habit_detail_error),
                modifier = modifier.fillMaxSize(),
            )
        }
        is HabitDetailUiState.Content -> {
            val contentModifier =
                modifier.fillMaxSize().padding(StepwiseSpacing.medium).verticalScroll(rememberScrollState())
            Column(modifier = contentModifier) {
                val metric = state.metrics.firstOrNull { it.id == state.habit.metricId }
                val unit = metric?.canonicalUnit ?: state.habit.metricId.value
                Text(
                    text = stringResource(R.string.habit_detail_target_label, state.habit.targetValue.toString(), unit),
                    style = MaterialTheme.typography.bodyMedium,
                )
                val archiveRes =
                    if (state.habit.archived) {
                        R.string.activity_detail_unarchive
                    } else {
                        R.string.activity_detail_archive
                    }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(StepwiseSpacing.small),
                    modifier = Modifier.padding(top = StepwiseSpacing.small),
                ) {
                    StepwiseOutlinedButton(
                        text = stringResource(R.string.activity_detail_edit),
                        onClick = onEditClicked,
                    )
                    StepwiseOutlinedButton(text = stringResource(archiveRes), onClick = onToggleArchived)
                }
                GoalLinksSection(
                    links = state.goalLinks,
                    goals = state.goals,
                    metrics = state.metrics,
                    onLinkGoalClicked = onLinkGoalClicked,
                    onUnlinkClicked = onUnlinkClicked,
                )
                SessionHistorySection(
                    sessions = state.sessions,
                    onLogSessionClicked = onLogSessionClicked,
                    onEditSessionClicked = onEditSessionClicked,
                    onDeleteSessionClicked = onDeleteSessionClicked,
                )
            }
            HabitDetailDialogs(
                state = state,
                onLinkGoalSelected = onLinkGoalSelected,
                onLinkMetricSelected = onLinkMetricSelected,
                onConfirmLinkGoal = onConfirmLinkGoal,
                onLinkGoalDialogDismissed = onLinkGoalDialogDismissed,
                onManualEntryDateChanged = onManualEntryDateChanged,
                onManualEntryStatusSelected = onManualEntryStatusSelected,
                onManualEntryMetricValueChanged = onManualEntryMetricValueChanged,
                onConfirmManualEntry = onConfirmManualEntry,
                onManualEntryDialogDismissed = onManualEntryDialogDismissed,
                onConfirmUnlink = onConfirmUnlink,
                onUnlinkDialogDismissed = onUnlinkDialogDismissed,
                onEditSessionMetricValueChanged = onEditSessionMetricValueChanged,
                onConfirmEditSession = onConfirmEditSession,
                onEditSessionDialogDismissed = onEditSessionDialogDismissed,
                onConfirmDeleteSession = onConfirmDeleteSession,
                onDeleteSessionDialogDismissed = onDeleteSessionDialogDismissed,
            )
        }
    }
}

@Suppress("LongParameterList") // One callback per dialog field/action - mirrors HabitDetailContent's own surface.
@Composable
private fun HabitDetailDialogs(
    state: HabitDetailUiState.Content,
    onLinkGoalSelected: (GoalId) -> Unit,
    onLinkMetricSelected: (MetricId) -> Unit,
    onConfirmLinkGoal: () -> Unit,
    onLinkGoalDialogDismissed: () -> Unit,
    onManualEntryDateChanged: (String) -> Unit,
    onManualEntryStatusSelected: (ExecutionStatus) -> Unit,
    onManualEntryMetricValueChanged: (MetricId, String) -> Unit,
    onConfirmManualEntry: () -> Unit,
    onManualEntryDialogDismissed: () -> Unit,
    onConfirmUnlink: () -> Unit,
    onUnlinkDialogDismissed: () -> Unit,
    onEditSessionMetricValueChanged: (MetricId, String) -> Unit,
    onConfirmEditSession: () -> Unit,
    onEditSessionDialogDismissed: () -> Unit,
    onConfirmDeleteSession: () -> Unit,
    onDeleteSessionDialogDismissed: () -> Unit,
) {
    if (state.linkGoalDialogVisible) {
        LinkGoalDialog(
            state = state.linkGoalDialog,
            goals = state.goals,
            metrics = state.metrics,
            onGoalSelected = onLinkGoalSelected,
            onMetricSelected = onLinkMetricSelected,
            onConfirm = onConfirmLinkGoal,
            onDismiss = onLinkGoalDialogDismissed,
        )
    }
    val manualEntry = state.manualEntryDialog
    if (state.manualEntryDialogVisible && manualEntry != null) {
        ManualEntryDialog(
            state = manualEntry,
            onDateChanged = onManualEntryDateChanged,
            onStatusSelected = onManualEntryStatusSelected,
            onMetricValueChanged = onManualEntryMetricValueChanged,
            onConfirm = onConfirmManualEntry,
            onDismiss = onManualEntryDialogDismissed,
        )
    }
    if (state.unlinkConfirmLinkId != null) {
        ConfirmActionDialog(
            title = stringResource(R.string.unlink_confirm_title),
            message = stringResource(R.string.unlink_confirm_message),
            confirmLabel = stringResource(R.string.detail_unlink_action),
            onConfirm = onConfirmUnlink,
            onDismiss = onUnlinkDialogDismissed,
        )
    }
    val editSession = state.editSessionDialog
    if (editSession != null) {
        EditSessionDialog(
            state = editSession,
            onMetricValueChanged = onEditSessionMetricValueChanged,
            onConfirm = onConfirmEditSession,
            onDismiss = onEditSessionDialogDismissed,
        )
    }
    if (state.deleteConfirmSessionId != null) {
        ConfirmActionDialog(
            title = stringResource(R.string.delete_session_confirm_title),
            message = stringResource(R.string.delete_session_confirm_message),
            confirmLabel = stringResource(R.string.session_delete_action),
            onConfirm = onConfirmDeleteSession,
            onDismiss = onDeleteSessionDialogDismissed,
        )
    }
}
