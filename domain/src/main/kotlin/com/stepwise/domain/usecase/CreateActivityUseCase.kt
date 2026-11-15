package com.stepwise.domain.usecase

import com.stepwise.core.common.AppResult
import com.stepwise.core.common.Clock
import com.stepwise.core.common.IdGenerator
import com.stepwise.core.model.Activity
import com.stepwise.core.model.ActivityId
import com.stepwise.core.model.SyncMetadata
import com.stepwise.domain.repository.ActivityRepository
import com.stepwise.domain.repository.LifeAreaRepository

/**
 * Creates a new [Activity] (`/ROADMAP.md` DEV-012) — verifies an attached [lifeAreaId][
 * CreateActivityCommand.lifeAreaId] belongs to the same caller first ([CrossEntityOwnership],
 * the same DEV-011 adversarial-review-sourced check [com.stepwise.domain.usecase.CreateGoalUseCase]
 * already applies to a Goal's own `lifeAreaId`).
 */
class CreateActivityUseCase(
    private val activityRepository: ActivityRepository,
    private val lifeAreaRepository: LifeAreaRepository,
    private val idGenerator: IdGenerator,
    private val clock: Clock,
) {
    suspend fun execute(command: CreateActivityCommand): AppResult<Activity> =
        CrossEntityOwnership.verifyLifeArea(lifeAreaRepository, command.userId, command.lifeAreaId).andThen {
            val activity = buildActivity(command)
            activityRepository.upsert(activity).map { activity }
        }

    private fun buildActivity(command: CreateActivityCommand): Activity =
        Activity(
            id = ActivityId(idGenerator.newId()),
            userId = command.userId,
            lifeAreaId = command.lifeAreaId,
            title = command.title,
            icon = command.icon,
            archived = false,
            sync = SyncMetadata(updatedAt = clock.now(), version = 1, deletedAt = null),
        )
}
