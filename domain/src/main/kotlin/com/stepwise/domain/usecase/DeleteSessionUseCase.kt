package com.stepwise.domain.usecase

import com.stepwise.core.common.AppError
import com.stepwise.core.common.AppResult
import com.stepwise.core.common.Clock
import com.stepwise.core.model.Session
import com.stepwise.core.model.SessionId
import com.stepwise.core.model.UserId
import com.stepwise.domain.repository.SessionRepository

/**
 * Tombstones a Session (`/ARCHITECTURE.md` §19.3's execution-command set, "Session...delete") —
 * a delete is [com.stepwise.core.model.SyncMetadata.deletedAt] being set, never a row actually
 * removed (`/ARCHITECTURE.md` §9.1/ADR-006: "a stale offline device's edit to an already-deleted
 * record doesn't resurrect it"). Reuses [SessionRepository.upsertSessionWithMetricValues]
 * (DEV-008) with an empty metric-value list — the existing `session_metric_values` rows are left
 * as they are; only the Session's own tombstone marker changes, matching what
 * `enforce_sync_metadata()`/RLS already assume about a deleted row still existing underneath.
 */
class DeleteSessionUseCase(
    private val sessionRepository: SessionRepository,
    private val clock: Clock,
) {
    suspend fun execute(
        userId: UserId,
        sessionId: SessionId,
    ): AppResult<Unit> =
        sessionRepository.getById(sessionId).andThen { existing ->
            verifyDeletable(existing, userId)
        }.andThen { session ->
            tombstone(session)
        }

    private fun verifyDeletable(
        session: Session?,
        userId: UserId,
    ): AppResult<Session> =
        when {
            session == null -> AppResult.Failure(AppError.Validation("Session not found", field = "sessionId"))
            session.userId != userId -> AppResult.Failure(AppError.Auth("Not authorized for this Session"))
            else -> AppResult.Success(session)
        }

    private suspend fun tombstone(session: Session): AppResult<Unit> {
        val deleted = session.copy(sync = session.sync.copy(updatedAt = clock.now(), deletedAt = clock.now()))
        return sessionRepository.upsertSessionWithMetricValues(deleted, metricValues = emptyList())
    }
}
