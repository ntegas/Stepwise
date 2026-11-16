package com.stepwise.feature.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stepwise.core.designsystem.component.StepwiseCard
import com.stepwise.core.designsystem.component.StepwiseEmptyState
import com.stepwise.core.designsystem.component.StepwiseErrorState
import com.stepwise.core.designsystem.component.StepwiseLinearProgressIndicator
import com.stepwise.core.designsystem.component.StepwiseLoadingState
import com.stepwise.core.designsystem.theme.StepwiseSpacing
import com.stepwise.core.model.TodayAgenda
import com.stepwise.core.model.TodayItem
import kotlinx.datetime.LocalTime

/** Matches the icon size `IconButton`'s default content occupies, so the pending swap doesn't shift the row. */
private val PendingIndicatorSize = 24.dp

@Composable
fun TodayRoute(viewModel: TodayViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pendingItemKeys by viewModel.pendingItemKeys.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val completionFailedMessage = stringResource(R.string.today_completion_failed)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is TodayEvent.CompletionFailed -> snackbarHostState.showSnackbar(completionFailedMessage)
            }
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        TodayScreen(
            state = state,
            pendingItemKeys = pendingItemKeys,
            onQuickDone = viewModel::onQuickDone,
            modifier = Modifier.padding(padding),
        )
    }
}

@Composable
fun TodayScreen(
    state: TodayUiState,
    pendingItemKeys: Set<String>,
    onQuickDone: (TodayItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        is TodayUiState.Loading -> {
            StepwiseLoadingState(modifier = modifier, label = stringResource(R.string.today_loading))
        }
        is TodayUiState.SignedOut -> Unit
        is TodayUiState.Error -> StepwiseErrorState(modifier = modifier, message = state.message)
        is TodayUiState.Content -> {
            TodayAgendaContent(
                modifier = modifier,
                agenda = state.agenda,
                pendingItemKeys = pendingItemKeys,
                onQuickDone = onQuickDone,
            )
        }
    }
}

@Composable
private fun TodayAgendaContent(
    agenda: TodayAgenda,
    pendingItemKeys: Set<String>,
    onQuickDone: (TodayItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (agenda.items.isEmpty() && agenda.overdue.isEmpty()) {
        StepwiseEmptyState(modifier = modifier, message = stringResource(R.string.today_empty))
        return
    }
    // Order matches /PRODUCT_CANON.md §9's own mockup: Focus, then the day's list, then
    // Progress as a trailing summary - an earlier draft showed Progress before the list it
    // summarizes (DEV-015 review, design reviewer, MEDIUM).
    LazyColumn(modifier = modifier.fillMaxSize().padding(StepwiseSpacing.medium)) {
        agenda.focus?.let { focus ->
            item {
                val text = stringResource(R.string.today_focus, focus.title)
                Text(text = text, style = MaterialTheme.typography.titleMedium)
            }
            item { Spacer(modifier = Modifier.height(StepwiseSpacing.small)) }
        }
        items(agenda.items, key = { it.stableKey() }) { todayItem ->
            val isPending = todayItem.stableKey() in pendingItemKeys
            TodayItemRow(item = todayItem, isPending = isPending, onQuickDone = onQuickDone)
        }
        if (agenda.totalCount > 0) {
            item {
                Text(
                    text = stringResource(R.string.today_progress, agenda.completedCount, agenda.totalCount),
                    modifier = Modifier.padding(top = StepwiseSpacing.small),
                )
                StepwiseLinearProgressIndicator(
                    progress = agenda.completedCount.toFloat() / agenda.totalCount,
                    modifier = Modifier.padding(top = StepwiseSpacing.extraSmall, bottom = StepwiseSpacing.medium),
                )
            }
        }
        if (agenda.overdue.isNotEmpty()) {
            item {
                val text = stringResource(R.string.today_overdue_header)
                Text(text = text, style = MaterialTheme.typography.titleMedium)
            }
            items(agenda.overdue, key = { it.stableKey() }) { todayItem ->
                val isPending = todayItem.stableKey() in pendingItemKeys
                TodayItemRow(item = todayItem, isPending = isPending, onQuickDone = onQuickDone)
            }
        }
    }
}

@Composable
private fun TodayItemRow(
    item: TodayItem,
    isPending: Boolean,
    onQuickDone: (TodayItem) -> Unit,
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
            if (isPending) {
                CircularProgressIndicator(modifier = Modifier.size(PendingIndicatorSize))
            } else {
                val icon = if (item.isCompleted) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle
                val descriptionRes =
                    if (item.isCompleted) R.string.today_item_completed else R.string.today_item_mark_done
                IconButton(onClick = { onQuickDone(item) }, enabled = !item.isCompleted) {
                    Icon(imageVector = icon, contentDescription = stringResource(descriptionRes))
                }
            }
        }
    }
}

private fun LocalTime.formatted(): String = "%02d:%02d".format(hour, minute)
