package com.stepwise.data.sync

import com.stepwise.core.model.ExecutableKind
import com.stepwise.core.model.ExecutionStatus
import com.stepwise.core.model.Session
import com.stepwise.core.model.SessionId
import com.stepwise.core.model.SyncMetadata
import com.stepwise.core.model.UserId
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.time.Instant

class SessionSyncMapperTest {
    private val now = Instant.fromEpochMilliseconds(1_000_000)

    @Test
    fun `toSyncPayload uses snake_case keys matching the sessions table, with lowercase enum values`() {
        val session =
            Session(
                id = SessionId("session-1"),
                userId = UserId("user-1"),
                sourceType = ExecutableKind.ACTIVITY,
                sourceId = "activity-1",
                date = LocalDate(2026, 9, 15),
                status = ExecutionStatus.DONE,
                clientEventId = "client-event-1",
                notes = "felt great",
                sync = SyncMetadata(updatedAt = now, version = 1, deletedAt = null),
            )

        val payload = session.toSyncPayload()

        assertEquals("session-1", payload["id"]?.jsonPrimitive?.content)
        assertEquals("user-1", payload["user_id"]?.jsonPrimitive?.content)
        assertEquals("activity", payload["source_type"]?.jsonPrimitive?.content)
        assertEquals("activity-1", payload["source_id"]?.jsonPrimitive?.content)
        assertEquals("2026-09-15", payload["date"]?.jsonPrimitive?.content)
        assertEquals("done", payload["status"]?.jsonPrimitive?.content)
        assertEquals("client-event-1", payload["client_event_id"]?.jsonPrimitive?.content)
        assertEquals("felt great", payload["notes"]?.jsonPrimitive?.content)
        assertNull(payload["version"]) // server-enforced, never sent by the client (0003_sync_engine.sql)
        assertNull(payload["updated_at"])
    }

    @Test
    fun `toSyncPayload carries deleted_at for a tombstoned session so the delete actually syncs`() {
        val deletedAt = Instant.fromEpochMilliseconds(2_000_000)
        val session =
            Session(
                id = SessionId("session-1"),
                userId = UserId("user-1"),
                sourceType = ExecutableKind.TASK,
                sourceId = "task-1",
                date = LocalDate(2026, 9, 15),
                status = ExecutionStatus.CANCELLED,
                clientEventId = "client-event-1",
                notes = null,
                sync = SyncMetadata(updatedAt = now, version = 2, deletedAt = deletedAt),
            )

        val payload = session.toSyncPayload()

        assertEquals(deletedAt.toString(), payload["deleted_at"]?.jsonPrimitive?.content)
    }
}
