package com.stepwise.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
class OutboxDaoTest {
    private lateinit var database: StepwiseDatabase

    private val now = Instant.fromEpochMilliseconds(1_000_000)
    private val later = Instant.fromEpochMilliseconds(2_000_000)

    @Before
    fun createDatabase() {
        database =
            Room
                .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), StepwiseDatabase::class.java)
                .allowMainThreadQueries()
                .build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `enqueueing the same entity twice replaces the pending entry rather than duplicating it`() =
        runTest {
            val dao = database.outboxDao()
            dao.enqueue(
                OutboxEntryEntity(
                    tableName = "goals",
                    entityId = "goal-1",
                    payload = """{"title":"first edit"}""",
                    expectedVersion = null,
                    isProgressAffecting = false,
                    ownerUserId = "user-1",
                    createdAt = now,
                ),
            )
            dao.enqueue(
                OutboxEntryEntity(
                    tableName = "goals",
                    entityId = "goal-1",
                    payload = """{"title":"second edit"}""",
                    expectedVersion = null,
                    isProgressAffecting = false,
                    ownerUserId = "user-1",
                    createdAt = later,
                ),
            )

            val all = dao.getAll()

            assertEquals(1, all.size)
            assertEquals("""{"title":"second edit"}""", all.single().payload)
        }

    @Test
    fun `deleteIfUnchanged removes a drained entry whose createdAt still matches`() =
        runTest {
            val dao = database.outboxDao()
            val entry =
                OutboxEntryEntity(
                    tableName = "sessions",
                    entityId = "session-1",
                    payload = """{"status":"done"}""",
                    expectedVersion = 1,
                    isProgressAffecting = true,
                    ownerUserId = "user-1",
                    createdAt = now,
                )
            dao.enqueue(entry)

            dao.deleteIfUnchanged(entry.tableName, entry.entityId, entry.createdAt)

            assertEquals(0, dao.getAll().size)
        }

    @Test
    fun `deleteIfUnchanged is a no-op when a newer edit replaced the entry mid-drain (CRITICAL fix, DEV-008 review)`() =
        runTest {
            val dao = database.outboxDao()
            val originalEntry =
                OutboxEntryEntity(
                    tableName = "sessions",
                    entityId = "session-1",
                    payload = """{"status":"done"}""",
                    expectedVersion = 1,
                    isProgressAffecting = true,
                    ownerUserId = "user-1",
                    createdAt = now,
                )
            dao.enqueue(originalEntry)

            // A newer local edit lands while a drain of `originalEntry` is (simulated as) still
            // in flight — REPLACEs the same primary key with a different createdAt.
            dao.enqueue(originalEntry.copy(payload = """{"status":"partial"}""", createdAt = later))

            // The drain for the ORIGINAL entry finally completes and tries to delete it by its
            // (now-stale) createdAt — must be a no-op, not delete the replacement.
            dao.deleteIfUnchanged(originalEntry.tableName, originalEntry.entityId, originalEntry.createdAt)

            val remaining = dao.getAll()
            assertEquals(1, remaining.size)
            assertEquals("""{"status":"partial"}""", remaining.single().payload)
        }

    @Test
    fun `recordFailure increments attemptCount and stores the error without removing the entry`() =
        runTest {
            val dao = database.outboxDao()
            dao.enqueue(
                OutboxEntryEntity(
                    tableName = "sessions",
                    entityId = "session-1",
                    payload = """{"status":"done"}""",
                    expectedVersion = 1,
                    isProgressAffecting = true,
                    ownerUserId = "user-1",
                    createdAt = now,
                ),
            )

            dao.recordFailure("sessions", "session-1", "network timeout")
            dao.recordFailure("sessions", "session-1", "network timeout again")

            val entry = dao.getAll().single()
            assertEquals(2, entry.attemptCount)
            assertEquals("network timeout again", entry.lastError)
        }

    @Test
    fun `count reflects the number of distinct pending entities, not the number of enqueue calls`() =
        runTest {
            val dao = database.outboxDao()
            dao.enqueue(
                OutboxEntryEntity("goals", "goal-1", "{}", null, false, "user-1", now),
            )
            dao.enqueue(
                OutboxEntryEntity("goals", "goal-1", "{}", null, false, "user-1", later),
            )
            dao.enqueue(
                OutboxEntryEntity("sessions", "session-1", "{}", 1, true, "user-1", now),
            )

            assertEquals(2, dao.count())
        }

    @Test
    fun `a brand-new entry has no expectedVersion and no error`() =
        runTest {
            val dao = database.outboxDao()
            dao.enqueue(
                OutboxEntryEntity(
                    tableName = "goals",
                    entityId = "goal-1",
                    payload = "{}",
                    expectedVersion = null,
                    isProgressAffecting = false,
                    ownerUserId = "user-1",
                    createdAt = now,
                ),
            )

            val entry = dao.getAll().single()
            assertNull(entry.expectedVersion)
            assertNull(entry.lastError)
            assertEquals(0, entry.attemptCount)
        }
}
