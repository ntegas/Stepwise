package com.stepwise.data.sync

import com.stepwise.core.model.Session
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * [Session]'s shape as `sessions` expects it (`/supabase/migrations/0002_canonical_schema.sql`)
 * — snake_case keys matching the Postgres columns exactly, per `PostgrestSyncClient`'s own KDoc
 * ("this class does no field-name translation"). `version`/`updated_at` are deliberately absent:
 * the backend's `enforce_sync_metadata()` trigger (`0003_sync_engine.sql`) assigns both
 * server-side on every write regardless of what the client sends, so including them here would
 * be dead weight, not a trust decision.
 *
 * [JsonObject.toString] is valid JSON text by kotlinx.serialization's own documented contract —
 * no separate `Json.encodeToString` call needed for an already-built [JsonObject].
 */
internal fun Session.toSyncPayloadJson(): String = toSyncPayload().toString()

internal fun Session.toSyncPayload(): JsonObject =
    buildJsonObject {
        put("id", id.value)
        put("user_id", userId.value)
        put("source_type", sourceType.name.lowercase())
        put("source_id", sourceId)
        put("date", date.toString())
        put("status", status.name.lowercase())
        put("client_event_id", clientEventId)
        put("notes", notes)
        put("deleted_at", sync.deletedAt?.toString())
    }
