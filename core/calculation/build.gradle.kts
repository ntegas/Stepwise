plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Pure Kotlin/JVM, like :core:sync/:domain - per ARCHITECTURE.md §5, this module never touches a
// Repository interface or any data-access concern. Every function here takes already-fetched
// domain values (Session, SessionMetricValue, Milestone, ...) and "today"/the period boundary as
// plain parameters, and returns a plain result - no Clock, no coroutines, no Android dependency
// of any kind, so it stays the most directly, locally unit-testable module in the whole app
// (CALCULATION_ENGINE.md's own "heavily unit-tested" requirement, ARCHITECTURE.md §24).
kotlin {
    jvmToolchain(21)
}

dependencies {
    api(project(":core:model"))
    api(project(":core:common"))

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test.junit5)
}

tasks.test {
    useJUnitPlatform()
}
