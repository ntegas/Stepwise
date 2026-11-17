package com.stepwise.core.calculation

import com.stepwise.core.model.ExecutionStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ExecutionRateCalculatorTest {
    @Test
    fun `an empty input list rates null, never a false zero`() {
        assertNull(executionRate(emptyList()))
    }

    @Test
    fun `DONE rates 100`() {
        assertEquals(100.0, executionRate(listOf(ExecutionRateInput(ExecutionStatus.DONE))))
    }

    @Test
    fun `MISSED rates 0`() {
        assertEquals(0.0, executionRate(listOf(ExecutionRateInput(ExecutionStatus.MISSED))))
    }

    @Test
    fun `PARTIAL rates actual over planned, uncapped above 100`() {
        val input = ExecutionRateInput(ExecutionStatus.PARTIAL, plannedValue = 30.0, actualValue = 45.0)

        assertEquals(150.0, executionRate(listOf(input)))
    }

    @Test
    fun `PARTIAL with no plannedValue is excluded, not treated as 0 or a crash`() {
        val input = ExecutionRateInput(ExecutionStatus.PARTIAL, plannedValue = null, actualValue = 20.0)

        assertNull(executionRate(listOf(input)))
    }

    @Test
    fun `PARTIAL with plannedValue 0 is excluded rather than dividing by zero`() {
        val input = ExecutionRateInput(ExecutionStatus.PARTIAL, plannedValue = 0.0, actualValue = 20.0)

        assertNull(executionRate(listOf(input)))
    }

    @Test
    fun `CANCELLED is excluded from the denominator entirely`() {
        val inputs = listOf(ExecutionRateInput(ExecutionStatus.DONE), ExecutionRateInput(ExecutionStatus.CANCELLED))

        assertEquals(100.0, executionRate(inputs))
    }

    @Test
    fun `RESCHEDULED is excluded, not counted as Missed`() {
        val inputs = listOf(ExecutionRateInput(ExecutionStatus.DONE), ExecutionRateInput(ExecutionStatus.RESCHEDULED))

        assertEquals(100.0, executionRate(inputs))
    }

    @Test
    fun `IN_PROGRESS is excluded, nothing to rate yet`() {
        val inputs = listOf(ExecutionRateInput(ExecutionStatus.DONE), ExecutionRateInput(ExecutionStatus.IN_PROGRESS))

        assertEquals(100.0, executionRate(inputs))
    }

    @Test
    fun `the rate is the mean of every qualifying occurrence`() {
        val inputs =
            listOf(
                ExecutionRateInput(ExecutionStatus.DONE),
                ExecutionRateInput(ExecutionStatus.MISSED),
                ExecutionRateInput(ExecutionStatus.PARTIAL, plannedValue = 60.0, actualValue = 30.0),
            )

        // 100 + 0 + 50, divided by 3.
        assertEquals(50.0, executionRate(inputs))
    }
}
