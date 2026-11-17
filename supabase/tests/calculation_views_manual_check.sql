-- Manual local verification for 0004_calculation_views.sql (DEV-019) - seeds one representative
-- row per GoalProgressMode/ExecutionStatus branch and asserts the view's output against
-- CALCULATION_ENGINE.md's own formulas, by hand, the same class of real-but-not-independent
-- verification /supabase/tests/README.md already documents for DEV-007/DEV-008. Not wired into
-- CI (no Postgres available there) - run locally when this file or 0004 changes.
set client_min_messages to warning;

insert into auth.users (id, email) values ('11111111-1111-1111-1111-111111111111', 'u1@example.com');

-- 0002_canonical_schema.sql already seeds the default metric catalog, duration_minutes
-- included - reuse it rather than inserting a duplicate key.


-- METRIC goal: two Sessions (one DONE, one PARTIAL) linked via one Activity.
insert into goals (id, user_id, title, type, progress_mode, target_value, status)
values ('00000000-0000-0000-0000-00000000a001', '11111111-1111-1111-1111-111111111111',
        'Metric goal', 'cumulative', 'metric', 100, 'active');

insert into activities (id, user_id, title) values
  ('00000000-0000-0000-0000-0000000a0001', '11111111-1111-1111-1111-111111111111', 'Boxing');

insert into goal_activity_links (id, source_type, source_id, goal_id, metric_id)
select '00000000-0000-0000-0000-0000000b0001'::uuid, 'activity'::progress_source_kind,
       '00000000-0000-0000-0000-0000000a0001'::uuid, '00000000-0000-0000-0000-00000000a001'::uuid, id
from metrics where key = 'duration_minutes';

insert into sessions (id, user_id, source_type, source_id, date, status, client_event_id) values
  ('00000000-0000-0000-0000-0000000c0001', '11111111-1111-1111-1111-111111111111', 'activity',
   '00000000-0000-0000-0000-0000000a0001', '2026-11-17', 'done', 'evt-1'),
  ('00000000-0000-0000-0000-0000000c0002', '11111111-1111-1111-1111-111111111111', 'activity',
   '00000000-0000-0000-0000-0000000a0001', '2026-11-18', 'partial', 'evt-2');

insert into session_metric_values (id, session_id, metric_id, planned_value, actual_value)
select '00000000-0000-0000-0000-0000000d0001'::uuid, '00000000-0000-0000-0000-0000000c0001'::uuid, id, 30, 40
from metrics where key = 'duration_minutes'
union all
select '00000000-0000-0000-0000-0000000d0002'::uuid, '00000000-0000-0000-0000-0000000c0002'::uuid, id, 60, 30
from metrics where key = 'duration_minutes';

-- MILESTONES goal: one binary (completed), one gradable (50%), weights 2:1.
insert into goals (id, user_id, title, type, progress_mode, status)
values ('00000000-0000-0000-0000-00000000a002', '11111111-1111-1111-1111-111111111111',
        'Milestones goal', 'percentage', 'milestones', 'active');

insert into milestones (id, goal_id, title, weight, is_binary, completed, progress_percent) values
  ('00000000-0000-0000-0000-0000000e0001', '00000000-0000-0000-0000-00000000a002', 'Phase 1', 2, true, true, null),
  ('00000000-0000-0000-0000-0000000e0002', '00000000-0000-0000-0000-00000000a002', 'Phase 2', 1, false, false, 50);

-- TASKS goal: one done, one open.
insert into goals (id, user_id, title, type, progress_mode, status)
values ('00000000-0000-0000-0000-00000000a003', '11111111-1111-1111-1111-111111111111',
        'Tasks goal', 'percentage', 'tasks', 'active');

insert into tasks (id, user_id, goal_id, title) values
  ('00000000-0000-0000-0000-0000000f0001', '11111111-1111-1111-1111-111111111111',
   '00000000-0000-0000-0000-00000000a003', 'Sub-task done'),
  ('00000000-0000-0000-0000-0000000f0002', '11111111-1111-1111-1111-111111111111',
   '00000000-0000-0000-0000-00000000a003', 'Sub-task open');

insert into sessions (id, user_id, source_type, source_id, date, status, client_event_id) values
  ('00000000-0000-0000-0000-0000000c0003', '11111111-1111-1111-1111-111111111111', 'task',
   '00000000-0000-0000-0000-0000000f0001', '2026-11-17', 'done', 'evt-3');

-- Execution Rate: a CANCELLED and a RESCHEDULED Session, to confirm both are excluded (NULL).
insert into sessions (id, user_id, source_type, source_id, date, status, client_event_id) values
  ('00000000-0000-0000-0000-0000000c0004', '11111111-1111-1111-1111-111111111111', 'activity',
   '00000000-0000-0000-0000-0000000a0001', '2026-11-19', 'cancelled', 'evt-4'),
  ('00000000-0000-0000-0000-0000000c0005', '11111111-1111-1111-1111-111111111111', 'activity',
   '00000000-0000-0000-0000-0000000a0001', '2026-11-20', 'rescheduled', 'evt-5');

\echo '--- goal_progress (expect: metric=70, milestones=83.33.., tasks=50) ---'
select goal_id, progress_mode, current_value from goal_progress order by goal_id;

\echo '--- execution_rate (expect: done=100, partial=50, cancelled=NULL, rescheduled=NULL) ---'
select session_id, status, rate_percent from execution_rate order by session_id;

\echo '--- activity_stats_lifetime for the Boxing activity (expect planned=90, actual=70, session_count=3 [cancelled excluded, rescheduled counted], execution_rate=75 [avg of 100 and 50]) ---'
select planned_total, actual_total, session_count, execution_rate
from activity_stats_lifetime
where source_id = '00000000-0000-0000-0000-0000000a0001';

do $$
declare
  v double precision;
begin
  select current_value into v from goal_progress where goal_id = '00000000-0000-0000-0000-00000000a001';
  if v is distinct from 70.0 then raise exception 'METRIC goal_progress mismatch: got %, expected 70.0', v; end if;

  select current_value into v from goal_progress where goal_id = '00000000-0000-0000-0000-00000000a002';
  if abs(v - 250.0/3.0) > 0.0001 then raise exception 'MILESTONES goal_progress mismatch: got %, expected 83.33..', v; end if;

  select current_value into v from goal_progress where goal_id = '00000000-0000-0000-0000-00000000a003';
  if v is distinct from 50.0 then raise exception 'TASKS goal_progress mismatch: got %, expected 50.0', v; end if;

  select rate_percent into v from execution_rate where session_id = '00000000-0000-0000-0000-0000000c0001';
  if v is distinct from 100.0 then raise exception 'DONE execution_rate mismatch: got %, expected 100.0', v; end if;

  select rate_percent into v from execution_rate where session_id = '00000000-0000-0000-0000-0000000c0002';
  if v is distinct from 50.0 then raise exception 'PARTIAL execution_rate mismatch: got %, expected 50.0', v; end if;

  select rate_percent into v from execution_rate where session_id = '00000000-0000-0000-0000-0000000c0004';
  if v is not null then raise exception 'CANCELLED execution_rate should be NULL, got %', v; end if;

  select rate_percent into v from execution_rate where session_id = '00000000-0000-0000-0000-0000000c0005';
  if v is not null then raise exception 'RESCHEDULED execution_rate should be NULL, got %', v; end if;

  select planned_total into v from activity_stats_lifetime where source_id = '00000000-0000-0000-0000-0000000a0001';
  if v is distinct from 90.0 then raise exception 'activity_stats_lifetime planned_total mismatch: got %, expected 90.0', v; end if;

  raise notice 'All assertions passed';
end $$;
