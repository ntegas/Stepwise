package com.stepwise.core.common

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Deterministic [Clock] for tests (DEVELOPMENT_PROTOCOL.md rule 41 — centralized fake
 * test utilities, not reinvented per module/test). Lives in `testFixtures` so every
 * future module that depends on `:core:common` can reuse this one fake instead of
 * writing its own.
 */
class FakeClock(private var instant: Instant) : Clock {
    override fun now(): Instant = instant

    @OptIn(ExperimentalTime::class)
    override fun todayIn(timeZone: TimeZone): LocalDate =
        instant.toLocalDateTime(timeZone).date

    fun advanceTo(newInstant: Instant) {
        instant = newInstant
    }
}
