# ADR-001: Native Android Platform (Kotlin + Jetpack Compose)

**Status**: Accepted (supersedes an earlier React Native/Expo decision)

**Context**: The product's first client needed a concrete platform choice. An earlier phase chose React Native/Expo for code-sharing with a parked Next.js web app. The user later gave explicit direction to build native.

**Decision**: Native Android — Kotlin, Jetpack Compose, Material 3.

**Alternatives considered**: React Native/Expo (the prior decision); Flutter.

**Reason**: Explicit user direction; best fit for deep platform integration (exact alarms, Play Billing, notification channels, Home Screen Widget) that a cross-platform layer would fight.

**Consequences / Risks**: Loses the earlier RN code-sharing angle with the parked web app; iOS needs its own build later (kept open via Kotlin Multiplatform — see the domain-layer discipline in ADR-013).

**Reversibility**: Hard — a real rewrite to change later, so this was locked deliberately.

See `ARCHITECTURE.md` §1–3 for the full stack this enables.
