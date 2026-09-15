import io.gitlab.arturbosch.detekt.extensions.DetektExtension

// Root build script. Static analysis (detekt) and formatting (ktlint) are wired
// here, once, for every subproject (DEVELOPMENT_PROTOCOL.md rule 42: "one
// formatter, lint config, Kotlin static analysis... run locally and in CI") —
// a new module picks both up automatically without repeating config. A full
// build-logic convention-plugin setup is still deferred: with two lightweight,
// purely-additive plugins this `subprojects` block is enough; introduce
// build-logic when a module needs a genuinely different (e.g. Android-specific)
// convention, not before.
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
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
