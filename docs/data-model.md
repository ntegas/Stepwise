# Data Model

Postgres schema (Supabase). Every table carries `user_id uuid references auth.users` and Row Level Security restricting rows to their owner, since this is a single-user-per-account product with no sharing/networking (§46).

## Entities

### `goals`
Answers "what do I want to achieve" (§9–11).

| column | type | notes |
|---|---|---|
| id | uuid pk | |
| user_id | uuid | |
| title | text | |
| type | enum | `cumulative \| quantity \| distance \| financial \| target \| frequency \| percentage` (§10) — each type has its own progress-calculation logic |
| target_value | numeric | |
| current_value | numeric | derived — written only by the cascade trigger, never directly by the client |
| unit | text | h, km, books, €, kg, % ... |
| deadline | date, nullable | |
| priority | int, nullable | |
| project_id | uuid, nullable fk → projects | optional (§13) |
| category | text, nullable | |
| description | text, nullable | |

### `milestones`
Stages of a complex Goal (§12). Optional — simple Goals have none.

id, goal_id (fk), title, sort_order, percent/status.

### `projects`
Groups Tasks under a complex Goal (§13). Optional.

id, user_id, goal_id (nullable fk), title.

### `activities`
Ongoing activity, independent of any single Goal (§15–16).

| column | type | notes |
|---|---|---|
| id | uuid pk | |
| user_id | uuid | |
| title | text | |
| icon | text | |
| default_goal_id | uuid, nullable fk → goals | the "remembered link" (§18) — once set, every completed session for this Activity auto-applies to this Goal without asking again |
| recurrence_rule | text, nullable | e.g. iCal RRULE for §21 |

### `tasks`
A concrete one-off action (§14).

id, user_id, goal_id (nullable), project_id (nullable), title, date, time, deadline, duration, priority, reminder, recurrence, metric, planned_result, actual_result, status. All fields except title are optional (§14, §40).

### `habits`
Recurring small commitment (§22–23).

id, user_id, activity_id (nullable fk), goal_id (nullable fk), title, target_per_period, unit, recurrence.

### `sessions`
One real occurrence of doing something — created by every completion, partial, timer stop, or manual entry (§17, §24).

| column | type | notes |
|---|---|---|
| id | uuid pk | |
| user_id | uuid | |
| source_type | enum | `task \| activity \| habit` |
| source_id | uuid | fk into the matching table |
| date | date | |
| planned_amount | numeric, nullable | minutes/km/€/etc, matches the source's unit |
| actual_amount | numeric, nullable | |
| status | enum | `done \| partial \| missed \| rescheduled \| in_progress \| cancelled` (§6) |
| notes | text, nullable | |

### `progress_events`
The single fan-out record required by §19/§45 — **one write here per user action**, everything else is derived from it.

id, user_id, session_id (fk), goal_id (nullable — resolved via the session's source → its `default_goal_id`/`goal_id`), amount, created_at.

A Postgres trigger `on sessions insert/update` is responsible for:
1. Writing/updating the matching `progress_events` row.
2. Recomputing `goals.current_value` for the linked goal (per its `type` logic).
3. Nothing else is stored redundantly — weekly/monthly/yearly/lifetime numbers, execution rate, pace, and forecast are all **views**, not columns, computed from `sessions`/`progress_events` on read (see below). This is what makes "editing a past Actual recalculates everything downstream" (§29) true by construction rather than by a cache-invalidation step we'd have to maintain.

## Derived views (no duplicated app logic, §45)

- `activity_stats_weekly` / `_monthly` / `_yearly` / `_lifetime` — per-activity totals, session count, avg session, avg/week (§27–28).
- `goal_execution_rate` — planned vs actual %, status breakdown counts (§26).
- `goal_pace` — remaining/time-left vs current rate, ahead/behind (§32).
- `goal_forecast` — projected completion date from current pace vs deadline (§33).

These views are the single source both the web client and any future Android client read — neither client re-implements the math.

## Relationships at a glance

```
goals 1─* milestones
goals 1─* projects (optional)
projects 1─* tasks
goals 1─* tasks (optional, direct)
activities *─1 goals (default_goal_id, optional)
habits *─1 activities (optional), habits *─1 goals (optional)
tasks/activities/habits 1─* sessions (source_type + source_id)
sessions 1─1 progress_events
```
