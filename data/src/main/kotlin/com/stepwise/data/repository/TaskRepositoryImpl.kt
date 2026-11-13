package com.stepwise.data.repository

import com.stepwise.core.common.AppResult
import com.stepwise.core.database.TaskDao
import com.stepwise.core.database.toDomain
import com.stepwise.core.database.toEntity
import com.stepwise.core.model.Task
import com.stepwise.core.model.TaskId
import com.stepwise.core.model.UserId
import com.stepwise.data.runCatchingDatabaseCall
import com.stepwise.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TaskRepositoryImpl(
    private val dao: TaskDao,
) : TaskRepository {
    override fun observeAll(userId: UserId): Flow<List<Task>> =
        dao.observeAll(userId.value).map { entities -> entities.map { it.toDomain() } }

    override fun observeInbox(userId: UserId): Flow<List<Task>> =
        dao.observeInbox(userId.value).map { entities -> entities.map { it.toDomain() } }

    override suspend fun getById(id: TaskId): AppResult<Task?> =
        runCatchingDatabaseCall { dao.getById(id.value)?.toDomain() }

    override suspend fun upsert(task: Task): AppResult<Unit> = runCatchingDatabaseCall { dao.upsert(task.toEntity()) }
}
