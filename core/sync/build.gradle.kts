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

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.kotlinx.coroutines.test)
    // FakeSyncTransport needs to read the `id` field a real payload carries, to keep its
    // bookkeeping consistent across upsert/compareAndSwapUpdate (real PostgrestSyncClient.upsert
    // has no separate `id` parameter either — the id lives inside the payload).
    testImplementation(libs.kotlinx.serialization.json)
}

tasks.test {
    useJUnitPlatform()
}
