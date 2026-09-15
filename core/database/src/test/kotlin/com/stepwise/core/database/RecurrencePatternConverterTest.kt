package com.stepwise.core.database

import com.stepwise.core.model.RecurrencePattern
import kotlinx.datetime.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Test

// Pure string encode/decode, no Room/Android dependency exercised — a plain JUnit 4
// test with no Robolectric runner needed, unlike StepwiseDatabaseTest.
class RecurrencePatternConverterTest {
    @Test
    fun `daily round-trips`() {
        assertRoundTrips(RecurrencePattern.Daily)
    }

    @Test
    fun `weekly round-trips with multiple days`() {
        assertRoundTrips(RecurrencePattern.Weekly(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)))
    }

    @Test
    fun `every n days round-trips`() {
        assertRoundTrips(RecurrencePattern.EveryNDays(intervalDays = 3))
    }

    @Test
    fun `monthly round-trips`() {
        assertRoundTrips(RecurrencePattern.Monthly(dayOfMonth = 15))
    }

    @Test
    fun `custom rrule round-trips, including an embedded colon`() {
        assertRoundTrips(RecurrencePattern.Custom(rrule = "DTSTART:20260101T090000"))
    }

    private fun assertRoundTrips(pattern: RecurrencePattern) {
        val encoded = encodeRecurrencePattern(pattern)
        val decoded = decodeRecurrencePattern(encoded)

        assertEquals(pattern, decoded)
    }
}
