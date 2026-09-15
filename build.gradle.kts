// Root build script. Deliberately near-empty: with only one module (:core:common)
// existing so far, a shared build-logic convention-plugin setup would itself be
// speculative infrastructure (nothing to share it with yet). Introduce build-logic
// once a second JVM/Android module needs the same conventions (tracked as a
// DEV-001 follow-up note in PROJECT_STATE.md, not deferred silently).
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
}
