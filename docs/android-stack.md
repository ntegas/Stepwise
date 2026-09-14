# Android Stack Decision

## Choice: React Native + Expo, TypeScript

Stepwise's Android app is built with React Native (managed Expo workflow), not native Kotlin.

## Why

- **Reuses `/packages/domain`** — the TypeScript types and calculation helpers written in Phase 0 are consumed as-is by the app. A native Kotlin app would need a second, parallel copy of every entity type and formula, which is exactly the kind of duplicated logic the architecture (see `/docs/data-model.md`) is designed to avoid.
- **Reuses `@supabase/supabase-js`** — the same client library, auth flow (magic link / OAuth), and query patterns already used in `/apps/web`. Both clients read the same Postgres rollup views (`goal_execution_rate`, `goal_pace`, `goal_forecast`, `activity_stats_*`) as the single source of truth for derived numbers — neither client re-implements the math.
- **EAS Build** (Expo's cloud build service) produces a signed Android App Bundle (.aab) ready for Play Store submission without a local Android Studio/SDK/emulator install. Native Kotlin development requires Android Studio and an emulator or physical device to iterate — tooling this environment doesn't have. Expo Go gives a live preview on a real phone during development instead.
- Native Kotlin + Jetpack Compose would give the tightest platform integration (best performance, deepest OS feature access) but at a real cost here: no local build/test loop, and a second implementation of every calculation to keep in sync with the SQL views. Given Stepwise's UI (lists, forms, a timer, charts) doesn't need anything Kotlin-only, that cost isn't justified.

## What this means for later phases

- Package name / application ID (`app.json` → `android.package`) is chosen once and is irreversible after the first Play Store upload — tracked in `/docs/play-store-checklist.md`.
- Notifications go through `expo-notifications`, which relies on Firebase Cloud Messaging on Android — see `/docs/android-architecture.md`.
- Offline/local storage uses Expo-compatible libraries (e.g. `expo-sqlite` or `@react-native-async-storage/async-storage`) rather than anything native-only.
- i18n uses `i18next` + `react-i18next` (works identically in Expo and in the existing Next.js web app, so translation files could eventually be shared between both clients).
