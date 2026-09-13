# Functional Analysis

Breakdown of the 48-section Stepwise concept (see `/CLAUDE.md`) into implementation modules. Each module lists its source sections and the concrete behavior it must deliver.

## 1. Core entities & the cascade rule (§9–19, 21–24)

Entities: Goal, Activity, Task, Project, Milestone, Habit, Session.

The mandatory rule (§18, §19, §45): a single user action (✓, Partial, timer stop, manual entry) creates exactly **one** `progress_event`. Everything else — Session, Activity totals, Goal totals, weekly/monthly/yearly/lifetime rollups, execution rate, pace, forecast, charts, comparisons, personal history — is a derived read from that event, never a second manual entry.

Goal ≠ Activity (§16): a Goal can complete and close while its Activity keeps accumulating lifetime history and can be attached to a new Goal.

## 2. Daily execution UX (§2, 4–8, 39–43)

- **Today screen** (§4): today's Focus item + today's list, a completion counter (`3/5`), nothing else — no charts, no full stats, no skill tree, no milestone list.
- **One-tap complete** (§5, §43): tapping ✓ on a planned item assumes `actual = planned`, no intermediate form. Partial/Missed/Reschedule/Cancel are reachable via swipe/long-press, not shown as competing buttons.
- **Six statuses** (§6): Done, Partial, Missed, Rescheduled, In Progress, Cancelled — modeled fully in data, surfaced progressively in UI.
- **Partial completion** (§7, §23): a partial amount (e.g. 40 of 60 min) is stored as-is and counted toward statistics/goal progress; it is never rounded down to zero.
- **Planned vs Actual** (§8, §26): both values always stored side by side; execution % = actual/planned.
- **Quick Add** (§39) + **Quick create** (§40) + **Progressive disclosure** (§41): every creation flow has a 1–2 field fast path (title + date, or title + target + deadline) with everything else under "More options". Quick Add is contextual — invoked from inside an Activity, it skips re-selecting that Activity.
- **Smart defaults** (§43) and **automation of remembered links** (§18, §42): once an Activity is linked to a Goal or a recurrence is set, the system never asks again.

## 3. Planning (§3, 20, 21)

- **Navigation** (§3): exactly four tabs — Today, Goals, Plan, Progress — plus a global ➕. No per-entity tabs.
- **Calendar** (§20): Day/Week/Month/Year views showing Tasks, Activities, Habits, deadlines, milestones, events.
- **Recurring activities** (§21): created once (e.g. "Boxing, Tue+Thu, 19:00, 1h"), schedule generates automatically thereafter.

## 4. Progress & analytics (§25–33, 36–38)

- **Progress hub** (§25): Week/Month/Year/All-Time totals per Activity/Goal/Habit.
- **Planned vs Actual statistics** (§26): totals plus status breakdown (Done/Partial/Missed/Rescheduled/Cancelled counts).
- **Period rollups** (§27, §28): per-Activity week/month/year/lifetime, session count, average session length, average/week.
- **History** (§29): editable log; editing a past Actual recalculates every derived number downstream.
- **Charts** (§30): trend, calendar heatmap, consistency, cumulative progress — live only in Progress, never on Today.
- **Period comparison** (§31): e.g. "↑22% vs last month".
- **Pace** (§32): remaining amount ÷ time left vs current rate → Ahead/Behind.
- **Forecast** (§33): projected completion date vs deadline, based on current pace.
- **Behavioral analytics** (§37) and **recommendations** (§38): pattern-based statements derived from stored history (planned vs actual gaps, trend drops, time-of-day completion rates), with a concrete suggested adjustment the user can accept — not a general-purpose chat feature.

## 5. Goal impact & skills (§34–35)

- **Goal Impact** (§34): tasks are ranked High/Medium/Low by how much they move a linked Goal; Today can surface high-impact items above merely-scheduled ones.
- **Skills** (§35): derived passively from Activity/Goal data (e.g. hours of Programming), never requiring a separate manual skill-log action.

## 6. History & personal record (§29, §36)

- Full editable history per Activity/Goal.
- **Personal Progress History** (§36): yearly and lifetime summaries across all Activities/Goals — the product's long-term memory of the person's development, not just a todo log.

## 7. Explicitly out of scope (§46)

No networking/CRM features: no contacts, companies, relationship tracking, or networking reminders, ever.

## Product loop (§47–48)

`GOAL → PLAN → DO → TRACK → ANALYZE → ADJUST → ACHIEVE`, with the Activity surviving past Goal completion. Promise: *"Turn your goals into action."* Principle: *"Powerful underneath. Simple every day."*
