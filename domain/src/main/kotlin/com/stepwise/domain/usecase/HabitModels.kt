package com.stepwise.domain.usecase

import com.stepwise.core.model.ActivityId
import com.stepwise.core.model.HabitId
import com.stepwise.core.model.MetricId
import com.stepwise.core.model.UserId

data class CreateHabitCommand(
    val userId: UserId,
    val title: String,
    val targetValue: Double,
    val metricId: MetricId,
    val activityId: ActivityId? = null,
)

/** Every field null means "leave unchanged" — the same convention [EditGoalCommand] uses. */
data class EditHabitCommand(
    val userId: UserId,
    val habitId: HabitId,
    val title: String? = null,
    val targetValue: Double? = null,
    val metricId: MetricId? = null,
    val activityId: ActivityId? = null,
    val archived: Boolean? = null,
)
