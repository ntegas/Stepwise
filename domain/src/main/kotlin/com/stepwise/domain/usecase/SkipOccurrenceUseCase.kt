package com.stepwise.domain.usecase

import com.stepwise.core.common.AppError
import com.stepwise.core.common.AppResult
import com.stepwise.core.common.Clock
import com.stepwise.core.model.OccurrenceIdentity
import com.stepwise.core.model.RecurrenceOccurrence
import com.stepwise.core.model.RecurrenceOccurrenceId
import com.stepwise.core.model.RecurrenceRuleId
import com.stepwise.core.model.SyncMetadata
import com.stepwise.core.model.UserId
import com.stepwise.domain.repository.RecurrenceOccurrenceRepository
import com.stepwise.domain.repository.RecurrenceRuleRepository
import kotlinx.datetime.LocalDate

/**
 * Marks one occurrence as deliberately never-going-to-happen — a distinct axis from any
 * [com.stepwise.core.model.ExecutionStatus] (`/core/model/.../Enums.kt`: DONE/PARTIAL/MISSED/
 * RESCHEDULED/IN_PROGRESS/CANCELLED, no "skipped" among them). Skipping never creates a
 * [com.stepwise.core.model.Session] — the execution fact [CompleteOccurrenceUseCase] (DEV-009)
 * owns exclusively — because "this never needed to happen" is a different claim than "it was
 * supposed to happen and didn't" (MISSED). [RecurrenceOccurrence.skipped] is its own field for
 * exactly this reason.
 *
 * Shares [RecurrenceRuleAccess.verifyOwnedRule] with [CompleteOccurrenceUseCase] rather than
 * re-deriving the same ownership check a second time.
 *
 * **Reads-then-writes atomically via [RecurrenceOccurrenceRepository.upsertAtomically]**, not a
 * separate `getById` + `upsert` — a real HIGH found at DEV-010 review (adversarial/logic
 * reviewers): a `CompleteOccurrenceUseCase` completion landing between a separate read and write
 * could race this use case's own "can't skip a completed occurrence" guard, either silently
 * discarding the completion's session link or letting the guard be bypassed by timing alone.
 */
class SkipOccurrenceUseCase(
    private val recurrenceRuleRepository: RecurrenceRuleRepository,
    private val recurrenceOccurrenceRepository: RecurrenceOccurrenceRepository,
    private val clock: Clock,
) {
    suspend fun execute(
        userId: UserId,
        recurrenceRuleId: RecurrenceRuleId,
        occurrenceDate: LocalDate,
    ): AppResult<Unit> =
        RecurrenceRuleAccess
            .verifyOwnedRule(recurrenceRuleRepository, userId, recurrenceRuleId)
            .andThen { skipAtomically(recurrenceRuleId, occurrenceDate) }
            .map { }

    private suspend fun skipAtomically(
        recurrenceRuleId: RecurrenceRuleId,
        occurrenceDate: LocalDate,
    ): AppResult<RecurrenceOccurrence> {
        val occurrenceId = OccurrenceIdentity.deterministicId(recurrenceRuleId, occurrenceDate)
        return recurrenceOccurrenceRepository.upsertAtomically(occurrenceId) { existing ->
            buildSkipped(recurrenceRuleId, occurrenceDate, occurrenceId, existing)
        }
    }

    private fun buildSkipped(
        recurrenceRuleId: RecurrenceRuleId,
        occurrenceDate: LocalDate,
        occurrenceId: RecurrenceOccurrenceId,
        existing: RecurrenceOccurrence?,
    ): AppResult<RecurrenceOccurrence> {
        if (existing?.sessionId != null) {
            return AppResult.Failure(AppError.Domain("Cannot skip an occurrence that already has a completion"))
        }
        val sync = existing?.sync?.copy(updatedAt = clock.now()) ?: freshSync()
        return AppResult.Success(
            RecurrenceOccurrence(
                id = occurrenceId,
                recurrenceRuleId = recurrenceRuleId,
                occurrenceDate = occurrenceDate,
                rescheduledToDate = existing?.rescheduledToDate,
                skipped = true,
                sessionId = null,
                sync = sync,
            ),
        )
    }

    private fun freshSync(): SyncMetadata = SyncMetadata(updatedAt = clock.now(), version = 1, deletedAt = null)
}
