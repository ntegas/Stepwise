package com.stepwise.core.database

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import com.stepwise.core.model.Goal
import com.stepwise.core.model.GoalId
import com.stepwise.core.model.GoalProgressMode
import com.stepwise.core.model.GoalType
import com.stepwise.core.model.LifeAreaId
import com.stepwise.core.model.LifecycleStatus
import com.stepwise.core.model.Milestone
import com.stepwise.core.model.MilestoneId
import com.stepwise.core.model.Project
import com.stepwise.core.model.ProjectId
import com.stepwise.core.model.UserId
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

@Entity(
    tableName = "goals",
    indices = [Index("userId"), Index("lifeAreaId"), Index("projectId")],
)
@Suppress("LongParameterList") // A real entity's genuine column count, mirroring core:model's Goal.
data class GoalEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val lifeAreaId: String?,
    val projectId: String?,
    val title: String,
    val type: GoalType,
    val progressMode: GoalProgressMode,
    val targetValue: Double?,
    val unit: String?,
    val deadline: LocalDate?,
    val priority: Int?,
    val category: String?,
    val description: String?,
    val status: LifecycleStatus,
    val createdAt: Instant,
    @Embedded val sync: SyncColumns,
)

fun GoalEntity.toDomain(): Goal =
    Goal(
        id = GoalId(id),
        userId = UserId(userId),
        lifeAreaId = lifeAreaId?.let(::LifeAreaId),
        projectId = projectId?.let(::ProjectId),
        title = title,
        type = type,
        progressMode = progressMode,
        targetValue = targetValue,
        unit = unit,
        deadline = deadline,
        priority = priority,
        category = category,
        description = description,
        status = status,
        createdAt = createdAt,
        sync = sync.toDomain(),
    )

fun Goal.toEntity(): GoalEntity =
    GoalEntity(
        id = id.value,
        userId = userId.value,
        lifeAreaId = lifeAreaId?.value,
        projectId = projectId?.value,
        title = title,
        type = type,
        progressMode = progressMode,
        targetValue = targetValue,
        unit = unit,
        deadline = deadline,
        priority = priority,
        category = category,
        description = description,
        status = status,
        createdAt = createdAt,
        sync = sync.toColumns(),
    )

@Dao
interface GoalDao {
    @Upsert
    suspend fun upsert(goal: GoalEntity)

    @Query("SELECT * FROM goals WHERE userId = :userId AND deletedAt IS NULL")
    fun observeAll(userId: String): Flow<List<GoalEntity>>

    @Query("SELECT * FROM goals WHERE id = :id")
    suspend fun getById(id: String): GoalEntity?
}

@Entity(tableName = "milestones", indices = [Index("goalId")])
data class MilestoneEntity(
    @PrimaryKey val id: String,
    val goalId: String,
    val title: String,
    val sortOrder: Int,
    val weight: Double,
    val isBinary: Boolean,
    val completed: Boolean,
    val progressPercent: Double?,
    @Embedded val sync: SyncColumns,
)

fun MilestoneEntity.toDomain(): Milestone =
    Milestone(
        id = MilestoneId(id),
        goalId = GoalId(goalId),
        title = title,
        sortOrder = sortOrder,
        weight = weight,
        isBinary = isBinary,
        completed = completed,
        progressPercent = progressPercent,
        sync = sync.toDomain(),
    )

fun Milestone.toEntity(): MilestoneEntity =
    MilestoneEntity(
        id = id.value,
        goalId = goalId.value,
        title = title,
        sortOrder = sortOrder,
        weight = weight,
        isBinary = isBinary,
        completed = completed,
        progressPercent = progressPercent,
        sync = sync.toColumns(),
    )

@Dao
interface MilestoneDao {
    @Upsert
    suspend fun upsert(milestone: MilestoneEntity)

    @Query("SELECT * FROM milestones WHERE goalId = :goalId AND deletedAt IS NULL ORDER BY sortOrder")
    fun observeForGoal(goalId: String): Flow<List<MilestoneEntity>>
}

@Entity(tableName = "projects", indices = [Index("userId"), Index("goalId")])
data class ProjectEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val goalId: String?,
    val lifeAreaId: String?,
    val title: String,
    val archived: Boolean,
    @Embedded val sync: SyncColumns,
)

fun ProjectEntity.toDomain(): Project =
    Project(
        id = ProjectId(id),
        userId = UserId(userId),
        goalId = goalId?.let(::GoalId),
        lifeAreaId = lifeAreaId?.let(::LifeAreaId),
        title = title,
        archived = archived,
        sync = sync.toDomain(),
    )

fun Project.toEntity(): ProjectEntity =
    ProjectEntity(
        id = id.value,
        userId = userId.value,
        goalId = goalId?.value,
        lifeAreaId = lifeAreaId?.value,
        title = title,
        archived = archived,
        sync = sync.toColumns(),
    )

@Dao
interface ProjectDao {
    @Upsert
    suspend fun upsert(project: ProjectEntity)

    @Query("SELECT * FROM projects WHERE userId = :userId AND deletedAt IS NULL")
    fun observeAll(userId: String): Flow<List<ProjectEntity>>
}
