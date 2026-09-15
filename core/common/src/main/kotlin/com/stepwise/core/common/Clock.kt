package com.stepwise.core.common

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

/**
 * The single source of "what time is it" for the whole app (DEVELOPMENT_PROTOCOL.md
 * rule 11). No domain, calculation, or use-case code calls a raw system time API
 * directly — everything goes through an injected [Clock] so time-dependent
 * behavior (day/week/month boundaries, recurrence, pace/forecast) is deterministic
 * and fakeable in tests.
 */
interface Clock {
    /** The current instant, independent of any calendar/timezone interpretation. */
    fun now(): Instant

    /** The current calendar date in the given time zone — the basis for day/week/month/year boundaries. */
    fun todayIn(timeZone: TimeZone): LocalDate
}
