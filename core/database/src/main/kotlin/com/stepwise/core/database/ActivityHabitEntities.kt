package com.stepwise.core.database

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import com.stepwise.core.model.Activity
import com.stepwise.core.model.ActivityId
import com.stepwise.core.model.Habit
import com.stepwise.core.model.HabitId
import com.stepwise.core.model.LifeAreaId
import com.stepwise.core.model.MetricId
import com.stepwise.core.model.UserId
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "activities", indices = [Index("userId"), Index("lifeAreaId")])
data class ActivityEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val lifeAreaId: String?,
    val title: String,
    val icon: String?,
    val archived: Boolean,
    @Embedded val sync: SyncColumns,
)

fun ActivityEntity.toDomain(): Activity =
    Activity(
        id = ActivityId(id),
        userId = UserId(userId),
        lifeAreaId = lifeAreaId?.let(::LifeAreaId),
        title = title,
        icon = icon,
        archived = archived,
        sync = sync.toDomain(),
    )

fun Activity.toEntity(): ActivityEntity =
    ActivityEntity(
        id = id.value,
        userId = userId.value,
        lifeAreaId = lifeAreaId?.value,
        title = title,
        icon = icon,
        archived = archived,
        sync = sync.toColumns(),
    )

@Dao
interface ActivityDao {
    @Upsert
    suspend fun upsert(activity: ActivityEntity)

    @Query("SELECT * FROM activities WHERE userId = :userId AND deletedAt IS NULL")
    fun observeAll(userId: String): Flow<List<ActivityEntity>>

    @Query("SELECT * FROM activities WHERE id = :id")
    suspend fun getById(id: String): ActivityEntity?
}

@Entity(tableName = "habits", indices = [Index("userId"), Index("activityId")])
data class HabitEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val activityId: String?,
    val title: String,
    val targetValue: Double,
    val metricId: String,
    val archived: Boolean,
    @Embedded val sync: SyncColumns,
)

fun HabitEntity.toDomain(): Habit =
    Habit(
        id = HabitId(id),
        userId = UserId(userId),
        activityId = activityId?.let(::ActivityId),
        title = title,
        targetValue = targetValue,
        metricId = MetricId(metricId),
        archived = archived,
        sync = sync.toDomain(),
    )

fun Habit.toEntity(): HabitEntity =
    HabitEntity(
        id = id.value,
        userId = userId.value,
        activityId = activityId?.value,
        title = title,
        targetValue = targetValue,
        metricId = metricId.value,
        archived = archived,
        sync = sync.toColumns(),
    )

@Dao
interface HabitDao {
    @Upsert
    suspend fun upsert(habit: HabitEntity)

    @Query("SELECT * FROM habits WHERE userId = :userId AND deletedAt IS NULL")
    fun observeAll(userId: String): Flow<List<HabitEntity>>
}
