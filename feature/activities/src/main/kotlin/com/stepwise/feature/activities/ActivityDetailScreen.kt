package com.stepwise.feature.activities

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

@Composable
fun ActivityDetailRoute(
    onBack: () -> Unit,
    viewModel: ActivityDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ActivityDetailScreen(
        state = state,
        onBack = onBack,
        onToggleArchived = viewModel::toggleArchived,
        onLinkGoalClicked = viewModel::onLinkGoalClicked,
        onLinkGoalDialogDismissed = viewModel::onLinkGoalDialogDismissed,
        onLinkGoalSelected = viewModel::onLinkGoalSelected,
        onLinkMetricSelected = viewModel::onLinkMetricSelected,
        onConfirmLinkGoal = viewModel::onConfirmLinkGoal,
        onUnlinkClicked = viewModel::onUnlinkClicked,
        onLogSessionClicked = viewModel::onLogSessionClicked,
        onManualEntryDialogDismissed = viewModel::onManualEntryDialogDismissed,
        onManualEntryDateChanged = viewModel::onManualEntryDateChanged,
        onManualEntryStatusSelected = viewModel::onManualEntryStatusSelected,
        onManualEntryMetricValueChanged = viewModel::onManualEntryMetricValueChanged,
        onConfirmManualEntry = viewModel::onConfirmManualEntry,
    )
}

@Suppress("LongParameterList") // One callback per lifecycle action/dialog field - this screen's genuine surface.
@Composable
fun ActivityDetailScreen(
    state: ActivityDetailUiState,
    onBack: () -> Unit,
    onToggleArchived: () -> Unit,
    onLinkGoalClicked: () -> Unit,
    onLinkGoalDialogDismissed: () -> Unit,
    onLinkGoalSelected: (GoalId) -> Unit,
    onLinkMetricSelected: (MetricId) -> Unit,
    onConfirmLinkGoal: () -> Unit,
    onUnlinkClicked: (GoalActivityLinkId) -> Unit,
    onLogSessionClicked: () -> Unit,
    onManualEntryDialogDismissed: () -> Unit,
    onManualEntryDateChanged: (String) -> Unit,
    onManualEntryStatusSelected: (ExecutionStatus) -> Unit,
    onManualEntryMetricValueChanged: (MetricId, String) -> Unit,
    onConfirmManualEntry: () -> Unit,
) {
    val activityTitle = (state as? ActivityDetailUiState.Content)?.activity?.title
    Scaffold(
        topBar = {
            StepwiseTopBar(
                title = activityTitle ?: stringResource(R.string.activities_title),
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                navigationIconContentDescription = stringResource(R.string.activity_form_back_content_description),
                onNavigationClick = onBack,
            )
        },
    ) { padding ->
        when (state) {
            is ActivityDetailUiState.Loading -> StepwiseLoadingState(modifier = Modifier.padding(padding).fillMaxSize())
            is ActivityDetailUiState.NotFound, is ActivityDetailUiState.Error -> {
                StepwiseErrorState(
                    message = stringResource(R.string.activity_detail_error),
                    modifier = Modifier.padding(padding).fillMaxSize(),
                )
            }
            is ActivityDetailUiState.Content -> {
                ActivityDetailContent(
                    modifier = Modifier.padding(padding),
                    state = state,
                    onToggleArchived = onToggleArchived,
                    onLinkGoalClicked = onLinkGoalClicked,
                    onLinkGoalDialogDismissed = onLinkGoalDialogDismissed,
                    onLinkGoalSelected = onLinkGoalSelected,
                    onLinkMetricSelected = onLinkMetricSelected,
                    onConfirmLinkGoal = onConfirmLinkGoal,
                    onUnlinkClicked = onUnlinkClicked,
                    onLogSessionClicked = onLogSessionClicked,
                    onManualEntryDialogDismissed = onManualEntryDialogDismissed,
                    onManualEntryDateChanged = onManualEntryDateChanged,
                    onManualEntryStatusSelected = onManualEntryStatusSelected,
                    onManualEntryMetricValueChanged = onManualEntryMetricValueChanged,
                    onConfirmManualEntry = onConfirmManualEntry,
                )
            }
        }
    }
}

@Suppress("LongParameterList") // One callback per lifecycle action/dialog field - this screen's genuine surface.
@Composable
private fun ActivityDetailContent(
    state: ActivityDetailUiState.Content,
    onToggleArchived: () -> Unit,
    onLinkGoalClicked: () -> Unit,
    onLinkGoalDialogDismissed: () -> Unit,
    onLinkGoalSelected: (GoalId) -> Unit,
    onLinkMetricSelected: (MetricId) -> Unit,
    onConfirmLinkGoal: () -> Unit,
    onUnlinkClicked: (GoalActivityLinkId) -> Unit,
    onLogSessionClicked: () -> Unit,
    onManualEntryDialogDismissed: () -> Unit,
    onManualEntryDateChanged: (String) -> Unit,
    onManualEntryStatusSelected: (ExecutionStatus) -> Unit,
    onManualEntryMetricValueChanged: (MetricId, String) -> Unit,
    onConfirmManualEntry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentModifier =
        modifier.fillMaxSize().padding(StepwiseSpacing.medium).verticalScroll(rememberScrollState())
    Column(modifier = contentModifier) {
        val archiveRes =
            if (state.activity.archived) R.string.activity_detail_unarchive else R.string.activity_detail_archive
        StepwiseOutlinedButton(text = stringResource(archiveRes), onClick = onToggleArchived)
        if (state.actionFailed) {
            Text(
                text = stringResource(R.string.activity_detail_action_error),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = StepwiseSpacing.small),
            )
        }
        GoalLinksSection(
            links = state.goalLinks,
            goals = state.goals,
            metrics = state.metrics,
            onLinkGoalClicked = onLinkGoalClicked,
            onUnlinkClicked = onUnlinkClicked,
        )
        SessionHistorySection(sessions = state.sessions, onLogSessionClicked = onLogSessionClicked)
    }
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
}
