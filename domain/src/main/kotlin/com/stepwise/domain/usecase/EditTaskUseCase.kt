package com.stepwise.domain.usecase

import com.stepwise.core.common.AppResult
import com.stepwise.core.common.Clock
import com.stepwise.core.model.GoalId
import com.stepwise.core.model.LifeAreaId
import com.stepwise.core.model.ProjectId
import com.stepwise.core.model.Task
import com.stepwise.core.model.UserId
import com.stepwise.domain.repository.GoalRepository
import com.stepwise.domain.repository.LifeAreaRepository
import com.stepwise.domain.repository.ProjectRepository
import com.stepwise.domain.repository.TaskRepository

/**
 * Edits an existing [Task]'s own fields (`/ROADMAP.md` DEV-013) — never a Session/execution
 * field, same reasoning [CreateTaskUseCase] states. Re-verifies [goalId][EditTaskCommand.goalId]/
 * [projectId][EditTaskCommand.projectId]/[lifeAreaId][EditTaskCommand.lifeAreaId] ownership on
 * the resulting Task the same way [EditGoalUseCase] does, since an edit can re-point any of them.
 */
class EditTaskUseCase(
    private val taskRepository: TaskRepository,
    private val goalRepository: GoalRepository,
    private val projectRepository: ProjectRepository,
    private val lifeAreaRepository: LifeAreaRepository,
    private val clock: Clock,
) {
    suspend fun execute(command: EditTaskCommand): AppResult<Task> =
        TaskAccess.verifyOwnedTask(taskRepository, command.userId, command.taskId).andThen { existing ->
            writeEdit(existing, command)
        }

    private suspend fun writeEdit(
        existing: Task,
        command: EditTaskCommand,
    ): AppResult<Task> {
        val updated = applyEdits(existing, command)
        return TaskValidation.validatePlannedResult(updated.plannedResult).andThen {
            verifyAttachments(updated.userId, updated.goalId, updated.projectId, updated.lifeAreaId).andThen {
                taskRepository.upsert(updated).map { updated }
            }
        }
    }

    private suspend fun verifyAttachments(
        userId: UserId,
        goalId: GoalId?,
        projectId: ProjectId?,
        lifeAreaId: LifeAreaId?,
    ): AppResult<Unit> =
        verifyGoal(userId, goalId).andThen {
            CrossEntityOwnership.verifyProject(projectRepository, userId, projectId).andThen {
                CrossEntityOwnership.verifyLifeArea(lifeAreaRepository, userId, lifeAreaId)
            }
        }

    private suspend fun verifyGoal(
        userId: UserId,
        goalId: GoalId?,
    ): AppResult<Unit> =
        if (goalId == null) {
            AppResult.Success(Unit)
        } else {
            GoalAccess.verifyOwnedGoal(goalRepository, userId, goalId).map { }
        }

    private fun applyEdits(
        existing: Task,
        command: EditTaskCommand,
    ): Task =
        existing.copy(
            title = command.title ?: existing.title,
            goalId = command.goalId ?: existing.goalId,
            projectId = command.projectId ?: existing.projectId,
            lifeAreaId = command.lifeAreaId ?: existing.lifeAreaId,
            date = if (command.clearDate) null else (command.date ?: existing.date),
            time = command.time ?: existing.time,
            deadline = command.deadline ?: existing.deadline,
            durationMinutes = command.durationMinutes ?: existing.durationMinutes,
            priority = command.priority ?: existing.priority,
            reminderId = command.reminderId ?: existing.reminderId,
            metricId = command.metricId ?: existing.metricId,
            plannedResult = command.plannedResult ?: existing.plannedResult,
            goalImpactScore = command.goalImpactScore ?: existing.goalImpactScore,
            archived = command.archived ?: existing.archived,
            sync = existing.sync.copy(updatedAt = clock.now()),
        )
}
