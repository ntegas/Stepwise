-- Stepwise Phase 2 schema, continued (DEV-008, /ROADMAP.md — Sync Engine).
--
-- Closes a confirmed, named dependency from DEV-007's own review (adversarial reviewer, HIGH,
-- demonstrated exploitable): 0002_canonical_schema.sql's `version`/`updated_at` columns were
-- plain client-settable values with no server-side enforcement — a stale or forged UPDATE could
-- resurrect a tombstone and set an arbitrary `version`/`updated_at` unconditionally. RLS was
-- never designed to catch this (it only knows `auth.uid() = user_id`); this migration is what
-- ARCHITECTURE.md §9.1 actually requires: "every syncable row has a server-assigned `updated_at`
-- ... and a monotonically incremented `version` integer."
--
-- Two pieces:
--   1. `enforce_sync_metadata()` — a BEFORE INSERT OR UPDATE trigger, applied to every syncable
--      table, that overwrites whatever the client sent for `updated_at`/`version` with a
--      server-computed value. This is what makes the version-checked conflict-detection pattern
--      in ARCHITECTURE.md §9.1 actually trustworthy: a client's PATCH with a stale `version`
--      filter now genuinely can't succeed by also forging a higher `version` in the body, because
--      the body's `version`/`updated_at` are discarded before the row is written either way.
--   2. `conflict_copies` — the non-destructive "conflict copy" record ADR-006/ARCHITECTURE.md
--      §9.1 describes for progress-affecting entities (sessions, session_metric_values,
--      recurrence_occurrences): when a client's compare-and-swap write loses (its `version`
--      filter matches zero rows because the server has moved on), its losing payload is
--      preserved here rather than discarded, instead of a server-side RPC resolving the
--      conflict atomically — see core/sync's own design note for why the CAS itself is done as
--      a plain Postgrest-filtered PATCH rather than a custom stored procedure (a generic
--      cross-table RPC taking a dynamic table name would need to validate that name against an
--      allowlist to avoid SQL injection, and would be meaningfully harder to audit than standard
--      PostgREST filter semantics for a security/data-integrity-critical path with no
--      independent reviewer available to check it, per AI_CODE_SECURITY.md).

-- ---------------------------------------------------------------------------
-- 1. Server-side version/updated_at enforcement
-- ---------------------------------------------------------------------------

create or replace function enforce_sync_metadata()
returns trigger
language plpgsql
as $$
begin
  if tg_op = 'INSERT' then
    new.updated_at := now();
    new.version := 1;
  elsif tg_op = 'UPDATE' then
    new.updated_at := now();
    new.version := old.version + 1;
  end if;
  return new;
end;
$$;

do $$
declare
  t text;
begin
  foreach t in array array[
    'life_areas', 'visions', 'goals', 'projects', 'milestones', 'activities', 'habits', 'tasks',
    'goal_activity_links', 'schedules', 'sessions', 'session_metric_values', 'recurrence_occurrences'
  ]
  loop
    execute format(
      'drop trigger if exists %I_enforce_sync_metadata on %I', t, t
    );
    execute format(
      'create trigger %I_enforce_sync_metadata before insert or update on %I '
      || 'for each row execute function enforce_sync_metadata()',
      t, t
    );
  end loop;
end $$;

-- ---------------------------------------------------------------------------
-- 2. Conflict copies — append-only, owner-scoped (direct user_id, same pattern as every other
-- top-level table). No UPDATE/DELETE policy: a conflict copy is an immutable historical record;
-- Postgres's own RLS default (enabled + no matching policy = deny) is correct here, not a gap.
-- ---------------------------------------------------------------------------

create table conflict_copies (
  id uuid primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  table_name text not null,
  entity_id uuid not null,
  payload jsonb not null,
  server_version_at_conflict int not null,
  created_at timestamptz not null default now(),
  constraint conflict_copies_table_name_check check (
    table_name in ('sessions', 'session_metric_values', 'recurrence_occurrences')
  )
);

create index on conflict_copies (user_id, table_name, entity_id);

alter table conflict_copies enable row level security;

create policy "own rows readable" on conflict_copies for select using (auth.uid() = user_id);
create policy "own rows insertable" on conflict_copies for insert with check (auth.uid() = user_id);
