-- Adversarial RLS test for DEV-007 / /docs/security/SECURITY_TEST_MATRIX.md rows 1, 2, 3, 5, 8.
-- Genuinely executed against real Postgres RLS enforcement (not a description of intended
-- behavior) — see /supabase/tests/README.md for how to run this yourself, and
-- /PROJECT_STATE.md's DEV-007 findings for when/how this was last run and what it found.
--
-- IMPORTANT — this does not by itself satisfy /docs/security/AI_CODE_SECURITY.md's
-- independent-review requirement for S3 work: it was written by the same AI that wrote
-- 0002_canonical_schema.sql. Treat a clean run of this file as "the policies behave as
-- documented when tested this way" — real evidence, not a substitute for an independent
-- reviewer rewriting or extending these cases from scratch before S3 sign-off.
--
-- Every check below is a machine-checkable assertion (RAISE EXCEPTION on mismatch), not just a
-- printed row count a human has to read — a fix made during DEV-007's own review (logic
-- reviewer, MEDIUM: the original version proved its claims only to a human reading `psql`
-- output, so a future silent regression could have passed unnoticed under automated re-runs).
--
-- Must be run as a non-superuser, non-table-owner role (`app_user` below) — RLS is bypassed
-- for superusers and table owners regardless of policy content, so running this as the
-- migration role would silently test nothing.

create or replace function assert_eq(actual bigint, expected bigint, label text) returns void
language plpgsql as $$
begin
  if actual is distinct from expected then
    raise exception 'ASSERTION FAILED (%): expected %, got %', label, expected, actual;
  end if;
  raise notice 'ok - %', label;
end;
$$;

grant usage on schema public, auth to app_user;
grant select, insert, update, delete on all tables in schema public to app_user;
grant select, insert on auth.users to app_user;

insert into auth.users (id, email) values
  ('11111111-1111-1111-1111-111111111111', 'user-a@test'),
  ('22222222-2222-2222-2222-222222222222', 'user-b@test');

set role app_user;

\echo '--- Setup: User A creates one row per owner-checked table, plus an Activity User B will try to forge a link to ---'
select set_config('request.jwt.claim.sub', '11111111-1111-1111-1111-111111111111', false);

insert into life_areas (id, user_id, name) values
  ('a0000000-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'Health');

insert into goals (id, user_id, title, type, progress_mode) values
  ('a0000000-0000-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111', 'Run 500km', 'distance', 'metric');

insert into milestones (id, goal_id, title, weight, is_binary, completed) values
  ('a0000000-0000-0000-0000-000000000003', 'a0000000-0000-0000-0000-000000000002', 'First 100km', 1.0, true, false);

insert into activities (id, user_id, title) values
  ('a0000000-0000-0000-0000-000000000004', '11111111-1111-1111-1111-111111111111', 'Running');

insert into goal_activity_links (id, source_type, source_id, goal_id, metric_id)
  select 'a0000000-0000-0000-0000-000000000005', 'activity', 'a0000000-0000-0000-0000-000000000004',
         'a0000000-0000-0000-0000-000000000002', id from metrics where key = 'distance_km';

insert into sessions (id, user_id, source_type, source_id, date, status, client_event_id) values
  ('a0000000-0000-0000-0000-000000000006', '11111111-1111-1111-1111-111111111111', 'activity',
   'a0000000-0000-0000-0000-000000000004', current_date, 'done', 'evt-a-1');

insert into session_metric_values (id, session_id, metric_id, actual_value)
  select 'a0000000-0000-0000-0000-000000000007', 'a0000000-0000-0000-0000-000000000006', id, 5.0
  from metrics where key = 'distance_km';

insert into schedules (id, user_id, owner_type, owner_id, pattern, starts_on) values
  ('a0000000-0000-0000-0000-000000000008', '11111111-1111-1111-1111-111111111111', 'activity',
   'a0000000-0000-0000-0000-000000000004', '{"type":"daily"}', current_date);

insert into recurrence_occurrences (id, recurrence_rule_id, occurrence_date) values
  ('occurrence:a0000000-0000-0000-0000-000000000008:' || current_date, 'a0000000-0000-0000-0000-000000000008', current_date);

\echo '--- Row 1: User B SELECTs User A rows — expect 0 rows everywhere ---'
select set_config('request.jwt.claim.sub', '22222222-2222-2222-2222-222222222222', false);

select assert_eq(count(*), 0, 'cross-user SELECT: life_areas') from life_areas where id = 'a0000000-0000-0000-0000-000000000001';
select assert_eq(count(*), 0, 'cross-user SELECT: goals') from goals where id = 'a0000000-0000-0000-0000-000000000002';
select assert_eq(count(*), 0, 'cross-user SELECT: milestones') from milestones where id = 'a0000000-0000-0000-0000-000000000003';
select assert_eq(count(*), 0, 'cross-user SELECT: activities') from activities where id = 'a0000000-0000-0000-0000-000000000004';
select assert_eq(count(*), 0, 'cross-user SELECT: goal_activity_links') from goal_activity_links where id = 'a0000000-0000-0000-0000-000000000005';
select assert_eq(count(*), 0, 'cross-user SELECT: sessions') from sessions where id = 'a0000000-0000-0000-0000-000000000006';
select assert_eq(count(*), 0, 'cross-user SELECT: session_metric_values') from session_metric_values where id = 'a0000000-0000-0000-0000-000000000007';
select assert_eq(count(*), 0, 'cross-user SELECT: schedules') from schedules where id = 'a0000000-0000-0000-0000-000000000008';
select assert_eq(count(*), 0, 'cross-user SELECT: recurrence_occurrences') from recurrence_occurrences where recurrence_rule_id = 'a0000000-0000-0000-0000-000000000008';

\echo '--- Row 2: User B attempts UPDATE on every owner-checked table — expect 0 rows affected everywhere ---'
with u as (update goals set title = 'HACKED' where id = 'a0000000-0000-0000-0000-000000000002' returning 1)
select assert_eq(count(*), 0, 'cross-user UPDATE: goals') from u;
with u as (update milestones set title = 'HACKED' where id = 'a0000000-0000-0000-0000-000000000003' returning 1)
select assert_eq(count(*), 0, 'cross-user UPDATE: milestones (nested via goal_id)') from u;
with u as (update goal_activity_links set metric_id = metric_id where id = 'a0000000-0000-0000-0000-000000000005' returning 1)
select assert_eq(count(*), 0, 'cross-user UPDATE: goal_activity_links (nested via goal_id)') from u;
with u as (update session_metric_values set actual_value = 999 where id = 'a0000000-0000-0000-0000-000000000007' returning 1)
select assert_eq(count(*), 0, 'cross-user UPDATE: session_metric_values (nested via session_id)') from u;
with u as (update recurrence_occurrences set skipped = true where recurrence_rule_id = 'a0000000-0000-0000-0000-000000000008' returning 1)
select assert_eq(count(*), 0, 'cross-user UPDATE: recurrence_occurrences (nested via recurrence_rule_id)') from u;

\echo '--- Row 2 (DELETE variant): User B attempts DELETE on every owner-checked table — expect 0 rows affected everywhere ---'
with d as (delete from sessions where id = 'a0000000-0000-0000-0000-000000000006' returning 1)
select assert_eq(count(*), 0, 'cross-user DELETE: sessions') from d;
with d as (delete from milestones where id = 'a0000000-0000-0000-0000-000000000003' returning 1)
select assert_eq(count(*), 0, 'cross-user DELETE: milestones (nested via goal_id)') from d;
with d as (delete from goal_activity_links where id = 'a0000000-0000-0000-0000-000000000005' returning 1)
select assert_eq(count(*), 0, 'cross-user DELETE: goal_activity_links (nested via goal_id)') from d;
with d as (delete from session_metric_values where id = 'a0000000-0000-0000-0000-000000000007' returning 1)
select assert_eq(count(*), 0, 'cross-user DELETE: session_metric_values (nested via session_id)') from d;
with d as (delete from recurrence_occurrences where recurrence_rule_id = 'a0000000-0000-0000-0000-000000000008' returning 1)
select assert_eq(count(*), 0, 'cross-user DELETE: recurrence_occurrences (nested via recurrence_rule_id)') from d;

\echo '--- Row 8: User B attempts INSERT claiming User A''s goal as the parent for a NEW nested row — expect every attempt to ERROR ---'
do $$
begin
  begin
    insert into milestones (id, goal_id, title, weight, is_binary, completed) values
      ('b0000000-0000-0000-0000-000000000001', 'a0000000-0000-0000-0000-000000000002', 'Forged', 1.0, true, false);
    raise exception 'ASSERTION FAILED (forged INSERT: milestones under User A''s goal_id should have been denied)';
  exception when insufficient_privilege then
    raise notice 'ok - forged INSERT denied: milestones under User A''s goal_id';
  end;
end $$;

do $$
begin
  begin
    insert into goal_activity_links (id, source_type, source_id, goal_id, metric_id)
      select 'b0000000-0000-0000-0000-000000000002', 'habit', gen_random_uuid(),
             'a0000000-0000-0000-0000-000000000002', id from metrics where key = 'count';
    raise exception 'ASSERTION FAILED (forged INSERT: goal_activity_links under User A''s goal_id should have been denied)';
  exception when insufficient_privilege then
    raise notice 'ok - forged INSERT denied: goal_activity_links under User A''s goal_id';
  end;
end $$;

do $$
begin
  begin
    insert into session_metric_values (id, session_id, metric_id, actual_value)
      select 'b0000000-0000-0000-0000-000000000003', 'a0000000-0000-0000-0000-000000000006', id, 1.0
      from metrics where key = 'count';
    raise exception 'ASSERTION FAILED (forged INSERT: session_metric_values under User A''s session_id should have been denied)';
  exception when insufficient_privilege then
    raise notice 'ok - forged INSERT denied: session_metric_values under User A''s session_id';
  end;
end $$;

do $$
begin
  begin
    insert into recurrence_occurrences (id, recurrence_rule_id, occurrence_date) values
      ('occurrence:a0000000-0000-0000-0000-000000000008:' || (current_date + 1), 'a0000000-0000-0000-0000-000000000008', current_date + 1);
    raise exception 'ASSERTION FAILED (forged INSERT: recurrence_occurrences under User A''s recurrence_rule_id should have been denied)';
  exception when insufficient_privilege then
    raise notice 'ok - forged INSERT denied: recurrence_occurrences under User A''s recurrence_rule_id';
  end;
end $$;

\echo '--- Row 8 (direct-owner variant): User B attempts INSERT claiming User A as the direct owner — expect ERROR ---'
do $$
begin
  begin
    insert into goals (id, user_id, title, type, progress_mode) values
      ('b0000000-0000-0000-0000-000000000099', '11111111-1111-1111-1111-111111111111', 'Forged', 'cumulative', 'metric');
    raise exception 'ASSERTION FAILED (forged INSERT: goals.user_id = User A should have been denied)';
  exception when insufficient_privilege then
    raise notice 'ok - forged INSERT denied: goals.user_id = User A';
  end;
end $$;

\echo '--- Ownership-consistency trigger (DEV-007 review, adversarial reviewer, HIGH): User A cannot link A''s OWN goal to User B''s activity ---'
reset role;
insert into activities (id, user_id, title) values
  ('b0000000-0000-0000-0000-000000000004', '22222222-2222-2222-2222-222222222222', 'B Secret Activity');
set role app_user;
select set_config('request.jwt.claim.sub', '11111111-1111-1111-1111-111111111111', false);
do $$
begin
  begin
    insert into goal_activity_links (id, source_type, source_id, goal_id, metric_id)
      select 'b0000000-0000-0000-0000-000000000005', 'activity', 'b0000000-0000-0000-0000-000000000004',
             'a0000000-0000-0000-0000-000000000002', id from metrics where key = 'distance_km';
    raise exception 'ASSERTION FAILED (User A linked own goal to User B''s activity — ownership trigger did not fire)';
  exception when others then
    if sqlerrm like 'goal_activity_links: source_id does not belong%' then
      raise notice 'ok - ownership-consistency trigger rejected a forged cross-user source_id';
    else
      raise;
    end if;
  end;
end $$;

\echo '--- Confirm the attempted attacks left User A''s data completely untouched ---'
select assert_eq(count(*), 1, 'User A''s goal title unchanged') from goals where id = 'a0000000-0000-0000-0000-000000000002' and title = 'Run 500km';
select assert_eq(count(*), 1, 'User A''s session still present') from sessions where id = 'a0000000-0000-0000-0000-000000000006';
select assert_eq(count(*), 1, 'User A''s milestone still present') from milestones where id = 'a0000000-0000-0000-0000-000000000003';
select assert_eq(count(*), 0, 'no forged goal exists') from goals where id = 'b0000000-0000-0000-0000-000000000099';
select assert_eq(count(*), 0, 'no forged goal_activity_link to User B''s activity exists') from goal_activity_links where id = 'b0000000-0000-0000-0000-000000000005';

\echo '--- Row 5: tombstone a session (User A), confirm User A still sees it, User B still cannot ---'
select set_config('request.jwt.claim.sub', '11111111-1111-1111-1111-111111111111', false);
update sessions set deleted_at = now() where id = 'a0000000-0000-0000-0000-000000000006';
select assert_eq(count(*), 1, 'owner still sees own tombstoned session') from sessions
  where id = 'a0000000-0000-0000-0000-000000000006' and deleted_at is not null;

select set_config('request.jwt.claim.sub', '22222222-2222-2222-2222-222222222222', false);
select assert_eq(count(*), 0, 'other user still cannot see the tombstoned session') from sessions
  where id = 'a0000000-0000-0000-0000-000000000006';

\echo '--- Metrics: readable by anyone, not writable by an ordinary authenticated user ---'
select assert_eq(count(*), 6, 'metrics catalog is readable') from metrics;
do $$
begin
  begin
    insert into metrics (id, key, canonical_unit) values ('c0000000-0000-0000-0000-000000000001', 'bogus', 'x');
    raise exception 'ASSERTION FAILED (metrics INSERT by an ordinary role should have been denied)';
  exception when insufficient_privilege then
    raise notice 'ok - metrics write denied for an ordinary authenticated role';
  end;
end $$;

\echo '--- True anon case: reconnect fresh (no JWT claim set at all) — everything owner-scoped denies, the catalog still reads ---'
\connect - -
set role app_user;
select assert_eq((select (auth.uid() is null)::int), 1, 'auth.uid() is NULL with no JWT claim set at all');
select assert_eq(count(*), 0, 'true-anon: goals denied') from goals;
select assert_eq(count(*), 0, 'true-anon: sessions denied') from sessions;
select assert_eq(count(*), 6, 'true-anon: metrics catalog still readable') from metrics;

\echo '--- All assertions passed ---'
