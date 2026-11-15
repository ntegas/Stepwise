package com.stepwise.domain.fake

import com.stepwise.core.common.AppResult
import com.stepwise.core.model.Project
import com.stepwise.core.model.ProjectId
import com.stepwise.core.model.UserId
import com.stepwise.domain.repository.ProjectRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeProjectRepository : ProjectRepository {
    private val projects = MutableStateFlow<Map<String, Project>>(emptyMap())

    fun seed(project: Project) {
        projects.value = projects.value + (project.id.value to project)
    }

    override fun observeAll(userId: UserId): Flow<List<Project>> =
        projects.map { all -> all.values.filter { it.userId == userId } }

    override suspend fun getById(id: ProjectId): AppResult<Project?> = AppResult.Success(projects.value[id.value])

    override suspend fun upsert(project: Project): AppResult<Unit> {
        projects.value = projects.value + (project.id.value to project)
        return AppResult.Success(Unit)
    }
}
