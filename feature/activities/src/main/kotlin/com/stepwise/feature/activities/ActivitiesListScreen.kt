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
import com.stepwise.core.model.Activity
import com.stepwise.core.model.ActivityId

@Composable
fun ActivitiesRoute(
    onActivityClick: (ActivityId) -> Unit,
    onAddActivityClick: () -> Unit,
    viewModel: ActivitiesListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = { StepwiseTopBar(title = stringResource(R.string.activities_title)) },
        floatingActionButton = {
            if (state !is ActivitiesListUiState.SignedOut) {
                FloatingActionButton(onClick = onAddActivityClick) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.activities_add_content_description),
                    )
                }
            }
        },
    ) { padding ->
        ActivitiesListScreen(
            state = state,
            onActivityClick = onActivityClick,
            modifier = Modifier.padding(padding),
        )
    }
}

@Composable
fun ActivitiesListScreen(
    state: ActivitiesListUiState,
    onActivityClick: (ActivityId) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        is ActivitiesListUiState.Loading -> {
            StepwiseLoadingState(modifier = modifier, label = stringResource(R.string.activities_loading))
        }
        is ActivitiesListUiState.SignedOut -> Unit
        is ActivitiesListUiState.Error -> {
            StepwiseErrorState(modifier = modifier, message = stringResource(R.string.activities_error_generic))
        }
        is ActivitiesListUiState.Content -> {
            ActivitiesListContent(modifier = modifier, state = state, onActivityClick = onActivityClick)
        }
    }
}

@Composable
private fun ActivitiesListContent(
    state: ActivitiesListUiState.Content,
    onActivityClick: (ActivityId) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.active.isEmpty() && state.archived.isEmpty()) {
        StepwiseEmptyState(modifier = modifier, message = stringResource(R.string.activities_empty))
        return
    }
    val activeHeader = stringResource(R.string.activities_section_active)
    val archivedHeader = stringResource(R.string.activities_section_archived)
    LazyColumn(modifier = modifier.fillMaxSize().padding(StepwiseSpacing.medium)) {
        activitySection(state.active, activeHeader, onActivityClick)
        activitySection(state.archived, archivedHeader, onActivityClick)
    }
}

private fun LazyListScope.activitySection(
    activities: List<Activity>,
    header: String,
    onActivityClick: (ActivityId) -> Unit,
) {
    if (activities.isEmpty()) return
    item { Text(text = header, style = MaterialTheme.typography.titleMedium) }
    items(activities, key = { it.id.value }) { activity -> ActivityRow(activity = activity, onClick = onActivityClick) }
}

@Composable
private fun ActivityRow(
    activity: Activity,
    onClick: (ActivityId) -> Unit,
) {
    StepwiseCard(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(bottom = StepwiseSpacing.small)
                .clickable { onClick(activity.id) },
    ) {
        Text(text = activity.title, style = MaterialTheme.typography.titleMedium)
    }
}
