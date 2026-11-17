package com.stepwise.feature.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stepwise.core.common.AppResult
import com.stepwise.core.common.Clock
import com.stepwise.core.common.minusDays
import com.stepwise.core.common.plusDays
import com.stepwise.core.common.startOfWeek
import com.stepwise.core.model.ExecutionStatus
import com.stepwise.core.model.PlanAgenda
import com.stepwise.core.model.TodayItem
import com.stepwise.core.model.UserId
import com.stepwise.domain.repository.AuthRepository
import com.stepwise.domain.usecase.CompleteOccurrenceCommand
import com.stepwise.domain.usecase.CompleteOccurrenceUseCase
import com.stepwise.domain.usecase.GetPlanRangeUseCase
import com.stepwise.domain.usecase.toCompletionTarget
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import javax.inject.Inject

/**
 * `:feature:plan`'s Week view (`/ROADMAP.md` DEV-017, first pass — see [PlanUiState]'s own KDoc
 * for the Day/Month/Year scope-narrowing). [GetPlanRangeUseCase] reactively drives [state] over
 * the currently-selected week (`weekStart`); [onPreviousWeek]/[onNextWeek] shift it by seven
 * days and simply re-subscribe, the same `flatMapLatest`-over-a-changing-key shape
 * `:feature:goals`'s own Goal Detail screen already established for a different changing key
 * (the selected Goal id).
 *
 * **Reschedule is the one write this screen makes, and it only ever calls
 * [CompleteOccurrenceUseCase]** (`/ROADMAP.md` DEV-017's own "reschedule только через canonical
 * use cases" acceptance criterion) — [toCompletionTarget] is the exact same mapping
 * `:feature:today`'s Quick Done already uses, shared from `:domain` rather than re-derived here,
 * so the two screens can never disagree about which occurrence a [TodayItem] row actually
 * represents.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlanViewModel
    @Inject
    constructor(
        private val authRepository: AuthRepository,
        private val getPlanRangeUseCase: GetPlanRangeUseCase,
        private val completeOccurrenceUseCase: CompleteOccurrenceUseCase,
        private val clock: Clock,
    ) : ViewModel() {
        private val weekStart = MutableStateFlow(today().startOfWeek())
        private var currentUserId: UserId? = null

        private val _state = MutableStateFlow<PlanUiState>(PlanUiState.Loading)
        val state: StateFlow<PlanUiState> = _state.asStateFlow()

        private val _events = MutableSharedFlow<PlanEvent>(extraBufferCapacity = 1)
        val events: SharedFlow<PlanEvent> = _events.asSharedFlow()

        private fun today(): LocalDate = clock.todayIn(TimeZone.currentSystemDefault())

        init {
            viewModelScope.launch {
                authRepository
                    .observeUserId()
                    .flatMapLatest { userId ->
                        currentUserId = userId
                        weekStart.flatMapLatest { start ->
                            if (userId == null) {
                                flowOf<AppResult<PlanAgenda>?>(null)
                            } else {
                                getPlanRangeUseCase.execute(userId, start..start.plusDays(DAYS_IN_WEEK - 1))
                            }
                        }
                    }.collect { result -> applyLoaded(result) }
            }
        }

        private fun applyLoaded(result: AppResult<PlanAgenda>?) {
            _state.value =
                when (result) {
                    null -> PlanUiState.SignedOut
                    is AppResult.Failure -> PlanUiState.Error
                    is AppResult.Success -> {
                        val current = _state.value
                        if (current is PlanUiState.Content) {
                            current.copy(agenda = result.value, today = today())
                        } else {
                            PlanUiState.Content(agenda = result.value, today = today())
                        }
                    }
                }
        }

        fun onPreviousWeek() {
            weekStart.value = weekStart.value.minusDays(DAYS_IN_WEEK)
        }

        fun onNextWeek() {
            weekStart.value = weekStart.value.plusDays(DAYS_IN_WEEK)
        }

        fun onJumpToToday() {
            weekStart.value = today().startOfWeek()
        }

        fun onRescheduleClicked(item: TodayItem) {
            updateContent {
                it.copy(
                    rescheduleTarget =
                        PlanRescheduleTarget(
                            title = item.title,
                            completionTarget = item.toCompletionTarget(),
                            isRecurring = item.recurrenceRuleId != null,
                        ),
                )
            }
        }

        fun onRescheduleDialogDismissed() {
            updateContent { it.copy(rescheduleTarget = null) }
        }

        fun onRescheduleDateSelected(newDate: LocalDate) {
            val content = _state.value as? PlanUiState.Content ?: return
            val target = content.rescheduleTarget ?: return
            updateContent { it.copy(rescheduleTarget = null) }
            viewModelScope.launch {
                val userId = currentUserId
                if (userId == null) {
                    _events.emit(PlanEvent.RescheduleFailed)
                    return@launch
                }
                val command =
                    CompleteOccurrenceCommand(
                        userId = userId,
                        target = target.completionTarget,
                        status = ExecutionStatus.RESCHEDULED,
                        rescheduledToDate = newDate,
                    )
                val result = completeOccurrenceUseCase.execute(command)
                if (result is AppResult.Failure) {
                    _events.emit(PlanEvent.RescheduleFailed)
                }
            }
        }

        private fun updateContent(transform: (PlanUiState.Content) -> PlanUiState.Content) {
            val current = _state.value
            if (current is PlanUiState.Content) {
                _state.value = transform(current)
            }
        }
    }

// startOfWeek()/plusDays()/minusDays() moved to :core:common's DateMath.kt at DEV-019, once
// :core:calculation needed the identical Monday-start week boundary - one shared implementation
// now, not a second hand-copied one.
private const val DAYS_IN_WEEK = 7
