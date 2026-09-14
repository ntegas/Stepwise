# ADR-013: Dependency Injection — Hilt at the Android Layer, Framework-Agnostic Core

**Status**: Accepted

**Context**: Needed a DI approach robust enough for a multi-layer app (ViewModel/UseCase/Repository/Room/Sync), while keeping a realistic path to Kotlin Multiplatform for iOS later, per the user's stated Android-first-then-iOS sequencing.

**Decision**: Hilt at the Android/app/feature layer. `:core:model`, `:core:calculation`, and `:domain` use plain constructor injection with no DI annotations, so they can be wired by Hilt on Android today and by Koin or manual wiring in a Kotlin Multiplatform shared module later, without change.

**Alternatives considered**: Koin everywhere (simpler setup, more KMP-portable, but less compile-time safety and weaker Compose/Navigation/WorkManager integration); Hilt everywhere (would tie the shared core to an Android/Dagger-only framework, foreclosing the KMP path).

**Reason**: Compile-time safety and best Compose/Navigation/WorkManager integration where it matters most (the Android app layer); keeping the shared core DI-framework-free preserves optionality without committing to KMP now.

**Consequences / Risks**: Two DI approaches to understand; contributors must respect the module boundary.

**Reversibility**: The Android-layer choice is reversible; the domain-layer discipline is what protects future flexibility.

See `ARCHITECTURE.md` §3, §5, §27.
