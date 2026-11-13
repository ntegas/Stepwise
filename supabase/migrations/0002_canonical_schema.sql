-- Stepwise Phase 2 schema — the canonical backend schema (DEV-007, /ROADMAP.md).
--
-- Supersedes 0001_init.sql (the Phase 0 schema, parked per /PROJECT_STATE.md — kept in the
-- repository as a historical record, not deleted, per explicit instruction). 0001_init.sql was
-- never applied to a live Supabase project, so this migration drops its objects defensively
-- (IF EXISTS) rather than assuming a particular starting state, and is self-contained: it can be
-- applied to a fresh project or to one that happened to run 0001 first, with the same result.
--
-- Built directly against /DATA_MODEL.md and the authoritative field-by-field shape in
-- core/model/src/main/kotlin/com/stepwise/core/model/ (DEV-004) — every table/column name and
-- type below traces to one of those two sources, not invented independently. See
-- /PROJECT_STATE.md's DEV-007 findings for the two real inconsistencies this migration's own
-- preflight found and resolved before writing a single CREATE TABLE statement:
--   1. DATA_MODEL.md's own `progress_events` section described a stored, trigger-maintained
--      table, contradicting /ARCHITECTURE.md §7/§12/§15 (decision B8: no such table, ever — the
--      fan-out is a live join). Fixed in DATA_MODEL.md; this migration creates no such table.
--   2. DATA_MODEL.md's `habits` section once implied a direct `goal_id` column; the Kotlin
--      canonical model (the more authoritative source per DATA_MODEL.md's own stated hierarchy)
--      has no such field — Habit→Goal links go through `goal_activity_links` only, same as
--      Activity. This migration follows the Kotlin model.
--
-- Deliberately out of scope for DEV-007 (stated, not a silent gap):
--   - `skills`/`skill_sources`, `reminders`, `reviews`, `insights` — none of these have a
--     canonical Kotlin shape yet (:core:model has no Skill/Review/Insight type at all, and
--     EntityIds.kt states plainly that the full Reminder entity is DEV-027's job). Building
--     their tables now would mean inventing a shape with nothing to keep it from drifting from
--     whatever those future DEV tasks actually decide — exactly the kind of speculative
--     infrastructure /ROADMAP.md's Operating Mode forbids. `tasks.reminder_id` and
--     `habits`/`tasks` stay reminder-shaped (a bare uuid column, no FK yet) for the same reason.
--   - `goal_progress` and every other analytics view (`activity_stats_*`, `life_area_balance`,
--     `goal_pace`, `goal_forecast`, `execution_rate`) — explicitly DEV-019 (Progress Calculation
--     Engine) / DEV-022 (Life Balance checkpoint)'s job per /ROADMAP.md, and the whole point of
--     the B8 correction above is that these views must stay behaviorally identical to
--     `:core:calculation`'s Kotlin implementation (/ARCHITECTURE.md §12) — writing them now,
--     before that Kotlin implementation exists to check them against, would risk exactly the
--     drift B8 was adopted to prevent.

-- ---------------------------------------------------------------------------
-- Drop Phase 0 objects defensively (never assume prior state)
-- ---------------------------------------------------------------------------

drop view if exists goal_forecast;
drop view if exists goal_pace;
drop view if exists goal_execution_rate;
drop view if exists activity_stats_by_period;
drop view if exists activity_stats_lifetime;
drop trigger if exists sessions_apply_progress on sessions;
drop function if exists apply_session_progress();
drop function if exists resolve_session_goal(text, uuid);
drop table if exists progress_events;
drop table if exists sessions;
drop table if exists habits;
drop table if exists tasks;
drop table if exists activities;
drop table if exists milestones;
drop table if exists goals;
drop table if exists projects;
drop type if exists session_source;
drop type if exists session_status;
drop type if exists goal_type;

-- ---------------------------------------------------------------------------
-- Enums — one-to-one with the Kotlin enums in :core:model (DEV-004)
-- ---------------------------------------------------------------------------

create type goal_type as enum (
  'cumulative', 'quantity', 'distance', 'financial', 'target', 'frequency', 'percentage'
);

create type goal_progress_mode as enum (
  'metric', 'target_value', 'frequency', 'milestones', 'tasks', 'manual'
);

create type lifecycle_status as enum ('active', 'paused', 'completed', 'archived');

create type execution_status as enum (
  'done', 'partial', 'missed', 'rescheduled', 'in_progress', 'cancelled'
);

-- Task|Activity|Habit — the owner of a schedules row, and the source of a sessions row.
create type executable_kind as enum ('task', 'activity', 'habit');

-- Activity|Habit only — narrower than executable_kind (a bare Task has no goal_activity_links
-- row of its own; see core/model/.../Enums.kt's ProgressSourceKind KDoc).
create type progress_source_kind as enum ('activity', 'habit');

-- ---------------------------------------------------------------------------
-- Strategic layer
-- ---------------------------------------------------------------------------

create table life_areas (
  id uuid primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  name text not null,
  icon text,
  category text,
  sort_order int not null default 0,
  archived boolean not null default false,
  updated_at timestamptz not null default now(),
  version int not null default 1,
  deleted_at timestamptz
);

create table visions (
  id uuid primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  life_area_id uuid references life_areas(id) on delete set null,
  content text not null,
  updated_at timestamptz not null default now(),
  version int not null default 1,
  deleted_at timestamptz
);

-- goals.project_id and projects.goal_id are mutually optional FKs (core/model/.../Goal.kt: both
-- Goal.projectId and Project.goalId are nullable, independently) — created in two steps to break
-- the circular reference, same pattern Phase 0 used for the same reason.
create table goals (
  id uuid primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  life_area_id uuid references life_areas(id) on delete set null,
  project_id uuid,
  title text not null,
  type goal_type not null,
  progress_mode goal_progress_mode not null,
  target_value double precision,
  unit text,
  deadline date,
  priority int,
  category text,
  description text,
  status lifecycle_status not null default 'active',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  version int not null default 1,
  deleted_at timestamptz
);

create table projects (
  id uuid primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  goal_id uuid references goals(id) on delete set null,
  life_area_id uuid references life_areas(id) on delete set null,
  title text not null,
  archived boolean not null default false,
  updated_at timestamptz not null default now(),
  version int not null default 1,
  deleted_at timestamptz
);

alter table goals
  add constraint goals_project_id_fkey foreign key (project_id) references projects(id) on delete set null;

-- No user_id — ownership is nested via goal_id (core/model/.../Goal.kt's Milestone has none
-- either); RLS below resolves auth.uid() through the parent goals row.
create table milestones (
  id uuid primary key,
  goal_id uuid not null references goals(id) on delete cascade,
  title text not null,
  sort_order int not null default 0,
  weight double precision not null,
  is_binary boolean not null,
  completed boolean not null default false,
  progress_percent double precision,
  updated_at timestamptz not null default now(),
  version int not null default 1,
  deleted_at timestamptz,
  constraint milestones_progress_percent_range check (
    progress_percent is null or (progress_percent >= 0 and progress_percent <= 100)
  )
);

-- ---------------------------------------------------------------------------
-- Metrics — the one global, unowned catalog table (core/model/.../Metric.kt's explicit
-- exception to "every table carries user_id"); RLS below is read-only for ordinary users.
-- ---------------------------------------------------------------------------

create table metrics (
  id uuid primary key,
  key text not null unique,
  canonical_unit text not null
);

-- ---------------------------------------------------------------------------
-- Doing layer
-- ---------------------------------------------------------------------------

create table activities (
  id uuid primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  life_area_id uuid references life_areas(id) on delete set null,
  title text not null,
  icon text,
  archived boolean not null default false,
  updated_at timestamptz not null default now(),
  version int not null default 1,
  deleted_at timestamptz
);

-- No goal_id column (core/model/.../Activity.kt: Habit→Goal links go through
-- goal_activity_links only, the same fan-out path Activities use — one path, not two).
create table habits (
  id uuid primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  activity_id uuid references activities(id) on delete set null,
  title text not null,
  target_value double precision not null,
  metric_id uuid not null references metrics(id),
  archived boolean not null default false,
  updated_at timestamptz not null default now(),
  version int not null default 1,
  deleted_at timestamptz
);

-- No actual_result/status columns (core/model/.../Task.kt's B1 correction: Task has no
-- independently-editable actual-execution field — that's a read projection off linked Sessions).
-- reminder_id has no FK yet — /DEV-027 owns the reminders table (see file header).
create table tasks (
  id uuid primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  goal_id uuid references goals(id) on delete set null,
  project_id uuid references projects(id) on delete set null,
  life_area_id uuid references life_areas(id) on delete set null,
  title text not null,
  date date,
  time time,
  deadline date,
  duration_minutes int,
  priority int,
  reminder_id uuid,
  metric_id uuid references metrics(id),
  planned_result double precision,
  goal_impact_score int,
  archived boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  version int not null default 1,
  deleted_at timestamptz,
  constraint tasks_goal_impact_score_range check (
    goal_impact_score is null or (goal_impact_score >= 0 and goal_impact_score <= 100)
  )
);

-- The mechanism behind "one Activity/Habit can update multiple Goals, each via a different
-- metric" (/DATA_MODEL.md). No user_id — ownership nested via goal_id.
create table goal_activity_links (
  id uuid primary key,
  source_type progress_source_kind not null,
  source_id uuid not null,
  goal_id uuid not null references goals(id) on delete cascade,
  metric_id uuid not null references metrics(id),
  updated_at timestamptz not null default now(),
  version int not null default 1,
  deleted_at timestamptz,
  constraint goal_activity_links_unique unique (source_type, source_id, goal_id, metric_id)
);

-- ---------------------------------------------------------------------------
-- Scheduling / execution
-- ---------------------------------------------------------------------------

-- One recurrence engine for Task/Activity/Habit (owner-polymorphic), not three
-- (/ARCHITECTURE.md §13). `pattern` mirrors core/model/.../Schedule.kt's RecurrencePattern sealed
-- type as jsonb — {"type": "daily"} / {"type": "weekly", "daysOfWeek": [...]} /
-- {"type": "everyNDays", "intervalDays": n} / {"type": "monthly", "dayOfMonth": n} /
-- {"type": "custom", "rrule": "..."}; the check constraint enforces the discriminant and each
-- variant's required key, not a full JSON schema (no library for that in bare Postgres, and the
-- discriminant + required-key check already catches a malformed row at write time).
create table schedules (
  id uuid primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  owner_type executable_kind not null,
  owner_id uuid not null,
  pattern jsonb not null,
  time time,
  starts_on date not null,
  ends_on date,
  updated_at timestamptz not null default now(),
  version int not null default 1,
  deleted_at timestamptz,
  -- Real bug found and fixed during DEV-007 review (logic reviewer, HIGH, verified live against
  -- Postgres 16): a CHECK constraint passes when its expression is TRUE *or NULL* — only FALSE
  -- fails it. `pattern ->> 'type'` on a pattern with no `type` key at all (e.g. '{}') evaluates to
  -- NULL, so `(pattern ->> 'type') in (...)` was NULL, not FALSE, and the row passed — the exact
  -- "no discriminant at all" case this constraint's own comment above claimed was caught. Fixed
  -- with an explicit `pattern ? 'type'` guard, which is FALSE (not NULL) for a missing key.
  constraint schedules_pattern_shape check (
    pattern ? 'type'
    and (pattern ->> 'type') in ('daily', 'weekly', 'everyNDays', 'monthly', 'custom')
    and case pattern ->> 'type'
      when 'weekly' then pattern ? 'daysOfWeek'
      when 'everyNDays' then pattern ? 'intervalDays'
      when 'monthly' then pattern ? 'dayOfMonth'
      when 'custom' then pattern ? 'rrule'
      else true
    end
  )
);

-- The domain event source of truth (/ARCHITECTURE.md §7) — a Session + its metric values is the
-- canonical execution fact, identified by client_event_id (the idempotency key, concept §60).
create table sessions (
  id uuid primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  source_type executable_kind not null,
  source_id uuid not null,
  date date not null,
  status execution_status not null,
  client_event_id text not null,
  notes text,
  updated_at timestamptz not null default now(),
  version int not null default 1,
  deleted_at timestamptz,
  constraint sessions_client_event_id_unique unique (user_id, client_event_id)
);

-- No user_id — ownership nested via session_id.
create table session_metric_values (
  id uuid primary key,
  session_id uuid not null references sessions(id) on delete cascade,
  metric_id uuid not null references metrics(id),
  planned_value double precision,
  actual_value double precision not null,
  updated_at timestamptz not null default now(),
  version int not null default 1,
  deleted_at timestamptz
);

-- Materialized occurrences only (/ARCHITECTURE.md §13, decision B9) — a virtual occurrence (the
-- common case) is not a row at all. `id` is NOT a uuid: core/model/.../OccurrenceIdentity.kt
-- computes it as the deterministic composite string "occurrence:<recurrenceRuleId>:<date>", not
-- a randomly chosen value, so two devices that independently materialize "the same" occurrence
-- converge on one row. No user_id — ownership nested via recurrence_rule_id.
create table recurrence_occurrences (
  id text primary key,
  recurrence_rule_id uuid not null references schedules(id) on delete cascade,
  occurrence_date date not null,
  rescheduled_to_date date,
  skipped boolean not null default false,
  session_id uuid references sessions(id) on delete set null,
  updated_at timestamptz not null default now(),
  version int not null default 1,
  deleted_at timestamptz
);

-- ---------------------------------------------------------------------------
-- Ownership-consistency triggers — defense in depth beyond RLS (DEV-007 review, adversarial
-- reviewer, HIGH, confirmed exploitable: `goal_activity_links`, `sessions.source_id`, and
-- `schedules.owner_id` are polymorphic references with no FK (they span 3 different tables),
-- and until now nothing checked that the referenced Task/Activity/Habit actually belongs to the
-- same user as the row pointing at it. Concretely: User A could INSERT a `goal_activity_links`
-- row linking A's own `goal_id` to User B's `activity`/`habit` `source_id` — the row's only RLS
-- check is against `goal_id`'s owner, so it was accepted. No cross-user data leak resulted
-- *today*, only because every other table (`activities`, `sessions`, `session_metric_values`)
-- still independently enforces its own `user_id = auth.uid()` RLS regardless of what a forged
-- link points at — but a future view/function computing goal progress by joining through this
-- link (DEV-019, deliberately deferred — see this file's header) could easily run with elevated
-- privilege for performance and silently aggregate another user's data on this unstated,
-- unenforced assumption. Closed now, while cheap, rather than left as a latent assumption later
-- work builds on.
-- ---------------------------------------------------------------------------

-- The actual owner of a Task/Activity/Habit row, by its polymorphic (kind, id) pair — NULL if no
-- such row exists. `kind` is `text` rather than either `executable_kind` or `progress_source_kind`
-- specifically so one function serves both enum domains (`goal_activity_links.source_type` only
-- ranges over activity|habit; `sessions.source_type`/`schedules.owner_type` also include task).
create or replace function resolve_executable_owner(kind text, ref_id uuid)
returns uuid
language sql
stable
as $$
  select case kind
    when 'task' then (select user_id from tasks where id = ref_id)
    when 'activity' then (select user_id from activities where id = ref_id)
    when 'habit' then (select user_id from habits where id = ref_id)
  end
$$;

create or replace function check_goal_activity_link_ownership()
returns trigger
language plpgsql
as $$
declare
  v_goal_owner uuid;
begin
  select user_id into v_goal_owner from goals where id = new.goal_id;
  if resolve_executable_owner(new.source_type::text, new.source_id) is distinct from v_goal_owner then
    raise exception 'goal_activity_links: source_id does not belong to the same user as goal_id';
  end if;
  return new;
end;
$$;

create trigger goal_activity_links_check_ownership
  before insert or update of source_type, source_id, goal_id on goal_activity_links
  for each row execute function check_goal_activity_link_ownership();

create or replace function check_session_source_ownership()
returns trigger
language plpgsql
as $$
begin
  if resolve_executable_owner(new.source_type::text, new.source_id) is distinct from new.user_id then
    raise exception 'sessions: source_id does not belong to the same user as this session';
  end if;
  return new;
end;
$$;

create trigger sessions_check_source_ownership
  before insert or update of source_type, source_id, user_id on sessions
  for each row execute function check_session_source_ownership();

create or replace function check_schedule_owner_ownership()
returns trigger
language plpgsql
as $$
begin
  if resolve_executable_owner(new.owner_type::text, new.owner_id) is distinct from new.user_id then
    raise exception 'schedules: owner_id does not belong to the same user as this schedule';
  end if;
  return new;
end;
$$;

create trigger schedules_check_owner_ownership
  before insert or update of owner_type, owner_id, user_id on schedules
  for each row execute function check_schedule_owner_ownership();

-- ---------------------------------------------------------------------------
-- Indexes
-- ---------------------------------------------------------------------------

create index on tasks (user_id, date);
create index on sessions (user_id, date);
create index on sessions (source_type, source_id);
create index on session_metric_values (session_id);
create index on goal_activity_links (goal_id);
create index on goal_activity_links (source_type, source_id);
create index on milestones (goal_id);
create index on schedules (owner_type, owner_id);
create index on recurrence_occurrences (recurrence_rule_id);

-- ---------------------------------------------------------------------------
-- Row Level Security — deny by default, every row scoped to its owner
-- (/docs/security/SECURITY_ARCHITECTURE.md — "RLS as the sole authorization boundary";
-- /docs/security/THREAT_MODEL.md's Supabase/RLS section).
-- ---------------------------------------------------------------------------

alter table life_areas enable row level security;
alter table visions enable row level security;
alter table goals enable row level security;
alter table projects enable row level security;
alter table milestones enable row level security;
alter table metrics enable row level security;
alter table activities enable row level security;
alter table habits enable row level security;
alter table tasks enable row level security;
alter table goal_activity_links enable row level security;
alter table schedules enable row level security;
alter table sessions enable row level security;
alter table session_metric_values enable row level security;
alter table recurrence_occurrences enable row level security;

-- Direct-owner tables: one uniform policy shape, every operation.
create policy "own rows" on life_areas for all using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "own rows" on visions for all using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "own rows" on goals for all using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "own rows" on projects for all using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "own rows" on activities for all using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "own rows" on habits for all using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "own rows" on tasks for all using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "own rows" on schedules for all using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "own rows" on sessions for all using (auth.uid() = user_id) with check (auth.uid() = user_id);

-- Nested-ownership tables: resolve the owner through the parent row (no user_id column here).
create policy "own rows via goal" on milestones for all
  using (auth.uid() = (select user_id from goals where goals.id = milestones.goal_id))
  with check (auth.uid() = (select user_id from goals where goals.id = milestones.goal_id));

create policy "own rows via goal" on goal_activity_links for all
  using (auth.uid() = (select user_id from goals where goals.id = goal_activity_links.goal_id))
  with check (auth.uid() = (select user_id from goals where goals.id = goal_activity_links.goal_id));

create policy "own rows via session" on session_metric_values for all
  using (auth.uid() = (select user_id from sessions where sessions.id = session_metric_values.session_id))
  with check (auth.uid() = (select user_id from sessions where sessions.id = session_metric_values.session_id));

create policy "own rows via schedule" on recurrence_occurrences for all
  using (auth.uid() = (select user_id from schedules where schedules.id = recurrence_occurrences.recurrence_rule_id))
  with check (auth.uid() = (select user_id from schedules where schedules.id = recurrence_occurrences.recurrence_rule_id));

-- Metrics: shared global catalog (no owner) — readable by any authenticated user, writable only
-- by the service role (which bypasses RLS entirely; no write policy is defined for `authenticated`
-- on purpose, so INSERT/UPDATE/DELETE from an ordinary client fails closed).
create policy "catalog is readable" on metrics for select using (true);

-- ---------------------------------------------------------------------------
-- Metrics seed data (/DATA_MODEL.md's named canonical units) — run as a privileged migration
-- role, which bypasses RLS, same as any other DDL/seed statement in this file.
-- ---------------------------------------------------------------------------

insert into metrics (id, key, canonical_unit) values
  (gen_random_uuid(), 'duration_minutes', 'min'),
  (gen_random_uuid(), 'distance_km', 'km'),
  (gen_random_uuid(), 'count', 'count'),
  (gen_random_uuid(), 'pages', 'pages'),
  (gen_random_uuid(), 'amount_minor_units', 'minor_units'),
  (gen_random_uuid(), 'weight_kg', 'kg');
