package com.stepwise.domain.repository

import com.stepwise.core.common.AppResult
import com.stepwise.core.model.Activity
import com.stepwise.core.model.ActivityId
import com.stepwise.core.model.Habit
import com.stepwise.core.model.HabitId
import com.stepwise.core.model.UserId
import kotlinx.coroutines.flow.Flow

interface ActivityRepository {
    fun observeAll(userId: UserId): Flow<List<Activity>>

    // Added at DEV-018 — ObserveActivityUseCase needs a narrow, PK-scoped reactive read for
    // Activity Detail, the same reason GoalRepository grew its own observeById at DEV-016 after
    // that screen's first draft filtered observeAll's entire per-user list instead
    // (/ARCHITECTURE.md §22's "one broad query multiple screens over-recompose from" anti-pattern).
    fun observeById(id: ActivityId): Flow<Activity?>

    suspend fun getById(id: ActivityId): AppResult<Activity?>

    suspend fun upsert(activity: Activity): AppResult<Unit>
}

interface HabitRepository {
    fun observeAll(userId: UserId): Flow<List<Habit>>

    // Added at DEV-018 — same reasoning as ActivityRepository.observeById above, for Habit Detail.
    fun observeById(id: HabitId): Flow<Habit?>

    // Added at DEV-009 — the Canonical Execution Engine needs to load a Habit by id to verify
    // ownership and read its targetValue/metricId before completing it, the same reason
    // TaskRepository/ActivityRepository already had their own getById.
    suspend fun getById(id: HabitId): AppResult<Habit?>

    suspend fun upsert(habit: Habit): AppResult<Unit>
}
