package com.stepwise.domain.usecase

import com.stepwise.core.common.AppResult
import com.stepwise.core.common.Clock
import com.stepwise.core.model.Activity
import com.stepwise.domain.repository.ActivityRepository
import com.stepwise.domain.repository.LifeAreaRepository

/**
 * Edits an existing [Activity]'s own fields (`/ROADMAP.md` DEV-012), including toggling
 * [Activity.archived] — unlike [com.stepwise.core.model.Goal]'s 4-state [com.stepwise.core.model.LifecycleStatus],
 * an Activity has only a plain boolean, so no separate status Use Case is needed here.
 */
class EditActivityUseCase(
    private val activityRepository: ActivityRepository,
    private val lifeAreaRepository: LifeAreaRepository,
    private val clock: Clock,
) {
    suspend fun execute(command: EditActivityCommand): AppResult<Activity> =
        ActivityAccess
            .verifyOwnedActivity(activityRepository, command.userId, command.activityId)
            .andThen { existing -> writeEdit(existing, command) }

    private suspend fun writeEdit(
        existing: Activity,
        command: EditActivityCommand,
    ): AppResult<Activity> {
        val updated = applyEdits(existing, command)
        return CrossEntityOwnership.verifyLifeArea(lifeAreaRepository, updated.userId, updated.lifeAreaId).andThen {
            activityRepository.upsert(updated).map { updated }
        }
    }

    private fun applyEdits(
        existing: Activity,
        command: EditActivityCommand,
    ): Activity =
        existing.copy(
            title = command.title ?: existing.title,
            lifeAreaId = command.lifeAreaId ?: existing.lifeAreaId,
            icon = command.icon ?: existing.icon,
            archived = command.archived ?: existing.archived,
            sync = existing.sync.copy(updatedAt = clock.now()),
        )
}
