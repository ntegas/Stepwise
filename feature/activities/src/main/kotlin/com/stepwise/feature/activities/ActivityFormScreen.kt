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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stepwise.core.designsystem.component.StepwiseButton
import com.stepwise.core.designsystem.component.StepwiseFilterChip
import com.stepwise.core.designsystem.component.StepwiseTextField
import com.stepwise.core.designsystem.component.StepwiseTopBar
import com.stepwise.core.designsystem.theme.StepwiseSpacing
import com.stepwise.core.model.LifeArea
import com.stepwise.core.model.LifeAreaId

@Composable
fun ActivityFormRoute(
    onSaved: () -> Unit,
    onBack: () -> Unit,
    viewModel: ActivityFormViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) {
        if (state.saved) onSaved()
    }
    ActivityFormScreen(
        state = state,
        onBack = onBack,
        onTitleChanged = viewModel::onTitleChanged,
        onLifeAreaSelected = viewModel::onLifeAreaSelected,
        onSave = viewModel::save,
    )
}

@Composable
fun ActivityFormScreen(
    state: ActivityFormUiState,
    onBack: () -> Unit,
    onTitleChanged: (String) -> Unit,
    onLifeAreaSelected: (LifeAreaId?) -> Unit,
    onSave: () -> Unit,
) {
    val isEdit = state.mode is ActivityFormMode.Edit
    val titleRes = if (isEdit) R.string.activity_form_title_edit else R.string.activity_form_title_create
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
        ActivityFormContent(
            state = state,
            padding = padding,
            onTitleChanged = onTitleChanged,
            onLifeAreaSelected = onLifeAreaSelected,
            onSave = onSave,
        )
    }
}

@Composable
private fun ActivityFormContent(
    state: ActivityFormUiState,
    padding: PaddingValues,
    onTitleChanged: (String) -> Unit,
    onLifeAreaSelected: (LifeAreaId?) -> Unit,
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
            label = stringResource(R.string.activity_form_name_label),
            isError = state.titleError,
            supportingText = if (state.titleError) stringResource(R.string.activity_form_name_required) else null,
        )
        Text(
            text = stringResource(R.string.activity_form_life_area_label),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = StepwiseSpacing.medium),
        )
        LifeAreaPicker(
            lifeAreas = state.lifeAreas,
            selectedId = state.lifeAreaId,
            onLifeAreaSelected = onLifeAreaSelected,
        )
        if (state.hasError) {
            Text(
                text = stringResource(R.string.activity_form_error_generic),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = StepwiseSpacing.small),
            )
        }
        StepwiseButton(
            text = stringResource(R.string.activity_form_save),
            onClick = onSave,
            enabled = !state.isSubmitting,
            modifier = Modifier.fillMaxWidth().padding(top = StepwiseSpacing.large),
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
                label = stringResource(R.string.activity_form_life_area_none),
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
