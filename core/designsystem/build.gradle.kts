// No explicit `kotlin.android` plugin: AGP 9.0+ has built-in Kotlin support
// enabled by default, and applying `org.jetbrains.kotlin.android` on top of it
// fails ("plugin is already on the classpath with an unknown version") — found
// via the actual CI failure, not assumed from AGP 8.x-era habit.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

// Depends on nothing but Compose (ARCHITECTURE.md §5) — no :core:model, no
// domain types, no Room/network. This is what keeps the design system reusable
// from every feature module without pulling in application logic.
android {
    namespace = "com.stepwise.core.designsystem"

    // 36 per docs/play-store-checklist.md's Target API level requirement, which
    // became mandatory for new-app submission on 2026-08-31 — already in effect
    // as of this pin, not a future deadline. AGP 9.4.0 supports up to API 37.
    compileSdk = 36

    defaultConfig {
        // No canonical minSdk decision exists elsewhere yet; 26 (Android 8.0) is a
        // common modern baseline — it's what notification channels already require
        // (ARCHITECTURE.md §18) — and a plain manifest value, trivially revisable
        // later without a structural change. Flagged here, not silently assumed to
        // be final.
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
}
