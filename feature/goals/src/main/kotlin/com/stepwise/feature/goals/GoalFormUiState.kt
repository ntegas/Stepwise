package com.stepwise.feature.goals

import com.stepwise.core.model.GoalId
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
 * validation messages; [error] is a save-time failure surfaced from the Use Case layer, shown
 * as-is rather than mapped to a fixed enum the way `:feature:onboarding-auth`'s Sign In form
 * does for its own errors — a Goal-save failure carries no account-enumeration risk, so there is
 * nothing here for a fixed mapping to protect against.
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
    val titleError: Boolean = false,
    val targetValueError: Boolean = false,
    val deadlineError: Boolean = false,
    val priorityError: Boolean = false,
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
)
