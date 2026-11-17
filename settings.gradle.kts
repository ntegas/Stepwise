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
// module graph (:app, :core:calculation, :domain, :core:network, :core:sync,
// :data, :core:notifications, :feature:*) — each is added only by the DEV task
// that actually populates it with real code, per ROADMAP.md's "do not create
// speculative infrastructure." :core:common (DEV-001), :core:designsystem
// (DEV-003), :core:model (DEV-004), :core:database (DEV-005), :domain and :data
// (DEV-006), :core:network (DEV-007) exist because those tasks build real content
// into them now.
include(":core:common")
include(":core:designsystem")
include(":core:model")
include(":core:database")
include(":domain")
include(":data")
include(":core:network")
include(":core:sync")
// DEV-015 populates both of these with real code (the first UI task) - :app is the
// composition root (Hilt DI graph, Navigation, MainActivity); :feature:today is the
// first :feature:* module, depending only on :domain/:core:model/:core:designsystem/
// :core:common per ARCHITECTURE.md §5, never :core:database/:core:network/:data directly.
include(":app")
include(":feature:today")
// `/ARCHITECTURE.md` §5 already names `:feature:onboarding-auth` in the canonical module graph
// (DEV-015 review, architecture reviewer, MEDIUM: an earlier draft built Sign In directly inside
// `:app` instead, routing around an already-decided module placement rather than surfacing a
// genuine conflict - there wasn't one, the doc already answered this, so fixed to match it).
include(":feature:onboarding-auth")
// DEV-016: the second real :feature:* module (Goals list/detail/create-edit, Milestones) -
// same module-boundary rule as :feature:today/:feature:onboarding-auth.
include(":feature:goals")
// DEV-017: the third real :feature:* module (Plan/Calendar) - same module-boundary rule.
include(":feature:plan")
// DEV-018: the fourth real :feature:* module (Activities/Habits) - same module-boundary rule.
include(":feature:activities")
// DEV-019: the Calculation Engine (Goal Progress/Execution Rate/Activity stats) - pure Kotlin,
// depends only on :core:model/:core:common, per ARCHITECTURE.md §5's own module table.
include(":core:calculation")
