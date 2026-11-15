package com.stepwise.domain.fake

import com.stepwise.core.common.AppResult
import com.stepwise.core.model.Task
import com.stepwise.core.model.TaskId
import com.stepwise.core.model.UserId
import com.stepwise.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * A fake, not a mock — real in-memory state, so a Use Case test exercises actual branching
 * against realistic repository behavior (same reasoning `/core/sync`'s `FakeSyncTransport`
 * already established at DEV-008). [seed] is the one way a test populates state up front;
 * [upsert]/[getById] mirror what the real `:data` implementation actually does, including
 * returning `null` for an unknown id rather than throwing.
 */
class FakeTaskRepository : TaskRepository {
    private val tasks = MutableStateFlow<Map<String, Task>>(emptyMap())

    fun seed(task: Task) {
        tasks.value = tasks.value + (task.id.value to task)
    }

    override fun observeAll(userId: UserId): Flow<List<Task>> =
        tasks.map { all -> all.values.filter { it.userId == userId } }

    override fun observeInbox(userId: UserId): Flow<List<Task>> =
        tasks.map { all -> all.values.filter { it.userId == userId && it.date == null } }

    override suspend fun getById(id: TaskId): AppResult<Task?> = AppResult.Success(tasks.value[id.value])

    override suspend fun upsert(task: Task): AppResult<Unit> {
        tasks.value = tasks.value + (task.id.value to task)
        return AppResult.Success(Unit)
    }
}
