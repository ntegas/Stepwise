package com.stepwise.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlin.time.Instant

/**
 * One pending (or failed, still-retrying) write destined for Supabase (`/ARCHITECTURE.md` §9.1,
 * DEV-008) — written in the *same* Room transaction as the local mutation it describes
 * (`:data`'s repository implementations, DEV-006's established write-path "seam"), so the
 * outbox can never desync from what was actually committed locally.
 *
 * Keyed by `(tableName, entityId)`, not a separate synthetic id — a deliberate collapsing
 * design, not the literal "one outbox row per mutation event" reading of `/ARCHITECTURE.md`
 * §9.1: if the same row is edited twice before sync drains the first write, only the *latest*
 * payload ever needs to reach the server — an intermediate state nobody else has seen has no
 * reason to round-trip. `REPLACE`-on-conflict at [OutboxDao.enqueue] achieves this without the
 * outbox growing unboundedly for a frequently-edited row.
 *
 * @param payload the row's current state as a JSON object whose keys already match the Postgres
 *   column names — `:core:network`'s `PostgrestSyncClient` does no field-name translation.
 * @param expectedVersion null for a row never before synced (plain insert path); otherwise the
 *   locally-known server `version`, used as the compare-and-swap filter for progress-affecting
 *   tables ([isProgressAffecting]). Metadata-tier tables ignore this — `PostgrestSyncClient.upsert`
 *   is a blind overwrite (`/ARCHITECTURE.md` §9.1's low-risk tier, ADR-006).
 * @param ownerUserId **not** sent to Postgrest as part of [payload] — purely for building a
 *   `conflict_copies` row on a lost CAS. Most progress-affecting tables carry `user_id` directly
 *   in [payload], but `session_metric_values` deliberately doesn't (nested ownership via
 *   `session_id`, DEV-007) and `conflict_copies.user_id` is `NOT NULL` regardless — this field is
 *   how `OutboxDrainer` gets an owner for a table whose own payload has none.
 */
@Entity(tableName = "outbox", primaryKeys = ["tableName", "entityId"])
data class OutboxEntryEntity(
    val tableName: String,
    val entityId: String,
    val payload: String,
    val expectedVersion: Int?,
    val isProgressAffecting: Boolean,
    val ownerUserId: String,
    val createdAt: Instant,
    val attemptCount: Int = 0,
    val lastError: String? = null,
)

@Dao
interface OutboxDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(entry: OutboxEntryEntity)

    @Query("SELECT * FROM outbox ORDER BY createdAt ASC")
    suspend fun getAll(): List<OutboxEntryEntity>

    /**
     * Real bug found and fixed during DEV-008 review (logic reviewer, CRITICAL): a plain
     * `@Delete` matches by primary key (`tableName`+`entityId`) only, not full-row equality. If a
     * newer local edit `REPLACE`s this row (same primary key, new `payload`/`createdAt`) while a
     * drain of the *old* payload is still in flight, deleting "the entry I fetched" by primary
     * key alone would delete the *replacement* instead — the newer, never-pushed edit vanishes
     * with no error, no retry, nothing recoverable. Conditioning the delete on `createdAt` too
     * (captured at fetch time) makes it a no-op instead: zero rows match, the replacement stays
     * queued for the next drain.
     */
    @Query("DELETE FROM outbox WHERE tableName = :tableName AND entityId = :entityId AND createdAt = :createdAt")
    suspend fun deleteIfUnchanged(
        tableName: String,
        entityId: String,
        createdAt: Instant,
    )

    @Query(
        "UPDATE outbox SET attemptCount = attemptCount + 1, lastError = :error " +
            "WHERE tableName = :tableName AND entityId = :entityId",
    )
    suspend fun recordFailure(
        tableName: String,
        entityId: String,
        error: String,
    )

    @Query("SELECT COUNT(*) FROM outbox")
    suspend fun count(): Int
}
