package com.stepwise.domain.usecase

import com.stepwise.core.common.AppError
import com.stepwise.core.common.AppResult
import com.stepwise.core.model.LifeAreaId
import com.stepwise.core.model.ProjectId
import com.stepwise.core.model.UserId
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
 * boundary (RLS is — `/docs/security/SECURITY_ARCHITECTURE.md`).
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
                    else -> AppResult.Success(Unit)
                }
            }
        }
}
