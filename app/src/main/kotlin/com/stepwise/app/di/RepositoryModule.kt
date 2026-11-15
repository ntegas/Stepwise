package com.stepwise.app.di

import com.stepwise.core.common.Clock
import com.stepwise.core.database.ActivityDao
import com.stepwise.core.database.HabitDao
import com.stepwise.core.database.RecurrenceRuleDao
import com.stepwise.core.database.StepwiseDatabase
import com.stepwise.core.database.TaskDao
import com.stepwise.core.network.SupabaseAuthClient
import com.stepwise.data.repository.ActivityRepositoryImpl
import com.stepwise.data.repository.AuthRepositoryImpl
import com.stepwise.data.repository.HabitRepositoryImpl
import com.stepwise.data.repository.RecurrenceOccurrenceRepositoryImpl
import com.stepwise.data.repository.RecurrenceRuleRepositoryImpl
import com.stepwise.data.repository.SessionRepositoryImpl
import com.stepwise.data.repository.TaskRepositoryImpl
import com.stepwise.domain.repository.ActivityRepository
import com.stepwise.domain.repository.AuthRepository
import com.stepwise.domain.repository.HabitRepository
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
}
