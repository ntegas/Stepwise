package com.stepwise.domain.usecase

import com.stepwise.core.common.AppError
import com.stepwise.core.common.AppResult
import com.stepwise.core.model.MetricId
import com.stepwise.domain.repository.MetricRepository

/**
 * The one place a [com.stepwise.core.model.Habit]'s own numeric/reference fields are
 * sanity-checked before being persisted — the same category of gap DEV-011/012's reviews found
 * for Goal's `targetValue`/Milestone's `weight`. [Habit.targetValue] feeds
 * [MetricResolver.habitDefaultMetrics] directly as a Session's planned value on completion
 * (DEV-009); a `NaN`/`Infinity`/non-positive value would poison that Session's metric value.
 */
object HabitValidation {
    fun validateTargetValue(targetValue: Double): AppResult<Unit> =
        when {
            !targetValue.isFinite() ->
                AppResult.Failure(AppError.Validation("targetValue must be a finite number", field = "targetValue"))
            targetValue <= 0.0 ->
                AppResult.Failure(AppError.Validation("targetValue must be greater than 0", field = "targetValue"))
            else -> AppResult.Success(Unit)
        }

    suspend fun validateMetricExists(
        repository: MetricRepository,
        metricId: MetricId,
    ): AppResult<Unit> =
        repository.getById(metricId).andThen { metric ->
            if (metric == null) {
                AppResult.Failure(AppError.Validation("Metric not found", field = "metricId"))
            } else {
                AppResult.Success(Unit)
            }
        }
}
