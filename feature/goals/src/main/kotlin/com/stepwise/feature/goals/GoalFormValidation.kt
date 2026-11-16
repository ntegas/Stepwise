package com.stepwise.feature.goals

import kotlinx.datetime.LocalDate

/**
 * Pure Create/Edit Goal form validation - extracted out of [GoalFormViewModel] once that class's
 * own `validate` crossed detekt's CyclomaticComplexMethod/ComplexCondition thresholds. A Target
 * needs a paired Unit (and vice versa isn't rejected, but a lone Unit with no Target is
 * meaningless) - enforced here, inline, rather than letting a Target-without-Unit reach
 * [com.stepwise.domain.usecase.CreateGoalUseCase]/[com.stepwise.domain.usecase.EditGoalUseCase]
 * only to bounce back as a confusing enum-name-bearing `GoalValidation` failure (DEV-016
 * design/user-flow review).
 */
internal data class ValidatedGoalFormInput(
    val title: String,
    val targetValue: Double?,
    val unit: String?,
    val deadline: LocalDate?,
    val priority: Int?,
)

internal data class GoalFormValidationResult(
    val validated: ValidatedGoalFormInput?,
    val titleError: Boolean,
    val targetValueError: Boolean,
    val unitError: Boolean,
    val deadlineError: Boolean,
    val priorityError: Boolean,
)

internal fun validateGoalForm(current: GoalFormUiState): GoalFormValidationResult {
    val title = current.title.trim()
    val targetValueText = current.targetValue.trim()
    val targetValue = targetValueText.toDoubleOrNull()
    val targetValueInvalid = isTargetValueInvalid(targetValueText, targetValue)
    val unitText = current.unit.trim()
    val unitInvalid = isUnitInvalid(targetValueText, targetValueInvalid, unitText)
    val deadlineText = current.deadline.trim()
    val deadline = if (deadlineText.isBlank()) null else parseDeadline(deadlineText)
    val deadlineInvalid = deadlineText.isNotBlank() && deadline == null
    val priorityText = current.priority.trim()
    val priority = priorityText.toIntOrNull()
    val priorityInvalid = priorityText.isNotBlank() && priority == null
    val titleInvalid = title.isBlank()
    val anyInvalid =
        listOf(titleInvalid, targetValueInvalid, unitInvalid, deadlineInvalid, priorityInvalid).any { it }
    return GoalFormValidationResult(
        validated =
            if (anyInvalid) {
                null
            } else {
                ValidatedGoalFormInput(
                    title = title,
                    targetValue = if (targetValueText.isBlank()) null else targetValue,
                    unit = unitText.ifBlank { null },
                    deadline = deadline,
                    priority = priority,
                )
            },
        titleError = titleInvalid,
        targetValueError = targetValueInvalid,
        unitError = unitInvalid,
        deadlineError = deadlineInvalid,
        priorityError = priorityInvalid,
    )
}

private fun isTargetValueInvalid(
    targetValueText: String,
    targetValue: Double?,
): Boolean {
    if (targetValueText.isBlank()) return false
    return targetValue == null || !targetValue.isFinite() || targetValue <= 0.0
}

private fun isUnitInvalid(
    targetValueText: String,
    targetValueInvalid: Boolean,
    unitText: String,
): Boolean {
    if (targetValueInvalid || targetValueText.isBlank()) return false
    return unitText.isBlank()
}

private fun parseDeadline(deadlineText: String): LocalDate? = runCatching { LocalDate.parse(deadlineText) }.getOrNull()
