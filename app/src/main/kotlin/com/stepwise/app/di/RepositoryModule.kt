package com.stepwise.app.di

import com.stepwise.core.common.Clock
import com.stepwise.core.database.ActivityDao
import com.stepwise.core.database.GoalDao
import com.stepwise.core.database.HabitDao
import com.stepwise.core.database.LifeAreaDao
import com.stepwise.core.database.MilestoneDao
import com.stepwise.core.database.ProjectDao
import com.stepwise.core.database.RecurrenceRuleDao
import com.stepwise.core.database.StepwiseDatabase
import com.stepwise.core.database.TaskDao
import com.stepwise.core.network.SupabaseAuthClient
import com.stepwise.data.repository.ActivityRepositoryImpl
import com.stepwise.data.repository.AuthRepositoryImpl
import com.stepwise.data.repository.GoalRepositoryImpl
import com.stepwise.data.repository.HabitRepositoryImpl
import com.stepwise.data.repository.LifeAreaRepositoryImpl
import com.stepwise.data.repository.MilestoneRepositoryImpl
import com.stepwise.data.repository.ProjectRepositoryImpl
import com.stepwise.data.repository.RecurrenceOccurrenceRepositoryImpl
import com.stepwise.data.repository.RecurrenceRuleRepositoryImpl
import com.stepwise.data.repository.SessionRepositoryImpl
import com.stepwise.data.repository.TaskRepositoryImpl
import com.stepwise.domain.repository.ActivityRepository
import com.stepwise.domain.repository.AuthRepository
import com.stepwise.domain.repository.GoalRepository
import com.stepwise.domain.repository.HabitRepository
import com.stepwise.domain.repository.LifeAreaRepository
import com.stepwise.domain.repository.MilestoneRepository
import com.stepwise.domain.repository.ProjectRepository
import com.stepwise.domain.repository.RecurrenceOccurrenceRepository
import com.stepwise.domain.repository.RecurrenceRuleRepository
import com.stepwise.domain.repository.SessionRepository
import com.stepwise.domain.repository.TaskRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Binds every `:domain` repository interface this checkpoint's flow needs to its `:data` impl. */
@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    @Provides
    fun provideTaskRepository(dao: TaskDao): TaskRepository = TaskRepositoryImpl(dao)

    @Provides
    fun provideActivityRepository(dao: ActivityDao): ActivityRepository = ActivityRepositoryImpl(dao)

    @Provides
    fun provideHabitRepository(dao: HabitDao): HabitRepository = HabitRepositoryImpl(dao)

    @Provides
    fun provideRecurrenceRuleRepository(dao: RecurrenceRuleDao): RecurrenceRuleRepository =
        RecurrenceRuleRepositoryImpl(dao)

    @Provides
    fun provideRecurrenceOccurrenceRepository(database: StepwiseDatabase): RecurrenceOccurrenceRepository =
        RecurrenceOccurrenceRepositoryImpl(database)

    @Provides
    fun provideSessionRepository(
        database: StepwiseDatabase,
        clock: Clock,
    ): SessionRepository = SessionRepositoryImpl(database, clock)

    @Provides
    fun provideAuthRepository(authClient: SupabaseAuthClient): AuthRepository = AuthRepositoryImpl(authClient)

    @Provides
    fun provideLifeAreaRepository(dao: LifeAreaDao): LifeAreaRepository = LifeAreaRepositoryImpl(dao)

    @Provides
    fun provideGoalRepository(dao: GoalDao): GoalRepository = GoalRepositoryImpl(dao)

    @Provides
    fun provideMilestoneRepository(dao: MilestoneDao): MilestoneRepository = MilestoneRepositoryImpl(dao)

    @Provides
    fun provideProjectRepository(dao: ProjectDao): ProjectRepository = ProjectRepositoryImpl(dao)
}
