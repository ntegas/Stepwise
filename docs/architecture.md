# Architecture

## Access requirement

Stepwise must be usable from a phone and a computer with the same data, with no dependency on any single local machine. **The primary client is a native Android app (Kotlin + Jetpack Compose), released on Google Play** — full technical architecture in `/docs/android-architecture-specification.md` (canonical), Play Store specifics in `/docs/play-store-checklist.md`. The Next.js web app built in Phase 0 stays in the repo as a parked secondary client, not the active development focus.

## Stack

- **Backend: Supabase** — hosted Postgres + Auth (email/OAuth) + Realtime + Row Level Security, accessed via `supabase-kt` / Retrofit from the Android app.
  - RLS enforces per-user data isolation at the database level rather than in application code, so every client inherits the same security guarantee for free.
  - Chosen over Firebase specifically because the relational model fits the multi-goal/multi-metric calculation engine and the source-of-truth requirement below — Firestore's standard scaling pattern (denormalized counters) is the anti-pattern §62 forbids. Full comparison in the Android spec's Architecture Decision Record.
- **Android app: native Kotlin + Jetpack Compose + Room + WorkManager**, Hilt for DI at the app layer. Supersedes the earlier React Native/Expo decision — see `/docs/android-architecture-specification.md` for the full stack, layering, module structure, offline/sync design, and ADR.
- **Web app (parked): Next.js (TypeScript)**, deployed to Vercel. Kept as a secondary/interim access point; not the active build target.
- **Derived calculations** (execution rate, pace, forecast, period rollups) live in Postgres views/functions, mirrored by a pure-Kotlin Calculation Engine on-device for offline reads — not duplicated ad hoc per screen (see `/docs/data-model.md` and the Android spec §12/§15).
- **Internationalization & units**: no hardcoded strings or unit assumptions — Android string resources per locale, a per-user unit preference for display.
- **iOS**: not built now; sequencing is Android first, iOS later. Keeping `:core:model`/`:core:calculation`/`:domain` as plain Kotlin (no Android SDK dependency, no DI annotations) keeps a Kotlin Multiplatform path realistic later without committing to it now — see the Android spec §26.

## Binding technical requirements (Master Product Concept §59–63)

These came out of the concept revision and are architecture constraints, not later hardening:

- **Single source of truth (§62)**: Goal progress, Life Area aggregates, and every period rollup are Postgres views over `sessions` / `session_metric_values` / `progress_events` — never a stored, independently-updated column. A cached running total is exactly the kind of number §62 says can desync from reality; see `/docs/data-model.md#goal-progress-is-a-view-not-a-column` for the concrete design (this reverses the Phase 0 migration's `goals.current_value` trigger-updated column).
- **Idempotency (§60)**: every `sessions` row carries a client-generated idempotency key, so a duplicate submission from a flaky connection upserts instead of double-counting. This must exist before offline sync is built on top of it, not after.
- **Edit/delete recomputation (§61)**: falls out for free from the source-of-truth rule above — changing or deleting a session changes what the views compute, with no separate "recalculate" step to invoke or forget.
- **Unified recurrence (§30, §33)**: one `schedules` table/engine serves Task, Activity, and Habit — not three parallel recurrence implementations to keep behaviorally consistent.
- **Life Area math kept separate (§63)**: Attention / Execution / Goal Progress / Trend are computed and displayed as four figures, never averaged into one composite score.

## Why not a Claude Artifact for this

An Artifact was considered (zero hosting, instant multi-device URL) but rejected for the primary build: Stepwise's relational model (Goals/Activities/Sessions/Tasks/Habits with cascading cross-entity updates, per-type goal math, pace/forecast analytics) and the requirement to also serve a future native Android client are better served by a real Postgres schema with SQL views than by an Artifact's document-style database.

## Repository layout

```
/CLAUDE.md           — canonical concept + living product decisions (read every session)
/docs
  functional-analysis.md
  data-model.md
  architecture.md                       — this file (high-level overview)
  android-architecture-specification.md — canonical detailed Android architecture
  play-store-checklist.md
  android-stack.md, android-architecture.md — superseded (React Native era), history only
/supabase
  migrations/          — schema, RLS policies, triggers, views (Phase 0 version; superseded by Phase 2's Canonical Data Model)
/apps
  web/                 — Next.js app (parked): Today / Goals / Plan / Progress tabs + Quick Add
/packages
  domain/              — shared TypeScript types, used by the parked web app only (not the native Android app)
/android             — native Android Gradle project (Phase 2 onward), multi-module per android-architecture-specification.md §5
```

## Build order

1. **Phase 0 (done)**: concept analysis, data model, Supabase schema + RLS + cascade trigger + rollup views, Next.js web app skeleton, README — built against the original 48-section concept.
2. **Phase 1 (done)**: Android pre-development analysis — stack decision (React Native/Expo, since superseded), Play Store checklist, a first scope-of-work breakdown.
3. **Phase 1.5 (done)**: reconciled `/CLAUDE.md`, `/docs/data-model.md`, `/docs/functional-analysis.md`, and this file against the Master Product Concept (§1–77) — the Vision/Life Areas/Life Balance strategic layer, multi-goal/multi-metric Sessions, source-of-truth/idempotency requirements. `/docs/scope-of-work.md` reconciled in the same pass.
4. **Phase 1.6 (done)**: `/docs/android-architecture-specification.md` — platform decision changed to native Kotlin/Compose; full technical architecture (layers, modules, offline/sync, backend, security, testing) locked, with a consistency audit and an ADR.
5. **Phase 2**: Canonical Data Model → Calculation Engine → UX/Navigation → Offline & Sync → Google Play Release specs (in that order, per the Android spec §28), each building on the last, followed by implementation. No MVP cut — the full concept is the target, not a phased subset.

Each stage is verified end-to-end (migration applies cleanly, app builds and runs, manual smoke test of the flow) before moving on.
