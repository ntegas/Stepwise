package com.stepwise.domain.usecase

import com.stepwise.core.common.AppError
import com.stepwise.core.common.AppResult
import com.stepwise.core.model.Goal
import com.stepwise.core.model.GoalId
import com.stepwise.core.model.UserId
import com.stepwise.domain.repository.GoalRepository

/**
 * Shared ownership check for any Use Case that needs to load a [Goal] and verify the caller
 * owns it before acting on it or anything under it (a [com.stepwise.core.model.Milestone]) —
 * the same `*Access` pattern [RecurrenceRuleAccess] already established at DEV-010, extracted
 * here because [EditGoalUseCase], [UpdateGoalStatusUseCase], [AddMilestoneUseCase], and
 * [EditMilestoneUseCase] all need the identical check.
 *
 * [userId] is trusted input — this layer has no session/token of its own to derive identity
 * from, same as [RecurrenceRuleAccess] (DEV-010) and `EditSessionUseCase` before it. The caller
 * is responsible for sourcing it from the authenticated session, never from UI-controllable
 * state; the real backstop regardless is Postgres RLS's `auth.uid() = user_id`
 * (`/docs/security/THREAT_MODEL.md`'s Auth section) once sync reaches this table.
 */
object GoalAccess {
    suspend fun verifyOwnedGoal(
        repository: GoalRepository,
        userId: UserId,
        goalId: GoalId,
    ): AppResult<Goal> =
        repository.getById(goalId).andThen { goal ->
            when {
                goal == null -> AppResult.Failure(AppError.Validation("Goal not found", field = "goalId"))
                goal.userId != userId -> AppResult.Failure(AppError.Auth("Not authorized for this Goal"))
                // Mirrors EditSessionUseCase.verifyEditable (DEV-009): a stale offline edit must
                // never resurrect an already-deleted record (ARCHITECTURE.md §9.1/B7).
                goal.sync.isDeleted -> AppResult.Failure(AppError.Domain("Cannot modify a deleted Goal"))
                else -> AppResult.Success(goal)
            }
        }
}
