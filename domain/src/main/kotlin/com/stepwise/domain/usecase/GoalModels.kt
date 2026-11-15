package com.stepwise.domain.usecase

import com.stepwise.core.model.GoalId
import com.stepwise.core.model.GoalProgressMode
import com.stepwise.core.model.GoalType
import com.stepwise.core.model.LifeAreaId
import com.stepwise.core.model.MilestoneId
import com.stepwise.core.model.ProjectId
import com.stepwise.core.model.UserId
import kotlinx.datetime.LocalDate

/**
 * [progressMode] and [type] are both required, deliberately not defaulted from one another
 * here — `/ARCHITECTURE.md` §6/B6 describes sensible *UI-level* suggestions per [type], not a
 * hard mapping, and "UI не делать" (`/ROADMAP.md` DEV-011) means picking a smart default is a
 * later task's job, not this Use Case's. Every other field is optional, matching DEV-011's own
 * "keep simple goal creation simple" acceptance criterion.
 */
data class CreateGoalCommand(
    val userId: UserId,
    val title: String,
    val type: GoalType,
    val progressMode: GoalProgressMode,
    val lifeAreaId: LifeAreaId? = null,
    val projectId: ProjectId? = null,
    val targetValue: Double? = null,
    val unit: String? = null,
    val deadline: LocalDate? = null,
    val priority: Int? = null,
    val category: String? = null,
    val description: String? = null,
)

/**
 * Every field null means "leave unchanged" — the same convention
 * [EditSessionUseCase][com.stepwise.domain.usecase.EditSessionUseCase]'s own command already
 * uses. Switching [progressMode] into one of [GoalValidation.TARGET_FREE_MODES] clears the
 * existing `targetValue`/`unit` automatically ([EditGoalUseCase] does this), so this command
 * never needs an explicit "clear" flag for that one case.
 *
 * **Disclosed limitation**: because `null` always means "leave unchanged," [lifeAreaId],
 * [projectId], [category], [description], and [priority] can be set but never explicitly
 * cleared back to `null` through this command once a value is attached — the same limitation
 * already accepted for `EditSessionCommand.notes`, worth the Goals UI task (DEV-016) knowing
 * about rather than rediscovering.
 */
data class EditGoalCommand(
    val userId: UserId,
    val goalId: GoalId,
    val title: String? = null,
    val progressMode: GoalProgressMode? = null,
    val lifeAreaId: LifeAreaId? = null,
    val projectId: ProjectId? = null,
    val targetValue: Double? = null,
    val unit: String? = null,
    val deadline: LocalDate? = null,
    val priority: Int? = null,
    val category: String? = null,
    val description: String? = null,
)

data class AddMilestoneCommand(
    val userId: UserId,
    val goalId: GoalId,
    val title: String,
    val sortOrder: Int,
    val weight: Double,
    val isBinary: Boolean,
)

/**
 * [isBinary] is deliberately not editable here — changing it after creation would invalidate
 * whichever of [completed]/[progressPercent] the Milestone was already tracking; narrowed out
 * of this task's scope rather than silently allowed to corrupt that pairing.
 */
data class EditMilestoneCommand(
    val userId: UserId,
    val goalId: GoalId,
    val milestoneId: MilestoneId,
    val title: String? = null,
    val sortOrder: Int? = null,
    val weight: Double? = null,
    val completed: Boolean? = null,
    val progressPercent: Double? = null,
)
