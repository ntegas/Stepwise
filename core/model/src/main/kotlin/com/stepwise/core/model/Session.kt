package com.stepwise.core.model

import kotlinx.datetime.LocalDate

/**
 * The record of one real occurrence (concept §22) — the domain event source of truth
 * (`/ARCHITECTURE.md` §7): this *is* the fact of what happened, whether it currently lives
 * only on-device or has reached the backend. All fields **CANONICAL** except [id]/[sync].
 *
 * @param sourceId intentionally a raw `String`, not [TaskId]/[ActivityId]/[HabitId] directly —
 *   same polymorphic-owner reasoning as [RecurrenceRule.ownerId]; [sourceType] is the
 *   discriminant.
 * @param clientEventId the idempotency key (concept §60, decision B4) — unique per user; a
 *   duplicate submission from a flaky connection with the same [clientEventId] upserts instead
 *   of double-counting. Distinct from [id]/[SyncMetadata.version]: this is a domain-level
 *   dedup key the *client* controls, not sync bookkeeping.
 * @param status **CANONICAL here** — this is the one place [ExecutionStatus] is genuinely
 *   stored, not derived (see [ExecutionStatus]'s own KDoc, and [Task]'s explicit note that its
 *   own `status` is instead a read projection off this field).
 */
data class Session(
    val id: SessionId,
    val userId: UserId,
    val sourceType: ExecutableKind,
    val sourceId: String,
    val date: LocalDate,
    val status: ExecutionStatus,
    val clientEventId: String,
    val notes: String?,
    val sync: SyncMetadata,
)

/**
 * One metric reading on a [Session] — a Session can carry more than one (concept §23: Running
 * has both duration and distance). All fields **CANONICAL** except [id]/[sync].
 *
 * Editing or deleting this row is what §61 ("edit/delete recomputation") flows from: the
 * `goal_progress` view (`/DATA_MODEL.md`) aggregates over these rows directly, so a correction
 * here changes what every downstream Goal-progress read sees, with no separate recompute step.
 */
data class SessionMetricValue(
    val id: SessionMetricValueId,
    val sessionId: SessionId,
    val metricId: MetricId,
    val plannedValue: Double?,
    val actualValue: Double,
    val sync: SyncMetadata,
)
