# Architecture

## Access requirement

Stepwise must be usable from a phone and a computer with the same data, with no dependency on any single local machine. The user will build a native Android client separately later; this repo currently delivers the shared backend plus a web client for computer/phone-browser use.

## Stack

- **Backend: Supabase** — hosted Postgres + Auth (email/OAuth) + auto-generated REST/GraphQL API + Realtime + Row Level Security.
  - One backend reachable identically from a web client today and a native Android (Kotlin) client later, via plain REST or Supabase's official SDKs (JS now, Kotlin later).
  - RLS enforces per-user data isolation at the database level rather than in application code, so every future client inherits the same security guarantee for free.
  - Free tier is sufficient for an MVP; no separate server to host or operate.
- **Web app: Next.js (TypeScript)**, deployed to Vercel. Responsive layout so the same app works on a desktop browser and a phone browser.
- **Android**: intentionally out of scope for this repo (the user's own track). The schema and API are kept client-agnostic — plain Postgres tables/views behind Supabase's REST layer, nothing tied to a web-only SDK feature — so a future Kotlin client can consume the exact same backend.
- **Derived calculations** (execution rate, pace, forecast, period rollups) live in Postgres views/functions, not duplicated per-client in app code (see `/docs/data-model.md`). This guarantees web and Android always agree on the numbers, since both simply read the same view.

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
  web/                 — Next.js app: Today / Goals / Plan / Progress tabs + Quick Add
/packages
  domain/              — shared TypeScript types + pure calculation helpers, mirroring the SQL logic for optimistic UI updates
```

## Build order

1. **Phase 0 (this phase)**: concept analysis, data model, Supabase schema + RLS + cascade trigger + rollup views, Next.js app skeleton (auth + 4 empty tabs), README.
2. **Phase 1 (MVP core loop)**: Today → Goals → Progress — real Goal/Activity/Task CRUD, one-tap complete with Partial, live rollups on Progress.
3. **Phase 2**: Calendar/Plan, Habits, recurring activities.
4. **Phase 3**: Milestones, Projects, Pace/Forecast UI, Goal Impact ranking, Skills, behavioral analytics/recommendations, editable History.

Each phase is verified end-to-end (migration applies cleanly, app builds and runs, manual smoke test of the flow) before the next begins.
