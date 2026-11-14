-- Adversarial test for DEV-008 / /supabase/migrations/0003_sync_engine.sql: the
-- enforce_sync_metadata() trigger and conflict_copies RLS. Genuinely executed against real
-- Postgres (see /supabase/tests/README.md for how to run this) — not a description of intended
-- behavior.
--
-- IMPORTANT — same AI_CODE_SECURITY.md caveat as rls_adversarial_test.sql applies, and more
-- pointedly here: this was written by the same AI that wrote 0003_sync_engine.sql, and the
-- DEV-008 security reviewer specifically flagged this migration (it governs every syncable
-- table's version/updated_at, plus the one append-only record conflict resolution relies on) as
-- S3-adjacent rather than the task's administrator-assigned S2, given how much of the conflict
-- model's integrity rests on it. A clean run here is real evidence the trigger/policies behave as
-- documented under the cases below — it is not independent review, and per that reviewer's own
-- finding, this gap (no permanent test existed before this file) was itself a HIGH finding fixed
-- by writing it; see /PROJECT_STATE.md's DEV-008 findings for the classification note.
--
-- Every check is a machine-checkable assertion (assert_eq, or a DO block expecting a specific
-- error), same pattern as rls_adversarial_test.sql. Run after 0002_canonical_schema.sql AND
-- 0003_sync_engine.sql, in the same disposable database (see README's "Running it" section).

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
select set_config('request.jwt.claim.sub', '11111111-1111-1111-1111-111111111111', false);

\echo '--- enforce_sync_metadata(): a forged version/updated_at on INSERT is discarded, server assigns version = 1 ---'
insert into goals (id, user_id, title, type, progress_mode, version, updated_at) values
  ('c0000000-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'Run 500km',
   'distance', 'metric', 999, '2000-01-01');

select assert_eq(version::bigint, 1, 'forged INSERT version discarded, server assigned 1')
  from goals where id = 'c0000000-0000-0000-0000-000000000001';
select assert_eq((updated_at > now() - interval '1 minute')::int::bigint, 1,
  'forged INSERT updated_at discarded, server assigned now()')
  from goals where id = 'c0000000-0000-0000-0000-000000000001';

\echo '--- enforce_sync_metadata(): a forged version/updated_at on UPDATE is discarded, server assigns old.version + 1 ---'
update goals set title = 'Run 600km', version = 999999, updated_at = '2000-01-01'
  where id = 'c0000000-0000-0000-0000-000000000001';

select assert_eq(version::bigint, 2, 'forged UPDATE version discarded, server assigned old.version + 1')
  from goals where id = 'c0000000-0000-0000-0000-000000000001';
select assert_eq((updated_at > now() - interval '1 minute')::int::bigint, 1,
  'forged UPDATE updated_at discarded, server assigned now()')
  from goals where id = 'c0000000-0000-0000-0000-000000000001';

\echo '--- Same trigger on a progress-affecting table (sessions) - the exact column pair /core/sync''s CAS pattern depends on ---'
insert into activities (id, user_id, title) values
  ('c0000000-0000-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111', 'Running');

insert into sessions (id, user_id, source_type, source_id, date, status, client_event_id, version, updated_at) values
  ('c0000000-0000-0000-0000-000000000003', '11111111-1111-1111-1111-111111111111', 'activity',
   'c0000000-0000-0000-0000-000000000002', current_date, 'done', 'evt-c-1', 500, '2000-01-01');

select assert_eq(version::bigint, 1, 'forged sessions INSERT version discarded, server assigned 1')
  from sessions where id = 'c0000000-0000-0000-0000-000000000003';

\echo '--- This is what makes the CAS filter trustworthy: forging a higher version in the body cannot beat WHERE version = :expected ---'
-- A stale write believes the row is still at version 1 and simultaneously tries to claim its own
-- write landed at a much higher version. The CAS filter (WHERE id = ... AND version = 1) still
-- matches - that part of the filter is honest - but the forged `version` value it sent is
-- discarded by the trigger regardless, so the row ends up at 2 (old.version + 1), never at the
-- client's forged value.
update sessions set notes = 'stale write', version = 777 where id = 'c0000000-0000-0000-0000-000000000003' and version = 1;
select assert_eq(version::bigint, 2, 'CAS UPDATE still gets a server-assigned version, not the forged value')
  from sessions where id = 'c0000000-0000-0000-0000-000000000003';

\echo '--- conflict_copies: owner can insert a conflict copy for themselves ---'
insert into conflict_copies (id, user_id, table_name, entity_id, payload, server_version_at_conflict) values
  ('d0000000-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'sessions',
   'c0000000-0000-0000-0000-000000000003', '{"status":"done"}'::jsonb, 2);

select assert_eq(count(*), 1, 'owner-inserted conflict_copies row exists')
  from conflict_copies where id = 'd0000000-0000-0000-0000-000000000001';

\echo '--- conflict_copies: table_name CHECK constraint rejects a non-progress-affecting table name ---'
do $$
begin
  begin
    insert into conflict_copies (id, user_id, table_name, entity_id, payload, server_version_at_conflict) values
      ('d0000000-0000-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111', 'goals',
       'c0000000-0000-0000-0000-000000000001', '{}'::jsonb, 1);
    raise exception 'ASSERTION FAILED (conflict_copies.table_name = ''goals'' should have violated the CHECK constraint)';
  exception when check_violation then
    raise notice 'ok - conflict_copies rejects a table_name outside the progress-affecting set';
  end;
end $$;

\echo '--- conflict_copies: a client cannot insert a conflict copy claiming a different user as the owner (RLS INSERT WITH CHECK) ---'
do $$
begin
  begin
    insert into conflict_copies (id, user_id, table_name, entity_id, payload, server_version_at_conflict) values
      ('d0000000-0000-0000-0000-000000000003', '22222222-2222-2222-2222-222222222222', 'sessions',
       'c0000000-0000-0000-0000-000000000003', '{"status":"forged"}'::jsonb, 2);
    raise exception 'ASSERTION FAILED (conflict_copies INSERT claiming a different user_id should have been denied)';
  exception when insufficient_privilege then
    raise notice 'ok - conflict_copies INSERT denied when user_id does not match auth.uid()';
  end;
end $$;

\echo '--- conflict_copies: cross-user SELECT denies (User B cannot read User A''s conflict copy) ---'
select set_config('request.jwt.claim.sub', '22222222-2222-2222-2222-222222222222', false);
select assert_eq(count(*), 0, 'cross-user SELECT: conflict_copies')
  from conflict_copies where id = 'd0000000-0000-0000-0000-000000000001';

\echo '--- conflict_copies: append-only - no UPDATE/DELETE policy exists, so even the OWNER cannot modify or remove a conflict copy ---'
select set_config('request.jwt.claim.sub', '11111111-1111-1111-1111-111111111111', false);

with u as (
  update conflict_copies set server_version_at_conflict = 999
    where id = 'd0000000-0000-0000-0000-000000000001' returning 1
)
select assert_eq(count(*), 0, 'owner UPDATE on conflict_copies affects 0 rows (append-only, no UPDATE policy)') from u;

with d as (
  delete from conflict_copies where id = 'd0000000-0000-0000-0000-000000000001' returning 1
)
select assert_eq(count(*), 0, 'owner DELETE on conflict_copies affects 0 rows (append-only, no DELETE policy)') from d;

select assert_eq(count(*), 1, 'conflict_copies row still present and unchanged after the attempted owner UPDATE/DELETE')
  from conflict_copies where id = 'd0000000-0000-0000-0000-000000000001' and server_version_at_conflict = 2;

\echo '--- All assertions passed ---'
