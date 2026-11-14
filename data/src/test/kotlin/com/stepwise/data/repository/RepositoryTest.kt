package com.stepwise.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.stepwise.core.common.AppError
import com.stepwise.core.common.AppResult
import com.stepwise.core.common.FakeClock
import com.stepwise.core.database.StepwiseDatabase
import com.stepwise.core.model.ExecutableKind
import com.stepwise.core.model.ExecutionStatus
import com.stepwise.core.model.MetricId
import com.stepwise.core.model.Session
import com.stepwise.core.model.SessionId
import com.stepwise.core.model.SessionMetricValue
import com.stepwise.core.model.SessionMetricValueId
import com.stepwise.core.model.SyncMetadata
import com.stepwise.core.model.UserId
import com.stepwise.core.model.Vision
import com.stepwise.core.model.VisionId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Instant

/**
 * Exercises :data's repository implementations against a real in-memory Room database —
 * `/ROADMAP.md` DEV-006's "local-first reads," "local mutation pipeline," "transaction
 * coordination," "error mapping," and "reactive flows" acceptance criteria, each covered by
 * one test below rather than only unit-testing the mapping functions in isolation.
 */
@RunWith(RobolectricTestRunner::class)
class RepositoryTest {
    private lateinit var database: StepwiseDatabase

    private val now = Instant.fromEpochMilliseconds(1_000_000)
    private val syncMetadata = SyncMetadata(updatedAt = now, version = 1, deletedAt = null)

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
    fun `local mutation pipeline - upsert through the repository is visible to a read through the repository`() =
        runTest {
            val repository = VisionRepositoryImpl(database.visionDao())
            val vision =
                Vision(
                    id = VisionId("vision-1"),
                    userId = UserId("user-1"),
                    lifeAreaId = null,
                    content = "Run a marathon this year",
                    sync = syncMetadata,
                )

            val upsertResult = repository.upsert(vision)
            val loaded = repository.getById(vision.id)

            assertEquals(AppResult.Success(Unit), upsertResult)
            assertEquals(AppResult.Success(vision), loaded)
        }

    @Test
    fun `reactive flow - observeAll emits the upsert without a separate re-read`() =
        runTest {
            val repository = VisionRepositoryImpl(database.visionDao())
            val vision =
                Vision(
                    id = VisionId("vision-1"),
                    userId = UserId("user-1"),
                    lifeAreaId = null,
                    content = "Run a marathon this year",
                    sync = syncMetadata,
                )

            repository.upsert(vision)
            val observed = repository.observeAll(UserId("user-1")).first()

            assertEquals(listOf(vision), observed)
        }

    @Test
    fun `transaction coordination - upsertSessionWithMetricValues commits session and metric values together`() =
        runTest {
            val repository = SessionRepositoryImpl(database, FakeClock(now))
            val session =
                Session(
                    id = SessionId("session-1"),
                    userId = UserId("user-1"),
                    sourceType = ExecutableKind.ACTIVITY,
                    sourceId = "activity-1",
                    date = LocalDate(2026, 9, 15),
                    status = ExecutionStatus.DONE,
                    clientEventId = "client-event-1",
                    notes = null,
                    sync = syncMetadata,
                )
            val metricValue =
                SessionMetricValue(
                    id = SessionMetricValueId("smv-duration"),
                    sessionId = SessionId("session-1"),
                    metricId = MetricId("duration_minutes"),
                    plannedValue = 60.0,
                    actualValue = 65.0,
                    sync = syncMetadata,
                )

            val result = repository.upsertSessionWithMetricValues(session, listOf(metricValue))
            val loadedSession = repository.getByClientEventId("client-event-1")
            val loadedValues = repository.getMetricValuesForSession(SessionId("session-1"))
            val outboxEntries = database.outboxDao().getAll()

            assertEquals(AppResult.Success(Unit), result)
            assertEquals(AppResult.Success(session), loadedSession)
            assertEquals(AppResult.Success(listOf(metricValue)), loadedValues)
            // DEV-008: the same transaction must also enqueue an outbox row for the Session AND
            // one per SessionMetricValue — a CRITICAL review finding (logic + architecture
            // reviewers): an earlier version of this repository enqueued the Session header only,
            // so the actual logged numbers (duration/distance/reps/etc.) never left the device.
            assertEquals(2, outboxEntries.size)
            val sessionEntry = outboxEntries.single { it.tableName == "sessions" }
            assertEquals("session-1", sessionEntry.entityId)
            assertTrue(sessionEntry.isProgressAffecting)
            val metricValueEntry = outboxEntries.single { it.tableName == "session_metric_values" }
            assertEquals("smv-duration", metricValueEntry.entityId)
            assertTrue(metricValueEntry.isProgressAffecting)
        }

    @Test
    fun `local-first read - getById returns null, not an error, for an id that was never written`() =
        runTest {
            val repository = VisionRepositoryImpl(database.visionDao())

            val loaded = repository.getById(VisionId("never-written"))

            assertEquals(AppResult.Success(null), loaded)
        }

    @Test
    fun `error mapping - a Room failure surfaces as AppResult Failure with AppError Database, not an exception`() =
        runTest {
            val repository = VisionRepositoryImpl(database.visionDao())
            database.close() // force every subsequent DAO call to fail

            val result = repository.getById(VisionId("vision-1"))

            assertTrue(result is AppResult.Failure)
            assertTrue((result as AppResult.Failure).error is AppError.Database)
        }
}
