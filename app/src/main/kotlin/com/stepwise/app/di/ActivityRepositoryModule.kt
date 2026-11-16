package com.stepwise.app.di

import com.stepwise.core.database.MetricDao
import com.stepwise.core.database.StepwiseDatabase
import com.stepwise.data.repository.GoalActivityLinkRepositoryImpl
import com.stepwise.data.repository.MetricRepositoryImpl
import com.stepwise.domain.repository.GoalActivityLinkRepository
import com.stepwise.domain.repository.MetricRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Binds the `:domain` repository interfaces DEV-018's Activities/Habits flow newly needs
 * ([GoalActivityLinkRepository] for "which Goals is this source linked to,"
 * [MetricRepository] for the global Metric catalog) - [ActivityRepository]/[HabitRepository]
 * themselves were already bound in [RepositoryModule] since DEV-009.
 */
@Module
@InstallIn(SingletonComponent::class)
object ActivityRepositoryModule {
    @Provides
    fun provideGoalActivityLinkRepository(database: StepwiseDatabase): GoalActivityLinkRepository =
        GoalActivityLinkRepositoryImpl(database)

    @Provides
    fun provideMetricRepository(dao: MetricDao): MetricRepository = MetricRepositoryImpl(dao)
}
