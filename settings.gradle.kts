pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "stepwise-android"

// Module set is intentionally minimal right now. ARCHITECTURE.md §5 defines the
// full target module graph (:app, :core:model, :core:calculation, :domain,
// :core:database, :core:network, :core:sync, :data, :core:designsystem,
// :core:notifications, :feature:*), but DEV-001 only creates modules it actually
// populates with real code today, per ROADMAP.md's "do not create speculative
// infrastructure." Every other module is added by the DEV task that first needs it
// (see ROADMAP.md), not scaffolded empty ahead of time.
include(":core:common")
