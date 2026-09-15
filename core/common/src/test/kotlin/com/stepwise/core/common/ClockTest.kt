package com.stepwise.core.common

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock as KotlinClock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class ClockTest {

    @Test
    fun `todayIn returns the calendar date for the given time zone`() {
        // 2026-09-15T23:30:00Z is still 2026-09-15 in UTC but already 2026-09-16 in UTC+1.
        val fixedInstant = Instant.parse("2026-09-15T23:30:00Z")
        val clock: Clock = FakeClock(fixedInstant)

        assertEquals(LocalDate(2026, 9, 15), clock.todayIn(TimeZone.UTC))
        assertEquals(LocalDate(2026, 9, 16), clock.todayIn(TimeZone.of("UTC+1")))
    }

    @Test
    fun `advanceTo changes what now and todayIn report`() {
        val clock = FakeClock(Instant.parse("2026-01-01T00:00:00Z"))
        assertEquals(LocalDate(2026, 1, 1), clock.todayIn(TimeZone.UTC))

        clock.advanceTo(Instant.parse("2026-06-15T12:00:00Z"))

        assertEquals(Instant.parse("2026-06-15T12:00:00Z"), clock.now())
        assertEquals(LocalDate(2026, 6, 15), clock.todayIn(TimeZone.UTC))
    }

    @Test
    fun `SystemClock reports a plausible current instant`() {
        val before = KotlinClock.System.now()
        val after = SystemClock().now()
        // SystemClock must never return a fixed/fake value — it must track real time.
        assert(after >= before) { "SystemClock.now() returned a time before the test started" }
    }
}
