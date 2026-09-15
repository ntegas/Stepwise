package com.stepwise.core.database

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import com.stepwise.core.model.GoalId
import com.stepwise.core.model.LifeAreaId
import com.stepwise.core.model.MetricId
import com.stepwise.core.model.ProjectId
import com.stepwise.core.model.ReminderId
import com.stepwise.core.model.Task
import com.stepwise.core.model.TaskId
import com.stepwise.core.model.UserId
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.time.Instant

@Entity(
    tableName = "tasks",
    indices = [Index("userId"), Index("goalId"), Index("projectId"), Index("date")],
)
@Suppress("LongParameterList") // A real entity's genuine column count, mirroring core:model's Task.
data class TaskEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val goalId: String?,
    val projectId: String?,
    val lifeAreaId: String?,
    val title: String,
    val date: LocalDate?,
    val time: LocalTime?,
    val deadline: LocalDate?,
    val durationMinutes: Int?,
    val priority: Int?,
    val reminderId: String?,
    val metricId: String?,
    val plannedResult: Double?,
    val goalImpactScore: Int?,
    val archived: Boolean,
    val createdAt: Instant,
    @Embedded val sync: SyncColumns,
)

fun TaskEntity.toDomain(): Task =
    Task(
        id = TaskId(id),
        userId = UserId(userId),
        goalId = goalId?.let(::GoalId),
        projectId = projectId?.let(::ProjectId),
        lifeAreaId = lifeAreaId?.let(::LifeAreaId),
        title = title,
        date = date,
        time = time,
        deadline = deadline,
        durationMinutes = durationMinutes,
        priority = priority,
        reminderId = reminderId?.let(::ReminderId),
        metricId = metricId?.let(::MetricId),
        plannedResult = plannedResult,
        goalImpactScore = goalImpactScore,
        archived = archived,
        createdAt = createdAt,
        sync = sync.toDomain(),
    )

fun Task.toEntity(): TaskEntity =
    TaskEntity(
        id = id.value,
        userId = userId.value,
        goalId = goalId?.value,
        projectId = projectId?.value,
        lifeAreaId = lifeAreaId?.value,
        title = title,
        date = date,
        time = time,
        deadline = deadline,
        durationMinutes = durationMinutes,
        priority = priority,
        reminderId = reminderId?.value,
        metricId = metricId?.value,
        plannedResult = plannedResult,
        goalImpactScore = goalImpactScore,
        archived = archived,
        createdAt = createdAt,
        sync = sync.toColumns(),
    )

@Dao
interface TaskDao {
    @Upsert
    suspend fun upsert(task: TaskEntity)

    @Query("SELECT * FROM tasks WHERE userId = :userId AND deletedAt IS NULL")
    fun observeAll(userId: String): Flow<List<TaskEntity>>

    // Inbox/Unscheduled (`/DATA_MODEL.md`'s "Inbox, Overdue, Archive" — a view over
    // tasks, not its own table): date IS NULL, not archived, not deleted.
    @Query("SELECT * FROM tasks WHERE userId = :userId AND date IS NULL AND NOT archived AND deletedAt IS NULL")
    fun observeInbox(userId: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: String): TaskEntity?
}
