package com.stepwise.core.calculation

import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PeriodRangeTest {
    @Test
    fun `week starts on Monday and spans exactly 7 days`() {
        // 2026-11-17 is a Tuesday.
        val range = PeriodRange.week(LocalDate(2026, 11, 17))

        assertEquals(LocalDate(2026, 11, 16), range.start)
        assertEquals(LocalDate(2026, 11, 22), range.end)
    }

    @Test
    fun `week anchored on a Monday starts on itself`() {
        val range = PeriodRange.week(LocalDate(2026, 11, 16))

        assertEquals(LocalDate(2026, 11, 16), range.start)
    }

    @Test
    fun `month spans the full calendar month including a leap February`() {
        val range = PeriodRange.month(LocalDate(2028, 2, 15))

        assertEquals(LocalDate(2028, 2, 1), range.start)
        assertEquals(LocalDate(2028, 2, 29), range.end)
    }

    @Test
    fun `year spans January 1st through December 31st`() {
        val range = PeriodRange.year(LocalDate(2026, 6, 1))

        assertEquals(LocalDate(2026, 1, 1), range.start)
        assertEquals(LocalDate(2026, 12, 31), range.end)
    }

    @Test
    fun `allTime has no lower bound`() {
        val range = PeriodRange.allTime(LocalDate(2026, 11, 17))

        assertNull(range.start)
        assertTrue(LocalDate(2000, 1, 1) in range)
        assertTrue(LocalDate(2026, 11, 17) in range)
        assertFalse(LocalDate(2026, 11, 18) in range)
    }

    @Test
    fun `contains respects both inclusive boundaries`() {
        val range = PeriodRange.week(LocalDate(2026, 11, 17))

        assertTrue(LocalDate(2026, 11, 16) in range)
        assertTrue(LocalDate(2026, 11, 22) in range)
        assertFalse(LocalDate(2026, 11, 15) in range)
        assertFalse(LocalDate(2026, 11, 23) in range)
    }

    @Test
    fun `previous week is the 7 days immediately before this one`() {
        val range = PeriodRange.week(LocalDate(2026, 11, 17))

        val previous = range.previous()

        assertEquals(LocalDate(2026, 11, 9), previous?.start)
        assertEquals(LocalDate(2026, 11, 15), previous?.end)
    }

    @Test
    fun `previous month is the immediately preceding calendar month`() {
        val range = PeriodRange.month(LocalDate(2026, 3, 15))

        val previous = range.previous()

        assertEquals(LocalDate(2026, 2, 1), previous?.start)
        assertEquals(LocalDate(2026, 2, 28), previous?.end)
    }

    @Test
    fun `previous month wraps from January back to December of the prior year`() {
        val range = PeriodRange.month(LocalDate(2026, 1, 15))

        val previous = range.previous()

        assertEquals(LocalDate(2025, 12, 1), previous?.start)
        assertEquals(LocalDate(2025, 12, 31), previous?.end)
    }

    @Test
    fun `previous year is the immediately preceding calendar year`() {
        val range = PeriodRange.year(LocalDate(2026, 6, 1))

        val previous = range.previous()

        assertEquals(LocalDate(2025, 1, 1), previous?.start)
        assertEquals(LocalDate(2025, 12, 31), previous?.end)
    }

    @Test
    fun `allTime has no previous period`() {
        val range = PeriodRange.allTime(LocalDate(2026, 11, 17))

        assertNull(range.previous())
    }
}
