package com.stepwise.core.database

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import com.stepwise.core.model.LifeArea
import com.stepwise.core.model.LifeAreaId
import com.stepwise.core.model.UserId
import com.stepwise.core.model.Vision
import com.stepwise.core.model.VisionId
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "visions")
data class VisionEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val lifeAreaId: String?,
    val content: String,
    @Embedded val sync: SyncColumns,
)

fun VisionEntity.toDomain(): Vision =
    Vision(
        id = VisionId(id),
        userId = UserId(userId),
        lifeAreaId = lifeAreaId?.let(::LifeAreaId),
        content = content,
        sync = sync.toDomain(),
    )

fun Vision.toEntity(): VisionEntity =
    VisionEntity(
        id = id.value,
        userId = userId.value,
        lifeAreaId = lifeAreaId?.value,
        content = content,
        sync = sync.toColumns(),
    )

@Dao
interface VisionDao {
    @Upsert
    suspend fun upsert(vision: VisionEntity)

    @Query("SELECT * FROM visions WHERE userId = :userId AND deletedAt IS NULL")
    fun observeAll(userId: String): Flow<List<VisionEntity>>

    @Query("SELECT * FROM visions WHERE id = :id")
    suspend fun getById(id: String): VisionEntity?
}

@Entity(tableName = "life_areas")
data class LifeAreaEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val name: String,
    val icon: String?,
    val category: String?,
    val sortOrder: Int,
    val archived: Boolean,
    @Embedded val sync: SyncColumns,
)

fun LifeAreaEntity.toDomain(): LifeArea =
    LifeArea(
        id = LifeAreaId(id),
        userId = UserId(userId),
        name = name,
        icon = icon,
        category = category,
        sortOrder = sortOrder,
        archived = archived,
        sync = sync.toDomain(),
    )

fun LifeArea.toEntity(): LifeAreaEntity =
    LifeAreaEntity(
        id = id.value,
        userId = userId.value,
        name = name,
        icon = icon,
        category = category,
        sortOrder = sortOrder,
        archived = archived,
        sync = sync.toColumns(),
    )

@Dao
interface LifeAreaDao {
    @Upsert
    suspend fun upsert(lifeArea: LifeAreaEntity)

    @Query("SELECT * FROM life_areas WHERE userId = :userId AND deletedAt IS NULL ORDER BY sortOrder")
    fun observeAll(userId: String): Flow<List<LifeAreaEntity>>

    @Query("SELECT * FROM life_areas WHERE id = :id")
    suspend fun getById(id: String): LifeAreaEntity?
}
