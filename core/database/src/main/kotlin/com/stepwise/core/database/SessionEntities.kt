package com.stepwise.core.database

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.stepwise.core.model.ExecutableKind
import com.stepwise.core.model.ExecutionStatus
import com.stepwise.core.model.MetricId
import com.stepwise.core.model.Session
import com.stepwise.core.model.SessionId
import com.stepwise.core.model.SessionMetricValue
import com.stepwise.core.model.SessionMetricValueId
import com.stepwise.core.model.UserId
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

@Entity(
    tableName = "sessions",
    indices = [Index("userId"), Index(value = ["sourceType", "sourceId"]), Index("clientEventId", unique = true)],
)
data class SessionEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val sourceType: ExecutableKind,
    val sourceId: String,
    val date: LocalDate,
    val status: ExecutionStatus,
    // Unique per user (concept §60, decision B4) — the idempotency key a duplicate
    // submission upserts against instead of double-counting.
    val clientEventId: String,
    val notes: String?,
    @Embedded val sync: SyncColumns,
)

fun SessionEntity.toDomain(): Session =
    Session(
        id = SessionId(id),
        userId = UserId(userId),
        sourceType = sourceType,
        sourceId = sourceId,
        date = date,
        status = status,
        clientEventId = clientEventId,
        notes = notes,
        sync = sync.toDomain(),
    )

fun Session.toEntity(): SessionEntity =
    SessionEntity(
        id = id.value,
        userId = userId.value,
        sourceType = sourceType,
        sourceId = sourceId,
        date = date,
        status = status,
        clientEventId = clientEventId,
        notes = notes,
        sync = sync.toColumns(),
    )

@Entity(tableName = "session_metric_values", indices = [Index("sessionId"), Index("metricId")])
data class SessionMetricValueEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val metricId: String,
    val plannedValue: Double?,
    val actualValue: Double,
    @Embedded val sync: SyncColumns,
)

fun SessionMetricValueEntity.toDomain(): SessionMetricValue =
    SessionMetricValue(
        id = SessionMetricValueId(id),
        sessionId = SessionId(sessionId),
        metricId = MetricId(metricId),
        plannedValue = plannedValue,
        actualValue = actualValue,
        sync = sync.toDomain(),
    )

fun SessionMetricValue.toEntity(): SessionMetricValueEntity =
    SessionMetricValueEntity(
        id = id.value,
        sessionId = sessionId.value,
        metricId = metricId.value,
        plannedValue = plannedValue,
        actualValue = actualValue,
        sync = sync.toColumns(),
    )

@Dao
interface SessionDao {
    @Upsert
    suspend fun upsertSession(session: SessionEntity)

    @Upsert
    suspend fun upsertMetricValues(values: List<SessionMetricValueEntity>)

    /**
     * The one multi-entity write this module demonstrates (`/ROADMAP.md` DEV-005's
     * "transactions"): a Session's own row and every one of its metric readings commit
     * together or not at all — matching `/ARCHITECTURE.md` §9.0's transaction-boundary
     * rule that a Session's canonical write must never leave metric values partially
     * applied. The full canonical-write orchestration (outbox enqueue, local
     * recalculation) is DEV-006/DEV-009's job, layered on top of this.
     */
    @Transaction
    suspend fun upsertSessionWithMetricValues(
        session: SessionEntity,
        metricValues: List<SessionMetricValueEntity>,
    ) {
        upsertSession(session)
        upsertMetricValues(metricValues)
    }

    @Query("SELECT * FROM sessions WHERE userId = :userId AND deletedAt IS NULL")
    fun observeAll(userId: String): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE sourceType = :sourceType AND sourceId = :sourceId AND deletedAt IS NULL")
    fun observeForSource(
        sourceType: ExecutableKind,
        sourceId: String,
    ): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE clientEventId = :clientEventId LIMIT 1")
    suspend fun getByClientEventId(clientEventId: String): SessionEntity?

    @Query("SELECT * FROM session_metric_values WHERE sessionId = :sessionId AND deletedAt IS NULL")
    suspend fun getMetricValuesForSession(sessionId: String): List<SessionMetricValueEntity>
}
