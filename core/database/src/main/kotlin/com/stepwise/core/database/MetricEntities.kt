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
    indices = [Index("goalId"), Index(value = ["sourceType", "sourceId"])],
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
}
