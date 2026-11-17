package com.stepwise.core.common

import kotlinx.datetime.LocalDate

private const val DAYS_IN_WEEK = 7

// A date's own days-since-Monday is ((epochDay - 4) mod 7), normalized into [0, 6] since Kotlin's
// `%` keeps the dividend's sign. Deliberately not `date.dayOfWeek.ordinal`, to avoid trusting an
// enum-ordering assumption without a real CI run verifying it (the exact reasoning this constant
// was first introduced under, in :feature:plan's own PlanViewModel.kt at DEV-017 - promoted here,
// not duplicated a second time, now that :core:calculation needs the identical week boundary).
private const val MONDAY_EPOCH_DAY_MOD = 4

/** The Monday that starts this date's week (inclusive) - the one week convention this app uses. */
fun LocalDate.startOfWeek(): LocalDate {
    val epochDay = toEpochDays()
    val daysSinceMonday = ((epochDay - MONDAY_EPOCH_DAY_MOD) % DAYS_IN_WEEK + DAYS_IN_WEEK) % DAYS_IN_WEEK
    return LocalDate.fromEpochDays(epochDay - daysSinceMonday)
}

fun LocalDate.plusDays(days: Int): LocalDate = LocalDate.fromEpochDays(toEpochDays() + days)

fun LocalDate.minusDays(days: Int): LocalDate = LocalDate.fromEpochDays(toEpochDays() - days)

/** The first day of this date's calendar month. */
fun LocalDate.startOfMonth(): LocalDate = LocalDate(year, monthNumber, 1)

/** The last day of this date's calendar month (28-31, leap years included). */
fun LocalDate.endOfMonth(): LocalDate {
    val firstOfNextMonth =
        if (monthNumber == 12) LocalDate(year + 1, 1, 1) else LocalDate(year, monthNumber + 1, 1)
    return firstOfNextMonth.minusDays(1)
}

/** The first day of this date's calendar year. */
fun LocalDate.startOfYear(): LocalDate = LocalDate(year, 1, 1)

/** The last day of this date's calendar year. */
fun LocalDate.endOfYear(): LocalDate = LocalDate(year, 12, 31)
