# RLS test harness (local Postgres, no Supabase project required)

Three test scripts live here, each exercising real Postgres behavior against this schema — not a
description of intended behavior, actually-executed tests:

- `rls_adversarial_test.sql` — against `/supabase/migrations/0002_canonical_schema.sql` (DEV-007).
- `sync_engine_adversarial_test.sql` — against `/supabase/migrations/0003_sync_engine.sql`
  (DEV-008): the `enforce_sync_metadata()` trigger and `conflict_copies` RLS/CHECK constraint.
  Run this one after applying both 0002 and 0003 to the same database.
- `calculation_views_manual_check.sql` — against `/supabase/migrations/0004_calculation_views.sql`
  (DEV-019): seeds one representative row per `GoalProgressMode`/`ExecutionStatus` branch and
  asserts `goal_progress`/`execution_rate`/`activity_stats_lifetime`'s output against
  `/CALCULATION_ENGINE.md`'s own formulas by hand. Run after applying 0002, 0003, and 0004 to the
  same database. Not an adversarial/RLS test (no cross-user attempt here) — a correctness check
  for the calculation formulas themselves, the Kotlin/SQL parity half of DEV-019's own
  completion criteria.

Both need a Postgres 13+ server (any local install works; last run against Postgres 16) and
`auth_shim.sql`, a small checked-in shim reproducing just enough of Supabase's `auth` schema for
RLS to evaluate the same way it will against a real project:

```sql
create schema if not exists auth;
create table auth.users (id uuid primary key, email text);

create or replace function auth.uid() returns uuid
language sql stable
as $$
  select current_setting('request.jwt.claim.sub', true)::uuid
$$;
```

This mirrors Supabase's own `auth.uid()` — it reads a per-request Postgres session setting that
Postgrest populates from the caller's JWT `sub` claim in a real deployment; the test scripts fake
that same setting directly with `set_config('request.jwt.claim.sub', '<uuid>', false)` to
simulate "request arrives authenticated as this user" without needing a running Supabase
instance or a real JWT.

## Running it

```bash
createdb stepwise_rls_test
psql -d stepwise_rls_test -c "create role app_user login;"   # skip if it already exists
psql -d stepwise_rls_test -f auth_shim.sql
psql -d stepwise_rls_test -v ON_ERROR_STOP=1 -f ../migrations/0002_canonical_schema.sql
psql -d stepwise_rls_test -f rls_adversarial_test.sql

# Sync engine (DEV-008) — same database, apply 0003 on top first:
psql -d stepwise_rls_test -v ON_ERROR_STOP=1 -f ../migrations/0003_sync_engine.sql
psql -d stepwise_rls_test -f sync_engine_adversarial_test.sql

# Calculation views (DEV-019) — same database, apply 0004 on top too. Runs as the migration
# role, not app_user - this script checks formula correctness, not RLS, and seeds its own user.
psql -d stepwise_rls_test -v ON_ERROR_STOP=1 -f ../migrations/0004_calculation_views.sql
psql -d stepwise_rls_test -v ON_ERROR_STOP=1 -f calculation_views_manual_check.sql
```

`app_user` must be a plain login role with **no** superuser/BYPASSRLS attribute and must not own
any table in the schema — RLS is bypassed for both, so running this as the migration role would
silently test nothing. Each test script grants `app_user` the table-level privileges it needs
(RLS restricts *which rows* a grant can touch, not whether the grant exists at all) and then does
`set role app_user;` for the remainder of the script.

## What `rls_adversarial_test.sql` checks, and what it doesn't

Covers `/docs/security/SECURITY_TEST_MATRIX.md` rows 1 (cross-user SELECT), 2 (cross-user
UPDATE/DELETE), 5 (tombstone visibility: owner still sees it, another user still doesn't), and 8
(a client-claimed `user_id` on INSERT is rejected outright, not silently reassigned) across both
directly-owned tables and the nested-ownership ones (`milestones`, `goal_activity_links`,
`session_metric_values`, `recurrence_occurrences`), plus the `metrics` global-catalog policy
(readable by anyone, writable by no ordinary authenticated role), the true-anonymous case (no
JWT claim set at all), and the ownership-consistency triggers that close a confirmed cross-user
forgery path through a polymorphic reference (DEV-007 review, adversarial reviewer — see
`0002_canonical_schema.sql`'s own header).

## What `sync_engine_adversarial_test.sql` checks, and what it doesn't

Covers: `enforce_sync_metadata()` discards a client-forged `version`/`updated_at` on both INSERT
and UPDATE, on a metadata-tier table (`goals`) and a progress-affecting one (`sessions`) — the
specific property the compare-and-swap conflict pattern in `/ARCHITECTURE.md` §9.1/ADR-006
depends on (a stale client can't beat the CAS filter by also forging a higher version in the
body); `conflict_copies`' `table_name` CHECK constraint rejects a table outside the
progress-affecting set; its RLS INSERT policy rejects a claimed `user_id` that doesn't match
`auth.uid()`; cross-user SELECT denies; and, since no UPDATE/DELETE policy exists at all, even
the row's own owner cannot modify or remove a conflict copy once written (append-only, RLS's
default-deny with no matching policy, not a gap). It does not re-test anything
`rls_adversarial_test.sql` already covers (ordinary cross-user SELECT/UPDATE/DELETE on the
pre-existing tables) and does not exercise every one of the 13 triggered tables individually —
one metadata-tier and one progress-affecting table stand in for the shared trigger logic, since
the trigger function itself (not the per-table wiring) is what the test targets.

Every check in both scripts is a machine-checkable assertion (`assert_eq`, or a `DO` block
expecting a specific error) that raises an exception on mismatch — not a printed row count a
human has to eyeball. A clean run means `psql`'s exit code is 0 and the script's last line of
output is "All assertions passed"; any regression fails loudly and stops the script immediately.

Neither script tests: Supabase Auth itself (token issuance/refresh/revocation — this harness has
no real Auth server, just a static `auth.users` row and a faked claim), Edge Functions / the
service role, or anything above the database layer. And per
`/docs/security/AI_CODE_SECURITY.md`, a clean run of either file is real evidence that the
policies behave as documented under the cases tested here — it is not, by itself, the
independent review S3 work requires, since the same AI wrote both the migrations and these
tests. The DEV-008 security reviewer additionally flagged `0003_sync_engine.sql` specifically as
S3-adjacent despite the task's overall S2 classification, given how much of the conflict model's
integrity rests on this one trigger — see `/PROJECT_STATE.md`'s DEV-008 findings.
