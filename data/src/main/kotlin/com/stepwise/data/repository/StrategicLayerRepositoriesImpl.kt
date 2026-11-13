package com.stepwise.data.repository

import com.stepwise.core.common.AppResult
import com.stepwise.core.database.LifeAreaDao
import com.stepwise.core.database.VisionDao
import com.stepwise.core.database.toDomain
import com.stepwise.core.database.toEntity
import com.stepwise.core.model.LifeArea
import com.stepwise.core.model.LifeAreaId
import com.stepwise.core.model.UserId
import com.stepwise.core.model.Vision
import com.stepwise.core.model.VisionId
import com.stepwise.data.runCatchingDatabaseCall
import com.stepwise.domain.repository.LifeAreaRepository
import com.stepwise.domain.repository.VisionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class VisionRepositoryImpl(
    private val dao: VisionDao,
) : VisionRepository {
    override fun observeAll(userId: UserId): Flow<List<Vision>> =
        dao.observeAll(userId.value).map { entities -> entities.map { it.toDomain() } }

    override suspend fun getById(id: VisionId): AppResult<Vision?> =
        runCatchingDatabaseCall { dao.getById(id.value)?.toDomain() }

    override suspend fun upsert(vision: Vision): AppResult<Unit> =
        runCatchingDatabaseCall { dao.upsert(vision.toEntity()) }
}

class LifeAreaRepositoryImpl(
    private val dao: LifeAreaDao,
) : LifeAreaRepository {
    override fun observeAll(userId: UserId): Flow<List<LifeArea>> =
        dao.observeAll(userId.value).map { entities -> entities.map { it.toDomain() } }

    override suspend fun getById(id: LifeAreaId): AppResult<LifeArea?> =
        runCatchingDatabaseCall { dao.getById(id.value)?.toDomain() }

    override suspend fun upsert(lifeArea: LifeArea): AppResult<Unit> =
        runCatchingDatabaseCall { dao.upsert(lifeArea.toEntity()) }
}
