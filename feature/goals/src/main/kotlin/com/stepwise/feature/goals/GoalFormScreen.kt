package com.stepwise.feature.goals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stepwise.core.designsystem.component.StepwiseButton
import com.stepwise.core.designsystem.component.StepwiseFilterChip
import com.stepwise.core.designsystem.component.StepwiseTextButton
import com.stepwise.core.designsystem.component.StepwiseTextField
import com.stepwise.core.designsystem.component.StepwiseTopBar
import com.stepwise.core.designsystem.theme.StepwiseSpacing
import com.stepwise.core.model.LifeArea
import com.stepwise.core.model.LifeAreaId
import com.stepwise.core.model.Project
import com.stepwise.core.model.ProjectId

@Composable
fun GoalFormRoute(
    onSaved: () -> Unit,
    onBack: () -> Unit,
    viewModel: GoalFormViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) {
        if (state.saved) onSaved()
    }
    GoalFormScreen(
        state = state,
        onBack = onBack,
        onTitleChanged = viewModel::onTitleChanged,
        onTargetValueChanged = viewModel::onTargetValueChanged,
        onUnitChanged = viewModel::onUnitChanged,
        onDeadlineChanged = viewModel::onDeadlineChanged,
        onCategoryChanged = viewModel::onCategoryChanged,
        onDescriptionChanged = viewModel::onDescriptionChanged,
        onPriorityChanged = viewModel::onPriorityChanged,
        onLifeAreaSelected = viewModel::onLifeAreaSelected,
        onProjectSelected = viewModel::onProjectSelected,
        onMoreOptionsToggled = viewModel::onMoreOptionsToggled,
        onSave = viewModel::save,
    )
}

@Composable
fun GoalFormScreen(
    state: GoalFormUiState,
    onBack: () -> Unit,
    onTitleChanged: (String) -> Unit,
    onTargetValueChanged: (String) -> Unit,
    onUnitChanged: (String) -> Unit,
    onDeadlineChanged: (String) -> Unit,
    onCategoryChanged: (String) -> Unit,
    onDescriptionChanged: (String) -> Unit,
    onPriorityChanged: (String) -> Unit,
    onLifeAreaSelected: (LifeAreaId?) -> Unit,
    onProjectSelected: (ProjectId?) -> Unit,
    onMoreOptionsToggled: () -> Unit,
    onSave: () -> Unit,
) {
    val titleRes =
        if (state.mode is GoalFormMode.Edit) R.string.goal_form_title_edit else R.string.goal_form_title_create
    Scaffold(
        topBar = {
            StepwiseTopBar(
                title = stringResource(titleRes),
                navigationIcon = Icons.Filled.ArrowBack,
                navigationIconContentDescription = stringResource(R.string.goal_form_back_content_description),
                onNavigationClick = onBack,
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(StepwiseSpacing.medium)
                    .verticalScroll(rememberScrollState()),
        ) {
            StepwiseTextField(
                value = state.title,
                onValueChange = onTitleChanged,
                label = stringResource(R.string.goal_form_name_label),
                isError = state.titleError,
                supportingText = if (state.titleError) stringResource(R.string.goal_form_name_required) else null,
            )
            StepwiseTextField(
                value = state.targetValue,
                onValueChange = onTargetValueChanged,
                label = stringResource(R.string.goal_form_target_label),
                isError = state.targetValueError,
                supportingText =
                    if (state.targetValueError) stringResource(R.string.goal_form_target_invalid) else null,
                keyboardType = KeyboardType.Number,
                modifier = Modifier.padding(top = StepwiseSpacing.small),
            )
            StepwiseTextField(
                value = state.unit,
                onValueChange = onUnitChanged,
                label = stringResource(R.string.goal_form_unit_label),
                modifier = Modifier.padding(top = StepwiseSpacing.small),
            )
            StepwiseTextField(
                value = state.deadline,
                onValueChange = onDeadlineChanged,
                label = stringResource(R.string.goal_form_deadline_label),
                isError = state.deadlineError,
                supportingText = if (state.deadlineError) stringResource(R.string.goal_form_deadline_invalid) else null,
                modifier = Modifier.padding(top = StepwiseSpacing.small),
            )
            StepwiseTextButton(
                text = stringResource(R.string.goal_form_more_options),
                onClick = onMoreOptionsToggled,
                modifier = Modifier.padding(top = StepwiseSpacing.medium),
            )
            if (state.moreOptionsExpanded) {
                MoreOptions(
                    state = state,
                    onCategoryChanged = onCategoryChanged,
                    onDescriptionChanged = onDescriptionChanged,
                    onPriorityChanged = onPriorityChanged,
                    onLifeAreaSelected = onLifeAreaSelected,
                    onProjectSelected = onProjectSelected,
                )
            }
            if (state.error != null) {
                Text(
                    text = state.error,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = StepwiseSpacing.small),
                )
            }
            StepwiseButton(
                text = stringResource(R.string.goal_form_save),
                onClick = onSave,
                enabled = !state.isSubmitting,
                modifier = Modifier.fillMaxWidth().padding(top = StepwiseSpacing.large),
            )
        }
    }
}

@Composable
private fun MoreOptions(
    state: GoalFormUiState,
    onCategoryChanged: (String) -> Unit,
    onDescriptionChanged: (String) -> Unit,
    onPriorityChanged: (String) -> Unit,
    onLifeAreaSelected: (LifeAreaId?) -> Unit,
    onProjectSelected: (ProjectId?) -> Unit,
) {
    Column {
        Text(text = stringResource(R.string.goal_form_life_area_label), style = MaterialTheme.typography.labelLarge)
        LifeAreaPicker(
            lifeAreas = state.lifeAreas,
            selectedId = state.lifeAreaId,
            onLifeAreaSelected = onLifeAreaSelected,
        )
        Text(
            text = stringResource(R.string.goal_form_project_label),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = StepwiseSpacing.small),
        )
        ProjectPicker(
            projects = state.projects,
            selectedId = state.projectId,
            onProjectSelected = onProjectSelected,
        )
        StepwiseTextField(
            value = state.category,
            onValueChange = onCategoryChanged,
            label = stringResource(R.string.goal_form_category_label),
            modifier = Modifier.padding(top = StepwiseSpacing.small),
        )
        StepwiseTextField(
            value = state.description,
            onValueChange = onDescriptionChanged,
            label = stringResource(R.string.goal_form_description_label),
            singleLine = false,
            modifier = Modifier.padding(top = StepwiseSpacing.small),
        )
        StepwiseTextField(
            value = state.priority,
            onValueChange = onPriorityChanged,
            label = stringResource(R.string.goal_form_priority_label),
            isError = state.priorityError,
            supportingText = if (state.priorityError) stringResource(R.string.goal_form_priority_invalid) else null,
            keyboardType = KeyboardType.Number,
            modifier = Modifier.padding(top = StepwiseSpacing.small),
        )
    }
}

@Composable
private fun LifeAreaPicker(
    lifeAreas: List<LifeArea>,
    selectedId: LifeAreaId?,
    onLifeAreaSelected: (LifeAreaId?) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(StepwiseSpacing.extraSmall)) {
        item {
            StepwiseFilterChip(
                label = stringResource(R.string.goal_form_life_area_none),
                selected = selectedId == null,
                onSelectedChange = { onLifeAreaSelected(null) },
            )
        }
        items(lifeAreas, key = { it.id.value }) { lifeArea ->
            StepwiseFilterChip(
                label = lifeArea.name,
                selected = selectedId == lifeArea.id,
                onSelectedChange = { onLifeAreaSelected(if (selectedId == lifeArea.id) null else lifeArea.id) },
            )
        }
    }
}

@Composable
private fun ProjectPicker(
    projects: List<Project>,
    selectedId: ProjectId?,
    onProjectSelected: (ProjectId?) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(StepwiseSpacing.extraSmall)) {
        item {
            StepwiseFilterChip(
                label = stringResource(R.string.goal_form_project_none),
                selected = selectedId == null,
                onSelectedChange = { onProjectSelected(null) },
            )
        }
        items(projects, key = { it.id.value }) { project ->
            StepwiseFilterChip(
                label = project.title,
                selected = selectedId == project.id,
                onSelectedChange = { onProjectSelected(if (selectedId == project.id) null else project.id) },
            )
        }
    }
}
