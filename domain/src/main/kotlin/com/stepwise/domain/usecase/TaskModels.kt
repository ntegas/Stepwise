package com.stepwise.domain.usecase

import com.stepwise.core.model.GoalId
import com.stepwise.core.model.LifeAreaId
import com.stepwise.core.model.MetricId
import com.stepwise.core.model.ProjectId
import com.stepwise.core.model.ReminderId
import com.stepwise.core.model.TaskId
import com.stepwise.core.model.UserId
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

data class CreateTaskCommand(
    val userId: UserId,
    val title: String,
    val goalId: GoalId? = null,
    val projectId: ProjectId? = null,
    val lifeAreaId: LifeAreaId? = null,
    val date: LocalDate? = null,
    val time: LocalTime? = null,
    val deadline: LocalDate? = null,
    val durationMinutes: Int? = null,
    val priority: Int? = null,
    val reminderId: ReminderId? = null,
    val metricId: MetricId? = null,
    val plannedResult: Double? = null,
    val goalImpactScore: Int? = null,
)

/**
 * Every field null means "leave unchanged" — the same convention [EditGoalCommand] uses.
 *
 * **[date] is the one exception, given its own explicit clear path**: `/DATA_MODEL.md` defines
 * Inbox as `date IS NULL`, so "move back to Inbox" is a routine, expected user action here —
 * unlike [EditGoalCommand]'s own disclosed-and-accepted `lifeAreaId`/`category` stickiness,
 * silently making this one un-clearable would block a named product flow, not just an edge
 * case. Set [clearDate] to actually null it out; [date] itself still only ever *sets* a value.
 *
 * **Disclosed limitation, same category as [EditGoalCommand]'s own**: [goalId]/[projectId]/
 * [lifeAreaId]/[deadline]/[reminderId]/[metricId]/[goalImpactScore] can be set but never
 * explicitly cleared back to `null` through this command once attached — none of these are a
 * named product state the way Inbox is, so this is accepted rather than given the same
 * [clearDate]-style escape hatch.
 */
data class EditTaskCommand(
    val userId: UserId,
    val taskId: TaskId,
    val title: String? = null,
    val goalId: GoalId? = null,
    val projectId: ProjectId? = null,
    val lifeAreaId: LifeAreaId? = null,
    val date: LocalDate? = null,
    val clearDate: Boolean = false,
    val time: LocalTime? = null,
    val deadline: LocalDate? = null,
    val durationMinutes: Int? = null,
    val priority: Int? = null,
    val reminderId: ReminderId? = null,
    val metricId: MetricId? = null,
    val plannedResult: Double? = null,
    val goalImpactScore: Int? = null,
    val archived: Boolean? = null,
)
