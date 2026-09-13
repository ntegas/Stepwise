# Stepwise

Turn your goals into action. See `/CLAUDE.md` for the full product concept and `/docs` for the functional analysis, data model, and architecture behind this repo.

## Status

Phase 0: concept analysis, data model, Supabase schema, and a Next.js app skeleton (auth + empty Today/Goals/Plan/Progress tabs). No feature logic yet — see `/docs/architecture.md` for the build order.

## Repository layout

```
/CLAUDE.md           — canonical concept, read every session
/docs                — functional analysis, data model, architecture
/supabase/migrations — Postgres schema, RLS, cascade trigger, rollup views
/apps/web            — Next.js app
/packages/domain     — shared TypeScript types
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
