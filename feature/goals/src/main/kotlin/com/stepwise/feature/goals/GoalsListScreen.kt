package com.stepwise.feature.goals

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stepwise.core.common.toDisplayString
import com.stepwise.core.designsystem.component.StepwiseAssistChip
import com.stepwise.core.designsystem.component.StepwiseCard
import com.stepwise.core.designsystem.component.StepwiseEmptyState
import com.stepwise.core.designsystem.component.StepwiseErrorState
import com.stepwise.core.designsystem.component.StepwiseLoadingState
import com.stepwise.core.designsystem.component.StepwiseTopBar
import com.stepwise.core.designsystem.theme.StepwiseSpacing
import com.stepwise.core.model.Goal
import com.stepwise.core.model.GoalId
import com.stepwise.core.model.LifecycleStatus

@Composable
fun GoalsRoute(
    onGoalClick: (GoalId) -> Unit,
    onAddGoalClick: () -> Unit,
    viewModel: GoalsListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = { StepwiseTopBar(title = stringResource(R.string.goals_title)) },
        floatingActionButton = {
            // Hidden while SignedOut - this tab becomes unreachable the instant sign-out happens
            // (StepwiseNavHost pops to Sign In), but a FAB that's visible for an instant with no
            // working destination behind it is still worth not rendering in the first place.
            if (state !is GoalsListUiState.SignedOut) {
                FloatingActionButton(onClick = onAddGoalClick) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.goals_add_content_description),
                    )
                }
            }
        },
    ) { padding ->
        GoalsListScreen(
            state = state,
            onGoalClick = onGoalClick,
            modifier = Modifier.padding(padding),
        )
    }
}

@Composable
fun GoalsListScreen(
    state: GoalsListUiState,
    onGoalClick: (GoalId) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        is GoalsListUiState.Loading -> {
            StepwiseLoadingState(modifier = modifier, label = stringResource(R.string.goals_loading))
        }
        is GoalsListUiState.SignedOut -> Unit
        is GoalsListUiState.Error -> {
            StepwiseErrorState(modifier = modifier, message = stringResource(R.string.goals_error_generic))
        }
        is GoalsListUiState.Content -> {
            GoalsListContent(modifier = modifier, state = state, onGoalClick = onGoalClick)
        }
    }
}

@Composable
private fun GoalsListContent(
    state: GoalsListUiState.Content,
    onGoalClick: (GoalId) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.active.isEmpty() && state.completed.isEmpty() && state.archived.isEmpty()) {
        StepwiseEmptyState(modifier = modifier, message = stringResource(R.string.goals_empty))
        return
    }
    LazyColumn(modifier = modifier.fillMaxSize().padding(StepwiseSpacing.medium)) {
        goalSection(state.active, stringResource(R.string.goals_section_active), onGoalClick)
        goalSection(state.completed, stringResource(R.string.goals_section_completed), onGoalClick)
        goalSection(state.archived, stringResource(R.string.goals_section_archived), onGoalClick)
    }
}

private fun LazyListScope.goalSection(
    goals: List<Goal>,
    header: String,
    onGoalClick: (GoalId) -> Unit,
) {
    if (goals.isEmpty()) return
    item { Text(text = header, style = MaterialTheme.typography.titleMedium) }
    items(goals, key = { it.id.value }) { goal -> GoalRow(goal = goal, onGoalClick = onGoalClick) }
}

@Composable
private fun GoalRow(
    goal: Goal,
    onGoalClick: (GoalId) -> Unit,
) {
    StepwiseCard(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(bottom = StepwiseSpacing.small)
                .clickable { onGoalClick(goal.id) },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(text = goal.title, style = MaterialTheme.typography.titleMedium)
                if (goal.targetValue != null && goal.unit != null) {
                    Text(
                        text =
                            stringResource(
                                R.string.goals_target_label,
                                formatTargetValue(goal.targetValue),
                                goal.unit,
                            ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                goal.deadline?.let { deadline ->
                    Text(text = deadline.toDisplayString(), style = MaterialTheme.typography.bodySmall)
                }
            }
            if (goal.status == LifecycleStatus.PAUSED) {
                StepwiseAssistChip(label = stringResource(R.string.goals_status_paused), onClick = {})
            }
        }
    }
}
