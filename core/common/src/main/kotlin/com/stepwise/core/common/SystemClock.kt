package com.stepwise.core.common

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.time.Clock as KotlinClock

/**
 * The real, production [Clock] — the only place in the app that reads actual system
 * time. Uses `kotlin.time.Clock`/`Instant` (kotlin-stdlib) directly rather than the
 * deprecated `kotlinx.datetime.Clock`/`Instant` aliases that `kotlinx-datetime` 0.7+
 * superseded.
 */
class SystemClock : Clock {
    @OptIn(ExperimentalTime::class)
    override fun now(): Instant = KotlinClock.System.now()

    @OptIn(ExperimentalTime::class)
    override fun todayIn(timeZone: TimeZone): LocalDate = now().toLocalDateTime(timeZone).date
}
