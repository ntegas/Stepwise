package com.stepwise.domain.fake

import com.stepwise.core.common.AppResult
import com.stepwise.core.model.LifeArea
import com.stepwise.core.model.LifeAreaId
import com.stepwise.core.model.UserId
import com.stepwise.domain.repository.LifeAreaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeLifeAreaRepository : LifeAreaRepository {
    private val lifeAreas = MutableStateFlow<Map<String, LifeArea>>(emptyMap())

    fun seed(lifeArea: LifeArea) {
        lifeAreas.value = lifeAreas.value + (lifeArea.id.value to lifeArea)
    }

    override fun observeAll(userId: UserId): Flow<List<LifeArea>> =
        lifeAreas.map { all -> all.values.filter { it.userId == userId } }

    override suspend fun getById(id: LifeAreaId): AppResult<LifeArea?> = AppResult.Success(lifeAreas.value[id.value])

    override suspend fun upsert(lifeArea: LifeArea): AppResult<Unit> {
        lifeAreas.value = lifeAreas.value + (lifeArea.id.value to lifeArea)
        return AppResult.Success(Unit)
    }
}
