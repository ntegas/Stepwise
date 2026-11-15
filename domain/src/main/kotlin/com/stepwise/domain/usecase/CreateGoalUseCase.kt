package com.stepwise.domain.usecase

import com.stepwise.core.common.AppResult
import com.stepwise.core.common.Clock
import com.stepwise.core.common.IdGenerator
import com.stepwise.core.model.Goal
import com.stepwise.core.model.GoalId
import com.stepwise.core.model.LifeAreaId
import com.stepwise.core.model.LifecycleStatus
import com.stepwise.core.model.ProjectId
import com.stepwise.core.model.SyncMetadata
import com.stepwise.core.model.UserId
import com.stepwise.domain.repository.GoalRepository
import com.stepwise.domain.repository.LifeAreaRepository
import com.stepwise.domain.repository.ProjectRepository

/**
 * Creates a new [Goal] (`/ROADMAP.md` DEV-011) — the one place a Goal's
 * `progressMode`/`targetValue`/`unit` consistency is checked before the first write
 * ([GoalValidation]), and that an attached [lifeAreaId]/[projectId] actually belongs to the
 * same caller ([CrossEntityOwnership] — the DEV-011 adversarial review's own finding).
 *
 * **Never touches [com.stepwise.domain.repository.SessionRepository]** — a Goal's progress is
 * a computed view over Sessions, never a field this use case writes
 * (`/core/model/.../Goal.kt`'s own KDoc, ADR/`/ARCHITECTURE.md` §6's "no second progress
 * source"). Satisfying "изменение target не повреждает Sessions" (DEV-011's own acceptance
 * criterion) here means simply never reaching for that repository at all, not a guard to write.
 */
class CreateGoalUseCase(
    private val goalRepository: GoalRepository,
    private val lifeAreaRepository: LifeAreaRepository,
    private val projectRepository: ProjectRepository,
    private val idGenerator: IdGenerator,
    private val clock: Clock,
) {
    suspend fun execute(command: CreateGoalCommand): AppResult<Goal> =
        GoalValidation.validate(command.progressMode, command.targetValue, command.unit).andThen {
            verifyAttachments(command.userId, command.lifeAreaId, command.projectId).andThen {
                val goal = buildGoal(command)
                goalRepository.upsert(goal).map { goal }
            }
        }

    private suspend fun verifyAttachments(
        userId: UserId,
        lifeAreaId: LifeAreaId?,
        projectId: ProjectId?,
    ): AppResult<Unit> =
        CrossEntityOwnership.verifyLifeArea(lifeAreaRepository, userId, lifeAreaId).andThen {
            CrossEntityOwnership.verifyProject(projectRepository, userId, projectId)
        }

    private fun buildGoal(command: CreateGoalCommand): Goal =
        Goal(
            id = GoalId(idGenerator.newId()),
            userId = command.userId,
            lifeAreaId = command.lifeAreaId,
            projectId = command.projectId,
            title = command.title,
            type = command.type,
            progressMode = command.progressMode,
            targetValue = command.targetValue,
            unit = command.unit,
            deadline = command.deadline,
            priority = command.priority,
            category = command.category,
            description = command.description,
            status = LifecycleStatus.ACTIVE,
            createdAt = clock.now(),
            sync = SyncMetadata(updatedAt = clock.now(), version = 1, deletedAt = null),
        )
}
