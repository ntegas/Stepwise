package com.stepwise.domain.usecase

import com.stepwise.core.common.AppResult
import com.stepwise.core.common.Clock
import com.stepwise.core.common.IdGenerator
import com.stepwise.core.model.Milestone
import com.stepwise.core.model.MilestoneId
import com.stepwise.core.model.SyncMetadata
import com.stepwise.domain.repository.GoalRepository
import com.stepwise.domain.repository.MilestoneRepository

/**
 * Adds a new [Milestone] under an owned [com.stepwise.core.model.Goal] (`/ROADMAP.md` DEV-011:
 * "milestones... relationships").
 *
 * **No weight-sum-to-100 validation** — `/ARCHITECTURE.md` §6/B6's own weighted-completion
 * formula (`Σ(progress × weight) / Σ(weight)`) divides by the weight sum, making every
 * [Milestone.weight] a purely relative share rather than a percentage required to total 100.
 * No canonical doc states such a rule; inventing one here would be guessing past a design that
 * already doesn't need it. [weight] itself is still sanity-checked ([MilestoneValidation]) since
 * a non-finite/non-positive weight would poison that same division.
 */
class AddMilestoneUseCase(
    private val goalRepository: GoalRepository,
    private val milestoneRepository: MilestoneRepository,
    private val idGenerator: IdGenerator,
    private val clock: Clock,
) {
    suspend fun execute(command: AddMilestoneCommand): AppResult<Milestone> =
        GoalAccess.verifyOwnedGoal(goalRepository, command.userId, command.goalId).andThen {
            MilestoneValidation.validateWeight(command.weight).andThen {
                val milestone =
                    Milestone(
                        id = MilestoneId(idGenerator.newId()),
                        goalId = command.goalId,
                        title = command.title,
                        sortOrder = command.sortOrder,
                        weight = command.weight,
                        isBinary = command.isBinary,
                        completed = false,
                        progressPercent = if (command.isBinary) null else INITIAL_PROGRESS_PERCENT,
                        sync = SyncMetadata(updatedAt = clock.now(), version = 1, deletedAt = null),
                    )
                milestoneRepository.upsert(milestone).map { milestone }
            }
        }

    private companion object {
        const val INITIAL_PROGRESS_PERCENT = 0.0
    }
}
