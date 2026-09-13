-- Stepwise Phase 0 schema
-- See /docs/data-model.md for the entity/relationship rationale.
-- Simplifications explicitly deferred to a later phase are marked "PHASE 1+".

create extension if not exists pgcrypto;

-- ---------------------------------------------------------------------------
-- Enums
-- ---------------------------------------------------------------------------

create type goal_type as enum (
  'cumulative', 'quantity', 'distance', 'financial', 'target', 'frequency', 'percentage'
);

create type session_status as enum (
  'done', 'partial', 'missed', 'rescheduled', 'in_progress', 'cancelled'
);

create type session_source as enum ('task', 'activity', 'habit');

-- ---------------------------------------------------------------------------
-- Core tables
-- ---------------------------------------------------------------------------

create table goals (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  title text not null,
  type goal_type not null,
  target_value numeric,
  current_value numeric not null default 0,
  unit text,
  deadline date,
  priority int,
  category text,
  description text,
  created_at timestamptz not null default now()
);

create table projects (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  goal_id uuid references goals(id) on delete set null,
  title text not null,
  created_at timestamptz not null default now()
);

alter table goals
  add column project_id uuid references projects(id) on delete set null;

create table milestones (
  id uuid primary key default gen_random_uuid(),
  goal_id uuid not null references goals(id) on delete cascade,
  title text not null,
  sort_order int not null default 0,
  percent numeric not null default 0,
  created_at timestamptz not null default now()
);

create table activities (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  title text not null,
  icon text,
  default_goal_id uuid references goals(id) on delete set null,
  recurrence_rule text,
  created_at timestamptz not null default now()
);

create table tasks (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  goal_id uuid references goals(id) on delete set null,
  project_id uuid references projects(id) on delete set null,
  title text not null,
  date date,
  time time,
  deadline date,
  duration numeric,
  priority int,
  reminder timestamptz,
  recurrence text,
  metric text,
  planned_result numeric,
  actual_result numeric,
  status session_status not null default 'in_progress',
  created_at timestamptz not null default now()
);

create table habits (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  activity_id uuid references activities(id) on delete set null,
  goal_id uuid references goals(id) on delete set null,
  title text not null,
  target_per_period numeric,
  unit text,
  recurrence text,
  created_at timestamptz not null default now()
);

create table sessions (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  source_type session_source not null,
  source_id uuid not null,
  date date not null default current_date,
  planned_amount numeric,
  actual_amount numeric,
  status session_status not null default 'done',
  notes text,
  created_at timestamptz not null default now()
);

create table progress_events (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  session_id uuid not null references sessions(id) on delete cascade,
  goal_id uuid references goals(id) on delete set null,
  amount numeric not null default 0,
  created_at timestamptz not null default now(),
  unique (session_id)
);

create index on tasks (user_id, date);
create index on sessions (user_id, date);
create index on sessions (source_type, source_id);
create index on progress_events (goal_id);

-- ---------------------------------------------------------------------------
-- Cascade: one session write -> one progress_event -> goal.current_value
-- This is the mandatory "no double entry" rule (concept §19, §45).
-- ---------------------------------------------------------------------------

create or replace function resolve_session_goal(p_source_type session_source, p_source_id uuid)
returns uuid
language sql
stable
as $$
  select case p_source_type
    when 'activity' then (select default_goal_id from activities where id = p_source_id)
    when 'habit'    then (select goal_id from habits where id = p_source_id)
    when 'task'     then (select goal_id from tasks where id = p_source_id)
  end
$$;

create or replace function apply_session_progress()
returns trigger
language plpgsql
as $$
declare
  v_goal_id uuid;
  v_goal_type goal_type;
  v_delta numeric;
  v_prev_amount numeric := 0;
begin
  v_goal_id := resolve_session_goal(new.source_type, new.source_id);

  if v_goal_id is null then
    return new;
  end if;

  select type into v_goal_type from goals where id = v_goal_id;

  if tg_op = 'UPDATE' then
    select amount into v_prev_amount from progress_events where session_id = new.id;
    v_prev_amount := coalesce(v_prev_amount, 0);
  end if;

  -- target/percentage goals track an absolute latest value (e.g. weight, % complete);
  -- everything else accumulates. PHASE 1+: refine 'frequency' to a windowed count
  -- instead of a lifetime running total.
  if v_goal_type in ('target', 'percentage') then
    update goals set current_value = coalesce(new.actual_amount, 0) where id = v_goal_id;
    v_delta := coalesce(new.actual_amount, 0) - v_prev_amount;
  else
    v_delta := coalesce(new.actual_amount, 0) - v_prev_amount;
    update goals set current_value = current_value + v_delta where id = v_goal_id;
  end if;

  insert into progress_events (user_id, session_id, goal_id, amount)
  values (new.user_id, new.id, v_goal_id, coalesce(new.actual_amount, 0))
  on conflict (session_id) do update set amount = excluded.amount, goal_id = excluded.goal_id;

  return new;
end;
$$;

create trigger sessions_apply_progress
  after insert or update of actual_amount, source_type, source_id on sessions
  for each row
  execute function apply_session_progress();

-- ---------------------------------------------------------------------------
-- Row Level Security — every table is private to its owning user.
-- ---------------------------------------------------------------------------

alter table goals enable row level security;
alter table projects enable row level security;
alter table milestones enable row level security;
alter table activities enable row level security;
alter table tasks enable row level security;
alter table habits enable row level security;
alter table sessions enable row level security;
alter table progress_events enable row level security;

create policy "own rows" on goals for all using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "own rows" on projects for all using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "own rows" on activities for all using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "own rows" on tasks for all using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "own rows" on habits for all using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "own rows" on sessions for all using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "own rows" on progress_events for all using (auth.uid() = user_id) with check (auth.uid() = user_id);

create policy "own rows via goal" on milestones for all
  using (auth.uid() = (select user_id from goals where goals.id = milestones.goal_id))
  with check (auth.uid() = (select user_id from goals where goals.id = milestones.goal_id));

-- ---------------------------------------------------------------------------
-- Derived views — read-only rollups, no logic duplicated per client (§45).
-- PHASE 1+: currently scoped to sessions logged directly against an Activity
-- (source_type = 'activity'); extend to roll task/habit sessions up into
-- their linked activity once that association is modeled.
-- ---------------------------------------------------------------------------

create view activity_stats_lifetime as
select
  a.id as activity_id,
  a.user_id,
  count(s.id) as session_count,
  coalesce(sum(s.actual_amount), 0) as total_actual,
  coalesce(avg(s.actual_amount), 0) as avg_session
from activities a
left join sessions s on s.source_type = 'activity' and s.source_id = a.id
group by a.id, a.user_id;

create view activity_stats_by_period as
select
  a.id as activity_id,
  a.user_id,
  date_trunc('week', s.date)  as week_start,
  date_trunc('month', s.date) as month_start,
  date_trunc('year', s.date)  as year_start,
  sum(s.planned_amount) as planned,
  sum(s.actual_amount)  as actual
from activities a
join sessions s on s.source_type = 'activity' and s.source_id = a.id
group by a.id, a.user_id, week_start, month_start, year_start;

create view goal_execution_rate as
select
  g.id as goal_id,
  g.user_id,
  coalesce(sum(s.planned_amount), 0) as planned,
  coalesce(sum(s.actual_amount), 0) as actual,
  case when coalesce(sum(s.planned_amount), 0) = 0 then null
       else round(100.0 * sum(s.actual_amount) / sum(s.planned_amount), 1)
  end as execution_pct,
  count(*) filter (where s.status = 'done') as done_count,
  count(*) filter (where s.status = 'partial') as partial_count,
  count(*) filter (where s.status = 'missed') as missed_count,
  count(*) filter (where s.status = 'rescheduled') as rescheduled_count,
  count(*) filter (where s.status = 'cancelled') as cancelled_count
from goals g
left join progress_events pe on pe.goal_id = g.id
left join sessions s on s.id = pe.session_id
group by g.id, g.user_id;

create view goal_pace as
select
  g.id as goal_id,
  g.user_id,
  g.target_value - g.current_value as remaining,
  g.deadline,
  greatest(extract(epoch from (g.deadline - current_date)) / (7 * 86400), 0.01) as weeks_left,
  case when g.deadline is null then null
       else round((g.target_value - g.current_value) /
            greatest(extract(epoch from (g.deadline - current_date)) / (7 * 86400), 0.01), 2)
  end as required_per_week,
  coalesce((
    select sum(pe.amount) from progress_events pe
    join sessions s on s.id = pe.session_id
    where pe.goal_id = g.id and s.date >= current_date - interval '7 days'
  ), 0) as current_per_week
from goals g
where g.deadline is not null;

create view goal_forecast as
select
  gp.goal_id,
  gp.user_id,
  gp.deadline,
  case when gp.current_per_week > 0
       then (current_date + ((gp.remaining / gp.current_per_week) * 7)::int)::date
       else null
  end as estimated_completion,
  case when gp.current_per_week > 0 and gp.deadline is not null
       then gp.deadline - (current_date + ((gp.remaining / gp.current_per_week) * 7)::int)::date
       else null
  end as days_ahead
from goal_pace gp;
