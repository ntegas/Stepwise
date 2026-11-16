@file:OptIn(ExperimentalMaterial3Api::class)

package com.stepwise.feature.goals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stepwise.core.common.toDisplayString
import com.stepwise.core.designsystem.component.StepwiseAssistChip
import com.stepwise.core.designsystem.component.StepwiseButton
import com.stepwise.core.designsystem.component.StepwiseErrorState
import com.stepwise.core.designsystem.component.StepwiseLoadingState
import com.stepwise.core.designsystem.component.StepwiseOutlinedButton
import com.stepwise.core.designsystem.component.StepwiseTextButton
import com.stepwise.core.designsystem.component.StepwiseTextField
import com.stepwise.core.designsystem.component.StepwiseTopBar
import com.stepwise.core.designsystem.theme.StepwiseSpacing
import com.stepwise.core.model.Goal
import com.stepwise.core.model.GoalId
import com.stepwise.core.model.LifecycleStatus
import com.stepwise.core.model.Milestone

@Composable
fun GoalDetailRoute(
    onEditClick: (GoalId) -> Unit,
    onBack: () -> Unit,
    viewModel: GoalDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    GoalDetailScreen(
        state = state,
        onBack = onBack,
        onEditClick = onEditClick,
        onPause = viewModel::pause,
        onResume = viewModel::resume,
        onComplete = viewModel::complete,
        onArchive = viewModel::archive,
        onReopen = viewModel::reopen,
        onAddMilestoneClick = viewModel::onAddMilestoneClicked,
        onMilestoneDialogDismiss = viewModel::onMilestoneDialogDismissed,
        onMilestoneTitleChanged = viewModel::onMilestoneTitleChanged,
        onMilestoneWeightChanged = viewModel::onMilestoneWeightChanged,
        onMilestoneIsBinaryChanged = viewModel::onMilestoneIsBinaryChanged,
        onSaveMilestone = viewModel::saveMilestone,
        onToggleMilestone = viewModel::toggleMilestone,
        onMilestoneProgressChanged = viewModel::updateMilestoneProgress,
    )
}

@Composable
fun GoalDetailScreen(
    state: GoalDetailUiState,
    onBack: () -> Unit,
    onEditClick: (GoalId) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onComplete: () -> Unit,
    onArchive: () -> Unit,
    onReopen: () -> Unit,
    onAddMilestoneClick: () -> Unit,
    onMilestoneDialogDismiss: () -> Unit,
    onMilestoneTitleChanged: (String) -> Unit,
    onMilestoneWeightChanged: (String) -> Unit,
    onMilestoneIsBinaryChanged: (Boolean) -> Unit,
    onSaveMilestone: () -> Unit,
    onToggleMilestone: (Milestone) -> Unit,
    onMilestoneProgressChanged: (Milestone, Double) -> Unit,
) {
    Scaffold(
        topBar = {
            StepwiseTopBar(
                title = (state as? GoalDetailUiState.Content)?.goal?.title ?: stringResource(R.string.goals_title),
                navigationIcon = Icons.Filled.ArrowBack,
                navigationIconContentDescription = stringResource(R.string.goal_form_back_content_description),
                onNavigationClick = onBack,
            )
        },
    ) { padding ->
        when (state) {
            is GoalDetailUiState.Loading -> {
                StepwiseLoadingState(modifier = Modifier.padding(padding).fillMaxSize())
            }
            is GoalDetailUiState.NotFound -> {
                StepwiseErrorState(
                    message = stringResource(R.string.goal_detail_error),
                    modifier = Modifier.padding(padding).fillMaxSize(),
                )
            }
            is GoalDetailUiState.Error -> {
                StepwiseErrorState(
                    message = stringResource(R.string.goal_detail_error),
                    modifier = Modifier.padding(padding).fillMaxSize(),
                )
            }
            is GoalDetailUiState.Content -> {
                GoalDetailContent(
                    modifier = Modifier.padding(padding),
                    state = state,
                    onEditClick = onEditClick,
                    onPause = onPause,
                    onResume = onResume,
                    onComplete = onComplete,
                    onArchive = onArchive,
                    onReopen = onReopen,
                    onAddMilestoneClick = onAddMilestoneClick,
                    onMilestoneDialogDismiss = onMilestoneDialogDismiss,
                    onMilestoneTitleChanged = onMilestoneTitleChanged,
                    onMilestoneWeightChanged = onMilestoneWeightChanged,
                    onMilestoneIsBinaryChanged = onMilestoneIsBinaryChanged,
                    onSaveMilestone = onSaveMilestone,
                    onToggleMilestone = onToggleMilestone,
                    onMilestoneProgressChanged = onMilestoneProgressChanged,
                )
            }
        }
    }
}

@Composable
private fun GoalDetailContent(
    state: GoalDetailUiState.Content,
    onEditClick: (GoalId) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onComplete: () -> Unit,
    onArchive: () -> Unit,
    onReopen: () -> Unit,
    onAddMilestoneClick: () -> Unit,
    onMilestoneDialogDismiss: () -> Unit,
    onMilestoneTitleChanged: (String) -> Unit,
    onMilestoneWeightChanged: (String) -> Unit,
    onMilestoneIsBinaryChanged: (Boolean) -> Unit,
    onSaveMilestone: () -> Unit,
    onToggleMilestone: (Milestone) -> Unit,
    onMilestoneProgressChanged: (Milestone, Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(StepwiseSpacing.medium)
                .verticalScroll(rememberScrollState()),
    ) {
        GoalMetadata(goal = state.goal)
        LifecycleActions(
            goal = state.goal,
            onEditClick = onEditClick,
            onPause = onPause,
            onResume = onResume,
            onComplete = onComplete,
            onArchive = onArchive,
            onReopen = onReopen,
        )
        if (state.actionFailed) {
            Text(
                text = stringResource(R.string.goal_detail_action_error),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = StepwiseSpacing.small),
            )
        }
        Text(
            text = stringResource(R.string.goal_detail_milestones_header),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = StepwiseSpacing.large),
        )
        if (state.milestones.isEmpty()) {
            Text(
                text = stringResource(R.string.goal_detail_no_milestones),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = StepwiseSpacing.small),
            )
        } else {
            state.milestones.forEach { milestone ->
                MilestoneRow(
                    milestone = milestone,
                    onToggle = { onToggleMilestone(milestone) },
                    onProgressChanged = { progress -> onMilestoneProgressChanged(milestone, progress) },
                )
            }
        }
        StepwiseTextButton(
            text = stringResource(R.string.goal_detail_add_milestone),
            onClick = onAddMilestoneClick,
            modifier = Modifier.padding(top = StepwiseSpacing.small),
        )
    }
    if (state.milestoneDialogVisible) {
        MilestoneDialog(
            state = state,
            onDismiss = onMilestoneDialogDismiss,
            onTitleChanged = onMilestoneTitleChanged,
            onWeightChanged = onMilestoneWeightChanged,
            onIsBinaryChanged = onMilestoneIsBinaryChanged,
            onSave = onSaveMilestone,
        )
    }
}

@Composable
private fun GoalMetadata(goal: Goal) {
    val targetValue = goal.targetValue
    val unit = goal.unit
    // Goal is defined in :core:model, a different module - Kotlin won't smart-cast its
    // nullable properties across a module boundary, so bind to locals first.
    if (targetValue != null && unit != null) {
        Text(
            text = stringResource(R.string.goals_target_label, formatTargetValue(targetValue), unit),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    goal.deadline?.let { deadline ->
        Text(
            text = deadline.toDisplayString(),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = StepwiseSpacing.extraSmall),
        )
    }
    goal.description?.let { description ->
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = StepwiseSpacing.small),
        )
    }
    if (goal.status == LifecycleStatus.PAUSED) {
        StepwiseAssistChip(
            label = stringResource(R.string.goals_status_paused),
            onClick = {},
            modifier = Modifier.padding(top = StepwiseSpacing.small),
        )
    }
}

@Composable
private fun LifecycleActions(
    goal: Goal,
    onEditClick: (GoalId) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onComplete: () -> Unit,
    onArchive: () -> Unit,
    onReopen: () -> Unit,
) {
    Row(
        modifier = Modifier.padding(top = StepwiseSpacing.medium),
        horizontalArrangement = Arrangement.spacedBy(StepwiseSpacing.small),
    ) {
        when (goal.status) {
            LifecycleStatus.ACTIVE -> {
                StepwiseOutlinedButton(text = stringResource(R.string.goal_detail_pause), onClick = onPause)
                StepwiseOutlinedButton(text = stringResource(R.string.goal_detail_complete), onClick = onComplete)
                StepwiseTextButton(text = stringResource(R.string.goal_detail_archive), onClick = onArchive)
            }
            LifecycleStatus.PAUSED -> {
                StepwiseOutlinedButton(text = stringResource(R.string.goal_detail_resume), onClick = onResume)
                StepwiseOutlinedButton(text = stringResource(R.string.goal_detail_complete), onClick = onComplete)
                StepwiseTextButton(text = stringResource(R.string.goal_detail_archive), onClick = onArchive)
            }
            LifecycleStatus.COMPLETED -> {
                StepwiseOutlinedButton(text = stringResource(R.string.goal_detail_reopen), onClick = onReopen)
                StepwiseTextButton(text = stringResource(R.string.goal_detail_archive), onClick = onArchive)
            }
            LifecycleStatus.ARCHIVED -> {
                StepwiseOutlinedButton(text = stringResource(R.string.goal_detail_reopen), onClick = onReopen)
            }
        }
    }
    StepwiseTextButton(
        text = stringResource(R.string.goal_detail_edit),
        onClick = { onEditClick(goal.id) },
        modifier = Modifier.padding(top = StepwiseSpacing.extraSmall),
    )
}

@Composable
private fun MilestoneRow(
    milestone: Milestone,
    onToggle: () -> Unit,
    onProgressChanged: (Double) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = StepwiseSpacing.small)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (milestone.isBinary) {
                Checkbox(checked = milestone.completed, onCheckedChange = { onToggle() })
            }
            Text(text = milestone.title, style = MaterialTheme.typography.bodyLarge)
        }
        if (!milestone.isBinary) {
            var sliderPosition by remember(milestone.id) {
                mutableFloatStateOf(((milestone.progressPercent ?: 0.0) / PERCENT_SCALE).toFloat())
            }
            Text(
                text = "${(sliderPosition * PERCENT_SCALE).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
            )
            Slider(
                value = sliderPosition,
                onValueChange = { sliderPosition = it },
                onValueChangeFinished = { onProgressChanged(sliderPosition * PERCENT_SCALE) },
            )
        }
    }
}

@Composable
private fun MilestoneDialog(
    state: GoalDetailUiState.Content,
    onDismiss: () -> Unit,
    onTitleChanged: (String) -> Unit,
    onWeightChanged: (String) -> Unit,
    onIsBinaryChanged: (Boolean) -> Unit,
    onSave: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.milestone_dialog_title)) },
        text = {
            Column {
                StepwiseTextField(
                    value = state.milestoneTitle,
                    onValueChange = onTitleChanged,
                    label = stringResource(R.string.milestone_name_label),
                    isError = state.milestoneTitleError,
                    supportingText =
                        if (state.milestoneTitleError) stringResource(R.string.milestone_name_required) else null,
                )
                StepwiseTextField(
                    value = state.milestoneWeight,
                    onValueChange = onWeightChanged,
                    label = stringResource(R.string.milestone_weight_label),
                    isError = state.milestoneWeightError,
                    supportingText =
                        if (state.milestoneWeightError) {
                            stringResource(R.string.milestone_weight_invalid)
                        } else {
                            stringResource(R.string.milestone_weight_hint)
                        },
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.padding(top = StepwiseSpacing.small),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = StepwiseSpacing.small),
                ) {
                    Checkbox(checked = state.milestoneIsBinary, onCheckedChange = onIsBinaryChanged)
                    Text(
                        text = stringResource(R.string.milestone_binary_label),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (state.milestoneHasError) {
                    Text(
                        text = stringResource(R.string.milestone_error_generic),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = StepwiseSpacing.small),
                    )
                }
            }
        },
        confirmButton = {
            StepwiseButton(
                text = stringResource(R.string.milestone_save),
                onClick = onSave,
                enabled = !state.milestoneSaving,
            )
        },
        dismissButton = {
            StepwiseTextButton(text = stringResource(R.string.milestone_cancel), onClick = onDismiss)
        },
    )
}

private const val PERCENT_SCALE = 100.0
