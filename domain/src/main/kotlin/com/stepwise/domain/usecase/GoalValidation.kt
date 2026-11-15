package com.stepwise.domain.usecase

import com.stepwise.core.common.AppError
import com.stepwise.core.common.AppResult
import com.stepwise.core.model.GoalProgressMode

/**
 * The one place a [com.stepwise.core.model.Goal]'s `progressMode`/`targetValue`/`unit`
 * consistency is checked (`/ROADMAP.md` DEV-011: "Типы/units валидируются" — types/units are
 * validated) — shared between [CreateGoalUseCase] and [EditGoalUseCase] so the rule can't drift
 * between the two call sites.
 *
 * Rules, each cited rather than invented: [TARGET_FREE_MODES] (MILESTONES/TASKS/MANUAL) must
 * have a null `targetValue` — `/core/model/.../Goal.kt`'s own KDoc already states this
 * ("null when progressMode is MILESTONES, TASKS, or MANUAL"). [TARGET_VALUE]/[FREQUENCY]
 * compute "against a single numeric target" (`/ARCHITECTURE.md` §6, decision B6) and so require
 * a non-null `targetValue`. [METRIC] is deliberately left unconstrained on *requiredness* here
 * — its actual source of truth is a linked `GoalActivityLink` (a separate entity DEV-012's job,
 * per `/ROADMAP.md`), not this field, so [METRIC] is never forced to have (or forbidden from
 * having) a `targetValue` — but a `targetValue` it does carry is still sanity- and unit-checked
 * by the same unconditional rules below as every other mode's.
 *
 * **Two narrow implementation defaults, disclosed rather than silently assumed**: (1) a non-null
 * `targetValue` is required to carry a paired `unit` (`Goal.kt`'s own KDoc: "paired with
 * targetValue") — no canonical doc states this as a hard rule, it is the natural reading of that
 * pairing; (2) a non-null `targetValue` must be finite and strictly positive — no canonical doc
 * states this either, but a `NaN`/`Infinity`/non-positive target would poison the
 * `currentValue / targetValue` progress formula (`/ARCHITECTURE.md` §6/B6) `:core:calculation`
 * reads later (DEV-019), and nothing server-side rejects it (no Postgres `CHECK` on
 * `goals.target_value`), so this layer is this value's only gate. Both are the same category of
 * narrow, defensible default DEV-010's Monthly-clamp decision was.
 */
object GoalValidation {
    val TARGET_FREE_MODES: Set<GoalProgressMode> =
        setOf(GoalProgressMode.MILESTONES, GoalProgressMode.TASKS, GoalProgressMode.MANUAL)
    private val TARGET_REQUIRED_MODES: Set<GoalProgressMode> =
        setOf(GoalProgressMode.TARGET_VALUE, GoalProgressMode.FREQUENCY)

    fun validate(
        progressMode: GoalProgressMode,
        targetValue: Double?,
        unit: String?,
    ): AppResult<Unit> =
        when {
            progressMode in TARGET_FREE_MODES && targetValue != null ->
                AppResult.Failure(
                    AppError.Validation("$progressMode does not use a targetValue", field = "targetValue"),
                )
            progressMode in TARGET_REQUIRED_MODES && targetValue == null ->
                AppResult.Failure(AppError.Validation("$progressMode requires a targetValue", field = "targetValue"))
            targetValue != null && !targetValue.isFinite() ->
                AppResult.Failure(AppError.Validation("targetValue must be a finite number", field = "targetValue"))
            targetValue != null && targetValue <= 0.0 ->
                AppResult.Failure(AppError.Validation("targetValue must be greater than 0", field = "targetValue"))
            targetValue != null && unit == null ->
                AppResult.Failure(AppError.Validation("A targetValue needs a paired unit", field = "unit"))
            else -> AppResult.Success(Unit)
        }
}
