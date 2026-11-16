package com.stepwise.app.di

import com.stepwise.core.common.Clock
import com.stepwise.core.common.IdGenerator
import com.stepwise.domain.repository.ActivityRepository
import com.stepwise.domain.repository.GoalActivityLinkRepository
import com.stepwise.domain.repository.GoalRepository
import com.stepwise.domain.repository.HabitRepository
import com.stepwise.domain.repository.LifeAreaRepository
import com.stepwise.domain.repository.MetricRepository
import com.stepwise.domain.usecase.CreateActivityUseCase
import com.stepwise.domain.usecase.CreateHabitUseCase
import com.stepwise.domain.usecase.EditActivityUseCase
import com.stepwise.domain.usecase.EditHabitUseCase
import com.stepwise.domain.usecase.LinkActivityToGoalUseCase
import com.stepwise.domain.usecase.UnlinkActivityFromGoalUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * DEV-018's Activities/Habits write-side Use Cases - split out the same way
 * [GoalWriteUseCaseModule] was at DEV-016. [ActivityReadUseCaseModule] holds the read side.
 * [LinkActivityToGoalUseCase]/[UnlinkActivityFromGoalUseCase] (DEV-012) are wired here for the
 * first time - no UI called them before this task.
 */
@Module
@InstallIn(SingletonComponent::class)
object ActivityWriteUseCaseModule {
    @Provides
    fun provideCreateActivityUseCase(
        activityRepository: ActivityRepository,
        lifeAreaRepository: LifeAreaRepository,
        idGenerator: IdGenerator,
        clock: Clock,
    ): CreateActivityUseCase = CreateActivityUseCase(activityRepository, lifeAreaRepository, idGenerator, clock)

    @Provides
    fun provideEditActivityUseCase(
        activityRepository: ActivityRepository,
        lifeAreaRepository: LifeAreaRepository,
        clock: Clock,
    ): EditActivityUseCase = EditActivityUseCase(activityRepository, lifeAreaRepository, clock)

    @Provides
    fun provideCreateHabitUseCase(
        habitRepository: HabitRepository,
        activityRepository: ActivityRepository,
        metricRepository: MetricRepository,
        idGenerator: IdGenerator,
        clock: Clock,
    ): CreateHabitUseCase = CreateHabitUseCase(habitRepository, activityRepository, metricRepository, idGenerator, clock)

    @Provides
    fun provideEditHabitUseCase(
        habitRepository: HabitRepository,
        activityRepository: ActivityRepository,
        metricRepository: MetricRepository,
        clock: Clock,
    ): EditHabitUseCase = EditHabitUseCase(habitRepository, activityRepository, metricRepository, clock)

    @Provides
    @Suppress("LongParameterList") // One repository per thing this Use Case cross-verifies ownership against.
    fun provideLinkActivityToGoalUseCase(
        activityRepository: ActivityRepository,
        habitRepository: HabitRepository,
        goalRepository: GoalRepository,
        metricRepository: MetricRepository,
        goalActivityLinkRepository: GoalActivityLinkRepository,
        idGenerator: IdGenerator,
        clock: Clock,
    ): LinkActivityToGoalUseCase =
        LinkActivityToGoalUseCase(
            activityRepository,
            habitRepository,
            goalRepository,
            metricRepository,
            goalActivityLinkRepository,
            idGenerator,
            clock,
        )

    @Provides
    fun provideUnlinkActivityFromGoalUseCase(
        goalRepository: GoalRepository,
        goalActivityLinkRepository: GoalActivityLinkRepository,
        clock: Clock,
    ): UnlinkActivityFromGoalUseCase = UnlinkActivityFromGoalUseCase(goalRepository, goalActivityLinkRepository, clock)
}
