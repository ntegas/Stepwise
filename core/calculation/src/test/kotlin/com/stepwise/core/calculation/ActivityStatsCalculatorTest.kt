package com.stepwise.core.calculation

import com.stepwise.core.model.ExecutionStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ActivityStatsCalculatorTest {
    @Test
    fun `planned and actual totals sum every qualifying session`() {
        val currentPeriod =
            listOf(
                ActivityStatsSessionInput(ExecutionStatus.DONE, plannedValue = 30.0, actualValue = 30.0),
                ActivityStatsSessionInput(ExecutionStatus.PARTIAL, plannedValue = 60.0, actualValue = 40.0),
            )

        val stats = activityStats(currentPeriod, previousPeriod = emptyList())

        assertEquals(90.0, stats.plannedTotal)
        assertEquals(70.0, stats.actualTotal)
    }

    @Test
    fun `sessionCount excludes CANCELLED sessions`() {
        val currentPeriod =
            listOf(
                ActivityStatsSessionInput(ExecutionStatus.DONE, plannedValue = null, actualValue = null),
                ActivityStatsSessionInput(ExecutionStatus.CANCELLED, plannedValue = null, actualValue = null),
            )

        val stats = activityStats(currentPeriod, previousPeriod = emptyList())

        assertEquals(1, stats.sessionCount)
    }

    @Test
    fun `executionRate delegates to the shared Execution Rate formula`() {
        val currentPeriod =
            listOf(
                ActivityStatsSessionInput(ExecutionStatus.DONE, plannedValue = null, actualValue = null),
                ActivityStatsSessionInput(ExecutionStatus.MISSED, plannedValue = null, actualValue = null),
            )

        val stats = activityStats(currentPeriod, previousPeriod = emptyList())

        assertEquals(50.0, stats.executionRate)
    }

    @Test
    fun `trend is this period's rate minus the previous period's`() {
        val currentPeriod = listOf(ActivityStatsSessionInput(ExecutionStatus.DONE, null, null))
        val previousPeriod = listOf(ActivityStatsSessionInput(ExecutionStatus.MISSED, null, null))

        val stats = activityStats(currentPeriod, previousPeriod)

        assertEquals(100.0, stats.trend)
    }

    @Test
    fun `trend is null when either period has nothing to rate`() {
        val currentPeriod = listOf(ActivityStatsSessionInput(ExecutionStatus.DONE, null, null))

        val stats = activityStats(currentPeriod, previousPeriod = emptyList())

        assertNull(stats.trend)
    }
}
