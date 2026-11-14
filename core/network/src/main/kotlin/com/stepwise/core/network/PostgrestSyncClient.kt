package com.stepwise.core.network

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The one place this app calls supabase-kt's `Postgrest` plugin (`/DEVELOPMENT_PROTOCOL.md`'s
 * centralization principle, same reasoning as [SupabaseAuthClient] for the `Auth` plugin).
 * Deliberately generic (table name + JSON payload) rather than 13 hand-rolled typed DTOs — the
 * outbox this feeds (`:core:sync`) already stores a table-agnostic JSON snapshot per
 * `ARCHITECTURE.md` §9.1 ("a snapshot of the row"), so a typed-DTO layer here would just be a
 * second, redundant shape for the same data. `:core:sync`'s repositories are responsible for
 * producing a [JsonObject] whose keys already match the Postgres column names (snake_case) —
 * this class does no field-name translation of its own.
 *
 * **Two write paths, matching ARCHITECTURE.md §9.1/ADR-006's two conflict-resolution tiers —
 * a deliberate design decision, not left implicit**:
 * - [upsert] — blind overwrite, for low-risk metadata-tier tables (`life_areas`, `visions`,
 *   `goals`, `projects`, `milestones`, `activities`, `habits`, `tasks`, `goal_activity_links`,
 *   `schedules`). The backend's `enforce_sync_metadata()` trigger (DEV-008,
 *   `0003_sync_engine.sql`) always overwrites `version`/`updated_at` server-side regardless of
 *   what this sends, so "blind" here only means no compare-and-swap filter — not that the server
 *   trusts whatever the client claims about its own bookkeeping columns.
 * - [compareAndSwapUpdate] — a version-filtered `PATCH`, for a progress-affecting row this device
 *   already has a confirmed prior server version for. Zero rows matched means the server's
 *   `version` has already moved past [expectedVersion] — a genuine conflict, not an error, for
 *   the caller to resolve via a conflict-copy (`:core:sync`'s job, not this class's). A
 *   progress-affecting row with no confirmed prior version uses plain [upsert] too (not a
 *   separate insert path) — see `SyncEngine`'s own KDoc for why a plain insert would break
 *   idempotent retries.
 *
 * No server-side RPC/stored-procedure was used for the compare-and-swap, on purpose: a generic
 * cross-table RPC would need to validate a dynamic table-name parameter against an allowlist to
 * avoid SQL injection, and would be meaningfully harder to audit than standard PostgREST filter
 * semantics — not a tradeoff worth making for a security/data-integrity-critical path with no
 * independent reviewer available to check it (`AI_CODE_SECURITY.md`).
 */
class PostgrestSyncClient(
    private val client: SupabaseClient,
) {
    /**
     * Returns the server-confirmed `version` after the write (`enforce_sync_metadata()`,
     * `0003_sync_engine.sql`, always assigns one — 1 on a genuine insert, `old.version + 1` on an
     * upsert's update branch). Callers write this back into local state so a *second* edit to the
     * same row can use [compareAndSwapUpdate] instead of landing here again — closing the DEV-008
     * review's HIGH finding (architecture + security reviewers) that a permanently-null
     * `expectedVersion` made the CAS path dead code and let a stale write blindly overwrite a
     * newer one, including resurrecting a tombstone.
     */
    suspend fun upsert(
        table: String,
        payload: JsonObject,
    ): Int {
        val result =
            client.postgrest.from(table).upsert(payload) {
                select(Columns.list("version"))
            }
        val row = result.decodeList<JsonObject>().singleOrNull()
        val version =
            row
                ?.get("version")
                ?.jsonPrimitive
                ?.content
                ?.toIntOrNull()
        return requireNotNull(version) { "upsert into $table returned no confirmed version" }
    }

    /** Used directly for `conflict_copies` (append-only — never updated, so never an upsert target). */
    suspend fun insert(
        table: String,
        payload: JsonObject,
    ) {
        client.postgrest.from(table).insert(payload)
    }

    /**
     * Returns the server-confirmed new `version` if the write landed (a row with [id] and
     * [expectedVersion] existed and was updated), `null` if zero rows matched — the caller's only
     * signal of a conflict, since a version mismatch and "row doesn't exist at all" look identical
     * at this layer; distinguishing them (and deciding what a `null` means) is `:core:sync`'s job,
     * not this one's.
     */
    suspend fun compareAndSwapUpdate(
        table: String,
        id: String,
        expectedVersion: Int,
        payload: JsonObject,
    ): Int? {
        val result =
            client.postgrest.from(table).update(payload) {
                filter {
                    eq("id", id)
                    eq("version", expectedVersion)
                }
                select(Columns.list("version"))
            }
        return result
            .decodeList<JsonObject>()
            .singleOrNull()
            ?.get("version")
            ?.jsonPrimitive
            ?.content
            ?.toIntOrNull()
    }

    suspend fun fetchById(
        table: String,
        id: String,
    ): JsonObject? =
        client.postgrest
            .from(table)
            .select {
                filter { eq("id", id) }
            }
            .decodeSingleOrNull<JsonObject>()
}
