package com.stepwise.core.sync

import com.stepwise.core.common.IdGenerator
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private class FakeIdGenerator(
    private val fixedId: String = "generated-id",
) : IdGenerator {
    override fun newId(): String = fixedId
}

/**
 * A fake, not a mock — real in-memory state per table (`table -> id -> version`), so these tests
 * exercise [SyncEngine]'s actual branching against realistic transport behavior rather than a
 * list of pre-programmed return values that could pass even if the engine called things in the
 * wrong order (the DEV-006/DEV-007 retrospective's own lesson about contrived tests, applied here
 * before a real bug could ship, not after). `upsert`/`compareAndSwapUpdate` return the real
 * post-write server version, mirroring `enforce_sync_metadata()`'s actual behavior
 * (`0003_sync_engine.sql`: insert → 1, update → `old version + 1`) rather than a canned value.
 */
private class FakeSyncTransport : SyncTransport {
    val upsertCalls = mutableListOf<Pair<String, String>>()

    // One source of truth, keyed by (table, id) — real PostgrestSyncClient.upsert has no
    // separate `id` parameter either; the id lives inside the payload, same as production.
    private val serverVersions = mutableMapOf<String, MutableMap<String, Int>>()

    fun seedServerVersion(
        table: String,
        id: String,
        version: Int,
    ) {
        serverVersions.getOrPut(table) { mutableMapOf() }[id] = version
    }

    fun currentServerVersion(
        table: String,
        id: String,
    ): Int? = serverVersions[table]?.get(id)

    override suspend fun upsert(
        table: String,
        payloadJson: String,
    ): Int {
        upsertCalls += table to payloadJson
        val id = idFrom(payloadJson)
        val newVersion = (serverVersions[table]?.get(id) ?: 0) + 1
        serverVersions.getOrPut(table) { mutableMapOf() }[id] = newVersion
        return newVersion
    }

    override suspend fun compareAndSwapUpdate(
        table: String,
        id: String,
        expectedVersion: Int,
        payloadJson: String,
    ): Int? {
        val current = serverVersions[table]?.get(id)
        if (current != expectedVersion) return null
        val newVersion = expectedVersion + 1
        serverVersions.getOrPut(table) { mutableMapOf() }[id] = newVersion
        return newVersion
    }

    override suspend fun fetchVersion(
        table: String,
        id: String,
    ): Int? = serverVersions[table]?.get(id)

    private fun idFrom(payloadJson: String): String =
        (Json.parseToJsonElement(payloadJson) as JsonObject)["id"]?.jsonPrimitive?.content ?: "no-id"
}

class SyncEngineTest {
    @Test
    fun `a metadata-tier entry is always a blind upsert, never a CAS write`() =
        runTest {
            val transport = FakeSyncTransport()
            val engine = SyncEngine(transport, FakeIdGenerator())

            val outcome =
                engine.push(
                    OutboxEntry(
                        tableName = "goals",
                        entityId = "goal-1",
                        payloadJson = """{"title":"Run 500km"}""",
                        expectedVersion = 2,
                        isProgressAffecting = false,
                        ownerUserId = "user-1",
                    ),
                )

            check(outcome is PushOutcome.Success)
            assertEquals(1, transport.upsertCalls.size)
            assertEquals("goals" to """{"title":"Run 500km"}""", transport.upsertCalls.single())
        }

    @Test
    fun `a progress-affecting row with no known prior version is upserted, not CAS-gated`() =
        runTest {
            val transport = FakeSyncTransport()
            val engine = SyncEngine(transport, FakeIdGenerator())

            val outcome =
                engine.push(
                    OutboxEntry(
                        tableName = "sessions",
                        entityId = "session-1",
                        payloadJson = """{"status":"done"}""",
                        expectedVersion = null,
                        isProgressAffecting = true,
                        ownerUserId = "user-1",
                    ),
                )

            check(outcome is PushOutcome.Success)
            assertEquals(1, transport.upsertCalls.size)
        }

    @Test
    fun `a retried push with no known prior version does not error on a duplicate key (idempotency)`() =
        runTest {
            // Simulates: the first push actually landed server-side, but the success
            // acknowledgement never reached this device, so it retries with expectedVersion still
            // null. upsert (not insert) is what makes this safe to simply repeat.
            val transport = FakeSyncTransport().apply { seedServerVersion("sessions", "session-1", version = 1) }
            val engine = SyncEngine(transport, FakeIdGenerator())

            val outcome =
                engine.push(
                    OutboxEntry(
                        tableName = "sessions",
                        entityId = "session-1",
                        payloadJson = """{"status":"done"}""",
                        expectedVersion = null,
                        isProgressAffecting = true,
                        ownerUserId = "user-1",
                    ),
                )

            check(outcome is PushOutcome.Success)
        }

    @Test
    fun `an up-to-date progress-affecting write succeeds via compare-and-swap and returns the new version`() =
        runTest {
            val transport = FakeSyncTransport().apply { seedServerVersion("sessions", "session-1", version = 3) }
            val engine = SyncEngine(transport, FakeIdGenerator())

            val outcome =
                engine.push(
                    OutboxEntry(
                        tableName = "sessions",
                        entityId = "session-1",
                        payloadJson = """{"status":"done"}""",
                        expectedVersion = 3,
                        isProgressAffecting = true,
                        ownerUserId = "user-1",
                    ),
                )

            check(outcome is PushOutcome.Success)
            assertEquals(4, outcome.confirmedVersion)
        }

    @Test
    fun `a stale progress-affecting write preserves the losing payload as a conflict copy, not discarded`() =
        runTest {
            // Server has already moved to version 5; this client still thinks it's at 3.
            val transport = FakeSyncTransport().apply { seedServerVersion("sessions", "session-1", version = 5) }
            val engine = SyncEngine(transport, FakeIdGenerator(fixedId = "conflict-copy-1"))

            val outcome =
                engine.push(
                    OutboxEntry(
                        tableName = "sessions",
                        entityId = "session-1",
                        payloadJson = """{"status":"done","notes":"my stale edit"}""",
                        expectedVersion = 3,
                        isProgressAffecting = true,
                        ownerUserId = "user-1",
                    ),
                )

            check(outcome is PushOutcome.Conflict)
            assertEquals("conflict-copy-1", outcome.conflictCopy.id)
            assertEquals("sessions", outcome.conflictCopy.tableName)
            assertEquals("session-1", outcome.conflictCopy.entityId)
            assertEquals("""{"status":"done","notes":"my stale edit"}""", outcome.conflictCopy.payloadJson)
            assertEquals(5, outcome.conflictCopy.serverVersionAtConflict)
            // Confirm the losing payload was never pushed as if it had succeeded.
            assertTrue(transport.upsertCalls.isEmpty())
        }

    @Test
    fun `a conflict against a row that no longer exists remotely still produces a conflict copy, not a crash`() =
        runTest {
            val transport = FakeSyncTransport() // no seeded version at all - row doesn't exist server-side
            val engine = SyncEngine(transport, FakeIdGenerator())

            val outcome =
                engine.push(
                    OutboxEntry(
                        tableName = "sessions",
                        entityId = "session-1",
                        payloadJson = """{"status":"done"}""",
                        expectedVersion = 3,
                        isProgressAffecting = true,
                        ownerUserId = "user-1",
                    ),
                )

            check(outcome is PushOutcome.Conflict)
            // fetchVersion returned null (row missing) - falls back to the client's own expected
            // version rather than crashing or lying about a server version that doesn't exist.
            assertEquals(3, outcome.conflictCopy.serverVersionAtConflict)
        }

    @Test
    fun `BLOCKER scenario - a CAS tombstone cannot be resurrected once the caller uses the confirmed version`() =
        runTest {
            // Reproduces, at this layer, the exact exploit the adversarial reviewer demonstrated
            // live against Postgres: Device A pushes (no known version, confirmedVersion
            // returned = 1). Device B then tombstones via a correct CAS(expectedVersion = 1),
            // moving the server to version 2. Device A, retrying its ORIGINAL stale payload,
            // must now use its own confirmed version (1, from its own earlier push) as the CAS
            // filter - not null again - and the retry must be detected as a conflict, not
            // silently resurrect the row.
            val transport = FakeSyncTransport()
            val engine = SyncEngine(transport, FakeIdGenerator())

            val deviceAFirstPush =
                engine.push(
                    OutboxEntry(
                        tableName = "sessions",
                        entityId = "session-1",
                        payloadJson = """{"id":"session-1","status":"done","deleted_at":null}""",
                        expectedVersion = null,
                        isProgressAffecting = true,
                        ownerUserId = "user-1",
                    ),
                )
            check(deviceAFirstPush is PushOutcome.Success)
            assertEquals(1, deviceAFirstPush.confirmedVersion)

            val deviceBTombstone =
                engine.push(
                    OutboxEntry(
                        tableName = "sessions",
                        entityId = "session-1",
                        payloadJson = """{"id":"session-1","status":"cancelled","deleted_at":"2026-01-01T00:00:00Z"}""",
                        expectedVersion = deviceAFirstPush.confirmedVersion,
                        isProgressAffecting = true,
                        ownerUserId = "user-1",
                    ),
                )
            check(deviceBTombstone is PushOutcome.Success)
            assertEquals(2, deviceBTombstone.confirmedVersion)

            // Device A retries its ORIGINAL (stale, pre-tombstone) payload, correctly using the
            // version IT last confirmed (1) - not null.
            val deviceARetry =
                engine.push(
                    OutboxEntry(
                        tableName = "sessions",
                        entityId = "session-1",
                        payloadJson = """{"id":"session-1","status":"done","deleted_at":null}""",
                        expectedVersion = deviceAFirstPush.confirmedVersion,
                        isProgressAffecting = true,
                        ownerUserId = "user-1",
                    ),
                )

            check(deviceARetry is PushOutcome.Conflict)
            assertEquals(2, deviceARetry.conflictCopy.serverVersionAtConflict)
            // The tombstone is still in effect server-side - confirmed via fetchVersion/the
            // transport's own state, not resurrected by Device A's stale retry.
            assertEquals(2, transport.currentServerVersion("sessions", "session-1"))
        }
}
