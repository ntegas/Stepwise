package com.stepwise.domain.usecase

import com.stepwise.core.common.AppError
import com.stepwise.core.common.AppResult

/**
 * The one place a [com.stepwise.core.model.Milestone]'s own numeric fields are sanity-checked
 * before being persisted — shared between [AddMilestoneUseCase] and [EditMilestoneUseCase] so
 * the rules can't drift between the two call sites, the same reasoning [GoalValidation] already
 * follows for [com.stepwise.core.model.Goal].
 *
 * [weight]: `/ARCHITECTURE.md` §6/B6's weighted-completion formula (`Σ(progress × weight) /
 * Σ(weight)`) divides by the weight sum, so a non-finite or non-positive weight poisons that
 * division for the whole parent Goal, not just this Milestone — flagged by the DEV-011
 * adversarial review as unenforced anywhere in this layer.
 *
 * [progressPercent]: the Postgres schema already encodes `0.0..100.0` as a `CHECK` constraint
 * (`supabase/migrations/0002_canonical_schema.sql`'s `milestones_progress_percent_range`) —
 * mirrored here so an out-of-range value is rejected locally, before it can reach the sync
 * outbox and get stuck retrying against a constraint it can never satisfy.
 */
object MilestoneValidation {
    private val PROGRESS_PERCENT_RANGE = 0.0..100.0

    fun validateWeight(weight: Double): AppResult<Unit> =
        if (weight.isFinite() && weight > 0.0) {
            AppResult.Success(Unit)
        } else {
            AppResult.Failure(AppError.Validation("weight must be a finite number greater than 0", field = "weight"))
        }

    fun validateProgressPercent(progressPercent: Double?): AppResult<Unit> =
        when {
            progressPercent == null -> AppResult.Success(Unit)
            !progressPercent.isFinite() || progressPercent !in PROGRESS_PERCENT_RANGE ->
                AppResult.Failure(
                    AppError.Validation(
                        "progressPercent must be a finite number within 0.0..100.0",
                        field = "progressPercent",
                    ),
                )
            else -> AppResult.Success(Unit)
        }
}
