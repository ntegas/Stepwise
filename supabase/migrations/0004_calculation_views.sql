-- Stepwise Phase 2 schema — the Progress Calculation Engine's views (DEV-019, /ROADMAP.md).
--
-- Deliberately deferred by 0002_canonical_schema.sql's own header ("these views must stay
-- behaviorally identical to :core:calculation's Kotlin implementation... writing them now,
-- before that Kotlin implementation exists to check them against, would risk exactly the drift
-- B8 was adopted to prevent") - /CALCULATION_ENGINE.md is the binding specification both
-- implementations follow; see it for the full reasoning behind every formula below.
--
-- No SECURITY DEFINER, no elevated privilege, no separate re-check of ownership anywhere in
-- this file - 0002_canonical_schema.sql's own `goal_activity_links_check_ownership`/
-- `sessions_check_source_ownership` triggers already guarantee, at write time, that a
-- `goal_activity_links`/`sessions` row's `source_id` belongs to the same user as the row that
-- references it. That is the exact guarantee this file's views lean on: plain RLS on each
-- underlying table (goals, milestones, tasks, sessions, session_metric_values,
-- goal_activity_links) is sufficient for every join below to only ever touch the querying
-- user's own rows - precisely the risk 0002's own header flagged and closed "while cheap,
-- rather than left as a latent assumption later work builds on." This file is that later work,
-- confirming the assumption held.
--
-- **GoalProgressMode.FREQUENCY is intentionally absent from `goal_progress` below** - unlike
-- METRIC/TARGET_VALUE/MILESTONES/TASKS, it is inherently period-scoped (CALCULATION_ENGINE.md:
-- "there is no single 'frequency so far' without a period"), so it cannot be expressed as a
-- parameter-free view column the way the other four can. A caller computing FREQUENCY progress
-- queries `sessions`/`goal_activity_links` directly with its own date-range filter and the same
-- qualifying-status rule `execution_rate` below already encodes - tracked as the same kind of
-- disclosed gap CALCULATION_ENGINE.md's own "Kotlin ↔ SQL parity" section already names
-- (no automated parity suite exists yet either).
--
-- **GoalProgressMode.MANUAL is absent entirely** - CALCULATION_ENGINE.md's own "MANUAL mode"
-- section: no field exists anywhere in `goals` to read a stored value from yet.

-- ---------------------------------------------------------------------------
-- execution_rate - one row per Session, CALCULATION_ENGINE.md's binding Execution Rate formula.
-- `rate_percent` is NULL (never 0) for CANCELLED/RESCHEDULED/IN_PROGRESS and for a PARTIAL
-- session with no usable planned_value - "no data" and "a real zero" are never conflated, the
-- same rule the Kotlin ExecutionRateCalculator follows. A caller averages rate_percent over
-- whatever period/source it's asking about (this view is deliberately not pre-aggregated).
-- ---------------------------------------------------------------------------
create or replace view execution_rate as
select
  s.id as session_id,
  s.user_id,
  s.source_type,
  s.source_id,
  s.date,
  s.status,
  case s.status
    when 'done' then 100.0
    when 'missed' then 0.0
    when 'partial' then (
      select
        case
          when sum(smv.planned_value) is null or sum(smv.planned_value) = 0 then null
          else sum(smv.actual_value) / sum(smv.planned_value) * 100.0
        end
      from session_metric_values smv
      where smv.session_id = s.id and smv.deleted_at is null
    )
    else null
  end as rate_percent
from sessions s
where s.deleted_at is null;

-- ---------------------------------------------------------------------------
-- goal_progress - one row per Goal, the METRIC/TARGET_VALUE/MILESTONES/TASKS branches of
-- CALCULATION_ENGINE.md's "Goal Progress, per GoalProgressMode". `current_value`/`percent`
-- follow the exact same "null means nothing to compute from yet, never a false zero" rule the
-- Kotlin GoalProgressResult does, with METRIC's own sum being the one legitimate real zero
-- (a Goal with zero contributions so far genuinely has zero progress, the same distinction
-- GetGoalProgressUseCaseTest's own "METRIC mode with no linked values yet" test asserts).
-- ---------------------------------------------------------------------------
create or replace view goal_progress as
select
  g.id as goal_id,
  g.user_id,
  g.progress_mode,
  case g.progress_mode
    when 'metric' then (
      select coalesce(sum(smv.actual_value), 0.0)
      from goal_activity_links gal
      join sessions s on s.source_type = gal.source_type::text::executable_kind and s.source_id = gal.source_id
      join session_metric_values smv on smv.session_id = s.id and smv.metric_id = gal.metric_id
      where gal.goal_id = g.id and gal.deleted_at is null and s.deleted_at is null and smv.deleted_at is null
    )
    when 'target_value' then (
      -- Tie-break on the Session's own updated_at, not the SessionMetricValue's - matching
      -- Kotlin's Session.isMoreRecentThan (GetGoalProgressUseCase.kt). The two rows can diverge
      -- (a metric value corrected without touching its parent Session's updated_at), so this has
      -- to name one canonical field rather than "the latest sync.updatedAt" staying ambiguous
      -- between the two tables - caught by the DEV-019 logic reviewer.
      select smv.actual_value
      from goal_activity_links gal
      join sessions s on s.source_type = gal.source_type::text::executable_kind and s.source_id = gal.source_id
      join session_metric_values smv on smv.session_id = s.id and smv.metric_id = gal.metric_id
      where gal.goal_id = g.id and gal.deleted_at is null and s.deleted_at is null and smv.deleted_at is null
      order by s.date desc, s.updated_at desc
      limit 1
    )
    when 'milestones' then (
      select
        case
          when sum(m.weight) is null or sum(m.weight) = 0 then null
          else
            sum(m.weight * (case when m.is_binary then (case when m.completed then 100.0 else 0.0 end)
                                  else coalesce(m.progress_percent, 0.0) end)) / sum(m.weight)
        end
      from milestones m
      where m.goal_id = g.id and m.deleted_at is null
    )
    when 'tasks' then (
      select
        case
          when count(*) = 0 then null
          else
            100.0 * count(*) filter (
              where exists (
                select 1 from sessions s
                where s.source_type = 'task' and s.source_id = t.id
                  and s.status = 'done' and s.deleted_at is null
              )
            ) / count(*)
        end
      from tasks t
      where t.goal_id = g.id and t.archived = false and t.deleted_at is null
    )
    else null
  end as current_value,
  g.target_value
from goals g
where g.deleted_at is null;

-- ---------------------------------------------------------------------------
-- activity_stats_weekly/monthly/yearly/lifetime - /DATA_MODEL.md's own named rollup views,
-- CALCULATION_ENGINE.md's "Activity/Habit/Task period statistics". date_trunc('week', ...)
-- truncates to Monday - the exact same week-start convention :core:common's LocalDate.startOfWeek()
-- uses, confirmed by inspection rather than assumed.
--
-- Joins a pre-aggregated per-session subquery (session_metric_sums below), not the raw
-- session_metric_values table directly - a session with more than one linked metric (the normal
-- case per DATA_MODEL.md §23, e.g. a Running session tracking both duration and distance) would
-- otherwise fan out into one row per metric *before* the join to execution_rate, so avg(rate_percent)
-- counted that session's single rate N times instead of once, skewing execution_rate/trend toward
-- multi-metric sessions. Caught by the DEV-019 logic reviewer; planned_total/actual_total were
-- unaffected (sum() over the fanned-out rows still summed each metric row exactly once either way).
-- ---------------------------------------------------------------------------
create or replace view session_metric_sums as
select
  session_id,
  sum(planned_value) as planned_total,
  sum(actual_value) as actual_total
from session_metric_values
where deleted_at is null
group by session_id;

create or replace view activity_stats_weekly as
select
  s.user_id,
  s.source_type,
  s.source_id,
  date_trunc('week', s.date)::date as period_start,
  coalesce(sum(smv.planned_total), 0.0) as planned_total,
  coalesce(sum(smv.actual_total), 0.0) as actual_total,
  count(distinct s.id) filter (where s.status != 'cancelled') as session_count,
  avg(er.rate_percent) as execution_rate
from sessions s
left join session_metric_sums smv on smv.session_id = s.id
left join execution_rate er on er.session_id = s.id
where s.deleted_at is null
group by s.user_id, s.source_type, s.source_id, date_trunc('week', s.date);

create or replace view activity_stats_monthly as
select
  s.user_id,
  s.source_type,
  s.source_id,
  date_trunc('month', s.date)::date as period_start,
  coalesce(sum(smv.planned_total), 0.0) as planned_total,
  coalesce(sum(smv.actual_total), 0.0) as actual_total,
  count(distinct s.id) filter (where s.status != 'cancelled') as session_count,
  avg(er.rate_percent) as execution_rate
from sessions s
left join session_metric_sums smv on smv.session_id = s.id
left join execution_rate er on er.session_id = s.id
where s.deleted_at is null
group by s.user_id, s.source_type, s.source_id, date_trunc('month', s.date);

create or replace view activity_stats_yearly as
select
  s.user_id,
  s.source_type,
  s.source_id,
  date_trunc('year', s.date)::date as period_start,
  coalesce(sum(smv.planned_total), 0.0) as planned_total,
  coalesce(sum(smv.actual_total), 0.0) as actual_total,
  count(distinct s.id) filter (where s.status != 'cancelled') as session_count,
  avg(er.rate_percent) as execution_rate
from sessions s
left join session_metric_sums smv on smv.session_id = s.id
left join execution_rate er on er.session_id = s.id
where s.deleted_at is null
group by s.user_id, s.source_type, s.source_id, date_trunc('year', s.date);

create or replace view activity_stats_lifetime as
select
  s.user_id,
  s.source_type,
  s.source_id,
  coalesce(sum(smv.planned_total), 0.0) as planned_total,
  coalesce(sum(smv.actual_total), 0.0) as actual_total,
  count(distinct s.id) filter (where s.status != 'cancelled') as session_count,
  avg(er.rate_percent) as execution_rate
from sessions s
left join session_metric_sums smv on smv.session_id = s.id
left join execution_rate er on er.session_id = s.id
where s.deleted_at is null
group by s.user_id, s.source_type, s.source_id;
