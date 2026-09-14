# Stepwise

Turn your goals into action. Native Android app (Kotlin + Jetpack Compose) backed by Supabase. See `/CLAUDE.md` for the full canonical documentation index — start there.

## Status

See `/PROJECT_STATE.md` for the live, detailed phase status. In short: product concept, data model, and technical architecture are canonical and locked; the Android Gradle project and backend schema for Phase 2 haven't been built yet. The Next.js app below is a parked Phase 0 artifact, not the active client.

## Repository layout

```
/CLAUDE.md            — session entry point (auto-loaded), points at everything below
/PRODUCT_CANON.md      — product concept
/ARCHITECTURE.md        — technical architecture
/DATA_MODEL.md          — entities and relationships
/DEVELOPMENT_PROTOCOL.md, /ANTI_ERROR_STANDARD.md, /PROJECT_STATE.md
/CALCULATION_ENGINE.md, /DESIGN_SYSTEM.md — scaffolded
/ADR/                  — architecture decision records
/docs/security         — security architecture, threat model, dependency policy, etc.
/docs                  — supporting breakdowns (functional analysis, scope of work, Play Store checklist)
/supabase/migrations   — Postgres schema (Phase 0 version; superseded by Phase 2's Canonical Data Model)
/apps/web              — Next.js app (parked, Phase 0)
/packages/domain       — shared TypeScript types (web app only)
```

## Running the web app locally

```bash
npm install
cp apps/web/.env.example apps/web/.env.local   # fill in your Supabase project URL + anon key
npm run dev
```

The app runs at http://localhost:3000 and redirects to `/today`.

## Setting up Supabase

1. Create a project at https://supabase.com.
2. Apply the schema: either paste `/supabase/migrations/0001_init.sql` into the SQL editor, or, with the Supabase CLI installed and linked to your project, run:
   ```bash
   supabase db push
   ```
3. Copy your project's URL and anon key into `apps/web/.env.local` (see `.env.example`).

## Access from phone and computer

There is no local server to keep running: Supabase hosts the database and auth, and the web app is meant to be deployed (e.g. to Vercel) so it's reachable from any browser — phone or computer — with the same account and data. A native Android client can be built later against the same Supabase backend.
