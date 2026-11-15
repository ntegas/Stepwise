package com.stepwise.app.di

import android.content.Context
import androidx.room.Room
import com.stepwise.core.database.ActivityDao
import com.stepwise.core.database.HabitDao
import com.stepwise.core.database.RecurrenceRuleDao
import com.stepwise.core.database.STEPWISE_DATABASE_MIGRATIONS
import com.stepwise.core.database.StepwiseDatabase
import com.stepwise.core.database.TaskDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The one place a real, on-device [StepwiseDatabase] is ever built (DEVELOPMENT_PROTOCOL.md's
 * centralization principle) - every DAO is read off this one `@Singleton` instance, never a
 * second `Room.databaseBuilder` call, so every repository shares one SQLite connection/write
 * lock. `addMigrations(*STEPWISE_DATABASE_MIGRATIONS)` is the first real place those migrations
 * (DEV-012's `MIGRATION_1_2`/`MIGRATION_2_3`) actually run against a device install, rather than
 * only ever being exercised by a Robolectric in-memory database in a test.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    @Suppress("SpreadOperator") // Room.addMigrations is vararg; this builder runs once per
    // app process, not a hot path, so the array-copy cost a spread causes is negligible.
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): StepwiseDatabase =
        Room
            .databaseBuilder(context, StepwiseDatabase::class.java, "stepwise.db")
            .addMigrations(*STEPWISE_DATABASE_MIGRATIONS)
            .build()

    @Provides
    fun provideTaskDao(database: StepwiseDatabase): TaskDao = database.taskDao()

    @Provides
    fun provideActivityDao(database: StepwiseDatabase): ActivityDao = database.activityDao()

    @Provides
    fun provideHabitDao(database: StepwiseDatabase): HabitDao = database.habitDao()

    @Provides
    fun provideRecurrenceRuleDao(database: StepwiseDatabase): RecurrenceRuleDao = database.recurrenceRuleDao()
}
