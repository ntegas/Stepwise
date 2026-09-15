package com.stepwise.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.stepwise.core.model.ExecutableKind
import com.stepwise.core.model.ExecutionStatus
import com.stepwise.core.model.Goal
import com.stepwise.core.model.GoalId
import com.stepwise.core.model.GoalProgressMode
import com.stepwise.core.model.GoalType
import com.stepwise.core.model.LifecycleStatus
import com.stepwise.core.model.MetricId
import com.stepwise.core.model.Session
import com.stepwise.core.model.SessionId
import com.stepwise.core.model.SessionMetricValue
import com.stepwise.core.model.SessionMetricValueId
import com.stepwise.core.model.SyncMetadata
import com.stepwise.core.model.Task
import com.stepwise.core.model.TaskId
import com.stepwise.core.model.UserId
import com.stepwise.core.model.Vision
import com.stepwise.core.model.VisionId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Instant

/**
 * Room DAO tests need a real Android SQLite implementation — Robolectric provides that
 * on the JVM without an emulator. An in-memory database, fresh per test, so tests never
 * share state (`@Before`/`@After` open and close a new one each time).
 */
@RunWith(RobolectricTestRunner::class)
class StepwiseDatabaseTest {
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
    fun `vision round-trips through the mapper and DAO`() =
        runTest {
            val vision =
                Vision(
                    id = VisionId("vision-1"),
                    userId = UserId("user-1"),
                    lifeAreaId = null,
                    content = "Run a marathon this year",
                    sync = syncMetadata,
                )

            database.visionDao().upsert(vision.toEntity())
            val loaded = database.visionDao().getById("vision-1")?.toDomain()

            assertEquals(vision, loaded)
        }

    @Test
    fun `goal type-converters round-trip enums and dates correctly`() =
        runTest {
            val goal =
                Goal(
                    id = GoalId("goal-1"),
                    userId = UserId("user-1"),
                    lifeAreaId = null,
                    projectId = null,
                    title = "Run 500km",
                    type = GoalType.DISTANCE,
                    progressMode = GoalProgressMode.METRIC,
                    targetValue = 500.0,
                    unit = "km",
                    deadline = LocalDate(2026, 12, 31),
                    priority = 1,
                    category = "Fitness",
                    description = null,
                    status = LifecycleStatus.ACTIVE,
                    createdAt = now,
                    sync = syncMetadata,
                )

            database.goalDao().upsert(goal.toEntity())
            val loaded = database.goalDao().getById("goal-1")?.toDomain()

            assertEquals(goal, loaded)
        }

    @Test
    fun `upsertSessionWithMetricValues commits the session and every metric value together`() =
        runTest {
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
                ).toEntity()
            val durationValue =
                SessionMetricValue(
                    id = SessionMetricValueId("smv-duration"),
                    sessionId = SessionId("session-1"),
                    metricId = MetricId("duration_minutes"),
                    plannedValue = 60.0,
                    actualValue = 65.0,
                    sync = syncMetadata,
                ).toEntity()
            val distanceValue =
                SessionMetricValue(
                    id = SessionMetricValueId("smv-distance"),
                    sessionId = SessionId("session-1"),
                    metricId = MetricId("distance_km"),
                    plannedValue = null,
                    actualValue = 8.5,
                    sync = syncMetadata,
                ).toEntity()

            database.sessionDao().upsertSessionWithMetricValues(session, listOf(durationValue, distanceValue))

            val loadedSession = database.sessionDao().getByClientEventId("client-event-1")
            val loadedValues = database.sessionDao().getMetricValuesForSession("session-1")

            assertEquals(session, loadedSession)
            assertEquals(setOf(durationValue, distanceValue), loadedValues.toSet())
        }

    @Test
    fun `task inbox query excludes scheduled, archived, and deleted tasks`() =
        runTest {
            val inboxTask = taskWithDefaults(id = "task-inbox", date = null, archived = false)
            val scheduledTask = taskWithDefaults(id = "task-scheduled", date = LocalDate(2026, 9, 20), archived = false)
            val archivedTask = taskWithDefaults(id = "task-archived", date = null, archived = true)

            database.taskDao().upsert(inboxTask.toEntity())
            database.taskDao().upsert(scheduledTask.toEntity())
            database.taskDao().upsert(archivedTask.toEntity())

            val result = database.taskDao().observeInbox("user-1").first()

            assertEquals(listOf("task-inbox"), result.map { it.id })
            assertNull(result.find { it.id == "task-scheduled" })
        }

    private fun taskWithDefaults(
        id: String,
        date: LocalDate?,
        archived: Boolean,
    ) = Task(
        id = TaskId(id),
        userId = UserId("user-1"),
        goalId = null,
        projectId = null,
        lifeAreaId = null,
        title = "Task $id",
        date = date,
        time = null,
        deadline = null,
        durationMinutes = null,
        priority = null,
        reminderId = null,
        metricId = null,
        plannedResult = null,
        goalImpactScore = null,
        archived = archived,
        createdAt = now,
        sync = syncMetadata,
    )
}
