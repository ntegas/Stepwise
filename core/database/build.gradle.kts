plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

// Standard Android module (not Kotlin Multiplatform) — confirmed with the product
// owner at DEV-005: Room/androidx.sqlite artifacts are Google-Maven-only regardless
// of Android vs JVM target, so a KMP module here would not have bought any real
// local-verification benefit in this sandbox (dl.google.com is blocked entirely,
// not just for AGP), only added Gradle-module complexity. Room 2.x (androidx.room),
// not the KMP-focused 3.0 rewrite (androidx.room3) — see libs.versions.toml.
android {
    namespace = "com.stepwise.core.database"
    compileSdk = 37

    defaultConfig {
        // Room 2.8+ requires API 23 minimum (checked live) — well under this
        // project's existing minSdk 26 (core/designsystem/build.gradle.kts), so
        // no change needed there.
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

// JDK 21, matching :core:model (the project's other genuinely-run-locally-and-in-CI
// module) and the single JDK the CI workflow actually installs ("Set up JDK 21") —
// not :core:designsystem's JDK 17 (that module never runs a Test task with real
// runtime dependencies, so its toolchain choice was never exercised the way this
// one is). A real CI failure (run 34987253276) showed why the two can't be mixed
// carelessly here: with jvmToolchain(17), both testDebugUnitTest classes failed
// with java.lang.UnsupportedClassVersionError at class-load time, before any test
// method ran — some class on the Robolectric/androidx-test/JUnit4 runtime classpath
// needs a newer JVM than a separately-provisioned JDK 17 toolchain supplied. Using
// the same JDK 21 the whole rest of the build already runs under removes the
// mismatch entirely rather than chasing which specific dependency needed it.
kotlin {
    jvmToolchain(21)
}

// Room schema export via a plain KSP arg rather than the separate `androidx.room`
// Gradle plugin (which exists mainly for its `room {}` DSL sugar over this exact
// setting) — one fewer new plugin to register/apply-false at root for what a single
// build-arg line already does.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":core:model"))

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.datetime)

    // Room DAO tests need a real Android runtime (Room generates actual
    // SQLite-backed implementations) — Robolectric on plain JUnit 4, a deliberate
    // exception to this project's usual JUnit 5, since Robolectric has no official
    // JUnit 5 support yet (checked live; see libs.versions.toml's junit4 comment).
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.room.testing)
    testImplementation(libs.kotlinx.coroutines.test)
}

// Robolectric's sandbox classloader reflectively opens JDK internals (java.lang,
// java.util, ...) that the JPMS module system blocks by default from JDK 17+ —
// without these, every Robolectric-backed test fails at class-load time with
// java.lang.IllegalAccessException from AndroidInterceptors (a real CI failure,
// run 34987949082, not a hypothetical). The first attempt at this set (run
// 34988427735) was still missing jdk.internal.access and java.desktop/
// java.awt.font — Robolectric's own AndroidInterceptors reflects into both,
// and the identical IllegalAccessException persisted until both were added.
// This full list matches Robolectric's own documented JDK 17+ configuration,
// not something specific to this project.
tasks.withType<Test>().configureEach {
    jvmArgs(
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "--add-opens=java.base/java.util=ALL-UNNAMED",
        "--add-opens=java.base/java.util.concurrent=ALL-UNNAMED",
        "--add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED",
        "--add-opens=java.base/java.io=ALL-UNNAMED",
        "--add-opens=java.base/java.net=ALL-UNNAMED",
        "--add-opens=java.base/java.security=ALL-UNNAMED",
        "--add-opens=java.base/java.text=ALL-UNNAMED",
        "--add-opens=java.base/java.lang.invoke=ALL-UNNAMED",
        "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
        "--add-opens=java.desktop/java.awt.font=ALL-UNNAMED",
    )
}
