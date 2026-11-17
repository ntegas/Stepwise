package com.stepwise.core.calculation

import com.stepwise.core.model.ExecutionStatus

/**
 * One occurrence's inputs to the Execution Rate formula (`CALCULATION_ENGINE.md`'s binding
 * version of `/PRODUCT_CANON.md` §29) - [plannedValue]/[actualValue] only matter when [status]
 * is [ExecutionStatus.PARTIAL]; every other status ignores them.
 */
data class ExecutionRateInput(
    val status: ExecutionStatus,
    val plannedValue: Double? = null,
    val actualValue: Double? = null,
)

/**
 * The Done=100%/Partial=actual÷planned/Missed=0%/Cancelled-excluded/Rescheduled-not-missed rule,
 * averaged over every qualifying input - `null` (never `0.0`) when nothing qualifies, since "no
 * data" and "a real zero" are different facts this engine never conflates (`CALCULATION_ENGINE.md`).
 */
fun executionRate(inputs: List<ExecutionRateInput>): Double? {
    val percents = inputs.mapNotNull { it.toPercentOrNull() }
    return if (percents.isEmpty()) null else percents.sum() / percents.size
}

private fun ExecutionRateInput.toPercentOrNull(): Double? =
    when (status) {
        ExecutionStatus.DONE -> HUNDRED_PERCENT
        ExecutionStatus.MISSED -> 0.0
        ExecutionStatus.PARTIAL -> partialPercentOrNull()
        ExecutionStatus.CANCELLED, ExecutionStatus.RESCHEDULED, ExecutionStatus.IN_PROGRESS -> null
    }

private fun ExecutionRateInput.partialPercentOrNull(): Double? {
    val planned = plannedValue
    val actual = actualValue
    return if (planned == null || planned == 0.0 || actual == null) null else actual / planned * HUNDRED_PERCENT
}

private const val HUNDRED_PERCENT = 100.0
