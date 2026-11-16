package com.stepwise.feature.goals

import com.stepwise.core.model.GoalId
import com.stepwise.core.model.GoalProgressMode
import com.stepwise.core.model.GoalType
import com.stepwise.core.model.UserId
import com.stepwise.domain.usecase.CreateGoalCommand
import com.stepwise.domain.usecase.EditGoalCommand

/**
 * Maps [ValidatedGoalFormInput] (plus the rest of [GoalFormUiState]) to the Create/Edit Use Case
 * commands - extracted out of [GoalFormViewModel] once that class crossed detekt's
 * TooManyFunctions threshold.
 *
 * **[GoalType]/[GoalProgressMode] default, disclosed rather than invented**: `/ARCHITECTURE.md`
 * §6/B6 only describes UI-level *suggestions* per [GoalType], not a hard mapping, and no
 * canonical doc says what a type/progressMode-picker-free form should default to.
 * [inferGoalTypeAndMode] uses [GoalProgressMode.TARGET_VALUE]/[GoalType.QUANTITY] when a target
 * value and unit are both given, else [GoalProgressMode.MILESTONES]/[GoalType.CUMULATIVE]
 * (progress tracked entirely via Goal Detail's Milestones list) - a narrow default a later task
 * is free to replace with an actual picker. Only [toCreateGoalCommand] uses it: [toEditGoalCommand]
 * never redefines an existing Goal's mode (DEV-016 logic review, CRITICAL, fixed) - the first
 * draft called this same inference on Edit too, which silently flipped a `METRIC`/`FREQUENCY`/
 * `TASKS`/`MANUAL` Goal's mode to `MILESTONES`/`TARGET_VALUE` on *any* edit, even just a title
 * change - `GoalProgressMode`'s own KDoc states it is "set at creation and changed only by a
 * deliberate user edit, never computed." [toEditGoalCommand] always passes `progressMode = null`
 * ("leave unchanged") and only forwards the typed Target/Unit at all when
 * [GoalFormUiState.existingProgressMode] already uses them ([GoalValidation.TARGET_REQUIRED_MODES]);
 * for any other existing mode, whatever is typed into those two fields is not sent - a disclosed
 * limitation (no progressMode picker exists to make switching modes a deliberate action) rather
 * than a silent, confusing [GoalValidation] rejection the first draft could hit instead.
 */
internal fun toCreateGoalCommand(
    validated: ValidatedGoalFormInput,
    formState: GoalFormUiState,
    userId: UserId,
): CreateGoalCommand {
    val (type, progressMode) = inferGoalTypeAndMode(validated)
    return CreateGoalCommand(
        userId = userId,
        title = validated.title,
        type = type,
        progressMode = progressMode,
        lifeAreaId = formState.lifeAreaId,
        projectId = formState.projectId,
        targetValue = validated.targetValue,
        unit = validated.unit,
        deadline = validated.deadline,
        priority = validated.priority,
        category = formState.category.trim().ifBlank { null },
        description = formState.description.trim().ifBlank { null },
    )
}

internal fun toEditGoalCommand(
    validated: ValidatedGoalFormInput,
    formState: GoalFormUiState,
    goalId: GoalId,
    userId: UserId,
): EditGoalCommand {
    val existingMode = formState.existingProgressMode
    val usesTarget = existingMode == GoalProgressMode.TARGET_VALUE || existingMode == GoalProgressMode.FREQUENCY
    return EditGoalCommand(
        userId = userId,
        goalId = goalId,
        title = validated.title,
        // Never redefined here - see this function's own KDoc for why.
        progressMode = null,
        lifeAreaId = formState.lifeAreaId,
        projectId = formState.projectId,
        targetValue = if (usesTarget) validated.targetValue else null,
        unit = if (usesTarget) validated.unit else null,
        deadline = validated.deadline,
        priority = validated.priority,
        category = formState.category.trim().ifBlank { null },
        description = formState.description.trim().ifBlank { null },
    )
}

private fun inferGoalTypeAndMode(validated: ValidatedGoalFormInput): Pair<GoalType, GoalProgressMode> =
    if (validated.targetValue != null && validated.unit != null) {
        GoalType.QUANTITY to GoalProgressMode.TARGET_VALUE
    } else {
        GoalType.CUMULATIVE to GoalProgressMode.MILESTONES
    }
