plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Pure Kotlin/JVM, like :domain — SyncEngine's actual decision logic (push vs. CAS, conflict-copy
// construction) takes OutboxDao/PostgrestSyncClient as plain constructor parameters, with no
// direct Android/Room/Ktor dependency of its own, so it's genuinely unit-testable in this sandbox
// without Robolectric (unlike :core:database/:core:network themselves, which stay CI-only). Any
// future WorkManager Worker wrapping this engine belongs in :data/:app once one exists — this
// module deliberately stays Android-free so its core logic keeps being locally verifiable.
kotlin {
    jvmToolchain(21)
}

dependencies {
    api(project(":core:model"))
    api(project(":core:common"))
    implementation(libs.kotlinx.coroutines.core)
    // DEV-009: SyncEngine.payloadsEquivalent needs real JSON comparison (not just test fixtures
    // reading an id back out of a payload, the only reason this was test-only before) to tell a
    // harmless retry apart from a genuine cross-device conflict at the same deterministic id.
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.test {
    useJUnitPlatform()
}
