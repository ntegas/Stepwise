import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

// Environment isolation (ARCHITECTURE.md §11, §10; SECURITY_ARCHITECTURE.md's "environment
// isolation" — "a dev client never accidentally operates with production administrative
// credentials"). Three flavors, each with its own SUPABASE_URL/SUPABASE_ANON_KEY — the anon key
// is Supabase's intentionally-public, client-embeddable project identifier (never the service
// role key; see SECURITY_ARCHITECTURE.md's "service role protection" — that key never appears in
// any client artifact, anywhere, full stop). RLS is the real authorization boundary regardless
// of which anon key a build embeds (SECURITY_ARCHITECTURE.md's own point: "the backend never
// trusts a client-supplied claim"), so embedding the anon key is not itself a secret-handling
// violation — it is the credential Supabase's own client SDK model expects a shipped app to
// carry.
//
// Values are read, in order: a CI environment variable (STEPWISE_<FLAVOR>_SUPABASE_URL / _KEY —
// how GitHub Actions supplies them from repository secrets) first, then local.properties (a
// developer's own machine; already .gitignored project-wide, confirmed in .gitignore). Neither
// source is read into this build script's own logged output — only passed straight into
// buildConfigField. Missing in both places resolves to an empty string rather than failing the
// build: no module yet calls this module's client factory (DEV-008 is the first real caller), so
// failing the build over an unset credential before anything consumes it would be a speculative
// failure mode, not a real one — revisit once DEV-008 adds a genuine runtime dependency on these
// being non-empty.
fun supabaseConfigValue(
    flavor: String,
    key: String,
): String {
    val envVar = "STEPWISE_${flavor.uppercase()}_$key"
    System.getenv(envVar)?.let { return it }

    val localProperties = Properties()
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { localProperties.load(it) }
    }
    return localProperties.getProperty("$flavor.$key", "")
}

android {
    namespace = "com.stepwise.core.network"
    compileSdk = 37

    defaultConfig {
        // supabase-kt's documented minimum (checked live against its README) — already this
        // project's existing floor (core/database/build.gradle.kts), so no change needed there.
        minSdk = 26
    }

    buildFeatures {
        buildConfig = true
    }

    flavorDimensions += "environment"
    productFlavors {
        // Matches ARCHITECTURE.md §21's "dev/staging/production are separated" requirement.
        // :app (DEV-015+) must declare these same three flavor names when it's created — Gradle
        // requires a consuming app's flavors to match a library's, per the standard Android
        // multi-module flavor model.
        create("dev") {
            dimension = "environment"
            buildConfigField("String", "SUPABASE_URL", "\"${supabaseConfigValue("dev", "SUPABASE_URL")}\"")
            buildConfigField("String", "SUPABASE_ANON_KEY", "\"${supabaseConfigValue("dev", "SUPABASE_ANON_KEY")}\"")
        }
        create("staging") {
            dimension = "environment"
            buildConfigField("String", "SUPABASE_URL", "\"${supabaseConfigValue("staging", "SUPABASE_URL")}\"")
            buildConfigField(
                "String",
                "SUPABASE_ANON_KEY",
                "\"${supabaseConfigValue("staging", "SUPABASE_ANON_KEY")}\"",
            )
        }
        create("prod") {
            dimension = "environment"
            buildConfigField("String", "SUPABASE_URL", "\"${supabaseConfigValue("prod", "SUPABASE_URL")}\"")
            buildConfigField(
                "String",
                "SUPABASE_ANON_KEY",
                "\"${supabaseConfigValue("prod", "SUPABASE_ANON_KEY")}\"",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":core:model"))

    implementation(platform(libs.supabase.kt.bom))
    implementation(libs.supabase.auth.kt)
    implementation(libs.supabase.postgrest.kt)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)

    implementation(libs.androidx.security.crypto)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
}
