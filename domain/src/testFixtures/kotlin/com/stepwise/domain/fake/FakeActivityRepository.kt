package com.stepwise.domain.fake

import com.stepwise.core.common.AppResult
import com.stepwise.core.model.Activity
import com.stepwise.core.model.ActivityId
import com.stepwise.core.model.UserId
import com.stepwise.domain.repository.ActivityRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeActivityRepository : ActivityRepository {
    private val activities = MutableStateFlow<Map<String, Activity>>(emptyMap())

    fun seed(activity: Activity) {
        activities.value = activities.value + (activity.id.value to activity)
    }

    fun get(id: ActivityId): Activity? = activities.value[id.value]

    override fun observeAll(userId: UserId): Flow<List<Activity>> =
        activities.map { all -> all.values.filter { it.userId == userId } }

    override suspend fun getById(id: ActivityId): AppResult<Activity?> = AppResult.Success(activities.value[id.value])

    override suspend fun upsert(activity: Activity): AppResult<Unit> {
        activities.value = activities.value + (activity.id.value to activity)
        return AppResult.Success(Unit)
    }
}
