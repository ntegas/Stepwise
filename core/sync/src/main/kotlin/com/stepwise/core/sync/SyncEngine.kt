package com.stepwise.core.sync

import com.stepwise.core.common.IdGenerator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * One pending write, as [SyncEngine] sees it — deliberately a plain data class with no Room type
 * (`:core:database`'s `OutboxEntryEntity` maps to this at `:data`'s boundary, the same mapper
 * pattern every other entity uses, `/ARCHITECTURE.md` §6), so this module can stay pure Kotlin/JVM.
 */
data class OutboxEntry(
    val tableName: String,
    val entityId: String,
    val payloadJson: String,
    val expectedVersion: Int?,
    val isProgressAffecting: Boolean,
    val ownerUserId: String,
)

/**
 * What [SyncEngine.push] decided happened, for the caller (`:data`) to act on — delete the
 * outbox entry, insert a conflict copy, or record a retryable failure. [SyncEngine] itself
 * performs no Room writes; deciding *what* to do and doing it are kept separate on purpose, the
 * same split `:core:common`'s `AppResult` enforces everywhere else in this codebase.
 */
sealed class PushOutcome {
    /**
     * [confirmedVersion] is the server-assigned value after this write (`enforce_sync_metadata()`,
     * `0003_sync_engine.sql`). The caller (`:data`) must write this back into the local row — this
     * is what makes a *second* edit to the same row use [SyncTransport.compareAndSwapUpdate]
     * instead of landing here again with no known version. Real BLOCKER found and fixed during
     * DEV-008 review (adversarial reviewer, reproduced live against Postgres): without this
     * round-trip, every write for an entity takes the blind-[SyncTransport.upsert] path forever,
     * which can silently resurrect a tombstone or overwrite a newer edit from another device —
     * not merely "fail to detect a conflict," but actively undo one.
     */
    data class Success(
        val confirmedVersion: Int,
    ) : PushOutcome()

    data class Conflict(
        val conflictCopy: ConflictCopyRecord,
    ) : PushOutcome()

    data class Failure(
        val message: String,
    ) : PushOutcome()
}

/**
 * Mirrors `0003_sync_engine.sql`'s `conflict_copies` table shape exactly — one field per column.
 * [ownerUserId] comes from [OutboxEntry.ownerUserId] — a trusted field the repository itself
 * set — never parsed back out of [payloadJson]. Real gap found and fixed during DEV-008 review
 * (adversarial reviewer, MEDIUM): deriving the owner from inside the payload JSON would let a
 * modified client/direct Postgrest call insert a `conflict_copies` row whose embedded payload
 * claims a different user than the row's own `user_id` column, since nothing enforced the two
 * matched. Threading the owner through as its own field end-to-end removes the mismatch
 * possibility entirely rather than needing a new enforcement trigger to catch it after the fact.
 */
data class ConflictCopyRecord(
    val id: String,
    val tableName: String,
    val entityId: String,
    val payloadJson: String,
    val serverVersionAtConflict: Int,
    val ownerUserId: String,
)

/**
 * A row already on the server, as [SyncTransport.fetchRow] returns it — added at DEV-009 to
 * close a real CRITICAL the logic reviewer found: DEV-009's deterministic Session ids
 * (`:core:model`'s `SessionIdentity`) mean two *different* devices can now independently compute
 * the *same* id for a progress-affecting row, something B4's original design (always a fresh
 * random per-device id) made structurally impossible. See [SyncEngine.push]'s own KDoc for the
 * full exploit and the fix this type enables.
 */
data class RemoteRow(
    val version: Int,
    val payloadJson: String,
)

/**
 * What [SyncEngine] needs from the network — implemented in `:data` as a thin adapter over
 * `:core:network`'s `PostgrestSyncClient` (`:data` is "the ONLY module allowed to depend on both
 * `:core:database` and `:core:network` at once," `/ARCHITECTURE.md` §5 — this interface is what
 * lets `:core:sync` itself stay free of both). A fake implementation of this interface is what
 * makes [SyncEngine] unit-testable without Robolectric/a real network, same value DEV-006's
 * `Fake*Repository` pattern would have had, now actually needed.
 */
interface SyncTransport {
    /** Returns the server-confirmed `version` after the write. */
    suspend fun upsert(
        table: String,
        payloadJson: String,
    ): Int

    /** The confirmed new `version` if the write landed; `null` if zero rows matched (version is stale). */
    suspend fun compareAndSwapUpdate(
        table: String,
        id: String,
        expectedVersion: Int,
        payloadJson: String,
    ): Int?

    /** The server's current `version` for this row, or `null` if it no longer exists remotely. */
    suspend fun fetchVersion(
        table: String,
        id: String,
    ): Int?

    /**
     * The server's current full row (version + payload) at this id, or `null` if it doesn't
     * exist remotely. Added at DEV-009 so a no-known-version push can tell "a row already exists
     * here" apart from "this is genuinely new" before blindly upserting — see [SyncEngine.push]'s
     * KDoc.
     */
    suspend fun fetchRow(
        table: String,
        id: String,
    ): RemoteRow?
}

/**
 * The actual conflict-resolution decision from `/ARCHITECTURE.md` §9.1/ADR-006's two tiers — the
 * one place this logic lives, so `:data`'s wiring code never has to re-decide "is this entity
 * metadata or progress-affecting" itself.
 *
 * - **Metadata tier** ([OutboxEntry.isProgressAffecting] `false`): [SyncTransport.upsert] — a
 *   blind overwrite. The backend's `enforce_sync_metadata()` trigger (`0003_sync_engine.sql`)
 *   still assigns the real `version`/`updated_at` server-side regardless, so this is "blind"
 *   only in the sense of no compare-and-swap filter, never a trust decision about those columns.
 * - **Progress-affecting tier** ([OutboxEntry.isProgressAffecting] `true`): a row this device has
 *   no confirmed server version for yet ([OutboxEntry.expectedVersion] `null` — either genuinely
 *   new, or a retry of a push whose success acknowledgement never arrived) goes through
 *   [pushWithNoKnownVersion], not a plain insert — a plain insert would error on a duplicate key
 *   for the retry case, defeating the whole point of `client_event_id`/idempotent retries
 *   (`/ARCHITECTURE.md` §9.0). A row with a known prior version goes through
 *   [SyncTransport.compareAndSwapUpdate]. A lost CAS becomes a [PushOutcome.Conflict] carrying the
 *   losing payload as a [ConflictCopyRecord] — never silently discarded (ADR-006: "nothing a user
 *   recorded ever silently vanishes").
 *
 * **CRITICAL found and fixed at DEV-009 review (logic reviewer)**: [pushWithNoKnownVersion] used
 * to go straight to [SyncTransport.upsert], unconditionally — safe only under B4's original
 * assumption that a progress-affecting row's id is always a fresh, per-device-random value, so
 * "I have no known version" could only mean "genuinely new" or "my own earlier push." DEV-009's
 * `SessionIdentity` (`:core:model`) breaks that assumption on purpose, deterministically deriving
 * a Session's id from the occurrence it completes specifically so two devices *converge* on one
 * row (§19.4) — which also means two devices can now reach this exact branch for the *same* id
 * with genuinely *different* data, before either has ever synced. The old code would have let
 * whichever push landed second silently overwrite the first, with no conflict — violating
 * ADR-006 for the first time since it was written, not because ADR-006 changed, but because
 * DEV-009 changed what "no known version" could mean. Fixed by fetching the row first
 * ([SyncTransport.fetchRow]): no existing row, or an existing row whose content already matches
 * what this device is about to send (the legitimate idempotent-retry/harmless-convergence case) →
 * still a plain upsert; an existing row with genuinely *different* content → [PushOutcome.Conflict],
 * never blindly overwritten. **Residual, disclosed risk**: this closes the realistic case (one
 * device syncs well before the other) but not a true simultaneous race — two devices' first pushes
 * for the same id landing in the same instant could still both pass the "no existing row" check
 * before either's upsert commits, with Postgres's own upsert then letting the second silently win.
 * Closing that fully needs a database-level atomic conditional insert, whose exact behavior
 * through supabase-kt's API this sandbox cannot verify (no network access to the library) —
 * tracked in `/PROJECT_STATE.md`'s VERIFICATION DEBT rather than claimed as fully closed here.
 *
 * **The version round-trip is the caller's responsibility, not this class's** — [push] returns
 * [PushOutcome.Success.confirmedVersion], but doesn't write it anywhere. An earlier draft of this
 * engine shipped with no round-trip at all and a hardcoded `expectedVersion = null` at the one
 * production call site (`SessionRepositoryImpl`) — the adversarial reviewer demonstrated live
 * against Postgres that this let a stale device's retry blindly resurrect an already-tombstoned
 * row via [upsert], with no conflict, no error, nothing distinguishing it from an ordinary sync.
 * Fixed by having the repository look up the existing local row's version before enqueueing (so
 * only a genuinely-new, never-seen-before row gets `expectedVersion = null`) and write
 * [PushOutcome.Success.confirmedVersion] back into local state after every push, success or CAS
 * alike — see `SessionRepositoryImpl`/`OutboxDrainer`'s own KDoc for exactly where each half lives.
 */
class SyncEngine(
    private val transport: SyncTransport,
    private val idGenerator: IdGenerator,
) {
    // Same justified-broad-catch reasoning as DataErrorMapping/NetworkErrorMapping.
    @Suppress("TooGenericExceptionCaught")
    suspend fun push(entry: OutboxEntry): PushOutcome =
        try {
            // Allowlist check (DEV-008 review, architecture reviewer, LOW/IMPROVEMENT): tableName
            // is a free-form String all the way from OutboxEntryEntity, with no compiler-enforced
            // set of values. Today only two literals (`"sessions"`, `"session_metric_values"`)
            // ever reach this call in production, so nothing is exploitable yet — but nothing
            // stopped a future repository from enqueueing an arbitrary string either. Failing
            // closed here, once, is cheaper than relying on every future call site getting it right.
            if (entry.tableName !in SYNCABLE_TABLES) {
                return PushOutcome.Failure("Unknown syncable table: ${entry.tableName}")
            }
            if (!entry.isProgressAffecting) {
                PushOutcome.Success(transport.upsert(entry.tableName, entry.payloadJson))
            } else {
                pushProgressAffecting(entry)
            }
        } catch (e: CancellationException) {
            if (!currentCoroutineContext().isActive) throw e
            PushOutcome.Failure(e.message ?: "Sync push failed")
        } catch (e: Exception) {
            PushOutcome.Failure(e.message ?: "Sync push failed")
        }

    private companion object {
        // Every table 0002_canonical_schema.sql's enforce_sync_metadata() trigger covers
        // (0003_sync_engine.sql) — the full set this project can legitimately sync today, not
        // just the two currently wired up. Extend this alongside wiring a new repository, never
        // ahead of one actually enqueueing rows for it.
        val SYNCABLE_TABLES =
            setOf(
                "life_areas",
                "visions",
                "goals",
                "projects",
                "milestones",
                "activities",
                "habits",
                "tasks",
                "goal_activity_links",
                "schedules",
                "sessions",
                "session_metric_values",
                "recurrence_occurrences",
            )
    }

    // Single return point (detekt ReturnCount) - an if/else expression, not three early returns,
    // covers the same three outcomes (no-known-version push / CAS success / CAS conflict).
    private suspend fun pushProgressAffecting(entry: OutboxEntry): PushOutcome {
        val expectedVersion = entry.expectedVersion
        return if (expectedVersion == null) {
            pushWithNoKnownVersion(entry)
        } else {
            val confirmedVersion =
                transport.compareAndSwapUpdate(entry.tableName, entry.entityId, expectedVersion, entry.payloadJson)
            if (confirmedVersion != null) {
                PushOutcome.Success(confirmedVersion)
            } else {
                val serverVersion = transport.fetchVersion(entry.tableName, entry.entityId) ?: expectedVersion
                PushOutcome.Conflict(conflictCopyFor(entry, serverVersion))
            }
        }
    }

    /**
     * See the class KDoc's "CRITICAL found and fixed at DEV-009 review" note for the full exploit
     * this closes and its one disclosed residual gap.
     */
    private suspend fun pushWithNoKnownVersion(entry: OutboxEntry): PushOutcome {
        val existingRow = transport.fetchRow(entry.tableName, entry.entityId)
        return if (existingRow == null || payloadsEquivalent(existingRow.payloadJson, entry.payloadJson)) {
            PushOutcome.Success(transport.upsert(entry.tableName, entry.payloadJson))
        } else {
            PushOutcome.Conflict(conflictCopyFor(entry, existingRow.version))
        }
    }

    private fun conflictCopyFor(
        entry: OutboxEntry,
        serverVersion: Int,
    ): ConflictCopyRecord =
        ConflictCopyRecord(
            id = idGenerator.newId(),
            tableName = entry.tableName,
            entityId = entry.entityId,
            payloadJson = entry.payloadJson,
            serverVersionAtConflict = serverVersion,
            ownerUserId = entry.ownerUserId,
        )

    /**
     * Compares only the keys present in [localPayloadJson] - [remotePayloadJson] always carries
     * extra server-only columns (`version`, `updated_at`, ...) that never appear in a client
     * payload (`SessionSyncMapper`/`SessionMetricValueSyncMapper`'s own deliberate omission,
     * DEV-008), so a naive full-object equality would always report "different" even for a
     * genuine no-op retry.
     */
    private fun payloadsEquivalent(
        remotePayloadJson: String,
        localPayloadJson: String,
    ): Boolean {
        val remote = Json.parseToJsonElement(remotePayloadJson) as JsonObject
        val local = Json.parseToJsonElement(localPayloadJson) as JsonObject
        return local.all { (key, value) -> remote[key] == value }
    }
}
