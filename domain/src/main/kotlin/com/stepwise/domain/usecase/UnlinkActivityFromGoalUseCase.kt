package com.stepwise.domain.usecase

import com.stepwise.core.common.AppError
import com.stepwise.core.common.AppResult
import com.stepwise.core.common.Clock
import com.stepwise.core.model.GoalActivityLink
import com.stepwise.core.model.GoalActivityLinkId
import com.stepwise.core.model.UserId
import com.stepwise.domain.repository.GoalActivityLinkRepository
import com.stepwise.domain.repository.GoalRepository

/**
 * Removes a [GoalActivityLink] (`/ROADMAP.md` DEV-012) — a tombstone `upsert`
 * (`SyncMetadata.deletedAt` set), not a second delete path, same convention every other entity
 * in this codebase uses (`/DATA_MODEL.md`'s tombstone model, `/ANTI_ERROR_STANDARD.md` §5).
 *
 * **Deliberately checks only the link's own [GoalActivityLink.goalId] ([GoalAccess]), not the
 * source side too** (DEV-012 logic review considered re-checking both, the same dual standard
 * [LinkActivityToGoalUseCase] holds itself to — rejected here on purpose): a link's `sourceId`/
 * `goalId` ownership pairing is fixed and immutable at creation time ([LinkActivityToGoalUseCase]
 * verifies both sides there), so re-checking the source here would be redundant in the case that
 * matters for security, and actively wrong in a case that doesn't — [ActivityAccess]/[HabitAccess]
 * both reject an already-tombstoned source, which would make a link's own cleanup (unlinking it)
 * permanently impossible once its source Activity/Habit is deleted, exactly the scenario where
 * removing the now-dangling link is the correct, desired action. Checking only the Goal side
 * avoids that regression while still closing the actual security-relevant check this task's own
 * [CrossEntityOwnership]/[ActivityAccess]/[GoalAccess] precedent cares about.
 */
class UnlinkActivityFromGoalUseCase(
    private val goalRepository: GoalRepository,
    private val goalActivityLinkRepository: GoalActivityLinkRepository,
    private val clock: Clock,
) {
    suspend fun execute(
        userId: UserId,
        linkId: GoalActivityLinkId,
    ): AppResult<Unit> =
        goalActivityLinkRepository
            .getById(linkId)
            .andThen { link -> verifyLinkOwnership(userId, link) }
            .andThen { link -> tombstoneLink(link) }

    private suspend fun verifyLinkOwnership(
        userId: UserId,
        link: GoalActivityLink?,
    ): AppResult<GoalActivityLink> =
        when (link) {
            null -> AppResult.Failure(AppError.Validation("Link not found", field = "linkId"))
            else -> GoalAccess.verifyOwnedGoal(goalRepository, userId, link.goalId).map { link }
        }

    private suspend fun tombstoneLink(link: GoalActivityLink): AppResult<Unit> {
        val updated = link.copy(sync = link.sync.copy(updatedAt = clock.now(), deletedAt = clock.now()))
        return goalActivityLinkRepository.upsert(updated).map { }
    }
}
