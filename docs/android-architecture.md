# Android-Specific Architecture

Concerns the Next.js web skeleton didn't need, because a phone app has different failure modes and platform requirements than a browser tab.

## Offline-first / sync

A habit/goal tracker has to let the daily ✓ work with no signal (gym, commute, flight) and reconcile once back online — a live-fetch-only model (fine for the web skeleton) isn't good enough here.

- Writes (session completions, quick-added tasks, etc.) go into a local queue first (e.g. `expo-sqlite`), applied to the UI optimistically, then flushed to Supabase when connectivity returns.
- Reads are cached locally so Today/Goals/Progress render immediately on cold start instead of blocking on a network round trip.
- Conflict handling: since `sessions`/`progress_events` writes are additive and keyed by session id (see `/docs/data-model.md`), a queued write replaying late is safe to apply as-is; edits to historical entries (History screen, concept §29) need a last-write-wins rule with the server timestamp as the tiebreaker.

## Push notifications

Task/Habit reminders (concept §14, §22) need real OS-level notifications, not just an in-app banner.

- `expo-notifications` on Android is backed by **Firebase Cloud Messaging** — needs a Firebase project linked to the Expo app before reminders can be built.
- Scheduling: local notifications (via `expo-notifications`' scheduled triggers) cover simple reminders without needing a server round trip; a server-triggered push (e.g. a Supabase Edge Function on a cron) is only needed for cross-device-consistent reminders later, not required for a single-device MVP of this feature.

## Internationalization

- `i18next` + `react-i18next`, same library the web app can use — translation files structured per language (`en.json`, `ru.json`, …) from the first screen, no hardcoded strings anywhere in components (per the user's explicit decision).
- Locale-sensitive formatting (dates, numbers) goes through the same layer rather than manual string building, so adding a language doesn't mean auditing every screen for missed hardcoded text.

## Units of measurement

- A per-user setting (metric/imperial, currency), stored alongside the user's profile in Supabase — not a build-time constant.
- Every screen that displays a `goals`/`sessions` amount converts from the stored canonical unit to the user's preferred display unit at render time; the stored value itself stays in one canonical unit (e.g. minutes, kilometers, base currency) so analytics/rollups never have to guess which unit a historical row was entered in.

## App identity & platform basics

- App icon, splash screen, adaptive icon (Android's foreground/background icon layers) — asset requirements come from Expo's config (`app.json`), separate from the Play Store *listing* assets tracked in `/docs/play-store-checklist.md`.
- Minimum supported Android version: follow Expo's currently supported minimum (checked at build time via the Expo SDK version in use), rather than picking an arbitrary floor now.
