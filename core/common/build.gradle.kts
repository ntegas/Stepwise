plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-test-fixtures`
}

// Pure Kotlin/JVM, no Android dependency (ARCHITECTURE.md §27) — keeps a future
// Kotlin Multiplatform iOS path realistic without a rewrite of this module.
kotlin {
    jvmToolchain(21)
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.datetime)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.kotlinx.coroutines.test)

    testFixturesImplementation(libs.kotlinx.coroutines.core)
    testFixturesImplementation(libs.kotlinx.coroutines.test)
    testFixturesImplementation(libs.kotlinx.datetime)
}

tasks.test {
    useJUnitPlatform()
}
