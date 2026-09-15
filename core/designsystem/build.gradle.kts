// AGP is back on 9.4.0 (gradle/libs.versions.toml — see that file for the full
// DEV-003 iteration history). Compose BOM 2026.08.00's own dependencies
// (androidx.compose.animation:animation-core-android:1.12.0 and others)
// require AGP >=9.1.0 and compileSdk >=37 — AGP 8.13.2 (tried at iterations
// 9-11) can't satisfy that at all, independent of anything else. kotlin-android
// and kotlin-compose are still applied explicitly rather than relying on
// AGP 9's built-in Kotlin support — see gradle.properties' android.
// builtInKotlin=false comment for why.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Depends on nothing but Compose (ARCHITECTURE.md §5) — no :core:model, no
// domain types, no Room/network. This is what keeps the design system reusable
// from every feature module without pulling in application logic.
android {
    namespace = "com.stepwise.core.designsystem"

    // 37, not the docs/play-store-checklist.md-mandated minimum of 36: Compose
    // BOM 2026.08.00's own dependencies (androidx.compose.animation:animation-
    // core-android:1.12.0 and others, discovered via a real CI AAR-metadata
    // failure at DEV-003 iteration 11) require compiling against API 37 or
    // later. AGP 9.4.0 supports up to API 37, so this is within range.
    compileSdk = 37

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
