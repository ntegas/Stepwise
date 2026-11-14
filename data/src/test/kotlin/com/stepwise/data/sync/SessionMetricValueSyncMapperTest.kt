package com.stepwise.data.sync

import com.stepwise.core.model.MetricId
import com.stepwise.core.model.SessionId
import com.stepwise.core.model.SessionMetricValue
import com.stepwise.core.model.SessionMetricValueId
import com.stepwise.core.model.SyncMetadata
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Instant

// Mirrors SessionSyncMapperTest's own reasoning exactly - see that file and this mapper's KDoc
// for why this test exists at all (DEV-008 review, architecture + logic reviewers, CRITICAL:
// an earlier draft never enqueued this mapper's output for any metric value at all).
class SessionMetricValueSyncMapperTest {
    private val now = Instant.fromEpochMilliseconds(1_000_000)

    @Test
    fun `toSyncPayload uses snake_case keys matching session_metric_values, with no version column`() {
        val metricValue =
            SessionMetricValue(
                id = SessionMetricValueId("smv-1"),
                sessionId = SessionId("session-1"),
                metricId = MetricId("duration_minutes"),
                plannedValue = 60.0,
                actualValue = 65.0,
                sync = SyncMetadata(updatedAt = now, version = 1, deletedAt = null),
            )

        val payload = metricValue.toSyncPayload()

        assertEquals("smv-1", payload["id"]?.jsonPrimitive?.content)
        assertEquals("session-1", payload["session_id"]?.jsonPrimitive?.content)
        assertEquals("duration_minutes", payload["metric_id"]?.jsonPrimitive?.content)
        assertEquals("60.0", payload["planned_value"]?.jsonPrimitive?.content)
        assertEquals("65.0", payload["actual_value"]?.jsonPrimitive?.content)
        assertNull(payload["version"]) // server-enforced, never sent by the client (0003_sync_engine.sql)
        assertNull(payload["updated_at"])
    }

    @Test
    fun `toSyncPayload allows a null plannedValue (no target set for this metric)`() {
        val metricValue =
            SessionMetricValue(
                id = SessionMetricValueId("smv-1"),
                sessionId = SessionId("session-1"),
                metricId = MetricId("distance_km"),
                plannedValue = null,
                actualValue = 5.0,
                sync = SyncMetadata(updatedAt = now, version = 1, deletedAt = null),
            )

        val payload = metricValue.toSyncPayload()

        // The key is still present, holding JSON null - not omitted entirely, unlike
        // version/updated_at above (never put() at all). The column itself is nullable
        // (0002_canonical_schema.sql), so an explicit JSON null is the correct wire value.
        assertTrue(payload["planned_value"] is JsonNull)
        assertEquals("5.0", payload["actual_value"]?.jsonPrimitive?.content)
    }
}
