package com.stepwise.app.di

import com.stepwise.domain.repository.GoalRepository
import com.stepwise.domain.repository.LifeAreaRepository
import com.stepwise.domain.repository.MilestoneRepository
import com.stepwise.domain.repository.ProjectRepository
import com.stepwise.domain.usecase.GetGoalByIdUseCase
import com.stepwise.domain.usecase.GetGoalsUseCase
import com.stepwise.domain.usecase.GetLifeAreasUseCase
import com.stepwise.domain.usecase.GetMilestonesUseCase
import com.stepwise.domain.usecase.GetProjectsUseCase
import com.stepwise.domain.usecase.ObserveGoalUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * DEV-016's Goals read-side Use Cases - split out of [UseCaseModule] once that object crossed
 * detekt's TooManyFunctions threshold. [GoalWriteUseCaseModule] holds the write side.
 */
@Module
@InstallIn(SingletonComponent::class)
object GoalReadUseCaseModule {
    @Provides
    fun provideGetGoalsUseCase(goalRepository: GoalRepository): GetGoalsUseCase = GetGoalsUseCase(goalRepository)

    @Provides
    fun provideGetGoalByIdUseCase(goalRepository: GoalRepository): GetGoalByIdUseCase =
        GetGoalByIdUseCase(goalRepository)

    @Provides
    fun provideObserveGoalUseCase(goalRepository: GoalRepository): ObserveGoalUseCase =
        ObserveGoalUseCase(goalRepository)

    @Provides
    fun provideGetMilestonesUseCase(
        goalRepository: GoalRepository,
        milestoneRepository: MilestoneRepository,
    ): GetMilestonesUseCase = GetMilestonesUseCase(goalRepository, milestoneRepository)

    @Provides
    fun provideGetLifeAreasUseCase(lifeAreaRepository: LifeAreaRepository): GetLifeAreasUseCase =
        GetLifeAreasUseCase(lifeAreaRepository)

    @Provides
    fun provideGetProjectsUseCase(projectRepository: ProjectRepository): GetProjectsUseCase =
        GetProjectsUseCase(projectRepository)
}
