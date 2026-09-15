import io.gitlab.arturbosch.detekt.extensions.DetektExtension

// Root build script. Static analysis (detekt) and formatting (ktlint) are wired
// here, once, for every subproject (DEVELOPMENT_PROTOCOL.md rule 42: "one
// formatter, lint config, Kotlin static analysis... run locally and in CI") —
// a new module picks both up automatically without repeating config. A full
// build-logic convention-plugin setup is still deferred: with two lightweight,
// purely-additive plugins this `subprojects` block is enough; introduce
// build-logic when a module needs a genuinely different (e.g. Android-specific)
// convention, not before.
// com.android.library is deliberately NOT declared here (even as `apply false`):
// the root project is always configured, on every invocation, regardless of which
// module's task is requested or whether `--configure-on-demand` is used — so an
// unresolvable plugin reference at root breaks every module's build, including
// pure-JVM ones with no Android dependency at all. Verified empirically in this
// sandbox: declaring `com.android.library apply false` here broke `:core:common`'s
// previously-working build, because `dl.google.com` (where AGP is hosted) is
// blocked. :core:designsystem declares and versions AGP itself instead.
//
// kotlin-android and kotlin-compose, by contrast, ARE declared here (apply false)
// even though only :core:designsystem applies them for real. This is DEV-003's
// actual root cause after 9 CI iterations (see DEVELOPMENT_LOG.md/PROJECT_STATE.md
// for the full account — most of that chain chased AGP 9's built-in Kotlin support
// as the cause and was wrong): org.jetbrains.kotlin.jvm and org.jetbrains.kotlin.
// android are different plugin IDs backed by the same underlying Kotlin Gradle
// Plugin artifact. With only kotlin-jvm apply-false'd here (for :core:common) and
// kotlin-android resolved for the first time inside :core:designsystem's own
// plugins{} block with a pinned version, Gradle's plugin resolution found the
// implementation classes already on the build's classpath without matching marker
// metadata and failed with "already on the classpath with an unknown version,
// so compatibility cannot be checked" — reproduced identically under both AGP
// 9.4.0 and 8.13.2, proving it was never AGP-version-specific. Neither plugin ID
// is hosted on dl.google.com (both resolve from the Kotlin repo / Maven Central),
// so declaring them here carries none of the risk that keeps android.library out.
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.ktlint) apply false
}

subprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    // No custom rule-set file yet — detekt's own default ruleset is sufficient
    // for now; a bespoke config.yml is added only once a real deviation from the
    // defaults is actually needed (same "don't build it before it's justified"
    // principle as DEV-001), not pre-authored empty.
    extensions.configure<DetektExtension> {
        buildUponDefaultConfig = true
        parallel = true
    }
}
