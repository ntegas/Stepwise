package com.stepwise.feature.activities

import com.stepwise.core.model.Activity
import com.stepwise.core.model.ActivityId
import com.stepwise.core.model.Habit
import com.stepwise.core.model.HabitId
import com.stepwise.core.model.Metric
import com.stepwise.core.model.MetricId

/** Create vs. Edit - [Edit.habitId] is the only thing distinguishing the two beyond pre-filled fields. */
sealed interface HabitFormMode {
    data object Create : HabitFormMode

    data class Edit(
        val habitId: HabitId,
    ) : HabitFormMode
}

/**
 * `:feature:activities`'s own Create/Edit Habit form state (`/ROADMAP.md` DEV-018) - the same
 * shape `GoalFormUiState`/`ActivityFormUiState` established, narrowed to [Habit]'s own field set
 * (title, targetValue, metricId, an optional [activityId] - `/core/model/.../Activity.kt`'s own
 * KDoc: "a Habit need not be tied to a pre-existing Activity").
 */
data class HabitFormUiState(
    val mode: HabitFormMode = HabitFormMode.Create,
    val title: String = "",
    val targetValue: String = "",
    val metricId: MetricId? = null,
    val activityId: ActivityId? = null,
    val metrics: List<Metric> = emptyList(),
    val activities: List<Activity> = emptyList(),
    val titleError: Boolean = false,
    val targetValueError: Boolean = false,
    val metricError: Boolean = false,
    val isSubmitting: Boolean = false,
    val hasError: Boolean = false,
    val saved: Boolean = false,
)

internal fun HabitFormUiState.copyFrom(habit: Habit): HabitFormUiState =
    copy(
        title = habit.title,
        targetValue = habit.targetValue.toString(),
        metricId = habit.metricId,
        activityId = habit.activityId,
    )
