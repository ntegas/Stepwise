package com.stepwise.core.database

import android.content.Context
import androidx.room.Room

/**
 * The single place `StepwiseDatabase` is ever constructed — so every build variant (the
 * real app, DEV-006+'s repository tests) gets the same migration wiring, not a
 * hand-rolled `Room.databaseBuilder(...)` call each has to remember to configure
 * identically (`DEVELOPMENT_PROTOCOL.md` centralization).
 *
 * No `.fallbackToDestructiveMigration()` — see [STEPWISE_DATABASE_MIGRATIONS]'s KDoc for
 * why that's never acceptable for this app's data.
 */
fun buildStepwiseDatabase(context: Context): StepwiseDatabase =
    Room
        .databaseBuilder(context.applicationContext, StepwiseDatabase::class.java, StepwiseDatabase.DATABASE_NAME)
        .addMigrations(*STEPWISE_DATABASE_MIGRATIONS)
        .build()
