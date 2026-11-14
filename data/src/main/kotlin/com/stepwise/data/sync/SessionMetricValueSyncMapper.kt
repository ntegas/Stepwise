package com.stepwise.data.sync

import com.stepwise.core.model.SessionMetricValue
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * [SessionMetricValue]'s shape as `session_metric_values` expects it
 * (`/supabase/migrations/0002_canonical_schema.sql`) — real gap found and fixed during DEV-008
 * review (architecture + logic reviewers, HIGH/CRITICAL): an earlier draft enqueued an outbox
 * row for the `Session` header only, never for its metric values — the actual logged numbers
 * (duration, distance, reps, whatever `actual_value` holds) never left the device at all. Mirrors
 * [com.stepwise.data.sync.toSyncPayload] (`SessionSyncMapper.kt`)'s own reasoning exactly:
 * snake_case keys, `version`/`updated_at` omitted (server-enforced).
 */
internal fun SessionMetricValue.toSyncPayloadJson(): String = toSyncPayload().toString()

internal fun SessionMetricValue.toSyncPayload(): JsonObject =
    buildJsonObject {
        put("id", id.value)
        put("session_id", sessionId.value)
        put("metric_id", metricId.value)
        put("planned_value", plannedValue)
        put("actual_value", actualValue)
        put("deleted_at", sync.deletedAt?.toString())
    }
