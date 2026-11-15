package com.stepwise.domain.usecase

import com.stepwise.core.common.AppError
import com.stepwise.core.common.AppResult

/**
 * The one place a [com.stepwise.core.model.Task]'s [com.stepwise.core.model.Task.plannedResult]
 * is sanity-checked before being persisted — the same category of gap DEV-011/012's reviews
 * found for Goal's `targetValue`/Milestone's `weight`/`progressPercent`. [MetricResolver]'s
 * "Done=Planned default" rule (DEV-009) feeds `plannedResult` straight into a Session's
 * `actualValue` when a Task completes as DONE with no override — a `NaN`/`Infinity`/negative
 * value would poison that Session's metric value, and nothing server-side rejects it (no
 * Postgres `CHECK` on `tasks.planned_result`).
 */
object TaskValidation {
    fun validatePlannedResult(plannedResult: Double?): AppResult<Unit> =
        when {
            plannedResult == null -> AppResult.Success(Unit)
            !plannedResult.isFinite() ->
                AppResult.Failure(AppError.Validation("plannedResult must be a finite number", field = "plannedResult"))
            plannedResult < 0.0 ->
                AppResult.Failure(AppError.Validation("plannedResult must not be negative", field = "plannedResult"))
            else -> AppResult.Success(Unit)
        }
}
