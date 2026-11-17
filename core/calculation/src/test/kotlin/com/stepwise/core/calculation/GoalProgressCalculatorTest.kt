package com.stepwise.core.calculation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class GoalProgressCalculatorTest {
    @Test
    fun `metricProgress sums every linked actual value, uncapped above 100`() {
        val result = metricProgress(targetValue = 50.0, metricActualValues = listOf(20.0, 20.0, 20.0))

        assertEquals(60.0, result.currentValue)
        assertEquals(120.0, result.percent)
    }

    @Test
    fun `metricProgress with no linked values yet has a zero currentValue, not null`() {
        // Sum of an empty list is a real 0 - this Goal genuinely has zero progress so far,
        // distinct from TARGET_VALUE's "no reading exists yet" null case below.
        val result = metricProgress(targetValue = 50.0, metricActualValues = emptyList())

        assertEquals(0.0, result.currentValue)
        assertEquals(0.0, result.percent)
    }

    @Test
    fun `targetValueProgress with no reading yet is null, not a false zero`() {
        val result = targetValueProgress(targetValue = 70.0, mostRecentActualValue = null)

        assertNull(result.currentValue)
        assertNull(result.percent)
    }

    @Test
    fun `targetValueProgress reads the single most recent value, not a sum`() {
        val result = targetValueProgress(targetValue = 70.0, mostRecentActualValue = 63.0)

        assertEquals(63.0, result.currentValue)
        assertEquals(90.0, result.percent)
    }

    @Test
    fun `frequencyProgress compares this period's qualifying count against the target`() {
        val result = frequencyProgress(targetFrequency = 4.0, qualifyingSessionCount = 3)

        assertEquals(3.0, result.currentValue)
        assertEquals(75.0, result.percent)
    }

    @Test
    fun `milestonesProgress with zero milestones is null, not a divide-by-zero NaN`() {
        val result = milestonesProgress(emptyList())

        assertNull(result.currentValue)
        assertNull(result.percent)
    }

    @Test
    fun `milestonesProgress weights binary and gradable milestones together`() {
        val milestones =
            listOf(
                MilestoneProgressInput(weight = 2.0, isBinary = true, completed = true, progressPercent = null),
                MilestoneProgressInput(weight = 1.0, isBinary = false, completed = false, progressPercent = 50.0),
            )

        // (2*100 + 1*50) / 3 = 83.33...
        val result = milestonesProgress(milestones)

        assertEquals(250.0 / 3.0, result.percent)
    }

    @Test
    fun `milestonesProgress treats an incomplete binary milestone as 0`() {
        val milestones =
            listOf(MilestoneProgressInput(weight = 1.0, isBinary = true, completed = false, progressPercent = null))

        assertEquals(0.0, milestonesProgress(milestones).percent)
    }

    @Test
    fun `milestonesProgress treats a non-binary milestone with no reading yet as 0, a deliberate default`() {
        val milestones =
            listOf(
                MilestoneProgressInput(weight = 1.0, isBinary = false, completed = false, progressPercent = null),
                MilestoneProgressInput(weight = 1.0, isBinary = false, completed = false, progressPercent = 80.0),
            )

        // (1*0 + 1*80) / 2 = 40 - the null-progressPercent milestone contributes 0, not a NaN
        // and not silently excluded from the weight denominator.
        assertEquals(40.0, milestonesProgress(milestones).percent)
    }

    @Test
    fun `tasksProgress with zero linked tasks is null`() {
        val result = tasksProgress(completedTaskCount = 0, totalTaskCount = 0)

        assertNull(result.currentValue)
        assertNull(result.percent)
    }

    @Test
    fun `tasksProgress is equal-weighted completed over total`() {
        val result = tasksProgress(completedTaskCount = 3, totalTaskCount = 4)

        assertEquals(3.0, result.currentValue)
        assertEquals(75.0, result.percent)
    }
}
