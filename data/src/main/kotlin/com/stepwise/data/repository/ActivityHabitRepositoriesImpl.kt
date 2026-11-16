package com.stepwise.data.repository

import com.stepwise.core.common.AppResult
import com.stepwise.core.database.ActivityDao
import com.stepwise.core.database.HabitDao
import com.stepwise.core.database.toDomain
import com.stepwise.core.database.toEntity
import com.stepwise.core.model.Activity
import com.stepwise.core.model.ActivityId
import com.stepwise.core.model.Habit
import com.stepwise.core.model.HabitId
import com.stepwise.core.model.UserId
import com.stepwise.data.runCatchingDatabaseCall
import com.stepwise.domain.repository.ActivityRepository
import com.stepwise.domain.repository.HabitRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ActivityRepositoryImpl(
    private val dao: ActivityDao,
) : ActivityRepository {
    override fun observeAll(userId: UserId): Flow<List<Activity>> =
        dao.observeAll(userId.value).map { entities -> entities.map { it.toDomain() } }

    override fun observeById(id: ActivityId): Flow<Activity?> =
        dao.observeById(id.value).map { entity -> entity?.toDomain() }

    override suspend fun getById(id: ActivityId): AppResult<Activity?> =
        runCatchingDatabaseCall { dao.getById(id.value)?.toDomain() }

    override suspend fun upsert(activity: Activity): AppResult<Unit> =
        runCatchingDatabaseCall { dao.upsert(activity.toEntity()) }
}

class HabitRepositoryImpl(
    private val dao: HabitDao,
) : HabitRepository {
    override fun observeAll(userId: UserId): Flow<List<Habit>> =
        dao.observeAll(userId.value).map { entities -> entities.map { it.toDomain() } }

    override fun observeById(id: HabitId): Flow<Habit?> = dao.observeById(id.value).map { entity -> entity?.toDomain() }

    override suspend fun getById(id: HabitId): AppResult<Habit?> =
        runCatchingDatabaseCall { dao.getById(id.value)?.toDomain() }

    override suspend fun upsert(habit: Habit): AppResult<Unit> =
        runCatchingDatabaseCall { dao.upsert(habit.toEntity()) }
}
