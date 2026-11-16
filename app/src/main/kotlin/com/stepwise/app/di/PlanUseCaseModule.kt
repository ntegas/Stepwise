package com.stepwise.app.di

import com.stepwise.domain.repository.ActivityRepository
import com.stepwise.domain.repository.HabitRepository
import com.stepwise.domain.repository.RecurrenceRuleRepository
import com.stepwise.domain.repository.SessionRepository
import com.stepwise.domain.repository.TaskRepository
import com.stepwise.domain.usecase.GetPlanRangeUseCase
import com.stepwise.domain.usecase.ResolveOccurrencesUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * DEV-017's Plan/Calendar Use Case - kept out of [UseCaseModule], which is explicitly scoped to
 * the Today/execution flow per its own KDoc.
 */
@Module
@InstallIn(SingletonComponent::class)
object PlanUseCaseModule {
    @Provides
    @Suppress("LongParameterList") // One parameter per GetPlanRangeUseCase constructor arg - see its own KDoc.
    fun provideGetPlanRangeUseCase(
        taskRepository: TaskRepository,
        activityRepository: ActivityRepository,
        habitRepository: HabitRepository,
        recurrenceRuleRepository: RecurrenceRuleRepository,
        sessionRepository: SessionRepository,
        resolveOccurrencesUseCase: ResolveOccurrencesUseCase,
    ): GetPlanRangeUseCase =
        GetPlanRangeUseCase(
            taskRepository,
            activityRepository,
            habitRepository,
            recurrenceRuleRepository,
            sessionRepository,
            resolveOccurrencesUseCase,
        )
}
