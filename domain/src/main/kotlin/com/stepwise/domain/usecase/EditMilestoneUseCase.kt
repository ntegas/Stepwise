package com.stepwise.domain.usecase

import com.stepwise.core.common.AppError
import com.stepwise.core.common.AppResult
import com.stepwise.core.common.Clock
import com.stepwise.core.model.GoalId
import com.stepwise.core.model.Milestone
import com.stepwise.domain.repository.GoalRepository
import com.stepwise.domain.repository.MilestoneRepository

/**
 * Edits an existing [Milestone]'s own fields (`/ROADMAP.md` DEV-011). [Milestone.isBinary] is
 * deliberately not editable here — see [EditMilestoneCommand]'s own KDoc.
 */
class EditMilestoneUseCase(
    private val goalRepository: GoalRepository,
    private val milestoneRepository: MilestoneRepository,
    private val clock: Clock,
) {
    suspend fun execute(command: EditMilestoneCommand): AppResult<Milestone> =
        GoalAccess
            .verifyOwnedGoal(goalRepository, command.userId, command.goalId)
            .andThen { loadAndVerifyMilestone(command) }
            .andThen { existing -> writeEdit(existing, command) }

    private suspend fun loadAndVerifyMilestone(command: EditMilestoneCommand): AppResult<Milestone> =
        milestoneRepository.getById(command.milestoneId).andThen { existing ->
            verifyBelongsToGoal(existing, command.goalId)
        }

    private fun verifyBelongsToGoal(
        milestone: Milestone?,
        goalId: GoalId,
    ): AppResult<Milestone> =
        when {
            milestone == null -> AppResult.Failure(AppError.Validation("Milestone not found", field = "milestoneId"))
            milestone.goalId != goalId ->
                AppResult.Failure(AppError.Validation("Milestone belongs to a different Goal", field = "milestoneId"))
            milestone.sync.isDeleted -> AppResult.Failure(AppError.Domain("Cannot modify a deleted Milestone"))
            else -> AppResult.Success(milestone)
        }

    private suspend fun writeEdit(
        existing: Milestone,
        command: EditMilestoneCommand,
    ): AppResult<Milestone> {
        val newWeight = command.weight ?: existing.weight
        // Milestone.kt's own KDoc: completed is meaningful only when isBinary, progressPercent
        // only when !isBinary - only the field matching the Milestone's own isBinary is ever
        // written, so a mismatched pairing can never be persisted through this edit.
        val newProgressPercent =
            if (existing.isBinary) existing.progressPercent else command.progressPercent ?: existing.progressPercent
        return MilestoneValidation.validateWeight(newWeight).andThen {
            MilestoneValidation.validateProgressPercent(newProgressPercent).andThen {
                val updated =
                    existing.copy(
                        title = command.title ?: existing.title,
                        sortOrder = command.sortOrder ?: existing.sortOrder,
                        weight = newWeight,
                        completed =
                            if (existing.isBinary) command.completed ?: existing.completed else existing.completed,
                        progressPercent = newProgressPercent,
                        sync = existing.sync.copy(updatedAt = clock.now()),
                    )
                milestoneRepository.upsert(updated).map { updated }
            }
        }
    }
}
