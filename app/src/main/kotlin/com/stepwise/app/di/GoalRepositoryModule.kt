package com.stepwise.app.di

import com.stepwise.core.database.GoalDao
import com.stepwise.core.database.LifeAreaDao
import com.stepwise.core.database.MilestoneDao
import com.stepwise.core.database.ProjectDao
import com.stepwise.data.repository.GoalRepositoryImpl
import com.stepwise.data.repository.LifeAreaRepositoryImpl
import com.stepwise.data.repository.MilestoneRepositoryImpl
import com.stepwise.data.repository.ProjectRepositoryImpl
import com.stepwise.domain.repository.GoalRepository
import com.stepwise.domain.repository.LifeAreaRepository
import com.stepwise.domain.repository.MilestoneRepository
import com.stepwise.domain.repository.ProjectRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Binds the `:domain` repository interfaces DEV-016's Goals flow needs to their `:data` impl. */
@Module
@InstallIn(SingletonComponent::class)
object GoalRepositoryModule {
    @Provides
    fun provideLifeAreaRepository(dao: LifeAreaDao): LifeAreaRepository = LifeAreaRepositoryImpl(dao)

    @Provides
    fun provideGoalRepository(dao: GoalDao): GoalRepository = GoalRepositoryImpl(dao)

    @Provides
    fun provideMilestoneRepository(dao: MilestoneDao): MilestoneRepository = MilestoneRepositoryImpl(dao)

    @Provides
    fun provideProjectRepository(dao: ProjectDao): ProjectRepository = ProjectRepositoryImpl(dao)
}
