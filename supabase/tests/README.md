# RLS test harness (local Postgres, no Supabase project required)

`rls_adversarial_test.sql` exercises real Postgres Row Level Security — not a description of
intended behavior, an actually-executed adversarial test — against the schema in
`/supabase/migrations/0002_canonical_schema.sql`. It needs a Postgres 13+ server (any local
install works; this was last run against Postgres 16) and a small shim reproducing just enough
of Supabase's `auth` schema for RLS to evaluate the same way it will against a real project:

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
Postgrest populates from the caller's JWT `sub` claim in a real deployment; the test script
fakes that same setting directly with `set_config('request.jwt.claim.sub', '<uuid>', false)` to
simulate "request arrives authenticated as this user" without needing a running Supabase
instance or a real JWT.

## Running it

```bash
createdb stepwise_rls_test
psql -d stepwise_rls_test -c "create role app_user login;"   # skip if it already exists
psql -d stepwise_rls_test -f auth_shim.sql                   # the snippet above, saved to a file
psql -d stepwise_rls_test -v ON_ERROR_STOP=1 -f ../migrations/0002_canonical_schema.sql
psql -d stepwise_rls_test -f rls_adversarial_test.sql
```

`app_user` must be a plain login role with **no** superuser/BYPASSRLS attribute and must not own
any table in the schema — RLS is bypassed for both, so running this as the migration role would
silently test nothing. The test script itself grants `app_user` the table-level privileges it
needs (RLS restricts *which rows* a grant can touch, not whether the grant exists at all) and
then does `set role app_user;` for the remainder of the script.

## What it checks, and what it doesn't

Covers `/docs/security/SECURITY_TEST_MATRIX.md` rows 1 (cross-user SELECT), 2 (cross-user
UPDATE/DELETE), 5 (tombstone visibility: owner still sees it, another user still doesn't), and 8
(a client-claimed `user_id` on INSERT is rejected outright, not silently reassigned) across both
directly-owned tables and the nested-ownership ones (`milestones`, `goal_activity_links`,
`session_metric_values`, `recurrence_occurrences`), plus the `metrics` global-catalog policy
(readable by anyone, writable by no ordinary authenticated role), the true-anonymous case (no
JWT claim set at all), and the ownership-consistency triggers that close a confirmed cross-user
forgery path through a polymorphic reference (DEV-007 review, adversarial reviewer — see
`0002_canonical_schema.sql`'s own header).

Every check is a machine-checkable assertion (`assert_eq`, or a `DO` block expecting a specific
error) that raises an exception on mismatch — not a printed row count a human has to eyeball. A
clean run means `psql`'s exit code is 0 and the script's last line of output is "All assertions
passed"; any regression fails loudly and stops the script immediately.

It does **not** test: Supabase Auth itself (token issuance/refresh/revocation — this harness has
no real Auth server, just a static `auth.users` row and a faked claim), Edge Functions / the
service role, or anything above the database layer. And per
`/docs/security/AI_CODE_SECURITY.md`, a clean run of this file is real evidence that the policies
behave as documented under the cases tested here — it is not, by itself, the independent review
S3 work requires, since the same AI wrote both the schema and this test.
