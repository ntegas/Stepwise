plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android.gradle.plugin)
}

// DEV-015: the composition root (Hilt DI graph, Navigation, MainActivity) - the first
// Android application module, same compileSdk/minSdk/JVM floor every other Android
// module in this project already uses.
android {
    namespace = "com.stepwise.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.stepwise.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0-dev"
    }

    // :core:network's own build.gradle.kts states the requirement explicitly ("`:app`
    // (DEV-015+) must declare these same three flavor names"): Gradle requires a
    // consuming app's flavors to match a library's dimension, or variant resolution
    // fails. Unlike :data, :app genuinely cares which one is active - it's what
    // determines which real Supabase backend an install talks to.
    flavorDimensions += "environment"
    productFlavors {
        create("dev") { dimension = "environment" }
        create("staging") { dimension = "environment" }
        create("prod") { dimension = "environment" }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":data"))
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:database"))
    implementation(project(":core:network"))
    implementation(project(":core:designsystem"))
    implementation(project(":feature:today"))
    implementation(project(":feature:onboarding-auth"))
    implementation(project(":feature:goals"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.datetime)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(testFixtures(project(":core:common")))
    testImplementation(testFixtures(project(":domain")))
}
