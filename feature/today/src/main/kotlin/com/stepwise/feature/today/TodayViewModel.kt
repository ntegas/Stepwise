package com.stepwise.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stepwise.core.common.AppResult
import com.stepwise.core.model.ExecutionStatus
import com.stepwise.core.model.TodayAgenda
import com.stepwise.core.model.TodayItem
import com.stepwise.core.model.UserId
import com.stepwise.domain.repository.AuthRepository
import com.stepwise.domain.usecase.CompleteOccurrenceCommand
import com.stepwise.domain.usecase.CompleteOccurrenceUseCase
import com.stepwise.domain.usecase.CompletionTarget
import com.stepwise.domain.usecase.GetTodayAgendaUseCase
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
import kotlinx.datetime.TimeZone
import javax.inject.Inject

/**
 * The exact "Today → item → execute → canonical Session" flow `/ROADMAP.md` DEV-015 names —
 * [GetTodayAgendaUseCase] (DEV-014) reactively drives [state], a tap routes through
 * [CompleteOccurrenceUseCase] (DEV-009) and never writes anything itself. No manual re-fetch
 * after a completion: [GetTodayAgendaUseCase]'s own `combine` over every underlying repository's
 * `observeAll` recomputes automatically once the written `Session` row reaches the same Room
 * database, the same reactivity Room's own invalidation tracking already gives every other screen
 * in this app for free.
 *
 * **`"Goal progress update → Progress representation"` stops short of DEV-015's own ROADMAP.md
 * one-liner** — `TodayAgenda.completedCount`/`totalCount` (Today-local) is all `events`/`state`
 * ever surface; no Goal-level progress number exists anywhere yet, since DEV-019's Calculation
 * Engine hasn't been built. Disclosed, not silently narrowed — tracked in `PROJECT_STATE.md`.
 *
 * **[onQuickDone] guards against a double-tap firing two concurrent completions** for the same
 * item (DEV-015 review, adversarial reviewer, MEDIUM) — [pendingItemKeys] tracks which items
 * already have a completion in flight and ignores a repeat tap until it resolves, closing the
 * realistic UI-driven race even though `item.isCompleted` alone doesn't update until the agenda's
 * own reactive round trip completes; `TodayScreen` also reads [pendingItemKeys] to show a pending
 * indicator instead of the plain icon while a tap is in flight (DEV-015 review, user-flow
 * reviewer, MEDIUM — a tap previously gave no feedback until the full round trip completed). A
 * failed completion is surfaced via [events] (DEV-015 review, user-flow reviewer, HIGH — the
 * earlier draft discarded the `AppResult` entirely, so a failed tap looked identical to a
 * successful one).
 *
 * `TimeZone.currentSystemDefault()` is the one place this app reads the device's own timezone —
 * everywhere else timezone-dependent (DEV-014's Use Case itself) takes it as a parameter rather
 * than reading platform state directly (DEVELOPMENT_PROTOCOL.md rule 11's "no raw system time API
 * outside Clock" extends the same way to timezone: a UI layer reading it once at this one
 * boundary, not a second place quietly re-deriving it).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TodayViewModel
    @Inject
    constructor(
        private val authRepository: AuthRepository,
        private val getTodayAgendaUseCase: GetTodayAgendaUseCase,
        private val completeOccurrenceUseCase: CompleteOccurrenceUseCase,
    ) : ViewModel() {
        private val _state = MutableStateFlow<TodayUiState>(TodayUiState.Loading)
        val state: StateFlow<TodayUiState> = _state.asStateFlow()

        private val _events = MutableSharedFlow<TodayEvent>(extraBufferCapacity = 1)
        val events: SharedFlow<TodayEvent> = _events.asSharedFlow()

        private var currentUserId: UserId? = null
        private val _pendingItemKeys = MutableStateFlow<Set<String>>(emptySet())
        val pendingItemKeys: StateFlow<Set<String>> = _pendingItemKeys.asStateFlow()

        init {
            viewModelScope.launch {
                authRepository
                    .observeUserId()
                    .flatMapLatest { userId ->
                        currentUserId = userId
                        if (userId == null) {
                            flowOf<AppResult<TodayAgenda>?>(null)
                        } else {
                            getTodayAgendaUseCase.execute(userId, TimeZone.currentSystemDefault())
                        }
                    }.collect { result -> _state.value = result.toUiState() }
            }
        }

        private fun AppResult<TodayAgenda>?.toUiState(): TodayUiState =
            when (this) {
                null -> TodayUiState.SignedOut
                is AppResult.Success -> TodayUiState.Content(value)
                is AppResult.Failure -> TodayUiState.Error(error.message)
            }

        /** "Quick Done" (`/PRODUCT_CANON.md` §9) — defaults to [ExecutionStatus.DONE], no metric entry. */
        fun onQuickDone(item: TodayItem) {
            val userId = currentUserId ?: return
            val key = item.stableKey()
            if (key in _pendingItemKeys.value) return
            _pendingItemKeys.value = _pendingItemKeys.value + key
            viewModelScope.launch {
                try {
                    val result =
                        completeOccurrenceUseCase.execute(
                            CompleteOccurrenceCommand(
                                userId = userId,
                                target = item.toCompletionTarget(),
                                status = ExecutionStatus.DONE,
                            ),
                        )
                    if (result is AppResult.Failure) {
                        _events.emit(TodayEvent.CompletionFailed)
                    }
                } finally {
                    _pendingItemKeys.value = _pendingItemKeys.value - key
                }
            }
        }

        private fun TodayItem.toCompletionTarget(): CompletionTarget {
            val ruleId = recurrenceRuleId
            return if (ruleId != null) {
                CompletionTarget.Recurring(sourceType, sourceId, occurrenceDate ?: date, ruleId)
            } else {
                CompletionTarget.OneOff(sourceType, sourceId, date)
            }
        }
    }

/** A one-off, non-persisted signal for `TodayScreen` to show (a Snackbar) and then forget. */
sealed interface TodayEvent {
    data object CompletionFailed : TodayEvent
}
