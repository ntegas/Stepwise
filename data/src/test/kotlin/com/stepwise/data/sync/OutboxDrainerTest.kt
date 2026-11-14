package com.stepwise.data.sync

import com.stepwise.core.sync.ConflictCopyRecord
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

// Pure JSON mapping, no Room/Android API touched — plain JUnit4, no Robolectric runner needed,
// same reasoning as DataErrorMappingTest/NetworkErrorMappingTest.
class OutboxDrainerTest {
    @Test
    fun `conflictCopyToPayload uses ownerUserId for the user_id column and includes the full payload nested`() {
        val copy =
            ConflictCopyRecord(
                id = "copy-1",
                tableName = "sessions",
                entityId = "session-1",
                payloadJson = """{"id":"session-1","status":"done","notes":"my stale edit"}""",
                serverVersionAtConflict = 5,
                ownerUserId = "user-1",
            )

        val payload = conflictCopyToPayload(copy)

        assertEquals("copy-1", payload["id"]?.jsonPrimitive?.content)
        assertEquals("user-1", payload["user_id"]?.jsonPrimitive?.content)
        assertEquals("sessions", payload["table_name"]?.jsonPrimitive?.content)
        assertEquals("session-1", payload["entity_id"]?.jsonPrimitive?.content)
        assertEquals("5", payload["server_version_at_conflict"]?.jsonPrimitive?.content)
        val nestedPayload = payload["payload"] as JsonObject
        assertEquals("done", nestedPayload["status"]?.jsonPrimitive?.content)
    }

    @Test
    fun `conflictCopyToPayload ignores a divergent user_id embedded in the payload (DEV-008, MEDIUM)`() {
        // A modified client/direct Postgrest call could put any value it likes inside the
        // payload JSON's own "user_id" field - the column must come from the trusted
        // ownerUserId field, never from inside the untrusted payload blob.
        val copy =
            ConflictCopyRecord(
                id = "copy-1",
                tableName = "sessions",
                entityId = "session-1",
                payloadJson = """{"id":"session-1","user_id":"someone-elses-id","status":"done"}""",
                serverVersionAtConflict = 5,
                ownerUserId = "user-1",
            )

        val payload = conflictCopyToPayload(copy)

        assertEquals("user-1", payload["user_id"]?.jsonPrimitive?.content)
    }
}
