import io.gitlab.arturbosch.detekt.extensions.DetektExtension

// Root build script. Static analysis (detekt) and formatting (ktlint) are wired
// here, once, for every subproject (DEVELOPMENT_PROTOCOL.md rule 42: "one
// formatter, lint config, Kotlin static analysis... run locally and in CI") —
// a new module picks both up automatically without repeating config. A full
// build-logic convention-plugin setup is still deferred: with two lightweight,
// purely-additive plugins this `subprojects` block is enough; introduce
// build-logic when a module needs a genuinely different (e.g. Android-specific)
// convention, not before.
// DEV-003's CI iterations 7-10 (see DEVELOPMENT_LOG.md/PROJECT_STATE.md for the
// full account) worked out why every one of android.library/kotlin.jvm/kotlin.
// android/kotlin.compose has to be declared here, apply false, together:
//   - iterations 1-9 chased AGP 9's built-in Kotlin support as the cause of
//     "org.jetbrains.kotlin.android ... already on the classpath with an unknown
//     version" — wrong: the identical error reproduced under plain AGP 8.13.2.
//   - iteration 10: the real cause was that kotlin-android was resolved for the
//     first time inside :core:designsystem's own plugins{} block, while only
//     kotlin-jvm was apply-false'd here. kotlin.jvm and kotlin.android are
//     different plugin IDs backed by the same underlying Kotlin Gradle Plugin
//     artifact, and Gradle's plugin resolution collided. Declaring kotlin-android
//     (and kotlin-compose, same reasoning) apply-false here fixed that error —
//     but surfaced a NEW one: NoClassDefFoundError on com.android.build.gradle.
//     BaseExtension, because the Kotlin Android Gradle plugin's own classes
//     statically reference AGP classes, and AGP itself still wasn't resolved
//     anywhere at root.
//   - iteration 11 (this one): declare android.library apply-false here too —
//     the standard Android Studio multi-module template convention ("every
//     plugin used anywhere in the build gets one apply-false declaration at
//     root") — which every prior iteration deviated from specifically to keep
//     :core:common locally buildable in this sandbox, where dl.google.com
//     (AGP's host) is blocked. That deviation is what caused this entire class
//     of problem: real GitHub Actions CI has full internet access and resolves
//     AGP fine, so this is the only combination consistent with both the actual
//     CI environment and Gradle's own plugin-resolution model.
// Accepted trade-off, stated plainly per this project's own verification
// principle for Android-dependent code (PROJECT_STATE.md/ROADMAP.md — "verify
// via real CI, never assume"): `:core:common:build` can no longer be verified
// locally in THIS sandbox specifically (the root project is always configured,
// so this apply-false triggers an AGP resolution attempt against dl.google.com
// even for a task that only targets :core:common) — CI is the source of truth
// for this build from here on, which was already true for anything touching
// :core:designsystem.
plugins {
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.ktlint) apply false
}

subprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    // config/detekt/detekt.yml (added at DEV-003 iteration 13, once real Compose
    // code hit real detekt findings — not pre-authored empty) only overrides
    // FunctionNaming/LongParameterList/MagicNumber for Compose's conventions;
    // buildUponDefaultConfig keeps every other rule at detekt's default.
    extensions.configure<DetektExtension> {
        buildUponDefaultConfig = true
        parallel = true
        config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    }
}
