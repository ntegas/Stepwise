plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

// Android module, not pure Kotlin/JVM: it depends on :core:database, which applies
// com.android.library itself — a plain kotlin.jvm module cannot consume an Android
// library module's AAR output, so this module must be Android too (same reasoning as
// :core:database's own build.gradle.kts). Same JVM toolchain and Robolectric
// --add-opens set as :core:database from the start, per ANTI_ERROR_STANDARD.md §12
// (DEV-005's retrospective) — not rediscovered fix-by-fix a second time.
android {
    namespace = "com.stepwise.data"
    compileSdk = 37

    defaultConfig {
        minSdk = 26

        // Real bug found and fixed during DEV-007 review (architecture reviewer, HIGH): this
        // module now depends on :core:network, which declares an "environment" flavor dimension
        // (dev/staging/prod — core/network/build.gradle.kts). AGP's variant-aware dependency
        // resolution requires a consuming module to either declare the same dimension or supply
        // a fallback, or variant matching against :core:network fails outright. :data itself has
        // no reason to care which environment is active, so it always falls back to "dev" rather
        // than declaring three flavors of its own for a distinction it doesn't use.
        missingDimensionStrategy("environment", "dev")
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

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":core:database"))
    implementation(project(":core:network"))
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(libs.kotlinx.coroutines.core)

    // Only for io.github.jan.supabase.exceptions.RestException (NetworkErrorMapping.kt) — this
    // module otherwise reaches supabase-kt exclusively through :core:network's own wrapper types,
    // per ARCHITECTURE.md §5's ":core:network owns DTOs + mappers to/from :core:model."
    implementation(platform(libs.supabase.kt.bom))
    implementation(libs.supabase.kt)

    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.room.testing)
    testImplementation(libs.kotlinx.coroutines.test)
}

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
