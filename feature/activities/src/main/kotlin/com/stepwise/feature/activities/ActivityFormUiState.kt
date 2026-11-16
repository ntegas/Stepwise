package com.stepwise.feature.activities

import com.stepwise.core.model.Activity
import com.stepwise.core.model.ActivityId
import com.stepwise.core.model.LifeArea
import com.stepwise.core.model.LifeAreaId

/** Create vs. Edit - [Edit.activityId] is the only thing distinguishing the two beyond pre-filled fields. */
sealed interface ActivityFormMode {
    data object Create : ActivityFormMode

    data class Edit(
        val activityId: ActivityId,
    ) : ActivityFormMode
}

/**
 * `:feature:activities`'s own Create/Edit Activity form state (`/ROADMAP.md` DEV-018) - the same
 * field-level-error/save-time-[hasError] shape `GoalFormUiState` (DEV-016) established.
 */
data class ActivityFormUiState(
    val mode: ActivityFormMode = ActivityFormMode.Create,
    val title: String = "",
    val lifeAreaId: LifeAreaId? = null,
    val lifeAreas: List<LifeArea> = emptyList(),
    val titleError: Boolean = false,
    val isSubmitting: Boolean = false,
    val hasError: Boolean = false,
    val saved: Boolean = false,
)

internal fun ActivityFormUiState.copyFrom(activity: Activity): ActivityFormUiState =
    copy(title = activity.title, lifeAreaId = activity.lifeAreaId)
