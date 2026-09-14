# Functional Analysis

Breakdown of the Master Product Concept (`/PRODUCT_CANON.md`, §1–77) into implementation modules. Supersedes the Phase 0 version, which was written against the earlier 48-section concept.

## 1. Strategic layer — Vision, Life Areas, Life Plan, Life Balance (§5–8)

- **My Vision** (§5): free-text, optional, whole-life or per-Life-Area. Never a required onboarding step.
- **Life Areas** (§6): user-defined groupings for Goals (Health, Finance, Career, …) — fully customizable (create/rename/reorder/icon), not a fixed enum.
- **Life Plan** (§7): strategic view of Vision + Life Areas + their Goals. A review screen, not a daily one.
- **Life Balance** (§8): automatically derived from real data (Goals/Activities/Sessions/Tasks/Habits) — never a manually-set percentage. Reports four *separate* figures per Life Area — Attention, Execution, Goal Progress, Trend (§63) — deliberately not blended into one composite score, since that would misrepresent what's actually true about each dimension.

## 2. Today & daily execution (§9, 25–29, 36–39, 43, 50)

- **Today** (§9): Focus item + today's list + a completion counter. No charts, no full stats, no Skills, no Milestones, no Life Balance detail — those live elsewhere.
- **One-tap complete** (§26, §43): ✓ assumes `actual = planned`, no intermediate form. Partial/Missed/Reschedule/Cancelled reachable via swipe/long-press (§25), not competing buttons.
- **Six execution statuses** (§25): Done, Partial, Missed, Rescheduled, In Progress, Cancelled — full data model, progressive UI.
- **Partial completion** (§27–28): a partial amount is stored and counted as-is, never rounded to zero. Planned and Actual always stored separately.
- **Execution calculation** (§29): Done=100%, Partial=actual/planned, Missed=0%, Cancelled excluded from the denominator, Rescheduled not counted as Missed on its original date. Non-numeric tasks: Done=100%, Partial=a user-set approximate percentage.
- **Quick Add + Natural Language Quick Add + Progressive Disclosure + Smart Defaults** (§36–39): every creation flow has a 1–2 field fast path; "Boxing tomorrow 19:00 for 1h" parses into structured fields for confirmation, as an *additional* input method, not a replacement for forms; advanced fields (recurrence, goal link, metric, reminder…) live behind "More options"; once a link/recurrence is set once, it's never asked again.
- **Goal Impact ranking** (§50): a 0–100 Impact Score per Task drives which "High/Medium/Low" label shows and lets Today surface what actually moves a Goal forward, not just what's scheduled.

## 3. Goals & lifecycle (§10–15)

- **Goal types** (§11), each with distinct math: Cumulative, Quantity, Distance, Financial, Target Value (moves toward a target, doesn't accumulate), Frequency, Percentage/Project.
- **Simple creation, deep options** (§12): name + target + deadline is a complete Goal; Life Area, milestones, priority, project, metric, schedule, category, description live under "More options".
- **Goal status / lifecycle** (§13): Active, Paused, Completed, Archived — a separate dimension from any Task/Session execution status. Paused is a real, non-punitive state ("temporarily paused" ≠ failed/cancelled).
- **Milestones** (§14): optional stages for complex Goals; simple Goals need none.
- **Projects** (§15): optional Goal→Project→Task grouping for complex Goals; never forced on a simple Goal.

## 4. Tasks, Inbox, Overdue (§16–18)

- **Tasks** (§16): almost every field beyond title is optional; a quick task is title + date.
- **Inbox / Unscheduled** (§17): a task with no date lands here — "capture now, organize later" — not lost, not forced into a schedule immediately.
- **Overdue** (§18): surfaced as its own block on Today (Complete/Reschedule/Skip/Edit), but never auto-dumped onto today's list — that would flood Today and violate its "not overloaded" requirement (§9).

## 5. Activities, multi-goal linking, Habits, Recurrence, Timer (§19–24, 30–34)

- **Activity ≠ Goal** (§19–20): an Activity outlives any single Goal; completing a Goal never resets or deletes the Activity's history — a new Goal can be attached to the same Activity later.
- **Activity ↔ multiple Goals, per metric** (§21, §23): one Activity can feed several Goals simultaneously, each via a different metric of the same Session (a Running session's distance feeds a distance Goal, its duration feeds an hours Goal) — modeled by `goal_activity_links` in `/DATA_MODEL.md`.
- **Habit ≠ Activity** (§31): Habit is a regularity rule ("how often"), Activity is the thing itself ("what"); completing a Habit creates a Session the same way an Activity does. Habits support Partial the same way (§32).
- **Unified recurrence** (§30, §33): one Schedule/RecurrenceRule mechanism serves Task, Activity, and Habit — not three separate scheduling systems to keep in sync.
- **Timer** (§34): optional start/stop convenience; manual duration entry always available as the alternative.
- **One action → one Progress Event** (§24): the mandatory no-double-entry rule — a single ✓ fans out through the mechanism above to every linked Goal, Activity total, and statistic. This is the same rule as Phase 0's §19, now precisely mechanized via `goal_activity_links` + `progress_events`.

## 6. Plan / Calendar (§35)

Day/Week/Month/Year views showing Tasks, Activities, Habits, deadlines, milestones, events; recurring items appear automatically from the unified Schedule/RecurrenceRule, never re-created manually.

## 7. Progress & analytics (§40–49, §72)

- **Progress hub** (§40–41): Week/Month/Year/All-Time totals; Planned vs Actual with execution % and status-count breakdown (§42).
- **Per-entity stats** (§43–44): works uniformly across Activities, Goals, Habits, and Life Areas — session count, average session, average/week, lifetime totals.
- **History** (§45): editable; an edit or delete recomputes every downstream number automatically (architecture guarantee, not a manual step — see `/DATA_MODEL.md`).
- **Graphs** (§46): trend, cumulative progress, calendar heatmap, consistency — live only in Progress, never crowding Today.
- **Period comparison** (§47): e.g. "↑22% vs last month".
- **Pace** (§48): required vs current rate toward a deadline → Ahead/Behind.
- **Forecast** (§49): projected completion date vs deadline, explicitly framed as a projection from current data, not a promise.
- **Drill-down** (§72): Progress → Life Area → Activity → Session History, plus an Overall view — not just a flat list of Activities.

## 8. Skills (§51)

Derived passively from linked Activities'/Goals' accumulated data — never a manual "+2% Skill" action. A profile/analytical feature, not a daily obligation.

## 9. History, Personal Progress History, Weekly Review (§45, §52–53)

- **Personal Progress History** (§52): yearly and lifetime summaries — the product's long-term memory of the person's development.
- **Weekly Review** (§53): auto-generated summary (goals progressed, planned/actual/execution, strongest/weakest Life Area, suggested next focus) — the user reviews it, never assembles it by hand.

## 10. Behavioral analytics & recommendations (§54–55)

Pattern-based observations from stored history (planned-vs-actual gaps, trend drops, time-of-day completion differences, at-risk deadlines) paired with a concrete, data-derived suggestion the user can Apply / Ignore / Edit Plan — not a general-purpose chat feature.

## 11. Search & Archive (§56–57)

- **Global Search** (§56): across Goal, Activity, Task, Project, Habit, History — necessary once data volume grows into the hundreds/thousands.
- **Archive** (§57): Active/Completed/Archived separation for Goals, Projects, Activities, Habits, keeping the active UI uncluttered without deleting history.

## 12. Settings, i18n, units (§58)

Language (Russian + English minimum, fully i18n-ready, no hardcoded strings), Units + Currency (per-user, never hardcoded), Notifications, Account (incl. delete), Export Data, Privacy, Theme, Backup/Sync configuration.

## 13. Technical requirements that aren't a screen (§59–63)

- **Offline-first** (§59): ✓ works with no connectivity; syncs on reconnect. Part of the architecture from the start, not retrofitted.
- **Idempotency** (§60): a duplicated submission (double-tap on bad connectivity) must never double-count — enforced via a client-generated idempotency key on `sessions` (`/DATA_MODEL.md`).
- **Edit/delete recomputation** (§61): changing or deleting a Session correctly recomputes every dependent number (Goal, Month/Year/Lifetime, Pace, Forecast, Life Balance) — guaranteed by computing all of those as views over Sessions/Progress Events rather than cached fields.
- **Single source of truth** (§62): Sessions/Progress Events only — no independently-stored, manually-maintained totals anywhere, since those are exactly what desyncs over time.
- **Life Area calculations kept separate** (§63): Attention / Execution / Goal Progress / Trend reported as distinct figures, never combined into one artificial score.

## 14. Screens (§70–71)

Primary (bottom nav): Today, Goals, Plan, Progress. Secondary: My Vision, Life Plan, Life Area, Goal/Activity/Task/Habit/Project/Session Details, History, Personal Progress History, Skills, Weekly Review, Inbox, Search, Archive, Quick Add, Settings, Auth, Onboarding. The Goals screen itself has two lenses (§71): grouped by Life Plan/Life Area, or a flat Active/Paused/Completed list.

## 15. Explicitly out of scope (§69)

No networking/CRM features, ever: no contacts, companies, relationship tracking, or "contact this person" reminders. "Relationships" as a Life Area means the user's own relational goals, not a CRM.

## Product loop, promise, and differentiation (§73–76)

Loop: `MY VISION → LIFE AREAS → GOALS → PLAN → TODAY → DO → TRACK → PROGRESS → ANALYZE → LIFE BALANCE → ADJUST → ACHIEVE → NEW GOALS`. Promise: *"Stepwise connects what you want tomorrow with what you do today."* Principle: *"Powerful underneath. Simple every day."* Every feature is checked against: does it help achieve goals, and does it add a daily action — if the latter, automate/derive/hide/merge before shipping it (§73).

## Change discipline (§77)

Binding on all future implementation work — restated in full in `/PRODUCT_CANON.md`. In short: no feature from the concept is cut or reinterpreted without first explaining the problem, naming affected entities/consumers, proposing options, and getting the user's choice.
