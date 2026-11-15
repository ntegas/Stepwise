package com.stepwise.feature.today

import com.stepwise.core.model.TodayAgenda
import com.stepwise.core.model.TodayItem

/** `:feature:today`'s own screen state — a loading/error/signed-out moment is always explicit. */
sealed interface TodayUiState {
    data object Loading : TodayUiState

    data object SignedOut : TodayUiState

    data class Content(
        val agenda: TodayAgenda,
    ) : TodayUiState

    data class Error(
        val message: String,
    ) : TodayUiState
}

/**
 * The one stable identity for a [TodayItem], shared between [TodayViewModel] (tracking an
 * in-flight completion) and `TodayScreen` (`LazyColumn` keys) — includes
 * [TodayItem.recurrenceRuleId]/[TodayItem.occurrenceDate], not just `kind`+`sourceId`, since one
 * owner can carry more than one [com.stepwise.core.model.RecurrenceRule]
 * (`/DATA_MODEL.md`'s "tasks/activities/habits 1─* schedules"), which would otherwise collide
 * (DEV-015 review, logic reviewer, MEDIUM — latent today, since no shipped UI yet lets a user
 * create a second rule for one owner, but a real landmine once one does).
 */
fun TodayItem.stableKey(): String = "$kind-$sourceId-${recurrenceRuleId?.value ?: ""}-${occurrenceDate ?: date}"
