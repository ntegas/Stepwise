package com.stepwise.core.model

import kotlin.time.Instant

/**
 * The 3 sync-metadata columns from `/DATA_MODEL.md` that carry real domain meaning and
 * so belong on the domain model itself — embedded on every entity that syncs
 * (`/ARCHITECTURE.md` §9.1). All three are **SYNC METADATA**.
 *
 * - [updatedAt] — server-assigned last-write timestamp; the basis for the low-risk-metadata
 *   last-write-wins path (§9.1). Never client-clock-derived (`/ARCHITECTURE.md` §9.1's "clock
 *   drift is irrelevant to correctness by construction").
 * - [version] — monotonically incremented on every write; the basis for the version-checked
 *   conflict path on progress-affecting entities (Sessions, materialized occurrences).
 * - [deletedAt] — tombstone marker (null = not deleted). A delete sets this rather than
 *   removing the row, so a stale offline device's edit to an already-deleted record doesn't
 *   resurrect it (§9.1).
 *
 * `id` is **not** part of this type — it's each entity's own typed ID
 * ([GoalId][com.stepwise.core.model.GoalId] etc.), the primary key, not metadata *about* the
 * entity. `sync_status` (pending/synced/conflict, `/DATA_MODEL.md`'s sync-metadata list) is
 * **deliberately excluded** too: it is **LOCAL STATE**, not a domain fact — it only tracks
 * whether *this device's* outbox has a pending write for the row, has no effect on what the
 * entity *is*, and is meaningless before the entity has ever synced. It belongs to
 * `:core:database`'s Room entity (the outbox bookkeeping layer, `/ARCHITECTURE.md` §9.1),
 * which maps down to this pure domain type at its boundary (§6) — not to `:core:model`, which
 * stays free of anything that isn't true of the entity regardless of sync state.
 */
data class SyncMetadata(
    val updatedAt: Instant,
    val version: Int,
    val deletedAt: Instant?,
) {
    /** Convenience read used across every entity's own `isDeleted` — never re-derived ad hoc. */
    val isDeleted: Boolean get() = deletedAt != null
}
