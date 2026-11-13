package com.stepwise.domain.repository

import com.stepwise.core.common.AppResult
import com.stepwise.core.model.Activity
import com.stepwise.core.model.ActivityId
import com.stepwise.core.model.Habit
import com.stepwise.core.model.UserId
import kotlinx.coroutines.flow.Flow

interface ActivityRepository {
    fun observeAll(userId: UserId): Flow<List<Activity>>

    suspend fun getById(id: ActivityId): AppResult<Activity?>

    suspend fun upsert(activity: Activity): AppResult<Unit>
}

interface HabitRepository {
    fun observeAll(userId: UserId): Flow<List<Habit>>

    suspend fun upsert(habit: Habit): AppResult<Unit>
}
