package com.stepwise.domain.usecase

import com.stepwise.core.common.AppResult
import com.stepwise.core.common.Clock
import com.stepwise.core.model.Goal
import com.stepwise.core.model.LifeAreaId
import com.stepwise.core.model.ProjectId
import com.stepwise.core.model.UserId
import com.stepwise.domain.repository.GoalRepository
import com.stepwise.domain.repository.LifeAreaRepository
import com.stepwise.domain.repository.ProjectRepository

/**
 * Edits an existing [Goal]'s own fields (`/ROADMAP.md` DEV-011) — never [Goal.status] (see
 * [UpdateGoalStatusUseCase]) and never anything in
 * [com.stepwise.domain.repository.SessionRepository] (a Goal's progress is a computed view,
 * never written here — same reasoning [CreateGoalUseCase] states). Re-verifies
 * [CrossEntityOwnership] on the resulting `lifeAreaId`/`projectId` the same way
 * [CreateGoalUseCase] does on creation, since an edit can re-point either one.
 */
class EditGoalUseCase(
    private val goalRepository: GoalRepository,
    private val lifeAreaRepository: LifeAreaRepository,
    private val projectRepository: ProjectRepository,
    private val clock: Clock,
) {
    suspend fun execute(command: EditGoalCommand): AppResult<Goal> =
        GoalAccess.verifyOwnedGoal(goalRepository, command.userId, command.goalId).andThen { existing ->
            writeEdit(existing, command)
        }

    private suspend fun writeEdit(
        existing: Goal,
        command: EditGoalCommand,
    ): AppResult<Goal> {
        val updated = applyEdits(existing, command)
        return verifyAttachments(updated.userId, updated.lifeAreaId, updated.projectId).andThen {
            GoalValidation.validate(updated.progressMode, updated.targetValue, updated.unit).andThen {
                goalRepository.upsert(updated).map { updated }
            }
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

    // Switching into a TARGET_FREE_MODES progressMode clears targetValue/unit by construction,
    // rather than requiring the caller to remember an explicit "clear" flag for that one case
    // (EditGoalCommand's own KDoc).
    private fun applyEdits(
        existing: Goal,
        command: EditGoalCommand,
    ): Goal {
        val newProgressMode = command.progressMode ?: existing.progressMode
        val clearsTarget = newProgressMode in GoalValidation.TARGET_FREE_MODES
        return existing.copy(
            title = command.title ?: existing.title,
            progressMode = newProgressMode,
            lifeAreaId = command.lifeAreaId ?: existing.lifeAreaId,
            projectId = command.projectId ?: existing.projectId,
            targetValue = if (clearsTarget) null else (command.targetValue ?: existing.targetValue),
            unit = if (clearsTarget) null else (command.unit ?: existing.unit),
            deadline = command.deadline ?: existing.deadline,
            priority = command.priority ?: existing.priority,
            category = command.category ?: existing.category,
            description = command.description ?: existing.description,
            sync = existing.sync.copy(updatedAt = clock.now()),
        )
    }
}
