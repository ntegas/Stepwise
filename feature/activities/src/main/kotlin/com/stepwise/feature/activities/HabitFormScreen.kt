@file:OptIn(ExperimentalMaterial3Api::class)

package com.stepwise.feature.activities

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.stepwise.core.designsystem.component.StepwiseTextField
import com.stepwise.core.designsystem.component.StepwiseTopBar
import com.stepwise.core.designsystem.theme.StepwiseSpacing
import com.stepwise.core.model.Activity
import com.stepwise.core.model.ActivityId
import com.stepwise.core.model.Metric
import com.stepwise.core.model.MetricId

@Composable
fun HabitFormRoute(
    onSaved: () -> Unit,
    onBack: () -> Unit,
    viewModel: HabitFormViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) {
        if (state.saved) onSaved()
    }
    HabitFormScreen(
        state = state,
        onBack = onBack,
        onTitleChanged = viewModel::onTitleChanged,
        onTargetValueChanged = viewModel::onTargetValueChanged,
        onMetricSelected = viewModel::onMetricSelected,
        onActivitySelected = viewModel::onActivitySelected,
        onSave = viewModel::save,
    )
}

@Suppress("LongParameterList") // One callback per form field - this screen's genuine surface.
@Composable
fun HabitFormScreen(
    state: HabitFormUiState,
    onBack: () -> Unit,
    onTitleChanged: (String) -> Unit,
    onTargetValueChanged: (String) -> Unit,
    onMetricSelected: (MetricId) -> Unit,
    onActivitySelected: (ActivityId?) -> Unit,
    onSave: () -> Unit,
) {
    val isEdit = state.mode is HabitFormMode.Edit
    val titleRes = if (isEdit) R.string.habit_form_title_edit else R.string.habit_form_title_create
    Scaffold(
        topBar = {
            StepwiseTopBar(
                title = stringResource(titleRes),
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                navigationIconContentDescription = stringResource(R.string.activity_form_back_content_description),
                onNavigationClick = onBack,
            )
        },
    ) { padding ->
        HabitFormContent(
            state = state,
            padding = padding,
            onTitleChanged = onTitleChanged,
            onTargetValueChanged = onTargetValueChanged,
            onMetricSelected = onMetricSelected,
            onActivitySelected = onActivitySelected,
            onSave = onSave,
        )
    }
}

@Suppress("LongParameterList") // One callback per form field - this screen's genuine surface.
@Composable
private fun HabitFormContent(
    state: HabitFormUiState,
    padding: PaddingValues,
    onTitleChanged: (String) -> Unit,
    onTargetValueChanged: (String) -> Unit,
    onMetricSelected: (MetricId) -> Unit,
    onActivitySelected: (ActivityId?) -> Unit,
    onSave: () -> Unit,
) {
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
            label = stringResource(R.string.habit_form_name_label),
            isError = state.titleError,
            supportingText = if (state.titleError) stringResource(R.string.habit_form_name_required) else null,
        )
        StepwiseTextField(
            value = state.targetValue,
            onValueChange = onTargetValueChanged,
            label = stringResource(R.string.habit_form_target_label),
            isError = state.targetValueError,
            supportingText = if (state.targetValueError) stringResource(R.string.habit_form_target_invalid) else null,
            keyboardType = KeyboardType.Number,
            modifier = Modifier.padding(top = StepwiseSpacing.small),
        )
        Text(
            text = stringResource(R.string.habit_form_metric_label),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = StepwiseSpacing.medium),
        )
        MetricPicker(metrics = state.metrics, selectedId = state.metricId, onMetricSelected = onMetricSelected)
        if (state.metricError) {
            Text(
                text = stringResource(R.string.habit_form_metric_required),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(
            text = stringResource(R.string.habit_form_activity_label),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = StepwiseSpacing.medium),
        )
        ActivityPicker(
            activities = state.activities,
            selectedId = state.activityId,
            onActivitySelected = onActivitySelected,
        )
        if (state.hasError) {
            Text(
                text = stringResource(R.string.habit_form_error_generic),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = StepwiseSpacing.small),
            )
        }
        StepwiseButton(
            text = stringResource(R.string.habit_form_save),
            onClick = onSave,
            enabled = !state.isSubmitting,
            modifier = Modifier.fillMaxWidth().padding(top = StepwiseSpacing.large),
        )
    }
}

@Composable
private fun MetricPicker(
    metrics: List<Metric>,
    selectedId: MetricId?,
    onMetricSelected: (MetricId) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(StepwiseSpacing.extraSmall)) {
        items(metrics, key = { it.id.value }) { metric ->
            StepwiseFilterChip(
                label = "${metric.key} (${metric.canonicalUnit})",
                selected = selectedId == metric.id,
                onSelectedChange = { onMetricSelected(metric.id) },
            )
        }
    }
}

@Composable
private fun ActivityPicker(
    activities: List<Activity>,
    selectedId: ActivityId?,
    onActivitySelected: (ActivityId?) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(StepwiseSpacing.extraSmall)) {
        item {
            StepwiseFilterChip(
                label = stringResource(R.string.habit_form_activity_none),
                selected = selectedId == null,
                onSelectedChange = { onActivitySelected(null) },
            )
        }
        items(activities, key = { it.id.value }) { activity ->
            StepwiseFilterChip(
                label = activity.title,
                selected = selectedId == activity.id,
                onSelectedChange = { onActivitySelected(if (selectedId == activity.id) null else activity.id) },
            )
        }
    }
}
