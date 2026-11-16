@file:OptIn(ExperimentalMaterial3Api::class)

package com.stepwise.feature.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stepwise.core.common.toDisplayString
import com.stepwise.core.designsystem.component.StepwiseCard
import com.stepwise.core.designsystem.component.StepwiseErrorState
import com.stepwise.core.designsystem.component.StepwiseLoadingState
import com.stepwise.core.designsystem.component.StepwiseTextButton
import com.stepwise.core.designsystem.component.StepwiseTopBar
import com.stepwise.core.designsystem.theme.StepwiseSpacing
import com.stepwise.core.model.PlanAgenda
import com.stepwise.core.model.PlanDay
import com.stepwise.core.model.TodayItem
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import java.time.Instant
import java.time.ZoneOffset

@Composable
fun PlanRoute(viewModel: PlanViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = { StepwiseTopBar(title = stringResource(R.string.plan_title)) },
    ) { padding ->
        PlanScreen(
            state = state,
            modifier = Modifier.padding(padding),
            onPreviousWeek = viewModel::onPreviousWeek,
            onNextWeek = viewModel::onNextWeek,
            onRescheduleClicked = viewModel::onRescheduleClicked,
            onRescheduleDialogDismissed = viewModel::onRescheduleDialogDismissed,
            onRescheduleDateSelected = viewModel::onRescheduleDateSelected,
        )
    }
}

@Composable
fun PlanScreen(
    state: PlanUiState,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onRescheduleClicked: (TodayItem) -> Unit,
    onRescheduleDialogDismissed: () -> Unit,
    onRescheduleDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        is PlanUiState.Loading -> {
            StepwiseLoadingState(modifier = modifier, label = stringResource(R.string.plan_loading))
        }
        is PlanUiState.SignedOut -> Unit
        is PlanUiState.Error -> StepwiseErrorState(modifier = modifier, message = stringResource(R.string.plan_error))
        is PlanUiState.Content -> {
            PlanContent(
                state = state,
                modifier = modifier,
                onPreviousWeek = onPreviousWeek,
                onNextWeek = onNextWeek,
                onRescheduleClicked = onRescheduleClicked,
                onRescheduleDialogDismissed = onRescheduleDialogDismissed,
                onRescheduleDateSelected = onRescheduleDateSelected,
            )
        }
    }
}

@Composable
private fun PlanContent(
    state: PlanUiState.Content,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onRescheduleClicked: (TodayItem) -> Unit,
    onRescheduleDialogDismissed: () -> Unit,
    onRescheduleDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        WeekHeader(
            agenda = state.agenda,
            onPreviousWeek = onPreviousWeek,
            onNextWeek = onNextWeek,
        )
        if (state.actionFailed) {
            Text(
                text = stringResource(R.string.plan_action_error),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = StepwiseSpacing.medium, vertical = StepwiseSpacing.small),
            )
        }
        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = StepwiseSpacing.medium)) {
            items(state.agenda.days, key = { it.date.toString() }) { day ->
                PlanDayRow(day = day, onRescheduleClicked = onRescheduleClicked)
            }
        }
    }
    val target = state.rescheduleTarget
    if (target != null) {
        RescheduleDialog(
            title = target.title,
            onDismiss = onRescheduleDialogDismissed,
            onDateSelected = onRescheduleDateSelected,
        )
    }
}

@Composable
private fun WeekHeader(
    agenda: PlanAgenda,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(StepwiseSpacing.medium),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPreviousWeek) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.plan_previous_week_content_description),
            )
        }
        Text(
            text = "${agenda.range.start.toDisplayString()} – ${agenda.range.endInclusive.toDisplayString()}",
            style = MaterialTheme.typography.titleMedium,
        )
        IconButton(onClick = onNextWeek) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.plan_next_week_content_description),
            )
        }
    }
}

@Composable
private fun PlanDayRow(
    day: PlanDay,
    onRescheduleClicked: (TodayItem) -> Unit,
) {
    Text(
        text = day.date.toDisplayString(),
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(top = StepwiseSpacing.small),
    )
    if (day.items.isEmpty()) {
        Text(
            text = stringResource(R.string.plan_day_empty),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(bottom = StepwiseSpacing.small),
        )
    } else {
        day.items.forEach { item -> PlanItemRow(item = item, onRescheduleClicked = onRescheduleClicked) }
    }
}

@Composable
private fun PlanItemRow(
    item: TodayItem,
    onRescheduleClicked: (TodayItem) -> Unit,
) {
    StepwiseCard(modifier = Modifier.fillMaxWidth().padding(bottom = StepwiseSpacing.small)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = item.time?.let { "${it.formatted()} — ${item.title}" } ?: item.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            IconButton(onClick = { onRescheduleClicked(item) }) {
                Icon(
                    imageVector = Icons.Filled.DateRange,
                    contentDescription = stringResource(R.string.plan_reschedule_content_description),
                )
            }
        }
    }
}

@Composable
private fun RescheduleDialog(
    title: String,
    onDismiss: () -> Unit,
    onDateSelected: (LocalDate) -> Unit,
) {
    val datePickerState = rememberDatePickerState()
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            StepwiseTextButton(
                text = stringResource(R.string.plan_reschedule_confirm),
                onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val localDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        onDateSelected(LocalDate(localDate.year, localDate.monthValue, localDate.dayOfMonth))
                    }
                },
            )
        },
        dismissButton = {
            StepwiseTextButton(text = stringResource(R.string.plan_reschedule_cancel), onClick = onDismiss)
        },
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = StepwiseSpacing.medium),
        )
        DatePicker(state = datePickerState)
    }
}

private fun LocalTime.formatted(): String = "%02d:%02d".format(hour, minute)
