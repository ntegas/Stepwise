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

1. **Phase 0 (done)**: concept analysis, data model, Supabase schema + RLS + cascade trigger + rollup views, Next.js web app skeleton, README.
2. **Phase 1 (done)**: Android pre-development analysis — stack decision, Play Store checklist, Android-specific architecture concerns, and the full scope-of-work breakdown (`/docs/scope-of-work.md`).
3. **Phase 2**: Android app build. Per the user's explicit decision there is **no MVP cut** — the target is the full concept from `/CLAUDE.md`, built out completely against `/docs/scope-of-work.md`, not a phased subset. Sequencing within Phase 2 (what gets built in what order) is defined in `/docs/scope-of-work.md` itself, since the user wanted that laid out for review as one document rather than pre-chunked here.

Each stage is verified end-to-end (migration applies cleanly, app builds and runs, manual smoke test of the flow) before moving on.
