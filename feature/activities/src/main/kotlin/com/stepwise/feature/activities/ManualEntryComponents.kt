@file:OptIn(ExperimentalMaterial3Api::class)

package com.stepwise.feature.activities

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.stepwise.core.designsystem.component.StepwiseButton
import com.stepwise.core.designsystem.component.StepwiseFilterChip
import com.stepwise.core.designsystem.component.StepwiseTextButton
import com.stepwise.core.designsystem.component.StepwiseTextField
import com.stepwise.core.designsystem.theme.StepwiseSpacing
import com.stepwise.core.model.ExecutionStatus
import com.stepwise.core.model.MetricId
import kotlinx.datetime.LocalDate
import java.time.Instant
import java.time.ZoneOffset

@Composable
internal fun ManualEntryDialog(
    state: ManualEntryDialogState,
    onDateChanged: (String) -> Unit,
    onStatusSelected: (ExecutionStatus) -> Unit,
    onMetricValueChanged: (MetricId, String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.manual_entry_dialog_title)) },
        text = {
            Column {
                ManualEntryDateField(value = state.date, onValueChange = onDateChanged)
                Text(
                    text = stringResource(R.string.manual_entry_status_label),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = StepwiseSpacing.small),
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(StepwiseSpacing.extraSmall)) {
                    items(MANUAL_ENTRY_STATUSES) { status ->
                        StepwiseFilterChip(
                            label = stringResource(executionStatusLabelRes(status)),
                            selected = state.status == status,
                            onSelectedChange = { onStatusSelected(status) },
                        )
                    }
                }
                ManualEntryMetricsSection(state = state, onMetricValueChanged = onMetricValueChanged)
                if (state.hasError) {
                    Text(
                        text = stringResource(R.string.manual_entry_error_generic),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = StepwiseSpacing.small),
                    )
                }
            }
        },
        confirmButton = {
            StepwiseButton(
                text = stringResource(R.string.manual_entry_save),
                onClick = onConfirm,
                enabled = !state.isSubmitting,
            )
        },
        dismissButton = {
            StepwiseTextButton(text = stringResource(R.string.manual_entry_cancel), onClick = onDismiss)
        },
    )
}

/**
 * [statusAcceptsMetrics]'s rendering half - shows the metric inputs when the selected status
 * resolves them, or an explanation instead of silently dropping an already-typed value
 * (DEV-018 review, design reviewer, HIGH: MetricResolver never resolves a metric for
 * MISSED/CANCELLED, so a value typed while one of those is selected would otherwise be dropped
 * on save with no indication this would happen).
 */
@Composable
private fun ManualEntryMetricsSection(
    state: ManualEntryDialogState,
    onMetricValueChanged: (MetricId, String) -> Unit,
) {
    if (statusAcceptsMetrics(state.status)) {
        state.metricInputs.forEach { metricInput ->
            StepwiseTextField(
                value = metricInput.value,
                onValueChange = { onMetricValueChanged(metricInput.metricId, it) },
                label = metricInput.label,
                isError = metricInput.error,
                supportingText =
                    if (metricInput.error) stringResource(R.string.manual_entry_metric_invalid) else null,
                keyboardType = KeyboardType.Number,
                modifier = Modifier.padding(top = StepwiseSpacing.small),
            )
        }
    } else if (state.metricInputs.isNotEmpty()) {
        Text(
            text = stringResource(R.string.manual_entry_metrics_not_applicable),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = StepwiseSpacing.small),
        )
    }
}

@Composable
private fun ManualEntryDateField(
    value: String,
    onValueChange: (String) -> Unit,
) {
    var pickerVisible by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        StepwiseTextField(
            value = value,
            onValueChange = onValueChange,
            label = stringResource(R.string.manual_entry_date_label),
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { pickerVisible = true }) {
            Icon(
                imageVector = Icons.Filled.DateRange,
                contentDescription = stringResource(R.string.manual_entry_date_picker_content_description),
            )
        }
    }
    if (pickerVisible) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { pickerVisible = false },
            confirmButton = {
                StepwiseTextButton(
                    text = stringResource(R.string.manual_entry_save),
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val localDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                            val isoDate = LocalDate(localDate.year, localDate.monthValue, localDate.dayOfMonth)
                            onValueChange(isoDate.toString())
                        }
                        pickerVisible = false
                    },
                )
            },
            dismissButton = {
                StepwiseTextButton(
                    text = stringResource(R.string.manual_entry_cancel),
                    onClick = { pickerVisible = false },
                )
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
