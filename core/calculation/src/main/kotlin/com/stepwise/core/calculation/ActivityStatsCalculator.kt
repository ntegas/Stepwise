package com.stepwise.core.calculation

import com.stepwise.core.model.ExecutionStatus

/**
 * One qualifying Session's contribution to [activityStats] - a Session the caller already
 * filtered to one source (`ExecutableKind`/`sourceId`) and one [PeriodRange]. [plannedValue]/
 * [actualValue] are that Session's own linked `SessionMetricValue` totals (summed first if a
 * Session carries more than one metric, per `CALCULATION_ENGINE.md`).
 */
data class ActivityStatsSessionInput(
    val status: ExecutionStatus,
    val plannedValue: Double?,
    val actualValue: Double?,
)

/**
 * Planned/actual totals, session count, and Execution Rate for one source over one period
 * (`CALCULATION_ENGINE.md`'s "Activity/Habit/Task period statistics"). [trend] is this period's
 * [executionRate] minus the immediately preceding period's, `null` when either side has nothing
 * to rate.
 */
data class ActivityStats(
    val plannedTotal: Double,
    val actualTotal: Double,
    val sessionCount: Int,
    val executionRate: Double?,
    val trend: Double?,
)

/**
 * [currentPeriod]/[previousPeriod] are both already filtered to one source and their own
 * [PeriodRange] by the caller (`:domain`'s `GetActivityStatsUseCase`) - this function only
 * aggregates, never queries. `CANCELLED` Sessions are excluded from [ActivityStats.sessionCount]
 * (matching [executionRate]'s own denominator rule) but are NOT excluded from plannedTotal/
 * actualTotal - CALCULATION_ENGINE.md's "Activity/Habit/Task period statistics" only says to
 * exclude CANCELLED from the count, and a cancelled Session can still carry a `plannedValue` set
 * before it was cancelled, which the totals correctly still sum.
 */
fun activityStats(
    currentPeriod: List<ActivityStatsSessionInput>,
    previousPeriod: List<ActivityStatsSessionInput>,
): ActivityStats {
    val currentRate = executionRate(currentPeriod.map { it.toExecutionRateInput() })
    val previousRate = executionRate(previousPeriod.map { it.toExecutionRateInput() })
    return ActivityStats(
        plannedTotal = currentPeriod.sumOf { it.plannedValue ?: 0.0 },
        actualTotal = currentPeriod.sumOf { it.actualValue ?: 0.0 },
        sessionCount = currentPeriod.count { it.status != ExecutionStatus.CANCELLED },
        executionRate = currentRate,
        trend = if (currentRate == null || previousRate == null) null else currentRate - previousRate,
    )
}

private fun ActivityStatsSessionInput.toExecutionRateInput() =
    ExecutionRateInput(status = status, plannedValue = plannedValue, actualValue = actualValue)
