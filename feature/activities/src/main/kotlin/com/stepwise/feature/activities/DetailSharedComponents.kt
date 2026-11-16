@file:OptIn(ExperimentalMaterial3Api::class)

package com.stepwise.feature.activities

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.stepwise.core.common.toDisplayString
import com.stepwise.core.designsystem.component.StepwiseButton
import com.stepwise.core.designsystem.component.StepwiseFilterChip
import com.stepwise.core.designsystem.component.StepwiseTextButton
import com.stepwise.core.designsystem.component.StepwiseTextField
import com.stepwise.core.designsystem.theme.StepwiseSpacing
import com.stepwise.core.model.ExecutionStatus
import com.stepwise.core.model.Goal
import com.stepwise.core.model.GoalActivityLink
import com.stepwise.core.model.GoalActivityLinkId
import com.stepwise.core.model.GoalId
import com.stepwise.core.model.Metric
import com.stepwise.core.model.MetricId
import com.stepwise.core.model.Session
import kotlinx.datetime.LocalDate
import java.time.Instant
import java.time.ZoneOffset

@Composable
internal fun GoalLinksSection(
    links: List<GoalActivityLink>,
    goals: List<Goal>,
    metrics: List<Metric>,
    onLinkGoalClicked: () -> Unit,
    onUnlinkClicked: (GoalActivityLinkId) -> Unit,
) {
    Text(
        text = stringResource(R.string.detail_goal_links_header),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = StepwiseSpacing.large),
    )
    if (links.isEmpty()) {
        Text(
            text = stringResource(R.string.detail_goal_links_empty),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = StepwiseSpacing.small),
        )
    } else {
        links.forEach { link ->
            GoalLinkRow(
                link = link,
                goalTitle = goals.firstOrNull { it.id == link.goalId }?.title,
                metricLabel = metrics.firstOrNull { it.id == link.metricId }?.let { "${it.key} (${it.canonicalUnit})" },
                onUnlinkClicked = onUnlinkClicked,
            )
        }
    }
    StepwiseTextButton(
        text = stringResource(R.string.detail_link_goal_action),
        onClick = onLinkGoalClicked,
        modifier = Modifier.padding(top = StepwiseSpacing.small),
    )
}

@Composable
private fun GoalLinkRow(
    link: GoalActivityLink,
    goalTitle: String?,
    metricLabel: String?,
    onUnlinkClicked: (GoalActivityLinkId) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = StepwiseSpacing.extraSmall),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(text = goalTitle ?: link.goalId.value, style = MaterialTheme.typography.bodyLarge)
            metricLabel?.let { Text(text = it, style = MaterialTheme.typography.bodySmall) }
        }
        StepwiseTextButton(
            text = stringResource(R.string.detail_unlink_action),
            onClick = { onUnlinkClicked(link.id) },
        )
    }
}

@Composable
internal fun SessionHistorySection(
    sessions: List<Session>,
    onLogSessionClicked: () -> Unit,
) {
    Text(
        text = stringResource(R.string.detail_session_history_header),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = StepwiseSpacing.large),
    )
    val sorted = sessions.sortedByDescending { it.date.toEpochDays() }
    if (sorted.isEmpty()) {
        Text(
            text = stringResource(R.string.detail_session_history_empty),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = StepwiseSpacing.small),
        )
    } else {
        sorted.forEach { session -> SessionRow(session = session) }
    }
    StepwiseTextButton(
        text = stringResource(R.string.detail_log_session_action),
        onClick = onLogSessionClicked,
        modifier = Modifier.padding(top = StepwiseSpacing.small),
    )
}

@Composable
private fun SessionRow(session: Session) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = StepwiseSpacing.extraSmall),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = session.date.toDisplayString(), style = MaterialTheme.typography.bodyMedium)
        Text(
            text = stringResource(executionStatusLabelRes(session.status)),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

internal fun executionStatusLabelRes(status: ExecutionStatus): Int =
    when (status) {
        ExecutionStatus.DONE -> R.string.execution_status_done
        ExecutionStatus.PARTIAL -> R.string.execution_status_partial
        ExecutionStatus.MISSED -> R.string.execution_status_missed
        ExecutionStatus.RESCHEDULED -> R.string.execution_status_rescheduled
        ExecutionStatus.IN_PROGRESS -> R.string.execution_status_in_progress
        ExecutionStatus.CANCELLED -> R.string.execution_status_cancelled
    }

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
                imageVector = androidx.compose.material.icons.Icons.Filled.DateRange,
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

@Composable
internal fun LinkGoalDialog(
    state: LinkGoalDialogState,
    goals: List<Goal>,
    metrics: List<Metric>,
    onGoalSelected: (GoalId) -> Unit,
    onMetricSelected: (MetricId) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.link_goal_dialog_title)) },
        text = {
            Column {
                Text(text = stringResource(R.string.link_goal_goal_label), style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(StepwiseSpacing.extraSmall)) {
                    items(goals, key = { it.id.value }) { goal ->
                        StepwiseFilterChip(
                            label = goal.title,
                            selected = state.selectedGoalId == goal.id,
                            onSelectedChange = { onGoalSelected(goal.id) },
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.link_goal_metric_label),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = StepwiseSpacing.small),
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(StepwiseSpacing.extraSmall)) {
                    items(metrics, key = { it.id.value }) { metric ->
                        StepwiseFilterChip(
                            label = "${metric.key} (${metric.canonicalUnit})",
                            selected = state.selectedMetricId == metric.id,
                            onSelectedChange = { onMetricSelected(metric.id) },
                        )
                    }
                }
                if (state.hasError) {
                    Text(
                        text = stringResource(R.string.link_goal_error_generic),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = StepwiseSpacing.small),
                    )
                }
            }
        },
        confirmButton = {
            StepwiseButton(
                text = stringResource(R.string.link_goal_save),
                onClick = onConfirm,
                enabled = !state.isSubmitting && state.selectedGoalId != null && state.selectedMetricId != null,
            )
        },
        dismissButton = {
            StepwiseTextButton(text = stringResource(R.string.link_goal_cancel), onClick = onDismiss)
        },
    )
}
