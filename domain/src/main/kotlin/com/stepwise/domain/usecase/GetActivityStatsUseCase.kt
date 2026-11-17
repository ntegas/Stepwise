package com.stepwise.domain.usecase

import com.stepwise.core.calculation.ActivityStats
import com.stepwise.core.calculation.ActivityStatsSessionInput
import com.stepwise.core.calculation.PeriodRange
import com.stepwise.core.calculation.activityStats
import com.stepwise.core.common.AppResult
import com.stepwise.core.model.ExecutableKind
import com.stepwise.core.model.Session
import com.stepwise.core.model.SessionMetricValue
import com.stepwise.core.model.UserId
import com.stepwise.domain.repository.SessionRepository
import kotlinx.coroutines.flow.first

/**
 * Planned/actual totals, session count, Execution Rate, and trend for one Activity/Habit/Task
 * over one [PeriodRange] (`CALCULATION_ENGINE.md`'s "Activity/Habit/Task period statistics") -
 * reuses [GetSessionHistoryUseCase] for the ownership check and the raw Session list, rather than
 * re-deriving the same `ExecutableKind` → `*Access` dispatch a second time, then layers this
 * task's own metric-value join and period filter on top. A plain one-shot `suspend fun`, same
 * "no reactive consumer exists yet" reasoning as [GetGoalProgressUseCase].
 */
class GetActivityStatsUseCase(
    private val getSessionHistoryUseCase: GetSessionHistoryUseCase,
    private val sessionRepository: SessionRepository,
) {
    suspend fun execute(
        userId: UserId,
        sourceType: ExecutableKind,
        sourceId: String,
        period: PeriodRange,
    ): AppResult<ActivityStats> {
        val allSessionsResult = getSessionHistoryUseCase.execute(userId, sourceType, sourceId).first()
        if (allSessionsResult is AppResult.Failure) return allSessionsResult
        check(allSessionsResult is AppResult.Success)
        val allSessions = allSessionsResult.value

        val currentInputsResult = inputsFor(allSessions, period)
        if (currentInputsResult is AppResult.Failure) return currentInputsResult
        check(currentInputsResult is AppResult.Success)

        val previousPeriod = period.previous()
        val previousInputsResult =
            if (previousPeriod == null) AppResult.Success(emptyList()) else inputsFor(allSessions, previousPeriod)
        if (previousInputsResult is AppResult.Failure) return previousInputsResult
        check(previousInputsResult is AppResult.Success)

        return AppResult.Success(activityStats(currentInputsResult.value, previousInputsResult.value))
    }

    private suspend fun inputsFor(
        sessions: List<Session>,
        period: PeriodRange,
    ): AppResult<List<ActivityStatsSessionInput>> {
        val inputs = mutableListOf<ActivityStatsSessionInput>()
        for (session in sessions) {
            if (session.date !in period) continue
            val metricValuesResult = sessionRepository.getMetricValuesForSession(session.id)
            if (metricValuesResult is AppResult.Failure) return metricValuesResult
            check(metricValuesResult is AppResult.Success)
            inputs += session.toStatsInput(metricValuesResult.value)
        }
        return AppResult.Success(inputs)
    }
}

private fun Session.toStatsInput(metricValues: List<SessionMetricValue>): ActivityStatsSessionInput =
    ActivityStatsSessionInput(
        status = status,
        plannedValue = if (metricValues.isEmpty()) null else metricValues.sumOf { it.plannedValue ?: 0.0 },
        actualValue = if (metricValues.isEmpty()) null else metricValues.sumOf { it.actualValue },
    )
