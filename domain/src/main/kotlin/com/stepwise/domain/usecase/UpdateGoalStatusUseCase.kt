package com.stepwise.domain.usecase

import com.stepwise.core.common.AppResult
import com.stepwise.core.common.Clock
import com.stepwise.core.model.Goal
import com.stepwise.core.model.GoalId
import com.stepwise.core.model.LifecycleStatus
import com.stepwise.core.model.UserId
import com.stepwise.domain.repository.GoalRepository

/**
 * Transitions a [Goal]'s [LifecycleStatus] (`/ROADMAP.md` DEV-011: "lifecycle
 * Active/Paused/Completed/Archived") — kept separate from [EditGoalUseCase] since this is a
 * distinct user action (`/PRODUCT_CANON.md`: a Goal's lifecycle status is its own control, not
 * one field among many on an edit form).
 *
 * **No transition is rejected** — no canonical doc states a restriction on which status can
 * follow which (e.g. nothing forbids reopening a COMPLETED goal back to ACTIVE), so none is
 * invented here. A no-op transition (setting the status a Goal already has) is accepted as an
 * ordinary success, not specially rejected either — both are narrow implementation defaults,
 * disclosed rather than silently assumed, same category as [GoalValidation]'s unit-pairing rule.
 */
class UpdateGoalStatusUseCase(
    private val goalRepository: GoalRepository,
    private val clock: Clock,
) {
    suspend fun execute(
        userId: UserId,
        goalId: GoalId,
        status: LifecycleStatus,
    ): AppResult<Goal> =
        GoalAccess.verifyOwnedGoal(goalRepository, userId, goalId).andThen { existing ->
            val updated = existing.copy(status = status, sync = existing.sync.copy(updatedAt = clock.now()))
            goalRepository.upsert(updated).map { updated }
        }
}
