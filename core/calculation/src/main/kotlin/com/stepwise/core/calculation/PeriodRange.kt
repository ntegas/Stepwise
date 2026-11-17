package com.stepwise.core.calculation

import com.stepwise.core.common.endOfMonth
import com.stepwise.core.common.endOfYear
import com.stepwise.core.common.minusDays
import com.stepwise.core.common.plusDays
import com.stepwise.core.common.startOfMonth
import com.stepwise.core.common.startOfWeek
import com.stepwise.core.common.startOfYear
import kotlinx.datetime.LocalDate

private const val DAYS_IN_WEEK = 7
private const val DECEMBER = 12

/** Which bucketing produced a [PeriodRange] - needed so [PeriodRange.previous] knows the right calendar step. */
enum class PeriodKind { WEEK, MONTH, YEAR, ALL_TIME }

/**
 * An inclusive date range to compute Goal Progress/Execution Rate/Activity stats over
 * (`CALCULATION_ENGINE.md`'s "Period bucketing"). [start] `== null` means All-Time - every date
 * up to and including [end] qualifies, with no lower bound.
 *
 * **[previous] is deliberately calendar-aware, not a generic "subtract this period's own
 * day-count" shift**: a day-count shift is correct for [PeriodKind.WEEK] (always 7 days) but
 * wrong for [PeriodKind.MONTH]/[PeriodKind.YEAR], whose lengths vary (subtracting March's 31
 * days from March 1st lands in January, not February) - caught by this module's own unit tests
 * before it ever reached a caller.
 */
data class PeriodRange(
    val start: LocalDate?,
    val end: LocalDate,
    val kind: PeriodKind,
) {
    operator fun contains(date: LocalDate): Boolean = (start == null || date >= start) && date <= end

    /** The immediately preceding period of the same kind, for trend comparisons. All-Time has none. */
    fun previous(): PeriodRange? {
        val periodStart = start ?: return null
        return when (kind) {
            PeriodKind.ALL_TIME -> null
            PeriodKind.WEEK -> week(periodStart.minusDays(DAYS_IN_WEEK))
            PeriodKind.MONTH -> month(periodStart.minusOneMonth())
            PeriodKind.YEAR -> year(LocalDate(periodStart.year - 1, 1, 1))
        }
    }

    companion object {
        /** The Monday-start week (`:core:common`'s own convention) containing [anchor]. */
        fun week(anchor: LocalDate): PeriodRange {
            val weekStart = anchor.startOfWeek()
            return PeriodRange(start = weekStart, end = weekStart.plusDays(DAYS_IN_WEEK - 1), kind = PeriodKind.WEEK)
        }

        /** The calendar month containing [anchor]. */
        fun month(anchor: LocalDate): PeriodRange =
            PeriodRange(start = anchor.startOfMonth(), end = anchor.endOfMonth(), kind = PeriodKind.MONTH)

        /** The calendar year containing [anchor]. */
        fun year(anchor: LocalDate): PeriodRange =
            PeriodRange(start = anchor.startOfYear(), end = anchor.endOfYear(), kind = PeriodKind.YEAR)

        /** Every date up to and including [through] - no lower bound. */
        fun allTime(through: LocalDate): PeriodRange =
            PeriodRange(start = null, end = through, kind = PeriodKind.ALL_TIME)
    }
}

private fun LocalDate.minusOneMonth(): LocalDate =
    if (monthNumber == 1) LocalDate(year - 1, DECEMBER, 1) else LocalDate(year, monthNumber - 1, 1)
