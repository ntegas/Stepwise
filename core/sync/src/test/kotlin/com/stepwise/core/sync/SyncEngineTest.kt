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
 * A fake, not a mock — real in-memory state per table (`table -> id -> version`/`-> payload`),
 * so these tests exercise [SyncEngine]'s actual branching against realistic transport behavior
 * rather than a list of pre-programmed return values that could pass even if the engine called
 * things in the wrong order (the DEV-006/DEV-007 retrospective's own lesson about contrived
 * tests, applied here before a real bug could ship, not after). `upsert`/`compareAndSwapUpdate`
 * return the real post-write server version, mirroring `enforce_sync_metadata()`'s actual
 * behavior (`0003_sync_engine.sql`: insert → 1, update → `old version + 1`) rather than a canned
 * value. `fetchRow` (DEV-009) returns the same payload actually stored by the most recent
 * `upsert`/`compareAndSwapUpdate` for that (table, id), not a separately-maintained value — so a
 * test can't accidentally seed a `fetchRow` response that the write path itself would never
 * produce.
 */
private class FakeSyncTransport : SyncTransport {
    val upsertCalls = mutableListOf<Pair<String, String>>()

    // One source of truth, keyed by (table, id) — real PostgrestSyncClient.upsert has no
    // separate `id` parameter either; the id lives inside the payload, same as production.
    private val serverVersions = mutableMapOf<String, MutableMap<String, Int>>()
    private val serverPayloads = mutableMapOf<String, MutableMap<String, String>>()

    fun seedServerRow(
        table: String,
        id: String,
        version: Int,
        payloadJson: String,
    ) {
        serverVersions.getOrPut(table) { mutableMapOf() }[id] = version
        serverPayloads.getOrPut(table) { mutableMapOf() }[id] = payloadJson
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
        serverPayloads.getOrPut(table) { mutableMapOf() }[id] = payloadJson
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
        serverPayloads.getOrPut(table) { mutableMapOf() }[id] = payloadJson
        return newVersion
    }

    override suspend fun fetchVersion(
        table: String,
        id: String,
    ): Int? = serverVersions[table]?.get(id)

    override suspend fun fetchRow(
        table: String,
        id: String,
    ): RemoteRow? {
        val version = serverVersions[table]?.get(id)
        val payload = serverPayloads[table]?.get(id)
        return if (version == null || payload == null) null else RemoteRow(version = version, payloadJson = payload)
    }

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
    fun `a progress-affecting row with no known prior version and no row server-side is upserted, not CAS-gated`() =
        runTest {
            val transport = FakeSyncTransport()
            val engine = SyncEngine(transport, FakeIdGenerator())

            val outcome =
                engine.push(
                    OutboxEntry(
                        tableName = "sessions",
                        entityId = "session-1",
                        payloadJson = """{"id":"session-1","status":"done"}""",
                        expectedVersion = null,
                        isProgressAffecting = true,
                        ownerUserId = "user-1",
                    ),
                )

            check(outcome is PushOutcome.Success)
            assertEquals(1, transport.upsertCalls.size)
        }

    @Test
    fun `a retried push with no known prior version and a matching server row does not error (idempotency)`() =
        runTest {
            // Simulates: the first push actually landed server-side with this exact content, but
            // the success acknowledgement never reached this device, so it retries with
            // expectedVersion still null. Matching content is what makes this a safe no-op upsert
            // rather than a conflict (DEV-009: fetchRow compares content, not just existence).
            val payload = """{"id":"session-1","status":"done"}"""
            val transport = FakeSyncTransport().apply { seedServerRow("sessions", "session-1", version = 1, payload) }
            val engine = SyncEngine(transport, FakeIdGenerator())

            val outcome =
                engine.push(
                    OutboxEntry(
                        tableName = "sessions",
                        entityId = "session-1",
                        payloadJson = payload,
                        expectedVersion = null,
                        isProgressAffecting = true,
                        ownerUserId = "user-1",
                    ),
                )

            check(outcome is PushOutcome.Success)
        }

    @Test
    fun `CRITICAL scenario - no-known-version pushes from two devices with DIFFERENT data never silently overwrite`() =
        runTest {
            // Reproduces the exploit the logic reviewer found at DEV-009 review: DEV-009's
            // deterministic Session ids mean two devices can compute the SAME id for the SAME
            // occurrence while both are offline, each with expectedVersion = null (neither has
            // ever synced this id before). Device A pushes first and lands on the server.
            // Device B, still believing expectedVersion = null (it never learned about A), must
            // NOT silently overwrite A's row just because it also has "no known version" - the
            // content differs, so this must become a Conflict, preserving B's payload.
            val transport = FakeSyncTransport()
            val engine = SyncEngine(transport, FakeIdGenerator(fixedId = "conflict-copy-1"))

            val deviceA =
                engine.push(
                    OutboxEntry(
                        tableName = "sessions",
                        entityId = "session-1",
                        payloadJson = """{"id":"session-1","status":"done","actual_value":60}""",
                        expectedVersion = null,
                        isProgressAffecting = true,
                        ownerUserId = "user-1",
                    ),
                )
            check(deviceA is PushOutcome.Success)
            assertEquals(1, deviceA.confirmedVersion)

            val deviceB =
                engine.push(
                    OutboxEntry(
                        tableName = "sessions",
                        entityId = "session-1",
                        payloadJson = """{"id":"session-1","status":"partial","actual_value":20}""",
                        expectedVersion = null,
                        isProgressAffecting = true,
                        ownerUserId = "user-1",
                    ),
                )

            check(deviceB is PushOutcome.Conflict)
            assertEquals(
                """{"id":"session-1","status":"partial","actual_value":20}""",
                deviceB.conflictCopy.payloadJson,
            )
            assertEquals(1, deviceB.conflictCopy.serverVersionAtConflict)
            // Device A's row on the server is untouched - not silently overwritten by B.
            assertEquals(1, transport.currentServerVersion("sessions", "session-1"))
        }

    @Test
    fun `no-known-version pushes from two devices with the SAME data converge without a spurious conflict`() =
        runTest {
            // The intended, common case §19.4 designs for: two devices independently completing
            // the SAME occurrence the SAME way should converge quietly, not manufacture a
            // conflict copy just because neither had a confirmed version yet.
            val transport = FakeSyncTransport()
            val engine = SyncEngine(transport, FakeIdGenerator())
            val payload = """{"id":"session-1","status":"done","actual_value":60}"""

            val entry =
                OutboxEntry(
                    tableName = "sessions",
                    entityId = "session-1",
                    payloadJson = payload,
                    expectedVersion = null,
                    isProgressAffecting = true,
                    ownerUserId = "user-1",
                )

            val deviceA = engine.push(entry)
            val deviceB = engine.push(entry)

            check(deviceA is PushOutcome.Success)
            check(deviceB is PushOutcome.Success)
        }

    @Test
    fun `an up-to-date progress-affecting write succeeds via compare-and-swap and returns the new version`() =
        runTest {
            val payload = """{"id":"session-1","status":"done"}"""
            val transport = FakeSyncTransport().apply { seedServerRow("sessions", "session-1", version = 3, payload) }
            val engine = SyncEngine(transport, FakeIdGenerator())

            val outcome =
                engine.push(
                    OutboxEntry(
                        tableName = "sessions",
                        entityId = "session-1",
                        payloadJson = """{"id":"session-1","status":"partial"}""",
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
            val payload = """{"id":"session-1","status":"done"}"""
            val transport = FakeSyncTransport().apply { seedServerRow("sessions", "session-1", version = 5, payload) }
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
