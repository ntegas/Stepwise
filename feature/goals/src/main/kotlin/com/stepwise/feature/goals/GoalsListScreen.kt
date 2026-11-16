@file:OptIn(ExperimentalMaterial3Api::class)

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
import androidx.compose.material3.ExperimentalMaterial3Api
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
    onActivitiesClick: () -> Unit,
    onHabitsClick: () -> Unit,
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
            onActivitiesClick = onActivitiesClick,
            onHabitsClick = onHabitsClick,
            modifier = Modifier.padding(padding),
        )
    }
}

@Composable
fun GoalsListScreen(
    state: GoalsListUiState,
    onGoalClick: (GoalId) -> Unit,
    onActivitiesClick: () -> Unit,
    onHabitsClick: () -> Unit,
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
            GoalsListContent(
                modifier = modifier,
                state = state,
                onGoalClick = onGoalClick,
                onActivitiesClick = onActivitiesClick,
                onHabitsClick = onHabitsClick,
            )
        }
    }
}

@Composable
private fun GoalsListContent(
    state: GoalsListUiState.Content,
    onGoalClick: (GoalId) -> Unit,
    onActivitiesClick: () -> Unit,
    onHabitsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // stringResource() is @Composable; LazyColumn's own content block is a plain
    // LazyListScope builder (not a @Composable scope itself - only item{}/items{} inside it
    // are), so these have to resolve out here, not as arguments evaluated inside that block.
    val activeHeader = stringResource(R.string.goals_section_active)
    val completedHeader = stringResource(R.string.goals_section_completed)
    val archivedHeader = stringResource(R.string.goals_section_archived)
    val isEmpty = state.active.isEmpty() && state.completed.isEmpty() && state.archived.isEmpty()
    LazyColumn(modifier = modifier.fillMaxSize().padding(StepwiseSpacing.medium)) {
        // Activities/Habits deliberately have no bottom-nav tab of their own
        // (/PRODUCT_CANON.md: no separate main tab per internal entity - they open from the
        // main screens instead). Surfaced here rather than Today/Plan, since GoalActivityLink
        // is specifically the Activity/Habit-to-Goal fan-out this screen already cares about.
        item { ManageRow(onActivitiesClick = onActivitiesClick, onHabitsClick = onHabitsClick) }
        if (isEmpty) {
            item { Text(text = stringResource(R.string.goals_empty), style = MaterialTheme.typography.bodyMedium) }
        } else {
            goalSection(state.active, activeHeader, onGoalClick)
            goalSection(state.completed, completedHeader, onGoalClick)
            goalSection(state.archived, archivedHeader, onGoalClick)
        }
    }
}

@Composable
private fun ManageRow(
    onActivitiesClick: () -> Unit,
    onHabitsClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = StepwiseSpacing.medium),
        horizontalArrangement = Arrangement.spacedBy(StepwiseSpacing.small),
    ) {
        StepwiseAssistChip(label = stringResource(R.string.goals_manage_activities), onClick = onActivitiesClick)
        StepwiseAssistChip(label = stringResource(R.string.goals_manage_habits), onClick = onHabitsClick)
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
                val targetValue = goal.targetValue
                val unit = goal.unit
                // Goal is defined in :core:model, a different module - Kotlin won't smart-cast
                // its nullable properties across a module boundary, so bind to locals first.
                if (targetValue != null && unit != null) {
                    Text(
                        text = stringResource(R.string.goals_target_label, formatTargetValue(targetValue), unit),
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
