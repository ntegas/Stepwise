package com.stepwise.core.model

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Instant

private val USER = UserId("user-1")

private fun rule(
    pattern: RecurrencePattern,
    startsOn: LocalDate,
    endsOn: LocalDate? = null,
): RecurrenceRule =
    RecurrenceRule(
        id = RecurrenceRuleId("rule-1"),
        userId = USER,
        ownerType = ExecutableKind.HABIT,
        ownerId = "habit-1",
        pattern = pattern,
        time = null,
        startsOn = startsOn,
        endsOn = endsOn,
        sync = SyncMetadata(updatedAt = Instant.fromEpochMilliseconds(0), version = 1, deletedAt = null),
    )

// Split into two classes at exactly the point SessionDao/SessionSyncDao (DEV-008) were split -
// detekt's TooManyFunctions (<=11 per class) is a real signal this generator's test coverage
// genuinely spans two concerns (per-pattern date math vs. range-bound/determinism/Custom-scope
// behavior), not a threshold worth raising.
class RecurrenceDateGeneratorPatternTest {
    @Test
    fun `Daily generates one occurrence per calendar day, with no timezone or DST dependency`() {
        // A DST transition weekend is just an ordinary pair of calendar dates to pure LocalDate
        // arithmetic - this test proves that directly rather than asserting it by assumption.
        // occurrenceDatesIn's own signature takes no TimeZone at all, so "timezone" and "DST" as
        // dev-plan-detailed.md's own named acceptance criteria are satisfied the same way: there
        // is nothing a caller could even pass that would change this function's output.
        val r = rule(RecurrencePattern.Daily, startsOn = LocalDate(2026, 3, 1))
        val dates = r.occurrenceDatesIn(LocalDate(2026, 3, 7)..LocalDate(2026, 3, 9))

        assertEquals(
            listOf(LocalDate(2026, 3, 7), LocalDate(2026, 3, 8), LocalDate(2026, 3, 9)),
            dates,
        )
    }

    @Test
    fun `Weekly across a DST transition weekend returns the same calendar dates any other week would`() {
        val r = rule(RecurrencePattern.Weekly(setOf(DayOfWeek.SUNDAY)), startsOn = LocalDate(2026, 1, 1))
        val dates = r.occurrenceDatesIn(LocalDate(2026, 3, 1)..LocalDate(2026, 3, 31))

        val expected =
            listOf(
                LocalDate(2026, 3, 1),
                LocalDate(2026, 3, 8),
                LocalDate(2026, 3, 15),
                LocalDate(2026, 3, 22),
                LocalDate(2026, 3, 29),
            )
        assertEquals(expected, dates)
    }

    @Test
    fun `EveryNDays starting Feb 25 in a leap year correctly lands on Feb 29`() {
        val r = rule(RecurrencePattern.EveryNDays(4), startsOn = LocalDate(2028, 2, 25))
        val dates = r.occurrenceDatesIn(LocalDate(2028, 2, 25)..LocalDate(2028, 3, 1))

        assertEquals(listOf(LocalDate(2028, 2, 25), LocalDate(2028, 2, 29)), dates)
    }

    @Test
    fun `EveryNDays with intervalDays 0 or negative throws, rather than an ArithmeticException from Long mod`() {
        val zero = rule(RecurrencePattern.EveryNDays(0), startsOn = LocalDate(2026, 1, 1))
        val negative = rule(RecurrencePattern.EveryNDays(-1), startsOn = LocalDate(2026, 1, 1))
        val range = LocalDate(2026, 1, 1)..LocalDate(2026, 1, 31)

        assertFailsWith<IllegalArgumentException> { zero.occurrenceDatesIn(range) }
        assertFailsWith<IllegalArgumentException> { negative.occurrenceDatesIn(range) }
    }

    @Test
    fun `a range spanning Feb 29 into March 1 in a leap year returns consecutive dates with no gap or duplicate`() {
        val r = rule(RecurrencePattern.Daily, startsOn = LocalDate(2028, 1, 1))
        val dates = r.occurrenceDatesIn(LocalDate(2028, 2, 28)..LocalDate(2028, 3, 1))

        assertEquals(listOf(LocalDate(2028, 2, 28), LocalDate(2028, 2, 29), LocalDate(2028, 3, 1)), dates)
    }

    @Test
    fun `Monthly(31) in April clamps to April 30, never skipped or wrapped into May`() {
        val r = rule(RecurrencePattern.Monthly(31), startsOn = LocalDate(2026, 1, 1))
        val dates = r.occurrenceDatesIn(LocalDate(2026, 4, 1)..LocalDate(2026, 4, 30))

        assertEquals(listOf(LocalDate(2026, 4, 30)), dates)
    }

    @Test
    fun `Monthly(31) in February applies the same clamp rule consistently in a leap and a non-leap year`() {
        val r = rule(RecurrencePattern.Monthly(31), startsOn = LocalDate(2026, 1, 1))

        val nonLeap = r.occurrenceDatesIn(LocalDate(2026, 2, 1)..LocalDate(2026, 2, 28))
        val leap = r.occurrenceDatesIn(LocalDate(2028, 2, 1)..LocalDate(2028, 2, 29))

        assertEquals(listOf(LocalDate(2026, 2, 28)), nonLeap)
        assertEquals(listOf(LocalDate(2028, 2, 29)), leap)
    }

    @Test
    fun `Monthly(29) in a non-leap-year February clamps to the 28th`() {
        val r = rule(RecurrencePattern.Monthly(29), startsOn = LocalDate(2026, 1, 1))
        val dates = r.occurrenceDatesIn(LocalDate(2026, 2, 1)..LocalDate(2026, 2, 28))

        assertEquals(listOf(LocalDate(2026, 2, 28)), dates)
    }
}

class RecurrenceDateGeneratorBoundsTest {
    @Test
    fun `a range entirely before a rule's startsOn returns no occurrences`() {
        val r = rule(RecurrencePattern.Daily, startsOn = LocalDate(2026, 6, 1))
        val dates = r.occurrenceDatesIn(LocalDate(2026, 1, 1)..LocalDate(2026, 1, 31))

        assertTrue(dates.isEmpty())
    }

    @Test
    fun `a range straddling startsOn returns only dates on or after startsOn`() {
        val r = rule(RecurrencePattern.Daily, startsOn = LocalDate(2026, 6, 15))
        val dates = r.occurrenceDatesIn(LocalDate(2026, 6, 10)..LocalDate(2026, 6, 20))

        assertEquals(LocalDate(2026, 6, 15), dates.first())
        assertEquals(LocalDate(2026, 6, 20), dates.last())
    }

    @Test
    fun `a range straddling endsOn returns only dates on or before endsOn`() {
        val r = rule(RecurrencePattern.Daily, startsOn = LocalDate(2026, 1, 1), endsOn = LocalDate(2026, 6, 15))
        val dates = r.occurrenceDatesIn(LocalDate(2026, 6, 10)..LocalDate(2026, 6, 20))

        assertEquals(LocalDate(2026, 6, 10), dates.first())
        assertEquals(LocalDate(2026, 6, 15), dates.last())
    }

    @Test
    fun `a range entirely after endsOn returns no occurrences`() {
        val r = rule(RecurrencePattern.Daily, startsOn = LocalDate(2026, 1, 1), endsOn = LocalDate(2026, 3, 1))
        val dates = r.occurrenceDatesIn(LocalDate(2026, 6, 1)..LocalDate(2026, 6, 30))

        assertTrue(dates.isEmpty())
    }

    @Test
    fun `a rule with no endsOn resolves a 5-year-out range correctly and terminates`() {
        val r = rule(RecurrencePattern.Monthly(15), startsOn = LocalDate(2026, 1, 1))
        val dates = r.occurrenceDatesIn(LocalDate(2031, 1, 1)..LocalDate(2031, 12, 31))

        assertEquals(12, dates.size)
        assertEquals(LocalDate(2031, 1, 15), dates.first())
        assertEquals(LocalDate(2031, 12, 15), dates.last())
    }

    @Test
    fun `resolving the same rule and range twice always yields identical dates - determinism, no duplicate engine`() {
        val pattern = RecurrencePattern.Weekly(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY))
        val r = rule(pattern, startsOn = LocalDate(2026, 1, 1))
        val range = LocalDate(2026, 5, 1)..LocalDate(2026, 5, 31)

        assertEquals(r.occurrenceDatesIn(range), r.occurrenceDatesIn(range))
    }

    @Test
    fun `Custom supports FREQ=YEARLY;BYMONTH;BYMONTHDAY for an annual date`() {
        val r = rule(RecurrencePattern.Custom("FREQ=YEARLY;BYMONTH=7;BYMONTHDAY=4"), startsOn = LocalDate(2020, 1, 1))
        val dates = r.occurrenceDatesIn(LocalDate(2026, 1, 1)..LocalDate(2028, 12, 31))

        assertEquals(listOf(LocalDate(2026, 7, 4), LocalDate(2027, 7, 4), LocalDate(2028, 7, 4)), dates)
    }

    @Test
    fun `Custom with an unsupported FREQ throws rather than silently producing wrong dates`() {
        val r = rule(RecurrencePattern.Custom("FREQ=MONTHLY;BYMONTHDAY=1"), startsOn = LocalDate(2026, 1, 1))

        assertFailsWith<IllegalArgumentException> {
            r.occurrenceDatesIn(LocalDate(2026, 1, 1)..LocalDate(2026, 12, 31))
        }
    }

    @Test
    fun `Custom with BYMONTH out of range throws, rather than an array-index crash`() {
        val tooHigh = rule(RecurrencePattern.Custom("FREQ=YEARLY;BYMONTH=13;BYMONTHDAY=1"), LocalDate(2026, 1, 1))
        val tooLow = rule(RecurrencePattern.Custom("FREQ=YEARLY;BYMONTH=0;BYMONTHDAY=1"), LocalDate(2026, 1, 1))
        val range = LocalDate(2026, 1, 1)..LocalDate(2026, 12, 31)

        assertFailsWith<IllegalArgumentException> { tooHigh.occurrenceDatesIn(range) }
        assertFailsWith<IllegalArgumentException> { tooLow.occurrenceDatesIn(range) }
    }
}
