# Data Model

Reconciled against the Master Product Concept (`/CLAUDE.md`, §1–77). This supersedes the Phase 0 data model: the biggest change is that **no entity stores its own progress as an editable column** (§62) — Goal progress, Life Area aggregates, and every period rollup are views computed from `sessions` / `session_metric_values` / `progress_events`, never a cached number a trigger writes and something else could desync from. The Phase 0 migration (`0001_init.sql`) predates this correction and will be superseded by a Phase 2 migration built against this document — it is not reused as-is.

All tables carry `user_id uuid references auth.users` and RLS restricting rows to their owner (single-tenant-per-account; no sharing/networking, §69).

## Entity list (§64)

`User · Vision · LifeArea · Goal · Milestone · Project · Task · Activity · Habit · Schedule/RecurrenceRule · Session · SessionMetricValue · ProgressEvent · Metric · GoalActivityLink · Skill · Reminder · Review · Insight`

## Strategic layer

### `visions`
Free text, optional (§5). `id, user_id, life_area_id (nullable — null = whole-life vision), content, updated_at`.

### `life_areas`
User-defined, fully customizable (§6). `id, user_id, name, icon, category, sort_order, archived`.

### `goals`
`id, user_id, life_area_id (nullable), project_id (nullable), title, type (cumulative|quantity|distance|financial|target|frequency|percentage — §11), target_value, unit, deadline, priority, category, description, status (active|paused|completed|archived — §13, independent of any session/task status), created_at`.

`current_value` is **not a column** — see [Goal progress is a view](#goal-progress-is-a-view-not-a-column) below.

### `milestones`
Optional stages of a Goal (§14). `id, goal_id, title, sort_order, percent_or_status`.

### `projects`
Optional grouping of Tasks under a Goal (§15). `id, user_id, goal_id (nullable), life_area_id (nullable), title`.

## Doing layer

### `activities`
Ongoing activity, independent of any Goal (§19–20). `id, user_id, life_area_id (nullable), title, icon, archived`. No `default_goal_id` column — Activity↔Goal links are many-to-many (§21), modeled by `goal_activity_links` below, not a single FK.

### `habits`
Regularity rule, distinct from Activity (§31). `id, user_id, activity_id (nullable — the "what"), title, target_value, metric_id, archived`. A Habit's own Goal links go through `goal_activity_links` too (source_type = `habit`), the same mechanism Activities use — one fan-out path, not two.

### `tasks`
`id, user_id, goal_id (nullable), project_id (nullable), life_area_id (nullable), title, date (nullable — null = Inbox/Unscheduled, §17), time, deadline, duration, priority, reminder_id (nullable), metric_id (nullable), planned_result, actual_result, goal_impact_score (0–100, §50), status (done|partial|missed|rescheduled|in_progress|cancelled), archived`. Almost every field beyond `title` is optional, per §16.

### `schedules` (unified Schedule/RecurrenceRule, §33)
One recurrence engine for Task, Activity, and Habit — not three. `id, owner_type (task|activity|habit), owner_id, rule (RRULE-style: days of week, time, interval), starts_on, ends_on (nullable)`. Occurrences (what shows up on a given day in Plan/Calendar, §35) are computed from this rule at read time, not pre-materialized as rows.

## Metrics, Sessions, and the multi-goal fan-out

This is the mechanically trickiest part of the concept (§21, §23, §60–62) — worth walking through end to end.

### `metrics`
A catalog of measurable dimensions, each with a canonical storage unit: `duration_minutes, distance_km, count, pages, amount_minor_units (money, stored as integer minor units + currency), weight_kg`, etc. `id, key, canonical_unit`. Display conversion (km↔mi, kg↔lb, currency symbol) happens in the client from the user's Settings unit preference (§58) — the stored value is always the canonical unit, so analytics never has to guess which unit a historical row used.

### `goal_activity_links`
The mechanism behind §21 ("one Activity can update multiple Goals, each via a different metric") and behind Habit→Goal links (§22): `id, source_type (activity|habit), source_id, goal_id, metric_id`. Example: Activity **Running** has two rows here — (Running, Goal "Run 500km", metric distance_km) and (Running, Goal "100 Hours Running", metric duration_minutes). One Session against Running fans out to both Goals automatically, each reading the metric it cares about.

### `sessions`
The record of one real occurrence (§22). `id, user_id, source_type (task|activity|habit), source_id, date, status (§25), client_event_id (unique per user — the idempotency key, §60: a duplicate submission from a flaky connection with the same client_event_id upserts instead of double-counting), notes`.

### `session_metric_values`
A Session can carry more than one metric (§23 — Running has both duration and distance). `id, session_id, metric_id, planned_value (nullable), actual_value`.

### `progress_events`
The single fan-out record (§24, §62). Generated (by a DB trigger on `session_metric_values` insert/update) as: for each `session_metric_values` row, join `goal_activity_links` on `(source_type, source_id, metric_id)` to find every Goal that cares about that metric, and write one `progress_events` row per match: `id, user_id, session_id, session_metric_value_id, goal_id, metric_id, amount, created_at`. A Task completion without any Activity/Goal link produces zero progress_events — that's fine, not every Task feeds a Goal.

### Goal progress is a view, not a column

This is the §62 correction from Phase 0: `goals.current_value` must not be a stored field a trigger increments, because a stored running total is exactly the "independently maintained number that can desync" §62 forbids. Instead, a view `goal_progress` computes it live:
- `cumulative | quantity | distance | financial | frequency` → `SUM(progress_events.amount)` for that goal, all time.
- `target` (e.g. weight) → the `actual_value` from the most recent `session_metric_values` row linked to that goal (a moving-toward-target reading, not a sum).
- `percentage` → same "most recent reading" logic, or derived from linked Milestones' completion if the Goal is a project-style rollup.

Editing or deleting a `session_metric_values` row changes `progress_events` (via the same trigger, on update/delete) which changes what the view aggregates — so §61 ("edit/delete recomputation") falls out of the architecture for free instead of being a separate recompute step someone has to remember to call.

### Other rollup views

`activity_stats_weekly/monthly/yearly/lifetime`, `life_area_balance` (Attention / Execution / Goal Progress / Trend, kept as **separate** figures per §63, never blended into one score), `goal_pace`, `goal_forecast`, `execution_rate` — all computed from `sessions` / `session_metric_values` / `progress_events`. Every client (Android now, iOS and the parked web app later) reads these same views, so none of them re-implement the math.

## Supporting entities

- **`skills`** — `id, user_id, title`, plus `skill_sources` (`skill_id, activity_id | goal_id`) mapping which Activities/Goals feed it. Skill "level" is a read-time aggregation over the linked Activities' stats — never a manually incremented value (§51).
- **`reminders`** — `id, user_id, owner_type (task|habit), owner_id, fires_at | relative_rule, channel`.
- **`reviews`** — Weekly Review (§53), auto-generated: `id, user_id, period_start, period_end, summary (jsonb: goals progressed, planned/actual/execution, strongest/weakest life area)`, read-only once generated.
- **`insights`** — Behavioral analytics/recommendations (§54–55): `id, user_id, kind, message, related_goal_id (nullable), status (new|applied|ignored), created_at`.

## Inbox, Overdue, Archive (§17–18, §57)

No separate tables — these are *views over `tasks`*, not distinct entities: Inbox = tasks where `date IS NULL AND NOT archived`; Overdue = tasks where `date < today AND status NOT IN (done, cancelled)`; Archive = `archived = true` on Goals/Projects/Activities/Habits. Keeping these as filters rather than separate tables avoids a second place a task's state could drift out of sync with its row in `tasks`.

## Sync metadata columns (applies to every synced table)

Every table that syncs between device and backend (i.e. everything above except pure read-time views) carries the same sync metadata columns, so the sync engine (`/ARCHITECTURE.md` §9, ADR-006) has one uniform shape to reason about rather than a per-table special case:

- `id: UUID` — client-generated, stable across devices; this is what makes independent-Session convergence and deterministic occurrence identity (`/ARCHITECTURE.md` §9, ADR-005) work without a server round-trip to get an ID first.
- `updated_at` — last-write timestamp, used by the low-risk-metadata last-write-wins path.
- `version` — monotonically incremented on every write, used by the version-checked conflict path for progress-affecting entities (Sessions, materialized occurrences) so a stale write is detected rather than silently overwriting newer data.
- `deleted_at` — tombstone marker; a delete sets this rather than removing the row, so a stale offline device's edit to an already-deleted record doesn't resurrect it (`/ARCHITECTURE.md` §9, ADR-006).
- `sync_status` — local-only (not synced itself): tracks whether a row has pending outbound changes, is confirmed synced, or is in conflict, for the offline-first outbox (`/ARCHITECTURE.md` §9.1).

This list originated in the now-superseded `/ARCHITECTURE.md` §29 ("Recommended Implementation Sequence") and is preserved here as the canonical, single location for it — §29 is superseded by `/ROADMAP.md`'s DEV-004 (Canonical Data Model) and DEV-008 (Sync Engine), which is where these columns get their concrete Room/Postgres schema.

## Relationships at a glance

```
users 1─* visions, life_areas, goals, activities, habits, tasks, projects
life_areas 1─* goals, tasks, projects   (all optional)
goals 1─* milestones
goals 1─* projects (optional) ─ 1─* tasks
activities/habits *─* goals   via goal_activity_links (+ metric_id)
tasks/activities/habits 1─* sessions   (source_type + source_id)
sessions 1─* session_metric_values
session_metric_values 1─* progress_events   (fan-out per linked goal)
skills *─* activities/goals   via skill_sources
```
