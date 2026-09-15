package com.stepwise.core.database

import androidx.room.migration.Migration

/**
 * Every schema change from here on gets one named [Migration] appended to this array —
 * never a version bump with no matching migration, and never
 * [androidx.room.RoomDatabase.Builder.fallbackToDestructiveMigration] in a shipped build
 * (that would silently drop a user's local data on upgrade, forbidden by this project's
 * offline-first/no-data-loss posture, `/ARCHITECTURE.md` §8). Empty at schema version 1 —
 * there is nothing to migrate from yet; this file exists now so the *convention* (one
 * array, one place, checked against `core/database/schemas/`'s exported JSON) is
 * established before it's ever actually needed, per `DEVELOPMENT_PROTOCOL.md`'s
 * centralization principle, not left to be invented ad hoc at the first real migration.
 */
val STEPWISE_DATABASE_MIGRATIONS: Array<Migration> = emptyArray()
