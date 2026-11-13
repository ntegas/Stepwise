plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Pure Kotlin/JVM (ARCHITECTURE.md §5: "domain and data-model modules stay
// framework-agnostic to keep a future Kotlin Multiplatform path to iOS open").
// `api`, not `implementation`, for :core:model/:core:common/kotlinx-coroutines:
// every Repository interface here exposes domain types, AppResult/AppError, and
// Flow directly in its public signature, so a consumer (:data implementing the
// interface, a :feature module calling it) needs all three on its own
// compile classpath too.
kotlin {
    jvmToolchain(21)
}

dependencies {
    api(project(":core:model"))
    api(project(":core:common"))
    api(libs.kotlinx.coroutines.core)
}

// No test dependencies yet: every type here is a bare Repository interface with no
// default-method logic to unit-test (ROADMAP.md's "no speculative infrastructure").
// Fake*Repository test doubles (ARCHITECTURE.md §24, "Repository tests against fakes")
// are deferred to whichever DEV task first needs one to test a Use Case against — most
// likely DEV-009's Canonical Execution Engine — not built ahead of that real need.
