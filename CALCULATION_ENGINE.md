# CALCULATION_ENGINE.md — Progress Calculation Engine

Written at `/ROADMAP.md`'s DEV-019, building on DEV-004's Canonical Data Model (`/core/model`) and DEV-009's Canonical Execution Engine (`Session`/`SessionMetricValue`, `/domain/usecase/CompleteOccurrenceUseCase.kt`). This is the exact, testable specification `/PRODUCT_CANON.md` and `/ARCHITECTURE.md` point to rather than restate — where either of those documents gives an illustrative formula (e.g. `/PRODUCT_CANON.md` §29's own Done/Partial/Missed example), this document is the final, binding version.

## Scope of this task, stated explicitly (a deliberate narrowing, not a silent one)

`/ROADMAP.md`'s DEV-019 line names: "central calculations for Week/Month/Year/All Time, Activity statistics, Planned vs Actual, Execution, trends, consistency, heatmap/cumulative data where justified." This document and the code it specifies cover:

- **Goal Progress**, per `GoalProgressMode` (METRIC, TARGET_VALUE, FREQUENCY, MILESTONES, TASKS — see "MANUAL mode" below for why that sixth mode is handled separately).
- **Execution Rate** (`/PRODUCT_CANON.md` §29) — the Done/Partial/Missed/Cancelled/Rescheduled formula.
- **Activity/Habit/Task period statistics** — planned total, actual total, session count, execution rate, and a period-over-period trend, for a given `PeriodRange`.
- **Period bucketing** — Week/Month/Year/All-Time ranges, built on the exact Monday-start week convention `/feature/plan` already established (DEV-017), now shared rather than reimplemented a second time.

**Explicitly out of scope for this task**, each already named as its own later `/ROADMAP.md` line, not invented here ahead of its phase:
- **Pace/Forecast** — DEV-020.
- **Life Balance's own Attention/Execution/Goal-Progress/Trend classification and the Life Area roll-up screen** — DEV-022. This task computes the *per-Goal*/*per-Activity* raw numbers; DEV-022 aggregates them per Life Area and attaches the four-dimension labeling `/PRODUCT_CANON.md` §63 requires ("Life Area has no manually-set progress percentage — it aggregates its Goals'/Activities' data").
- **Goal Impact scoring** (`Task.goalImpactScore`) — already a stored, user/system-set field since DEV-013, consumed by DEV-014's Today ranking; this task does not change or recompute it.
- **A dedicated "consistency" score and the calendar heatmap** — `/PRODUCT_CANON.md` never defines "consistency" as a precise formula anywhere (checked; it appears once, in the Progress/Activity-Detail screen's feature list, alongside "trend, cumulative progress, calendar heatmap," with no formula attached). Inventing one now would violate this project's own standing rule against inventing formulas ahead of an explicit specification. "Cumulative progress" is already covered (a period total, or the all-time `PeriodRange`); "trend" is covered (period-over-period comparison, below). A calendar heatmap is a UI rendering of per-day execution data this engine already exposes (one `ActivityStats` call per day) — no new calculation, so it's a feature-module concern, not an engine concern. "Consistency" specifically is tracked as VERIFICATION DEBT pending a concrete product definition.

## MANUAL mode — a real schema gap, surfaced not guessed around

`GoalProgressMode.MANUAL` is already a selectable, validated mode (`GoalValidation.kt`, since DEV-011) and `ARCHITECTURE.md` §6 describes it as the mode "for the case where no other source of truth exists at all, so the stored value is the sole input" — but **no field exists anywhere in the canonical `Goal` entity to hold that stored value**. `/core/model/.../Goal.kt` has no manual-progress field (by design — `current_value` is explicitly excluded per §62), so a MANUAL-mode Goal today has a `progressMode` with literally nothing behind it to compute from.

Put to the product owner directly during this task (`AskUserQuestion`); the answer was to defer the decision until the other five modes were visible as working code, rather than decide blind. With all five implemented below, the concrete choice is: **add a nullable `manualProgressPercent: Double?` field to `Goal`**, set only via a new, explicit "Update progress" action, used only when `progressMode == MANUAL`. This is not a §62 violation — §62 forbids a *duplicated* total for a fact Sessions/Milestones/Tasks already determine; MANUAL mode is specifically the case where nothing else determines it, so storing the sole input is correct, the same way `Milestone.progressPercent` already stores a value nothing else derives. **Not implemented in this pass** — adding a field to an already-reviewed, CI-green, synced entity (`Goal`) needs its own schema migration (`core/database`, `supabase/migrations`) and a UI entry point neither of which this task's own diff should smuggle in alongside five unrelated calculation formulas. Tracked as VERIFICATION DEBT below, with the concrete fix already decided rather than left as an open question for whoever picks it up next.

`GoalProgressCalculator` in `:core:calculation` therefore implements `metricProgress`/`targetValueProgress`/`frequencyProgress`/`milestonesProgress`/`tasksProgress` — five functions, not six. A caller that reaches a MANUAL-mode Goal gets no sixth function to call; `GetGoalProgressUseCase` returns an explicit "not supported" result for that one mode (never a guessed `0%` or `null` indistinguishable from "no data").

## Module boundary

Per `/ARCHITECTURE.md` §5: a new `:core:calculation` module, pure Kotlin/JVM, depending only on `:core:model` (for `Goal`/`Milestone`/`Session`/`SessionMetricValue`/the enums) and `:core:common` (for the shared `LocalDate` period-math extensions below — no `Clock` dependency inside the pure functions themselves; "today" is always a parameter the caller supplies, never read internally, so every function here stays a pure, deterministic, trivially-unit-testable computation over the values it's given). `:domain` depends on it and does the actual repository querying/joining — `:core:calculation` never touches a `Repository` interface, the same "pure calculation, data access stays in `:domain`" split `MetricResolver` already established at DEV-009.

## Period bucketing

`LocalDate.startOfWeek()` (Monday-anchored, the exact epoch-day arithmetic `/feature/plan/.../PlanViewModel.kt` introduced at DEV-017) is promoted from that file's own private copy into `:core:common`'s `DateMath.kt`, alongside new `startOfMonth()`/`startOfYear()`/`endOfMonth()`/`endOfYear()` — one shared implementation, not a second copy the way DEV-016/017's own retrospectives already flagged duplication risk for.

`PeriodRange(start: LocalDate?, end: LocalDate)` — `start == null` means All-Time (unbounded below `end`); both bounds inclusive otherwise. Four factories: `PeriodRange.week(anchor)`, `.month(anchor)`, `.year(anchor)`, `.allTime(through)`. `contains(date: LocalDate): Boolean` is the one predicate every caller needs (`start == null || date >= start) && date <= end`).

## Execution Rate (`/PRODUCT_CANON.md` §29, binding version)

Per-occurrence percent, from a `Session`'s own `status` plus (when `PARTIAL`) its linked metric's `plannedValue`/`actualValue`:

| `ExecutionStatus` | Contribution |
|---|---|
| `DONE` | 100% |
| `PARTIAL`, `plannedValue` present and non-zero | `actualValue / plannedValue × 100` (not capped at 100 — an actual that exceeds a stale/low planned value is a real data fact, not a calculation error, and clamping it would silently hide that) |
| `PARTIAL`, `plannedValue` null or zero | **excluded** — §29's own fallback ("Task type cannot be measured numerically... a user-set approximate percentage, or separate logic") has no field to read that approximate percentage from today (no such field exists on `Session`/`SessionMetricValue`); excluding rather than guessing a number keeps the rate honest about what it actually measured, the same "don't invent a formula ahead of its spec" principle MANUAL mode above follows. Tracked as VERIFICATION DEBT. |
| `MISSED` | 0% |
| `CANCELLED` | **excluded from the denominator entirely** — per §29's literal wording, not zero-weighted |
| `RESCHEDULED` | **excluded** — §29: "not counted as Missed on the original date"; it also isn't Done yet, so it contributes nothing either way until its new date resolves to one of the other statuses |
| `IN_PROGRESS` | **excluded** — not yet finished, nothing to rate |

The rate over a set of occurrences is the arithmetic mean of the included percentages, or `null` (never `0`) when nothing in the set qualifies — a `null` rate and a genuine `0%` rate are different facts (no data vs. a real zero), and this engine never conflates them.

## Goal Progress, per `GoalProgressMode`

All five read the Goal's own linked data (via `GoalActivityLink`, `Milestone`, or linked `Task`s, as applicable) — never a stored running total (§62), so an edit or delete to any underlying `Session`/`SessionMetricValue`/`Milestone`/`Task` is reflected the instant the next read happens, with no separate recompute step (§61, "falls out of the architecture for free").

- **`METRIC`** — `currentValue = Σ actualValue` over every `SessionMetricValue` reachable through this Goal's `GoalActivityLink` rows (`PRODUCT_CANON.md` §27: a `PARTIAL` session's real actual still contributes in full here — this is a cumulative total, not the Execution Rate above, and the two are never conflated). `percent = currentValue / targetValue × 100`, uncapped for the same reason Execution Rate is uncapped.
- **`TARGET_VALUE`** — a "moving toward a target" reading (e.g. current weight vs. a target weight), not a sum: `currentValue = ` the `actualValue` of the single most recent (`Session.date`, tie-broken by the latest `sync.updatedAt`) linked `SessionMetricValue`. `percent = currentValue / targetValue × 100`. `currentValue = null` (not `0`) when no reading exists yet.
- **`FREQUENCY`** — `currentValue = ` count of qualifying `Session`s (status `DONE` or `PARTIAL` — a session that happened at all, regardless of how much of it) linked to this Goal within the period the caller is asking about (there is no single "frequency so far" without a period — a weekly-frequency Goal's progress is inherently period-scoped, unlike `METRIC`'s all-time sum). `percent = currentValue / targetValue × 100`.
- **`MILESTONES`** — weighted completion: `percent = Σ(milestone.weight × milestonePercent) / Σ(milestone.weight)`, where `milestonePercent` is `100` or `0` for a binary Milestone (`completed`) and `progressPercent` otherwise. `null` when the Goal has zero Milestones (not a divide-by-zero `NaN`).
- **`TASKS`** — `percent = completedTaskCount / totalTaskCount × 100`, where `completedTaskCount` is the count of this Goal's linked, non-archived `Task`s with at least one `DONE`-status `Session`. **Equal-weighted, not the weighted fallback `ARCHITECTURE.md` §6 describes** ("`Task` carries an optional `weight`/effort field used only by the `TASKS` fallback mode") — `Task` has no such field anywhere in the canonical model (checked; only `plannedResult`, `goalImpactScore`, neither of which is a completion weight). A second doc-ahead-of-schema gap of the same shape as MANUAL mode above, bundled into the same future decision rather than raised as a second separate interruption: tracked as VERIFICATION DEBT, equal-weighting used as the honest interpretation of what the schema actually supports today.

`GoalProgressResult(currentValue: Double?, percent: Double?)` is the one return shape all five share — `percent == null` means "nothing to compute from yet" (zero Milestones, zero linked Tasks, no readings), always distinguished from a computed `0.0`.

## Activity/Habit/Task period statistics

One function, generic over `ExecutableKind`, for the per-source numbers `/PRODUCT_CANON.md`'s Progress/Activity-Detail screen and DEV-022's later Life Area roll-up both read from: given every `Session` for one source (`ExecutableKind`, `sourceId`) whose `date` falls inside a `PeriodRange`, plus that period's own linked metric values —

- `plannedTotal` / `actualTotal` — `Σ plannedValue` / `Σ actualValue` over the period's `SessionMetricValue`s (same §27 "a Partial's real actual counts in full" rule as `METRIC` mode above).
- `sessionCount` — how many qualifying Sessions (excluding `CANCELLED`, matching Execution Rate's own denominator rule).
- `executionRate` — this period's Execution Rate (above) over the same Sessions.
- `trend` — this period's `executionRate` minus the immediately preceding period of the same length's `executionRate` (e.g. this week vs. last week), `null` when either side has no qualifying data to rate.

## Kotlin ↔ SQL parity

`supabase/migrations/0004_calculation_views.sql` defines `goal_progress`, `execution_rate`, and `activity_stats` as Postgres views computing the exact same joins/formulas above directly over `sessions`/`session_metric_values`/`goal_activity_links`/`milestones`/`tasks` — never a second, independently-arrived-at formula. Verified locally against this sandbox's own Postgres 16 instance (the same shim DEV-007/008 used) by seeding representative rows for each `GoalProgressMode`/`ExecutionStatus` branch and comparing the view's output to this document's formulas by hand — the same class of verification DEV-007/008 already established for this project (real, executed, but not a genuinely independent review), not a live Supabase project. An automated, CI-running parity test suite (seed once, run both the Kotlin functions and the SQL views over identical fixture data, assert equality) is **not** built in this pass — this sandbox cannot run a Postgres-backed CI job (`./gradlew build` has no database), so such a suite would need to run somewhere this project doesn't yet have, and is tracked as VERIFICATION DEBT rather than stubbed out unverified.
