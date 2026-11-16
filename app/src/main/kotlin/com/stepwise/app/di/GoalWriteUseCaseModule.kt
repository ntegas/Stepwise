package com.stepwise.app.di

import com.stepwise.core.common.Clock
import com.stepwise.core.common.IdGenerator
import com.stepwise.domain.repository.GoalRepository
import com.stepwise.domain.repository.LifeAreaRepository
import com.stepwise.domain.repository.MilestoneRepository
import com.stepwise.domain.repository.ProjectRepository
import com.stepwise.domain.usecase.AddMilestoneUseCase
import com.stepwise.domain.usecase.CreateGoalUseCase
import com.stepwise.domain.usecase.EditGoalUseCase
import com.stepwise.domain.usecase.EditMilestoneUseCase
import com.stepwise.domain.usecase.UpdateGoalStatusUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * DEV-016's Goals write-side Use Cases - split out of [UseCaseModule] once that object crossed
 * detekt's TooManyFunctions threshold. [GoalReadUseCaseModule] holds the read side.
 */
@Module
@InstallIn(SingletonComponent::class)
object GoalWriteUseCaseModule {
    @Provides
    fun provideCreateGoalUseCase(
        goalRepository: GoalRepository,
        lifeAreaRepository: LifeAreaRepository,
        projectRepository: ProjectRepository,
        idGenerator: IdGenerator,
        clock: Clock,
    ): CreateGoalUseCase = CreateGoalUseCase(goalRepository, lifeAreaRepository, projectRepository, idGenerator, clock)

    @Provides
    fun provideEditGoalUseCase(
        goalRepository: GoalRepository,
        lifeAreaRepository: LifeAreaRepository,
        projectRepository: ProjectRepository,
        clock: Clock,
    ): EditGoalUseCase = EditGoalUseCase(goalRepository, lifeAreaRepository, projectRepository, clock)

    @Provides
    fun provideUpdateGoalStatusUseCase(
        goalRepository: GoalRepository,
        clock: Clock,
    ): UpdateGoalStatusUseCase = UpdateGoalStatusUseCase(goalRepository, clock)

    @Provides
    fun provideAddMilestoneUseCase(
        goalRepository: GoalRepository,
        milestoneRepository: MilestoneRepository,
        idGenerator: IdGenerator,
        clock: Clock,
    ): AddMilestoneUseCase = AddMilestoneUseCase(goalRepository, milestoneRepository, idGenerator, clock)

    @Provides
    fun provideEditMilestoneUseCase(
        goalRepository: GoalRepository,
        milestoneRepository: MilestoneRepository,
        clock: Clock,
    ): EditMilestoneUseCase = EditMilestoneUseCase(goalRepository, milestoneRepository, clock)
}
