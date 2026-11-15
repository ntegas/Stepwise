package com.stepwise.domain.fake

import com.stepwise.core.common.AppResult
import com.stepwise.core.model.Goal
import com.stepwise.core.model.GoalId
import com.stepwise.core.model.UserId
import com.stepwise.domain.repository.GoalRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeGoalRepository : GoalRepository {
    private val goals = MutableStateFlow<Map<String, Goal>>(emptyMap())

    fun seed(goal: Goal) {
        goals.value = goals.value + (goal.id.value to goal)
    }

    fun get(id: GoalId): Goal? = goals.value[id.value]

    override fun observeAll(userId: UserId): Flow<List<Goal>> =
        goals.map { all -> all.values.filter { it.userId == userId } }

    override suspend fun getById(id: GoalId): AppResult<Goal?> = AppResult.Success(goals.value[id.value])

    override suspend fun upsert(goal: Goal): AppResult<Unit> {
        goals.value = goals.value + (goal.id.value to goal)
        return AppResult.Success(Unit)
    }
}
