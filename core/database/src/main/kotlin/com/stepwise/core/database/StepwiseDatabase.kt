package com.stepwise.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * The local operational source of truth (`/ARCHITECTURE.md` §7) — every entity DEV-004/
 * DEV-005 covers, wired into one Room database. Repository implementations (DEV-006) read
 * and write through this; nothing above `:core:database` ever touches a Room type directly
 * (`/ARCHITECTURE.md` §5's module-boundary rule).
 *
 * `exportSchema = true` (Room's own default) + `libs.versions.toml`'s KSP `room.schemaLocation`
 * arg: every schema version is committed to `core/database/schemas/`, which is what makes a
 * real [Migration][androidx.room.migration.Migration] (rather than a guess) possible once
 * this ever needs one.
 */
@Database(
    version = 1,
    entities = [
        VisionEntity::class,
        LifeAreaEntity::class,
        GoalEntity::class,
        MilestoneEntity::class,
        ProjectEntity::class,
        ActivityEntity::class,
        HabitEntity::class,
        TaskEntity::class,
        RecurrenceRuleEntity::class,
        RecurrenceOccurrenceEntity::class,
        MetricEntity::class,
        GoalActivityLinkEntity::class,
        SessionEntity::class,
        SessionMetricValueEntity::class,
    ],
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class StepwiseDatabase : RoomDatabase() {
    abstract fun visionDao(): VisionDao

    abstract fun lifeAreaDao(): LifeAreaDao

    abstract fun goalDao(): GoalDao

    abstract fun milestoneDao(): MilestoneDao

    abstract fun projectDao(): ProjectDao

    abstract fun activityDao(): ActivityDao

    abstract fun habitDao(): HabitDao

    abstract fun taskDao(): TaskDao

    abstract fun recurrenceRuleDao(): RecurrenceRuleDao

    abstract fun recurrenceOccurrenceDao(): RecurrenceOccurrenceDao

    abstract fun metricDao(): MetricDao

    abstract fun goalActivityLinkDao(): GoalActivityLinkDao

    abstract fun sessionDao(): SessionDao

    companion object {
        const val DATABASE_NAME = "stepwise.db"
    }
}
