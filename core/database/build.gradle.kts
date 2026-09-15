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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

kotlin {
    jvmToolchain(17)
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
