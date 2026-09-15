plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Pure Kotlin/JVM, no Android dependency (ARCHITECTURE.md §5/§6 — "no Room, no network types")
// and deliberately no dependency on :core:common either: entities are constructed from typed
// IDs wrapping a plain String an outer layer already generated, so this module stays a leaf
// with zero project dependencies, per ROADMAP.md's Environment & Verification Debt framework
// ("division of labor: this sandbox does implementation... for :core:model, :core:calculation,
// :domain — deliberately Android-SDK-free") — fully buildable and testable in this sandbox,
// unlike :core:designsystem.
kotlin {
    jvmToolchain(21)
}

dependencies {
    api(libs.kotlinx.datetime)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test.junit5)
}

tasks.test {
    useJUnitPlatform()
}
