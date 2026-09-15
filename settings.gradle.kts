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

// Module set stays minimal on purpose. ARCHITECTURE.md §5 defines the full target
// module graph (:app, :core:calculation, :domain, :core:database, :core:network,
// :core:sync, :data, :core:notifications, :feature:*) — each is added only by the
// DEV task that actually populates it with real code, per ROADMAP.md's "do not
// create speculative infrastructure." :core:common (DEV-001), :core:designsystem
// (DEV-003), and :core:model (DEV-004) exist because those tasks build real
// content into them now.
include(":core:common")
include(":core:designsystem")
include(":core:model")
