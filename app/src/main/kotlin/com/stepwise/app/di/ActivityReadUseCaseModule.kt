package com.stepwise.app.di

import com.stepwise.domain.repository.ActivityRepository
import com.stepwise.domain.repository.GoalActivityLinkRepository
import com.stepwise.domain.repository.HabitRepository
import com.stepwise.domain.repository.MetricRepository
import com.stepwise.domain.repository.SessionRepository
import com.stepwise.domain.repository.TaskRepository
import com.stepwise.domain.usecase.GetActivitiesUseCase
import com.stepwise.domain.usecase.GetGoalLinksForSourceUseCase
import com.stepwise.domain.usecase.GetHabitsUseCase
import com.stepwise.domain.usecase.GetMetricsUseCase
import com.stepwise.domain.usecase.GetSessionHistoryUseCase
import com.stepwise.domain.usecase.ObserveActivityUseCase
import com.stepwise.domain.usecase.ObserveHabitUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * DEV-018's Activities/Habits read-side Use Cases - split out the same way
 * [GoalReadUseCaseModule]/[GoalWriteUseCaseModule] were at DEV-016. [ActivityWriteUseCaseModule]
 * holds the write side.
 */
@Module
@InstallIn(SingletonComponent::class)
object ActivityReadUseCaseModule {
    @Provides
    fun provideGetActivitiesUseCase(activityRepository: ActivityRepository): GetActivitiesUseCase =
        GetActivitiesUseCase(activityRepository)

    @Provides
    fun provideObserveActivityUseCase(activityRepository: ActivityRepository): ObserveActivityUseCase =
        ObserveActivityUseCase(activityRepository)

    @Provides
    fun provideGetHabitsUseCase(habitRepository: HabitRepository): GetHabitsUseCase = GetHabitsUseCase(habitRepository)

    @Provides
    fun provideObserveHabitUseCase(habitRepository: HabitRepository): ObserveHabitUseCase =
        ObserveHabitUseCase(habitRepository)

    @Provides
    fun provideGetGoalLinksForSourceUseCase(
        activityRepository: ActivityRepository,
        habitRepository: HabitRepository,
        goalActivityLinkRepository: GoalActivityLinkRepository,
    ): GetGoalLinksForSourceUseCase =
        GetGoalLinksForSourceUseCase(activityRepository, habitRepository, goalActivityLinkRepository)

    @Provides
    fun provideGetMetricsUseCase(metricRepository: MetricRepository): GetMetricsUseCase =
        GetMetricsUseCase(metricRepository)

    @Provides
    fun provideGetSessionHistoryUseCase(
        taskRepository: TaskRepository,
        activityRepository: ActivityRepository,
        habitRepository: HabitRepository,
        sessionRepository: SessionRepository,
    ): GetSessionHistoryUseCase =
        GetSessionHistoryUseCase(taskRepository, activityRepository, habitRepository, sessionRepository)
}
