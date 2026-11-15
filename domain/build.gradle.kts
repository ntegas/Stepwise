plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-test-fixtures`
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

    // DEV-009: the first Use Cases exist now, and with them the first real need for
    // Fake*Repository test doubles (ARCHITECTURE.md §24, "Repository tests against fakes") —
    // not built ahead of that need, per this file's own previous comment.
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(testFixtures(project(":core:common")))

    // DEV-015: every Fake*Repository moved here from src/test (not duplicated) once
    // :feature:today's own ViewModel test became the first consumer outside this module -
    // the java-test-fixtures plugin wires :domain's own test source set to these automatically,
    // so no existing test file's imports needed to change.
    testFixturesImplementation(libs.kotlinx.coroutines.core)
}

tasks.test {
    useJUnitPlatform()
}
