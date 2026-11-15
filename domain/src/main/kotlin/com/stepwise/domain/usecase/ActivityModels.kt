package com.stepwise.domain.usecase

import com.stepwise.core.model.ActivityId
import com.stepwise.core.model.GoalId
import com.stepwise.core.model.LifeAreaId
import com.stepwise.core.model.MetricId
import com.stepwise.core.model.ProgressSourceKind
import com.stepwise.core.model.UserId

data class CreateActivityCommand(
    val userId: UserId,
    val title: String,
    val lifeAreaId: LifeAreaId? = null,
    val icon: String? = null,
)

/**
 * Every field null means "leave unchanged" — the same convention [EditGoalCommand] uses.
 *
 * **Disclosed limitation**: because `null` always means "leave unchanged," [lifeAreaId] and
 * [icon] can be set but never explicitly cleared back to `null` through this command once a
 * value is attached — the same limitation [EditGoalCommand]'s own KDoc discloses for its
 * `lifeAreaId`/`projectId`/`category`/`description`/`priority` fields.
 */
data class EditActivityCommand(
    val userId: UserId,
    val activityId: ActivityId,
    val title: String? = null,
    val lifeAreaId: LifeAreaId? = null,
    val icon: String? = null,
    val archived: Boolean? = null,
)

/**
 * Creates a [com.stepwise.core.model.GoalActivityLink] (`/ROADMAP.md` DEV-012). [sourceId] is a
 * raw `String`, matching [com.stepwise.core.model.GoalActivityLink.sourceId]'s own polymorphic
 * representation — [sourceType] is the discriminant [LinkActivityToGoalUseCase] dispatches on.
 */
data class LinkActivityToGoalCommand(
    val userId: UserId,
    val sourceType: ProgressSourceKind,
    val sourceId: String,
    val goalId: GoalId,
    val metricId: MetricId,
)
