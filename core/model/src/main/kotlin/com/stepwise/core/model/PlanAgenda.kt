package com.stepwise.core.model

import kotlinx.datetime.LocalDate

/**
 * One calendar day on the Plan/Calendar view (`/ROADMAP.md` DEV-017) — reuses [TodayItem]
 * unchanged rather than inventing a parallel row shape, since [TodayItem] already carries
 * exactly what a calendar row needs: a display [TodayItem.date], an optional [TodayItem.time],
 * [TodayItem.isCompleted], and enough identity ([TodayItem.recurrenceRuleId]/
 * [TodayItem.occurrenceDate]) to act on the row later the same way `:feature:today` already
 * does. [date] here is this bucket's own calendar day; every [items] entry's own
 * [TodayItem.date] equals it (the grouping key), kept as an explicit field anyway so a caller
 * doesn't need a non-empty [items] list to know which day an otherwise-empty day represents.
 */
data class PlanDay(
    val date: LocalDate,
    val items: List<TodayItem>,
)

/**
 * The one unified read model [GetPlanRangeUseCase][com.stepwise.domain.usecase.GetPlanRangeUseCase]
 * produces for a queried date range — `/ARCHITECTURE.md` §13's "Today and Plan/Calendar call the
 * same occurrence-resolution function" extended one level up: this type is to a date *range* what
 * [TodayAgenda] is to a single day, built from the same per-day aggregation logic
 * [GetTodayAgendaUseCase][com.stepwise.domain.usecase.GetTodayAgendaUseCase] already established,
 * generalized from "today" to "every day in [range]" rather than re-derived from scratch.
 *
 * @param days one [PlanDay] per calendar day in the queried range, in date order, including days
 *   with an empty [PlanDay.items] list - a week view needs to render all seven days whether or
 *   not anything is scheduled on each one, not just the days that happen to have content.
 */
data class PlanAgenda(
    val range: ClosedRange<LocalDate>,
    val days: List<PlanDay>,
)
