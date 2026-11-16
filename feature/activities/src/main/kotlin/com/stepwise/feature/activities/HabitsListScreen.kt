@file:OptIn(ExperimentalMaterial3Api::class)

package com.stepwise.feature.activities

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stepwise.core.designsystem.component.StepwiseCard
import com.stepwise.core.designsystem.component.StepwiseEmptyState
import com.stepwise.core.designsystem.component.StepwiseErrorState
import com.stepwise.core.designsystem.component.StepwiseLoadingState
import com.stepwise.core.designsystem.component.StepwiseTopBar
import com.stepwise.core.designsystem.theme.StepwiseSpacing
import com.stepwise.core.model.Habit
import com.stepwise.core.model.HabitId

@Composable
fun HabitsRoute(
    onHabitClick: (HabitId) -> Unit,
    onAddHabitClick: () -> Unit,
    viewModel: HabitsListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = { StepwiseTopBar(title = stringResource(R.string.habits_title)) },
        floatingActionButton = {
            if (state !is HabitsListUiState.SignedOut) {
                FloatingActionButton(onClick = onAddHabitClick) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.habits_add_content_description),
                    )
                }
            }
        },
    ) { padding ->
        HabitsListScreen(state = state, onHabitClick = onHabitClick, modifier = Modifier.padding(padding))
    }
}

@Composable
fun HabitsListScreen(
    state: HabitsListUiState,
    onHabitClick: (HabitId) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        is HabitsListUiState.Loading -> {
            StepwiseLoadingState(modifier = modifier, label = stringResource(R.string.habits_loading))
        }
        is HabitsListUiState.SignedOut -> Unit
        is HabitsListUiState.Error -> {
            StepwiseErrorState(modifier = modifier, message = stringResource(R.string.habits_error_generic))
        }
        is HabitsListUiState.Content -> {
            HabitsListContent(modifier = modifier, state = state, onHabitClick = onHabitClick)
        }
    }
}

@Composable
private fun HabitsListContent(
    state: HabitsListUiState.Content,
    onHabitClick: (HabitId) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.active.isEmpty() && state.archived.isEmpty()) {
        StepwiseEmptyState(modifier = modifier, message = stringResource(R.string.habits_empty))
        return
    }
    val activeHeader = stringResource(R.string.habits_section_active)
    val archivedHeader = stringResource(R.string.habits_section_archived)
    LazyColumn(modifier = modifier.fillMaxSize().padding(StepwiseSpacing.medium)) {
        habitSection(state.active, activeHeader, onHabitClick)
        habitSection(state.archived, archivedHeader, onHabitClick)
    }
}

private fun LazyListScope.habitSection(
    habits: List<Habit>,
    header: String,
    onHabitClick: (HabitId) -> Unit,
) {
    if (habits.isEmpty()) return
    item { Text(text = header, style = MaterialTheme.typography.titleMedium) }
    items(habits, key = { it.id.value }) { habit -> HabitRow(habit = habit, onClick = onHabitClick) }
}

@Composable
private fun HabitRow(
    habit: Habit,
    onClick: (HabitId) -> Unit,
) {
    StepwiseCard(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(bottom = StepwiseSpacing.small)
                .clickable { onClick(habit.id) },
    ) {
        Text(text = habit.title, style = MaterialTheme.typography.titleMedium)
    }
}
