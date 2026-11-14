package com.stepwise.data.sync

import com.stepwise.core.database.OutboxDao
import com.stepwise.core.database.OutboxEntryEntity
import com.stepwise.core.network.PostgrestSyncClient
import com.stepwise.core.sync.ConflictCopyRecord
import com.stepwise.core.sync.OutboxEntry
import com.stepwise.core.sync.PushOutcome
import com.stepwise.core.sync.SyncEngine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Drains the whole outbox once, applying [SyncEngine]'s decision per entry — entity-agnostic by
 * construction (`OutboxEntryEntity`/`OutboxEntry` carry no entity-specific type), so wiring a new
 * `*RepositoryImpl` to enqueue outbox rows is the only thing a future entity needs; this loop
 * doesn't change. Only [com.stepwise.data.repository.SessionRepositoryImpl] enqueues rows today
 * (DEV-008's chosen first entity, per `/PROJECT_STATE.md`'s DEV-008 findings) — this drainer
 * already works for any other entity the moment its repository starts enqueueing too.
 *
 * [onVersionConfirmed] is the one entity-specific seam: after a successful push, *something* has
 * to write the server-confirmed version back into the local row for that table, and only the
 * caller (`:data`, which knows every concrete `*Dao`) can do that — this drainer stays generic by
 * taking it as a callback rather than switching on `tableName` itself. Not calling it at all was
 * the root cause of a BLOCKER the adversarial reviewer found (see `SyncEngine`'s own KDoc): with
 * no version ever confirmed locally, every push took the blind-upsert path forever.
 *
 * Not itself an `androidx.work.Worker` — `/ARCHITECTURE.md` §9.1's "a WorkManager periodic +
 * expedited worker drains the outbox" is a thin Android wrapper around [drainOnce], which
 * belongs in `:app` once that module exists (same deferred-until-needed reasoning DEV-006 gave
 * for Hilt bindings), not duplicated here ahead of a real caller.
 */
class OutboxDrainer(
    private val outboxDao: OutboxDao,
    private val syncEngine: SyncEngine,
    private val syncClient: PostgrestSyncClient,
    private val onVersionConfirmed: suspend (tableName: String, entityId: String, version: Int) -> Unit,
) {
    suspend fun drainOnce(): DrainSummary {
        var pushed = 0
        var conflicts = 0
        var failed = 0

        for (entry in outboxDao.getAll()) {
            when (val outcome = syncEngine.push(entry.toOutboxEntry())) {
                is PushOutcome.Success -> {
                    onVersionConfirmed(entry.tableName, entry.entityId, outcome.confirmedVersion)
                    // Conditioned on createdAt (DEV-008 review, logic reviewer, CRITICAL — see
                    // OutboxDao.deleteIfUnchanged's own KDoc): a newer local edit that REPLACEd
                    // this entry mid-push must not be deleted along with the push that just landed.
                    outboxDao.deleteIfUnchanged(entry.tableName, entry.entityId, entry.createdAt)
                    pushed++
                }

                is PushOutcome.Conflict -> {
                    if (recordConflictCopy(outcome.conflictCopy)) {
                        outboxDao.deleteIfUnchanged(entry.tableName, entry.entityId, entry.createdAt)
                        conflicts++
                    } else {
                        failed++
                    }
                }

                is PushOutcome.Failure -> {
                    outboxDao.recordFailure(entry.tableName, entry.entityId, outcome.message)
                    failed++
                }
            }
        }

        return DrainSummary(pushed = pushed, conflicts = conflicts, failed = failed)
    }

    /**
     * `false` on failure, not a thrown exception — a malformed payload or a network error here
     * must not abandon the rest of the batch (DEV-008 review, logic reviewer, MEDIUM: an earlier
     * draft let this call's exception escape `drainOnce` entirely, losing every other entry's
     * result along with it).
     */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun recordConflictCopy(conflictCopy: ConflictCopyRecord): Boolean =
        try {
            syncClient.insert("conflict_copies", conflictCopyToPayload(conflictCopy))
            true
        } catch (e: Exception) {
            outboxDao.recordFailure(
                conflictCopy.tableName,
                conflictCopy.entityId,
                "Failed to record conflict copy: ${e.message}",
            )
            false
        }

    private fun OutboxEntryEntity.toOutboxEntry() =
        OutboxEntry(
            tableName = tableName,
            entityId = entityId,
            payloadJson = payload,
            expectedVersion = expectedVersion,
            isProgressAffecting = isProgressAffecting,
            ownerUserId = ownerUserId,
        )
}

data class DrainSummary(
    val pushed: Int,
    val conflicts: Int,
    val failed: Int,
)

/**
 * Builds the `conflict_copies` row for a losing write (`0003_sync_engine.sql`'s shape) — a
 * top-level function, not a method, so it's unit-testable on its own without constructing a full
 * [OutboxDrainer] (which would otherwise need a real [PostgrestSyncClient]/[OutboxDao]/[SyncEngine]
 * just to exercise this pure JSON mapping). [ConflictCopyRecord.ownerUserId] is used directly for
 * the `user_id` column — never re-derived from inside [ConflictCopyRecord.payloadJson] (see that
 * class's own KDoc for the embedded-field-divergence issue this avoids).
 */
internal fun conflictCopyToPayload(copy: ConflictCopyRecord): JsonObject {
    val payloadObject = Json.parseToJsonElement(copy.payloadJson) as JsonObject
    return buildJsonObject {
        put("id", copy.id)
        put("user_id", copy.ownerUserId)
        put("table_name", copy.tableName)
        put("entity_id", copy.entityId)
        put("payload", payloadObject)
        put("server_version_at_conflict", copy.serverVersionAtConflict)
    }
}
