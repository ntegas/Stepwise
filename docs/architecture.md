# Architecture

## Access requirement

Stepwise must be usable from a phone and a computer with the same data, with no dependency on any single local machine. **The primary client is a native Android app, released on Google Play** (see `/docs/android-stack.md`, `/docs/play-store-checklist.md`, `/docs/android-architecture.md`). The Next.js web app built in Phase 0 stays in the repo as a parked secondary client, not the active development focus.

## Stack

- **Backend: Supabase** — hosted Postgres + Auth (email/OAuth) + auto-generated REST/GraphQL API + Realtime + Row Level Security.
  - One backend reachable identically from the Android app and the parked web client, via plain REST or Supabase's official SDKs.
  - RLS enforces per-user data isolation at the database level rather than in application code, so every client inherits the same security guarantee for free.
  - Free tier is sufficient to start; no separate server to host or operate.
- **Android app: React Native + Expo (TypeScript)** — reuses `/packages/domain` and `@supabase/supabase-js` the same way the web app does; built via EAS Build for Play Store submission. Full rationale in `/docs/android-stack.md`.
- **Web app (parked): Next.js (TypeScript)**, deployed to Vercel. Kept as a secondary/interim access point; not the active build target.
- **Derived calculations** (execution rate, pace, forecast, period rollups) live in Postgres views/functions, not duplicated per-client in app code (see `/docs/data-model.md`). This guarantees every client agrees on the numbers, since all of them read the same view.
- **Internationalization & units**: no hardcoded strings or unit assumptions — `i18next` for translations, a per-user unit preference for display. See `/docs/android-architecture.md`.
- **iOS**: not built now, but React Native/Expo means the same codebase targets iOS later without a rewrite — the user's stated sequencing (§1: Android first, iOS on the same base) is a scheduling choice, not a platform-lock-in risk.

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
  architecture.md      — this file
/supabase
  migrations/          — schema, RLS policies, triggers, views
/apps
  web/                 — Next.js app (parked): Today / Goals / Plan / Progress tabs + Quick Add
  mobile/              — React Native/Expo Android app (Phase 2 onward)
/packages
  domain/              — shared TypeScript types + pure calculation helpers, consumed by both apps
```

## Build order

1. **Phase 0 (done)**: concept analysis, data model, Supabase schema + RLS + cascade trigger + rollup views, Next.js web app skeleton, README — built against the original 48-section concept.
2. **Phase 1 (done)**: Android pre-development analysis — stack decision, Play Store checklist, Android-specific architecture concerns, and a first scope-of-work breakdown.
3. **Phase 1.5 (done)**: reconciled `/CLAUDE.md`, `/docs/data-model.md`, `/docs/functional-analysis.md`, and this file against the Master Product Concept (§1–77), which supersedes the original 48-section text — most notably the Vision/Life Areas/Life Balance strategic layer, multi-goal/multi-metric Sessions, and the source-of-truth/idempotency requirements above. `/docs/scope-of-work.md` is reconciled to match in the same pass.
4. **Phase 2**: Android app build, including a new Supabase migration written against the reconciled data model (the Phase 0 migration is superseded, not extended). Per the user's explicit decision there is **no MVP cut** — the full concept is the target, not a phased subset. Sequencing within Phase 2 is defined in `/docs/scope-of-work.md`.

Each stage is verified end-to-end (migration applies cleanly, app builds and runs, manual smoke test of the flow) before moving on.
