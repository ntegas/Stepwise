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
 * @param clientEventId a defense-in-depth submission marker, unique per user (concept §60) —
 *   **not** the idempotency key. Decision B4/`/ARCHITECTURE.md` §19.4 (corrected here at DEV-009,
 *   after `/DATA_MODEL.md`'s own `sessions` section overstated this the same way) are explicit
 *   that [id] itself is the real idempotency key: every sync upsert/compare-and-swap is keyed on
 *   it, including the deterministic derivation [SessionIdentity] gives a completion. This field
 *   only guards against a genuinely duplicated submission reaching the server under a different
 *   [id] (e.g. a client bug), which nothing else would catch.
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
