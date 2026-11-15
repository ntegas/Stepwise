package com.stepwise.core.model

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

/**
 * Computes the calendar dates a [RecurrenceRule] implies within [range] — the "occurrence
 * generation" half of DEV-010's job (`/ARCHITECTURE.md` §13 / `/DATA_MODEL.md` §59:
 * "Occurrences... are computed from this rule at read time, not pre-materialized as rows").
 *
 * **No timezone policy is documented anywhere in `/ARCHITECTURE.md`/`/DATA_MODEL.md` for
 * recurrence generation** — this function's own design is the answer rather than a gap left
 * unstated: it is pure calendar-date arithmetic on [LocalDate] alone, with no
 * [kotlinx.datetime.TimeZone]/[kotlin.time.Instant] conversion anywhere, so a DST transition
 * never enters the computation at all. Turning a generated [LocalDate] plus [RecurrenceRule.time]
 * into an actual reminder instant is a separate, later concern (Notifications/Reminder
 * scheduling) this function deliberately does not attempt.
 *
 * The result is clamped to [RecurrenceRule.startsOn]/[RecurrenceRule.endsOn] even when [range]
 * is wider than that bound, and to `emptyList()` when [range] doesn't overlap the rule's bound
 * at all.
 *
 * @throws IllegalArgumentException if [RecurrencePattern.Custom]'s `rrule` string isn't the
 *   narrow, deliberately-scoped subset this function supports — see [customDates]'s own KDoc.
 *   `:core:model` has no [com.stepwise.core.common.AppResult] of its own (zero project
 *   dependencies by design, see `build.gradle.kts`), so a caller at the `:domain` layer catches
 *   this at the boundary, the same pattern `SyncEngine` already uses for a comparable reason.
 */
fun RecurrenceRule.occurrenceDatesIn(range: ClosedRange<LocalDate>): List<LocalDate> {
    val clampedStart = maxOf(range.start, startsOn)
    val clampedEnd = endsOn?.let { minOf(range.endInclusive, it) } ?: range.endInclusive
    if (clampedStart > clampedEnd) return emptyList()
    val effectiveRange = clampedStart..clampedEnd
    return when (pattern) {
        is RecurrencePattern.Daily -> dailyDates(effectiveRange)
        is RecurrencePattern.Weekly -> weeklyDates(effectiveRange, pattern.daysOfWeek)
        is RecurrencePattern.EveryNDays -> everyNDaysDates(effectiveRange, startsOn, pattern.intervalDays)
        is RecurrencePattern.Monthly -> monthlyDates(effectiveRange, pattern.dayOfMonth)
        is RecurrencePattern.Custom -> customDates(effectiveRange, pattern.rrule)
    }
}

private fun dailyDates(range: ClosedRange<LocalDate>): List<LocalDate> {
    val startEpoch = range.start.toEpochDays()
    val endEpoch = range.endInclusive.toEpochDays()
    return (startEpoch..endEpoch).map { LocalDate.fromEpochDays(it) }
}

private fun weeklyDates(
    range: ClosedRange<LocalDate>,
    daysOfWeek: Set<DayOfWeek>,
): List<LocalDate> = dailyDates(range).filter { it.dayOfWeek in daysOfWeek }

private fun everyNDaysDates(
    range: ClosedRange<LocalDate>,
    anchor: LocalDate,
    intervalDays: Int,
): List<LocalDate> {
    // A real crash found at DEV-010 review (logic reviewer): intervalDays = 0 reached Long.mod's
    // divide-by-zero as an uncaught ArithmeticException, bypassing ResolveOccurrencesUseCase's
    // catch (which only catches IllegalArgumentException) entirely. Validated here instead, so
    // every invalid-input path throws the one exception type the :domain boundary actually catches.
    require(intervalDays > 0) { "EveryNDays.intervalDays must be positive, got $intervalDays" }
    val anchorEpoch = anchor.toEpochDays()
    val interval = intervalDays.toLong()
    return dailyDates(range).filter { (it.toEpochDays() - anchorEpoch).mod(interval) == 0L }
}

/**
 * [dayOfMonth] greater than the current month's actual length clamps to that month's last day
 * (e.g. `dayOfMonth = 31` in April lands on April 30) rather than skipping the month or
 * overflowing into the next one — a deliberate, documented choice (`/ARCHITECTURE.md`/
 * `/DATA_MODEL.md` specify no rule for this at all; clamp-to-last-day is the more common
 * convention of the two plausible readings, chosen here rather than left for a future task to
 * discover was never decided).
 */
private fun monthlyDates(
    range: ClosedRange<LocalDate>,
    dayOfMonth: Int,
): List<LocalDate> {
    val results = mutableListOf<LocalDate>()
    var year = range.start.year
    var month = range.start.monthNumber
    while (true) {
        val candidate = LocalDate(year, month, minOf(dayOfMonth, daysInMonth(year, month)))
        if (candidate > range.endInclusive) break
        if (candidate >= range.start) results += candidate
        if (month == DECEMBER) {
            month = JANUARY
            year += 1
        } else {
            month += 1
        }
    }
    return results
}

/**
 * [RecurrencePattern.Custom]'s "bounded custom form" (`/ARCHITECTURE.md` §13) has no concrete
 * grammar documented anywhere — a genuinely open scope question, not silently assumed. Rather
 * than build a general RRULE parser (unbounded scope for this task) or leave `Custom` entirely
 * unimplemented (silently dropping a `RecurrencePattern` variant DEV-004 already shipped),
 * this supports exactly one narrow, explicit shape covering the one common recurrence this
 * project's other four variants cannot express at all: an annual date (`FREQ=YEARLY;BYMONTH=m;
 * BYMONTHDAY=d`, e.g. a birthday/anniversary). Any other `rrule` string throws rather than
 * silently producing wrong or empty dates. Tracked as a disclosed scope limitation, not
 * verification debt — see `/PROJECT_STATE.md`'s DEV-010 findings.
 */
private fun customDates(
    range: ClosedRange<LocalDate>,
    rrule: String,
): List<LocalDate> {
    val tokens = rrule.split(";").mapNotNull { token -> token.split("=", limit = 2).takeIf { it.size == 2 } }
    val params = tokens.associate { it[0].trim() to it[1].trim() }
    require(params["FREQ"] == "YEARLY") { "Unsupported Custom rrule (only FREQ=YEARLY is implemented): $rrule" }
    val month = requireNotNull(params["BYMONTH"]?.toIntOrNull()) { "Custom rrule missing BYMONTH: $rrule" }
    val day = requireNotNull(params["BYMONTHDAY"]?.toIntOrNull()) { "Custom rrule missing BYMONTHDAY: $rrule" }
    // A real crash found at DEV-010 review (logic reviewer): an out-of-range BYMONTH indexed
    // MONTH_LENGTHS_NON_LEAP out of bounds (ArrayIndexOutOfBoundsException) BEFORE LocalDate's own
    // constructor ever got a chance to validate it - bypassing ResolveOccurrencesUseCase's catch
    // (IllegalArgumentException only) the same way EveryNDays' zero-interval case did.
    require(month in JANUARY..DECEMBER) { "Custom rrule BYMONTH out of range (1-12): $rrule" }
    require(day in 1..DAYS_31) { "Custom rrule BYMONTHDAY out of range (1-31): $rrule" }
    val results = mutableListOf<LocalDate>()
    var year = range.start.year
    while (true) {
        val candidate = LocalDate(year, month, minOf(day, daysInMonth(year, month)))
        if (candidate > range.endInclusive) break
        if (candidate >= range.start) results += candidate
        year += 1
    }
    return results
}

private fun daysInMonth(
    year: Int,
    month: Int,
): Int {
    val base = MONTH_LENGTHS_NON_LEAP[month - 1]
    return if (month == FEBRUARY && isLeapYear(year)) DAYS_29 else base
}

private fun isLeapYear(year: Int): Boolean =
    year % QUADRENNIAL == 0 && (year % CENTENNIAL != 0 || year % QUATERCENTENNIAL == 0)

private const val JANUARY = 1
private const val FEBRUARY = 2
private const val DECEMBER = 12

private const val DAYS_31 = 31
private const val DAYS_30 = 30
private const val DAYS_29 = 29
private const val DAYS_28 = 28

// January..December, in order - index 0 is January's length.
private val MONTH_LENGTHS_NON_LEAP =
    intArrayOf(
        DAYS_31,
        DAYS_28,
        DAYS_31,
        DAYS_30,
        DAYS_31,
        DAYS_30,
        DAYS_31,
        DAYS_31,
        DAYS_30,
        DAYS_31,
        DAYS_30,
        DAYS_31,
    )

private const val QUADRENNIAL = 4
private const val CENTENNIAL = 100
private const val QUATERCENTENNIAL = 400
