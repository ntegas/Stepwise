package com.stepwise.domain.repository

import com.stepwise.core.common.AppResult
import com.stepwise.core.model.Task
import com.stepwise.core.model.TaskId
import com.stepwise.core.model.UserId
import kotlinx.coroutines.flow.Flow

interface TaskRepository {
    fun observeAll(userId: UserId): Flow<List<Task>>

    // Inbox/Unscheduled (`/DATA_MODEL.md`'s "Inbox, Overdue, Archive") — a filtered view
    // over tasks, matching `TaskDao.observeInbox`, not a separate entity/table.
    fun observeInbox(userId: UserId): Flow<List<Task>>

    suspend fun getById(id: TaskId): AppResult<Task?>

    suspend fun upsert(task: Task): AppResult<Unit>
}
