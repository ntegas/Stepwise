package com.stepwise.domain.usecase

import com.stepwise.core.common.AppResult
import com.stepwise.core.common.Clock
import com.stepwise.core.common.IdGenerator
import com.stepwise.core.model.GoalId
import com.stepwise.core.model.SyncMetadata
import com.stepwise.core.model.Task
import com.stepwise.core.model.TaskId
import com.stepwise.core.model.UserId
import com.stepwise.domain.repository.GoalRepository
import com.stepwise.domain.repository.LifeAreaRepository
import com.stepwise.domain.repository.ProjectRepository
import com.stepwise.domain.repository.TaskRepository

/**
 * Creates a new [Task] (`/ROADMAP.md` DEV-013) — verifies an attached `goalId`/`projectId`/
 * `lifeAreaId` belongs to the same caller first ([GoalAccess]/[CrossEntityOwnership], the same
 * DEV-011/012-established defense-in-depth every other optional-attachment field in this
 * codebase already gets), and that [plannedResult][CreateTaskCommand.plannedResult] is sane
 * ([TaskValidation]).
 *
 * **Never derives or writes a Session/execution-status field** — `/core/model/.../Task.kt`'s own
 * KDoc states "Task has no independently-editable actual-execution field" (ADR B1); this Use
 * Case only ever writes the plan, never the outcome.
 */
class CreateTaskUseCase(
    private val taskRepository: TaskRepository,
    private val goalRepository: GoalRepository,
    private val projectRepository: ProjectRepository,
    private val lifeAreaRepository: LifeAreaRepository,
    private val idGenerator: IdGenerator,
    private val clock: Clock,
) {
    suspend fun execute(command: CreateTaskCommand): AppResult<Task> =
        TaskValidation.validatePlannedResult(command.plannedResult).andThen {
            verifyAttachments(command).andThen {
                val task = buildTask(command)
                taskRepository.upsert(task).map { task }
            }
        }

    private suspend fun verifyAttachments(command: CreateTaskCommand): AppResult<Unit> =
        verifyGoal(command.userId, command.goalId).andThen {
            CrossEntityOwnership.verifyProject(projectRepository, command.userId, command.projectId).andThen {
                CrossEntityOwnership.verifyLifeArea(lifeAreaRepository, command.userId, command.lifeAreaId)
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

    private fun buildTask(command: CreateTaskCommand): Task =
        Task(
            id = TaskId(idGenerator.newId()),
            userId = command.userId,
            goalId = command.goalId,
            projectId = command.projectId,
            lifeAreaId = command.lifeAreaId,
            title = command.title,
            date = command.date,
            time = command.time,
            deadline = command.deadline,
            durationMinutes = command.durationMinutes,
            priority = command.priority,
            reminderId = command.reminderId,
            metricId = command.metricId,
            plannedResult = command.plannedResult,
            goalImpactScore = command.goalImpactScore,
            archived = false,
            createdAt = clock.now(),
            sync = SyncMetadata(updatedAt = clock.now(), version = 1, deletedAt = null),
        )
}
