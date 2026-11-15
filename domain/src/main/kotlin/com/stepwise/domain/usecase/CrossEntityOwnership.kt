package com.stepwise.domain.usecase

import com.stepwise.core.common.AppError
import com.stepwise.core.common.AppResult
import com.stepwise.core.model.ActivityId
import com.stepwise.core.model.LifeAreaId
import com.stepwise.core.model.ProjectId
import com.stepwise.core.model.UserId
import com.stepwise.domain.repository.ActivityRepository
import com.stepwise.domain.repository.LifeAreaRepository
import com.stepwise.domain.repository.ProjectRepository

/**
 * Verifies a [com.stepwise.core.model.Goal]'s optional `lifeAreaId`/`projectId` actually belong
 * to the same caller before it's attached — a gap the DEV-011 adversarial review flagged: the
 * Postgres FK on `goals.life_area_id`/`goals.project_id` is satisfied as long as the row exists
 * at all, RLS on `goals` only checks the Goal's own `userId`, so nothing server-side stops a
 * Goal being attached to another user's Life Area/Project (`/docs/security/THREAT_MODEL.md`'s
 * sync section already closes this exact shape of gap for `sessions.source_id`/
 * `schedules.owner_id` with dedicated ownership-consistency triggers — Life Area/Project weren't
 * in that list). Checked here, client-side, as defense in depth; not itself the security
 * boundary (RLS is — `/docs/security/SECURITY_ARCHITECTURE.md`). Also rejects attaching to an
 * already-tombstoned Life Area/Project (DEV-012 review, logic reviewer — this object itself was
 * missing the tombstone check [GoalAccess]/[ActivityAccess]/[HabitAccess] all have, a gap DEV-011's
 * own Goal/Project consumers inherited silently until DEV-012's Activity consumer surfaced it).
 */
object CrossEntityOwnership {
    suspend fun verifyLifeArea(
        repository: LifeAreaRepository,
        userId: UserId,
        lifeAreaId: LifeAreaId?,
    ): AppResult<Unit> =
        if (lifeAreaId == null) {
            AppResult.Success(Unit)
        } else {
            repository.getById(lifeAreaId).andThen { lifeArea ->
                when {
                    lifeArea == null ->
                        AppResult.Failure(AppError.Validation("Life Area not found", field = "lifeAreaId"))
                    lifeArea.userId != userId -> AppResult.Failure(AppError.Auth("Not authorized for this Life Area"))
                    lifeArea.sync.isDeleted ->
                        AppResult.Failure(AppError.Domain("Cannot attach to a deleted Life Area"))
                    else -> AppResult.Success(Unit)
                }
            }
        }

    suspend fun verifyProject(
        repository: ProjectRepository,
        userId: UserId,
        projectId: ProjectId?,
    ): AppResult<Unit> =
        if (projectId == null) {
            AppResult.Success(Unit)
        } else {
            repository.getById(projectId).andThen { project ->
                when {
                    project == null -> AppResult.Failure(AppError.Validation("Project not found", field = "projectId"))
                    project.userId != userId -> AppResult.Failure(AppError.Auth("Not authorized for this Project"))
                    project.sync.isDeleted -> AppResult.Failure(AppError.Domain("Cannot attach to a deleted Project"))
                    else -> AppResult.Success(Unit)
                }
            }
        }

    // Delegates to ActivityAccess rather than re-implementing the same not-found/wrong-owner/
    // tombstoned check a third way - DEV-013, for Habit.activityId ("the what this Habit is a
    // regularity rule for"). Unlike lifeAreaId/projectId, Activity already has its own dedicated
    // *Access object (needed elsewhere too), so this wrapper just adds the null-means-optional
    // convention every CrossEntityOwnership consumer already expects.
    suspend fun verifyActivity(
        repository: ActivityRepository,
        userId: UserId,
        activityId: ActivityId?,
    ): AppResult<Unit> =
        if (activityId == null) {
            AppResult.Success(Unit)
        } else {
            ActivityAccess.verifyOwnedActivity(repository, userId, activityId).map { }
        }
}
