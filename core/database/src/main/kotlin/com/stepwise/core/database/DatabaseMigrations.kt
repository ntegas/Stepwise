package com.stepwise.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Adds the `outbox` table (DEV-008, [OutboxEntryEntity]) — additive only, no existing table
 * touched, so no data-loss risk on this one. Column list/types/primary key hand-written here
 * must match Room's own generated schema for [OutboxEntryEntity] exactly (`attemptCount`'s
 * Kotlin default of `0` is a construction-time default only — Room does not emit a SQL-level
 * `DEFAULT` clause for it, so none is written here either).
 *
 * Declared before [STEPWISE_DATABASE_MIGRATIONS] below, not just as a style choice — Kotlin
 * initializes top-level properties in a file in textual order, so [STEPWISE_DATABASE_MIGRATIONS]
 * referencing this one before its own declaration is a real compile error ("Variable
 * 'MIGRATION_1_2' must be initialized"), caught by CI, not a flake (fixed during DEV-008 by
 * reordering rather than by any other workaround).
 *
 * **Not yet independently verified against Room's real generated schema** — this sandbox has no
 * Android SDK and cannot run KSP to produce `core/database/schemas/2.json` locally (the CI
 * artifact-upload step added this round is meant to let a future session capture and commit it,
 * so a real `MigrationTestHelper` test becomes possible — that test does not exist yet; tracked
 * explicitly in `/PROJECT_STATE.md`'s VERIFICATION DEBT rather than claimed here as already done).
 */
val MIGRATION_1_2 =
    object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `outbox` (
                    `tableName` TEXT NOT NULL,
                    `entityId` TEXT NOT NULL,
                    `payload` TEXT NOT NULL,
                    `expectedVersion` INTEGER,
                    `isProgressAffecting` INTEGER NOT NULL,
                    `ownerUserId` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `attemptCount` INTEGER NOT NULL,
                    `lastError` TEXT,
                    PRIMARY KEY(`tableName`, `entityId`)
                )
                """.trimIndent(),
            )
        }
    }

/**
 * Adds a unique index on `goal_activity_links(sourceType, sourceId, goalId, metricId)` (DEV-012,
 * [GoalActivityLinkEntity]'s own `indices` list) — mirrors Postgres's own
 * `goal_activity_links_unique` constraint locally, a backstop against the exact duplicate-link
 * TOCTOU race the adversarial review found ([GoalActivityLinkRepositoryImpl.insertIfNotDuplicate]'s
 * own KDoc has the full exploit/fix). Additive only (an index, not a column), no data-loss risk;
 * `IF NOT EXISTS` matches Room's own generated-index idiom.
 */
val MIGRATION_2_3 =
    object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS " +
                    "`index_goal_activity_links_sourceType_sourceId_goalId_metricId` " +
                    "ON `goal_activity_links` (`sourceType`, `sourceId`, `goalId`, `metricId`)",
            )
        }
    }

/**
 * Every schema change from here on gets one named [Migration] appended to this array —
 * never a version bump with no matching migration, and never
 * [androidx.room.RoomDatabase.Builder.fallbackToDestructiveMigration] in a shipped build
 * (that would silently drop a user's local data on upgrade, forbidden by this project's
 * offline-first/no-data-loss posture, `/ARCHITECTURE.md` §8).
 */
val STEPWISE_DATABASE_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
