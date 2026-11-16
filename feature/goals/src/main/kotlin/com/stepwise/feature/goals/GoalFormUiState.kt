package com.stepwise.feature.goals

import com.stepwise.core.model.Goal
import com.stepwise.core.model.GoalId
import com.stepwise.core.model.GoalProgressMode
import com.stepwise.core.model.LifeArea
import com.stepwise.core.model.LifeAreaId
import com.stepwise.core.model.Project
import com.stepwise.core.model.ProjectId

/** Create vs. Edit — [Edit.goalId] is the only thing distinguishing the two beyond pre-filled fields. */
sealed interface GoalFormMode {
    data object Create : GoalFormMode

    data class Edit(
        val goalId: GoalId,
    ) : GoalFormMode
}

/**
 * `:feature:goals`'s own Create/Edit Goal form state. Field-level `*Error` flags drive inline
 * validation messages; [hasError] is a save-time failure from the Use Case layer (or a mid-save
 * sign-out) — `GoalFormScreen` resolves its own fixed, localized `goal_form_error_generic` string
 * for it rather than surfacing a raw [com.stepwise.core.common.AppError] message (DEV-016 design
 * review, HIGH — a non-English-locale user otherwise sees untranslated developer-facing English
 * text), the same UI-agnostic-signal convention `:feature:onboarding-auth`'s Sign In form already
 * uses, just collapsed to a single boolean since a Goal-save failure carries no
 * account-enumeration risk needing a richer mapping.
 *
 * [existingProgressMode] is the Goal's own mode as loaded in Edit mode (`null` in Create) —
 * [GoalFormViewModel] needs it at save time to decide whether the Target/Unit fields are even
 * meaningful for this Goal, without re-deriving a (possibly different) mode from what's currently
 * typed into them.
 */
data class GoalFormUiState(
    val mode: GoalFormMode = GoalFormMode.Create,
    val title: String = "",
    val targetValue: String = "",
    val unit: String = "",
    val deadline: String = "",
    val lifeAreaId: LifeAreaId? = null,
    val projectId: ProjectId? = null,
    val category: String = "",
    val description: String = "",
    val priority: String = "",
    val lifeAreas: List<LifeArea> = emptyList(),
    val projects: List<Project> = emptyList(),
    val moreOptionsExpanded: Boolean = false,
    val existingProgressMode: GoalProgressMode? = null,
    val titleError: Boolean = false,
    val targetValueError: Boolean = false,
    val unitError: Boolean = false,
    val deadlineError: Boolean = false,
    val priorityError: Boolean = false,
    val isSubmitting: Boolean = false,
    val hasError: Boolean = false,
    val saved: Boolean = false,
)

/**
 * Populates the form from a loaded [Goal] - extracted out of [GoalFormViewModel] once that class
 * crossed detekt's TooManyFunctions threshold.
 */
internal fun GoalFormUiState.copyFrom(goal: Goal): GoalFormUiState =
    copy(
        title = goal.title,
        targetValue = goal.targetValue?.toString() ?: "",
        unit = goal.unit ?: "",
        deadline = goal.deadline?.toString() ?: "",
        lifeAreaId = goal.lifeAreaId,
        projectId = goal.projectId,
        category = goal.category ?: "",
        description = goal.description ?: "",
        priority = goal.priority?.toString() ?: "",
        existingProgressMode = goal.progressMode,
    )
