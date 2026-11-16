package com.stepwise.domain.fake

import com.stepwise.core.common.AppResult
import com.stepwise.core.model.Habit
import com.stepwise.core.model.HabitId
import com.stepwise.core.model.UserId
import com.stepwise.domain.repository.HabitRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeHabitRepository : HabitRepository {
    private val habits = MutableStateFlow<Map<String, Habit>>(emptyMap())

    fun seed(habit: Habit) {
        habits.value = habits.value + (habit.id.value to habit)
    }

    override fun observeAll(userId: UserId): Flow<List<Habit>> =
        habits.map { all -> all.values.filter { it.userId == userId } }

    override fun observeById(id: HabitId): Flow<Habit?> = habits.map { all -> all[id.value] }

    override suspend fun getById(id: HabitId): AppResult<Habit?> = AppResult.Success(habits.value[id.value])

    override suspend fun upsert(habit: Habit): AppResult<Unit> {
        habits.value = habits.value + (habit.id.value to habit)
        return AppResult.Success(Unit)
    }
}
