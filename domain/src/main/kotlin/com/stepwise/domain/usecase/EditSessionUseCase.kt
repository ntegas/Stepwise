package com.stepwise.domain.usecase

import com.stepwise.core.common.AppError
import com.stepwise.core.common.AppResult
import com.stepwise.core.common.Clock
import com.stepwise.core.model.Session
import com.stepwise.core.model.SessionId
import com.stepwise.core.model.SessionIdentity
import com.stepwise.core.model.SessionMetricValue
import com.stepwise.core.model.UserId
import com.stepwise.domain.repository.SessionRepository

/**
 * Corrects an existing Session after the fact — notes and/or metric actual values, never its
 * identity/source/date (`/ARCHITECTURE.md` §19.3's execution-command set, "Session... edit...").
 * Reuses [SessionRepository.upsertSessionWithMetricValues] (DEV-008) rather than a second write
 * path, so an edit goes through the exact same transaction boundary and outbox enqueue a
 * completion does.
 *
 * Preserves the Session's own `sync.version` unchanged (only [com.stepwise.core.model.SyncMetadata.updatedAt]
 * is refreshed) — the same reasoning [CompleteOccurrenceUseCase] already applies: `version` only
 * ever legitimately advances via a server-confirmed push, never a local guess.
 */
class EditSessionUseCase(
    private val sessionRepository: SessionRepository,
    private val clock: Clock,
) {
    suspend fun execute(command: EditSessionCommand): AppResult<Session> =
        sessionRepository.getById(command.sessionId).andThen { existing ->
            verifyEditable(existing, command.userId)
        }.andThen { session ->
            resolveMetricValues(session, command.metricValues)
        }.andThen { (session, metricValues) ->
            writeEdit(session, command, metricValues)
        }

    private fun verifyEditable(
        session: Session?,
        userId: UserId,
    ): AppResult<Session> =
        when {
            session == null -> AppResult.Failure(AppError.Validation("Session not found", field = "sessionId"))
            session.userId != userId -> AppResult.Failure(AppError.Auth("Not authorized for this Session"))
            session.sync.isDeleted -> AppResult.Failure(AppError.Domain("Cannot edit a deleted Session"))
            else -> AppResult.Success(session)
        }

    // Editing is a correction, never a "Done=Planned" default - every entry must already carry
    // its own actual value, or the whole edit is rejected rather than silently dropping one.
    private fun resolveMetricValues(
        session: Session,
        entries: List<MetricEntry>?,
    ): AppResult<Pair<Session, List<SessionMetricValue>>> {
        if (entries == null) {
            return AppResult.Success(session to emptyList())
        }
        val missingActual = entries.firstOrNull { it.actualValue == null }
        return if (missingActual != null) {
            AppResult.Failure(
                AppError.Validation(
                    "An edited metric value needs an explicit actual (metric ${missingActual.metricId.value})",
                    field = "metricValues",
                ),
            )
        } else {
            val metricValues =
                entries.map { entry ->
                    SessionMetricValue(
                        id = SessionIdentity.deterministicMetricValueId(session.id, entry.metricId),
                        sessionId = session.id,
                        metricId = entry.metricId,
                        plannedValue = entry.plannedValue,
                        actualValue = checkNotNull(entry.actualValue),
                        sync = session.sync,
                    )
                }
            AppResult.Success(session to metricValues)
        }
    }

    private suspend fun writeEdit(
        session: Session,
        command: EditSessionCommand,
        metricValues: List<SessionMetricValue>,
    ): AppResult<Session> {
        val updated =
            session.copy(
                notes = command.notes ?: session.notes,
                sync = session.sync.copy(updatedAt = clock.now()),
            )
        return sessionRepository.upsertSessionWithMetricValues(updated, metricValues).map { updated }
    }
}

data class EditSessionCommand(
    val userId: UserId,
    val sessionId: SessionId,
    val notes: String? = null,
    // null = leave metric values untouched; a non-null list replaces them entirely, each entry
    // requiring an explicit actualValue - editing is a correction, never a "Done=Planned" default.
    val metricValues: List<MetricEntry>? = null,
)
