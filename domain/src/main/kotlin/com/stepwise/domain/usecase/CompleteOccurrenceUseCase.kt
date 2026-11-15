package com.stepwise.domain.usecase

import com.stepwise.core.common.AppError
import com.stepwise.core.common.AppResult
import com.stepwise.core.common.Clock
import com.stepwise.core.common.IdGenerator
import com.stepwise.core.model.ExecutionStatus
import com.stepwise.core.model.OccurrenceIdentity
import com.stepwise.core.model.RecurrenceOccurrence
import com.stepwise.core.model.RecurrenceOccurrenceId
import com.stepwise.core.model.Session
import com.stepwise.core.model.SessionId
import com.stepwise.core.model.SessionIdentity
import com.stepwise.core.model.SessionMetricValue
import com.stepwise.core.model.SyncMetadata
import com.stepwise.core.model.UserId
import com.stepwise.domain.repository.RecurrenceOccurrenceRepository
import com.stepwise.domain.repository.RecurrenceRuleRepository
import com.stepwise.domain.repository.SessionRepository

/**
 * The one entry point for Done/Partial/Missed/Rescheduled/In Progress/Cancelled
 * (`/ROADMAP.md` DEV-009, `/ARCHITECTURE.md` §19.3: "the one entry point... a second path
 * physically impossible to compile" — enforced here by being the only place that writes a
 * canonical execution Session at all, not by a module-graph rule this task doesn't add). A
 * ViewModel call-site and a future Widget `ActionCallback` call-site both call this same
 * function and get byte-identical results (`/DEVELOPMENT_PROTOCOL.md` rule 4).
 *
 * **Idempotency, local and not only at sync** (§19.4 extending B4/B9): [execute] never generates
 * a fresh random Session ID. [CompletionTarget]'s own identity (an occurrence's deterministic id,
 * or a one-off source+date) determines [SessionIdentity]'s output, so invoking this twice for the
 * same target — a double-tap, a retried command, two surfaces racing — resolves to the one Session
 * row [SessionRepository.upsertSessionWithMetricValues] (DEV-008) already upserts idempotently,
 * before sync is even involved. A metric value's own id is deterministic the same way
 * ([SessionIdentity.deterministicMetricValueId]), so the same re-invocation doesn't insert a
 * second metric-value row either.
 *
 * **A real CRITICAL finding, surfaced to the project owner rather than silently patched, now
 * fixed in `:core:sync` (DEV-009 review, logic reviewer)**: because two devices can now
 * independently compute the *same* deterministic Session id for the same occurrence before either
 * has synced, a device completing it with different data than another device's not-yet-seen
 * completion previously had no local way to know a conflict is even possible — both enqueue with
 * `expectedVersion = null`, which `:core:sync`'s `SyncEngine` (DEV-008) treated as a blind upsert,
 * not a CAS. Before this task, Session ids were always per-device-random, so this could never
 * happen; this task's own deterministic-id design is what reopened it. Because the fix touches
 * `:core:sync`'s already-reviewed, CI-green behavior rather than staying confined to this file,
 * it was surfaced via `AskUserQuestion` rather than decided unilaterally; the project owner chose
 * "fix it now." [SyncTransport.fetchRow][com.stepwise.core.sync.SyncTransport.fetchRow] now lets
 * `SyncEngine.pushWithNoKnownVersion` tell a harmless retry/convergence apart from a genuine
 * cross-device conflict by comparing payload content, turning the latter into a
 * `PushOutcome.Conflict` instead of a silent overwrite — see `SyncEngine`'s own KDoc for the full
 * exploit, the fix, and its one disclosed residual gap (a true simultaneous first-push race,
 * tracked in `/PROJECT_STATE.md`'s VERIFICATION DEBT, not fully closed by this fix).
 *
 * **Ownership is checked, not assumed** — for the completion target's source entity, by
 * [MetricResolver] (which loads it to read its default metric and is therefore the one place
 * already holding it to check against); for a pre-existing Session row at the same deterministic
 * id, by [verifySessionOwnership] below; for a [CompletionTarget.Recurring]'s own
 * [com.stepwise.core.model.RecurrenceRuleId], by [verifyRecurrenceRuleOwnership] below (DEV-009
 * review, security reviewer, MEDIUM — the first draft checked the source entity but never the
 * rule itself). Room has no RLS of its own (`/docs/security/SECURITY_ARCHITECTURE.md`'s RLS
 * boundary is a Postgres/Supabase concept, not a local one), so every one of these checks is this
 * module's own local defense against a local cross-user write — not a substitute for RLS, which
 * stays the real boundary once a write reaches the server.
 *
 * **In Progress is accepted, not implemented** — Timer's actual behavior (elapsed-time math,
 * resuming an already-running session, a `startedAt` field) is `/ROADMAP.md` DEV-026's job
 * (Timer Engine), explicitly later than this task. [execute] treats `IN_PROGRESS` like
 * `MISSED`/`CANCELLED`/`RESCHEDULED` for metric-value purposes (no actual-value resolution
 * applies to any of the four, [MetricResolver]'s own logic) so DEV-026 can call this same engine
 * once it exists, rather than this task inventing Timer-shaped fields ahead of that task's design.
 */
class CompleteOccurrenceUseCase(
    private val metricResolver: MetricResolver,
    private val recurrenceRuleRepository: RecurrenceRuleRepository,
    private val recurrenceOccurrenceRepository: RecurrenceOccurrenceRepository,
    private val sessionRepository: SessionRepository,
    private val idGenerator: IdGenerator,
    private val clock: Clock,
) {
    suspend fun execute(command: CompleteOccurrenceCommand): AppResult<Session> =
        validateCommand(command)
            .andThen { verifyRecurrenceRuleOwnership(command.userId, command.target) }
            .andThen { metricResolver.resolve(command.userId, command.target, command.status, command.metricValues) }
            .andThen { resolvedMetrics -> writeSession(command, resolvedMetrics) }
            .andThen { session -> linkOccurrenceIfRecurring(command, session).map { session } }

    private fun validateCommand(command: CompleteOccurrenceCommand): AppResult<Unit> {
        if (command.status == ExecutionStatus.RESCHEDULED && command.rescheduledToDate == null) {
            return AppResult.Failure(
                AppError.Validation("RESCHEDULED requires rescheduledToDate", field = "rescheduledToDate"),
            )
        }
        return AppResult.Success(Unit)
    }

    private suspend fun verifyRecurrenceRuleOwnership(
        userId: UserId,
        target: CompletionTarget,
    ): AppResult<Unit> {
        if (target !is CompletionTarget.Recurring) {
            return AppResult.Success(Unit)
        }
        return RecurrenceRuleAccess.verifyOwnedRule(recurrenceRuleRepository, userId, target.recurrenceRuleId).map { }
    }

    private suspend fun writeSession(
        command: CompleteOccurrenceCommand,
        resolvedMetrics: List<MetricEntry>,
    ): AppResult<Session> {
        val sessionId = sessionIdFor(command.target)
        return sessionRepository.getById(sessionId).andThen { existing ->
            verifySessionOwnership(existing, command.userId).andThen {
                sessionRepository.getMetricValuesForSession(sessionId).andThen { existingMetricValues ->
                    writeSessionAndMetrics(command, sessionId, existing, resolvedMetrics, existingMetricValues)
                }
            }
        }
    }

    private fun verifySessionOwnership(
        existing: Session?,
        userId: UserId,
    ): AppResult<Unit> {
        // A real MEDIUM found at DEV-009 review (security reviewer): the first draft trusted the
        // deterministic id alone to never collide across users and never actually checked this.
        if (existing != null && existing.userId != userId) {
            return AppResult.Failure(AppError.Auth("Not authorized for this Session"))
        }
        return AppResult.Success(Unit)
    }

    private suspend fun writeSessionAndMetrics(
        command: CompleteOccurrenceCommand,
        sessionId: SessionId,
        existing: Session?,
        resolvedMetrics: List<MetricEntry>,
        existingMetricValues: List<SessionMetricValue>,
    ): AppResult<Session> {
        val sync =
            existing
                ?.sync
                ?.copy(updatedAt = clock.now())
                ?: SyncMetadata(updatedAt = clock.now(), version = 1, deletedAt = null)
        val session =
            Session(
                id = sessionId,
                userId = command.userId,
                sourceType = command.target.sourceType,
                sourceId = command.target.sourceId,
                date = command.target.date,
                status = command.status,
                clientEventId = existing?.clientEventId ?: idGenerator.newId(),
                notes = command.notes,
                sync = sync,
            )
        val metricValues = mergeMetricValues(sessionId, resolvedMetrics, existingMetricValues)
        return sessionRepository.upsertSessionWithMetricValues(session, metricValues).map { session }
    }

    /**
     * [resolvedMetrics] is the *complete* current set for this status (empty for anything but
     * DONE/PARTIAL, [MetricResolver]'s own rule) — any [existingMetricValues] row for a metric
     * no longer in that set is tombstoned here rather than left behind. Real HIGH found at
     * DEV-009 review (logic reviewer): a Session first completed DONE (writing a metric value)
     * and later re-completed MISSED/CANCELLED/RESCHEDULED kept the stale DONE-era value attached
     * — an "independently surviving fact" `/DATA_MODEL.md` §62/ADR-004 forbid, since nothing
     * downstream (the future `goal_progress` view) is specified to filter it out by the parent
     * Session's current status. Each surviving/new row also carries *its own* existing
     * `sync`/version (not the Session's, a real MEDIUM the same review found) — session and
     * metric-value rows are independently versioned server-side, so borrowing the Session's own
     * version here could silently corrupt a metric value's local version bookkeeping the same way
     * [SessionRepositoryImpl][com.stepwise.data.repository.SessionRepositoryImpl]'s own
     * now-fixed TOCTOU bug did for the Session itself.
     */
    private fun mergeMetricValues(
        sessionId: SessionId,
        resolvedMetrics: List<MetricEntry>,
        existingMetricValues: List<SessionMetricValue>,
    ): List<SessionMetricValue> {
        val resolvedMetricIds = resolvedMetrics.map { it.metricId }.toSet()
        val tombstoned =
            existingMetricValues
                .filter { it.metricId !in resolvedMetricIds && !it.sync.isDeleted }
                .map { it.copy(sync = it.sync.copy(updatedAt = clock.now(), deletedAt = clock.now())) }
        val current =
            resolvedMetrics.map { entry ->
                val existingValue = existingMetricValues.firstOrNull { it.metricId == entry.metricId }
                val metricSync =
                    existingValue
                        ?.sync
                        ?.copy(updatedAt = clock.now(), deletedAt = null)
                        ?: SyncMetadata(updatedAt = clock.now(), version = 1, deletedAt = null)
                SessionMetricValue(
                    id = SessionIdentity.deterministicMetricValueId(sessionId, entry.metricId),
                    sessionId = sessionId,
                    metricId = entry.metricId,
                    plannedValue = entry.plannedValue,
                    actualValue =
                        requireNotNull(entry.actualValue) {
                            "MetricResolver must have already resolved every entry's actualValue"
                        },
                    sync = metricSync,
                )
            }
        return current + tombstoned
    }

    private fun sessionIdFor(target: CompletionTarget): SessionId =
        when (target) {
            is CompletionTarget.OneOff ->
                SessionIdentity.deterministicId(target.sourceType, target.sourceId, target.date)
            is CompletionTarget.Recurring -> {
                val occurrenceId = OccurrenceIdentity.deterministicId(target.recurrenceRuleId, target.date)
                SessionIdentity.deterministicId(occurrenceId)
            }
        }

    /**
     * [RecurrenceOccurrence.sessionId] is "set once this occurrence has been executed"
     * (`/core/model/.../Schedule.kt`'s own KDoc) — a best-effort follow-up write, deliberately not
     * in the same Room transaction as [writeSession] (that transaction's boundary is DEV-008's,
     * scoped to the Session + its metric values, not a third unrelated entity). If the app died
     * between the two writes, re-running the same idempotent completion fixes the link — no
     * duplicate Session is created either way, so this is a recoverable gap, not a correctness one.
     *
     * **Reads-then-writes atomically via [RecurrenceOccurrenceRepository.upsertAtomically]** — a
     * real HIGH found at DEV-010 review (adversarial/security/logic reviewers, independently):
     * the prior separate `getById`/`upsert` pair could race [SkipOccurrenceUseCase]'s own
     * read-then-write against the same row, in either direction (a completion's link silently
     * discarded by a stale skip write, or a skip silently reverted by a stale completion write).
     * `upsertAtomically` closes that the same way it closes the symmetric race on Skip's side.
     *
     * **A completion clears a stale [RecurrenceOccurrence.skipped] flag** — a real HIGH found at
     * DEV-010 review (adversarial/logic reviewers): the prior code copied `existing.skipped`
     * forward unchanged, so completing an occurrence a user had previously skipped left a row
     * simultaneously "never happening" (`skipped = true`) and "already happened" (a real
     * [Session] attached) — a state neither use case's own documented invariant allows. Completing
     * is the more recent, more specific action, so it supersedes an earlier skip rather than
     * coexisting with it.
     */
    private suspend fun linkOccurrenceIfRecurring(
        command: CompleteOccurrenceCommand,
        session: Session,
    ): AppResult<Unit> {
        val target = command.target
        if (target !is CompletionTarget.Recurring) {
            return AppResult.Success(Unit)
        }
        val occurrenceId = OccurrenceIdentity.deterministicId(target.recurrenceRuleId, target.date)
        return recurrenceOccurrenceRepository
            .upsertAtomically(occurrenceId) { existing ->
                AppResult.Success(buildLinkedOccurrence(command, target, occurrenceId, session, existing))
            }.map { }
    }

    private fun buildLinkedOccurrence(
        command: CompleteOccurrenceCommand,
        target: CompletionTarget.Recurring,
        occurrenceId: RecurrenceOccurrenceId,
        session: Session,
        existing: RecurrenceOccurrence?,
    ): RecurrenceOccurrence {
        val base =
            existing ?: RecurrenceOccurrence(
                id = occurrenceId,
                recurrenceRuleId = target.recurrenceRuleId,
                occurrenceDate = target.date,
                rescheduledToDate = null,
                skipped = false,
                sessionId = null,
                sync = SyncMetadata(updatedAt = clock.now(), version = 1, deletedAt = null),
            )
        return base.copy(
            sessionId = session.id,
            rescheduledToDate = command.rescheduledToDate ?: base.rescheduledToDate,
            skipped = false,
        )
    }
}
