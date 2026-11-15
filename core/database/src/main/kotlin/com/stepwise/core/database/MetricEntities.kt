package com.stepwise.core.database

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import com.stepwise.core.model.GoalActivityLink
import com.stepwise.core.model.GoalActivityLinkId
import com.stepwise.core.model.GoalId
import com.stepwise.core.model.Metric
import com.stepwise.core.model.MetricId
import com.stepwise.core.model.ProgressSourceKind
import kotlinx.coroutines.flow.Flow

// No userId, no SyncColumns — a global, shared reference catalog, not per-user
// data (see Metric's own KDoc in :core:model for why this is the one deliberate
// exception to "every table carries user_id and syncs").
@Entity(tableName = "metrics")
data class MetricEntity(
    @PrimaryKey val id: String,
    val key: String,
    val canonicalUnit: String,
)

fun MetricEntity.toDomain(): Metric = Metric(id = MetricId(id), key = key, canonicalUnit = canonicalUnit)

fun Metric.toEntity(): MetricEntity = MetricEntity(id = id.value, key = key, canonicalUnit = canonicalUnit)

@Dao
interface MetricDao {
    @Upsert
    suspend fun upsert(metric: MetricEntity)

    @Query("SELECT * FROM metrics")
    fun observeAll(): Flow<List<MetricEntity>>

    @Query("SELECT * FROM metrics WHERE id = :id")
    suspend fun getById(id: String): MetricEntity?
}

@Entity(
    tableName = "goal_activity_links",
    indices = [
        Index("goalId"),
        Index(value = ["sourceType", "sourceId"]),
        // Mirrors Postgres's own goal_activity_links_unique constraint (migration
        // 0002_canonical_schema.sql) as a local backstop against the same duplicate link ever
        // persisting twice - DEV-012 adversarial review, same two-layer defense sessions already
        // has via SessionEntity's own unique clientEventId index.
        Index(value = ["sourceType", "sourceId", "goalId", "metricId"], unique = true),
    ],
)
data class GoalActivityLinkEntity(
    @PrimaryKey val id: String,
    val sourceType: ProgressSourceKind,
    val sourceId: String,
    val goalId: String,
    val metricId: String,
    @Embedded val sync: SyncColumns,
)

fun GoalActivityLinkEntity.toDomain(): GoalActivityLink =
    GoalActivityLink(
        id = GoalActivityLinkId(id),
        sourceType = sourceType,
        sourceId = sourceId,
        goalId = GoalId(goalId),
        metricId = MetricId(metricId),
        sync = sync.toDomain(),
    )

fun GoalActivityLink.toEntity(): GoalActivityLinkEntity =
    GoalActivityLinkEntity(
        id = id.value,
        sourceType = sourceType,
        sourceId = sourceId,
        goalId = goalId.value,
        metricId = metricId.value,
        sync = sync.toColumns(),
    )

@Dao
interface GoalActivityLinkDao {
    @Upsert
    suspend fun upsert(link: GoalActivityLinkEntity)

    @Query(
        "SELECT * FROM goal_activity_links WHERE sourceType = :sourceType AND sourceId = :sourceId " +
            "AND deletedAt IS NULL",
    )
    fun observeForSource(
        sourceType: ProgressSourceKind,
        sourceId: String,
    ): Flow<List<GoalActivityLinkEntity>>

    @Query("SELECT * FROM goal_activity_links WHERE goalId = :goalId AND deletedAt IS NULL")
    fun observeForGoal(goalId: String): Flow<List<GoalActivityLinkEntity>>

    // Added at DEV-012 - see GoalActivityLinkRepository.getById's own comment.
    @Query("SELECT * FROM goal_activity_links WHERE id = :id")
    suspend fun getById(id: String): GoalActivityLinkEntity?

    // Added at DEV-012 - used only inside GoalActivityLinkRepositoryImpl.insertIfNotDuplicate's
    // own transaction (see its comment); never exposed as a standalone repository method, since
    // a standalone check-then-write pair is exactly the TOCTOU race that method exists to close.
    @Query(
        "SELECT * FROM goal_activity_links WHERE sourceType = :sourceType AND sourceId = :sourceId " +
            "AND goalId = :goalId AND metricId = :metricId AND deletedAt IS NULL LIMIT 1",
    )
    suspend fun getForSourceGoalMetric(
        sourceType: ProgressSourceKind,
        sourceId: String,
        goalId: String,
        metricId: String,
    ): GoalActivityLinkEntity?
}
