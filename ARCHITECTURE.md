# ARCHITECTURE.md — Stepwise Technical Architecture

Canonical technical foundation for Stepwise on Android. Source of truth for product behavior remains `/PRODUCT_CANON.md` (Master Product Concept, §1–77) — nothing here overrides it. This document supersedes `/docs/android-stack.md` and `/docs/android-architecture.md`, which were written for a React Native/Expo build; the platform decision has changed to **native Android: Kotlin + Jetpack Compose**, per explicit direction. iOS, App Store, and StoreKit are out of scope for this document by instruction. Formerly `docs/android-architecture-specification.md`, moved to the repo root per the canonical documentation layout (`/CLAUDE.md`).

This is the prerequisite for, in order: Canonical Data Model Specification (extends `/DATA_MODEL.md`), Calculation Engine Specification (`/CALCULATION_ENGINE.md`), UX/Navigation Specification, Offline & Sync Specification, Google Play Release Specification, Implementation. None of those should re-derive the decisions made here. Individual significant decisions from this document and its predecessors are also recorded as standalone files under `/ADR/`. Live phase status: `/PROJECT_STATE.md`.

## Access requirement, and why not a Claude Artifact

Stepwise must be usable from a phone and a computer with the same data, with no dependency on any single local machine. The Next.js web app built in Phase 0 stays in the repo as a parked secondary client, not the active development focus. A Claude Artifact was considered early on (zero hosting, instant multi-device URL) and rejected for the primary build: Stepwise's relational model (Goals/Activities/Sessions/Tasks/Habits with cascading cross-entity updates, per-type goal math, pace/forecast analytics) and the requirement to serve a native Android client are better served by a real Postgres schema with SQL views than by an Artifact's document-style database.

## Repository layout

```
/CLAUDE.md            — session entry point (auto-loaded), points at everything below
/PRODUCT_CANON.md      — Master Product Concept (the "what and why")
/ARCHITECTURE.md        — this file
/DATA_MODEL.md          — entities, relationships, source-of-truth rule
/DEVELOPMENT_PROTOCOL.md — code-architecture centralization rules
/ANTI_ERROR_STANDARD.md  — audit/change-management process
/PROJECT_STATE.md        — live phase/status
/CALCULATION_ENGINE.md, /DESIGN_SYSTEM.md — scaffolded, filled in their own phase
/ADR/                    — one file per significant decision
/docs
  security/              — SECURITY_ARCHITECTURE.md, THREAT_MODEL.md, SECURITY_TEST_MATRIX.md,
                            DEPENDENCY_POLICY.md, INCIDENT_RESPONSE.md, AI_CODE_SECURITY.md
  functional-analysis.md, scope-of-work.md, play-store-checklist.md
  android-stack.md, android-architecture.md — superseded (React Native era), history only
/supabase
  migrations/            — schema, RLS policies, triggers, views (Phase 0 version; superseded by Phase 2's Canonical Data Model)
/apps
  web/                   — Next.js app (parked): Today / Goals / Plan / Progress tabs + Quick Add
/packages
  domain/                — shared TypeScript types, used by the parked web app only (not the native Android app)
/android                — native Android Gradle project (Phase 2 onward), multi-module per §5 below
```

**Revision history**: tracked in `/PROJECT_STATE.md`, not duplicated here.

---

## 0. Consistency Audit & Requirements Inventory

Performed before proposing architecture, per instruction. Two parts: (A) architecturally-binding requirements pulled from the concept, (B) real ambiguities/tensions found, each resolved here with reasoning or escalated to Open Questions when it can't be resolved from the concept alone.

### A. Architecturally-binding requirements (traced to §)

| Requirement | Source | Binding on |
|---|---|---|
| One user action → one Progress Event, no double entry | §19, §24, §68 | Domain event model, UI action handlers |
| Idempotent processing (no double-count on retry/double-tap) | §60 | Sync engine, local write path |
| Edit/delete of a Session correctly recomputes everything downstream | §61 | Calculation Engine, view/query design |
| Single source of truth — no independently-maintained mutable totals | §62, §68 | Persistence architecture, forbids cached counters as canonical |
| Activity ↔ Goal is many-to-many, each link scoped to a specific Metric | §21, §23 | Domain model, Progress Event fan-out |
| Session can carry multiple Metric Values | §23 | Domain model, calculation engine |
| Life Area figures (Attention/Execution/Goal Progress/Trend) stay separate, never blended | §63 | Analytics/Life Balance architecture |
| Execution status (Done/Partial/Missed/Rescheduled/In Progress/Cancelled) is a different axis from lifecycle status (Active/Paused/Completed/Archived) | concept §13, §25 | Domain model — two enums, never conflated |
| One recurrence engine for Task/Activity/Habit | §30, §33 | Scheduling architecture |
| Offline-first: core actions work with zero connectivity | §59 | Every layer — this is the top structural constraint |
| i18n-ready, no hardcoded strings; units/currency user-configurable | §58 | UI layer, resource strategy |
| No networking/CRM features | §69 | Scope boundary — no contact/relationship domain objects, ever |
| No feature is cut/reinterpreted without explaining impact and getting a decision | §77 | This document's own audit discipline, and all future work |

### B. Ambiguities found, and how they're resolved

**B1. Task's own Planned/Actual/status fields vs. Session as canonical record. — Confirmed by the user, wording tightened.**
§16 lists Planned/Actual/status directly as Task fields; §22/§24 say a completion creates a Session. Left unresolved, an app could let both be edited independently — exactly the duplicated-total problem §62 forbids.
**Resolution:** `Task.plannedResult` (the plan) is a genuine Task-owned field — planning happens before any execution exists. But **actual execution values are never independently stored on Task.** Completing a Task creates a `sessions` row (`source_type = task`); the Task's displayed "Actual" is a *derived projection* read from that Session (or the aggregate of that Task's Sessions, if it's ever completed more than once — e.g. a recurring Task occurrence). There is exactly one place an actual value is entered or edited — the Session — never two independently-editable numbers (`Task.actual` and `Session.actual`) that could disagree. This is restated in §6 Domain Architecture and must carry through unchanged into the Canonical Data Model Specification's field-by-field definition of Task vs. Session.

**B2. Habit → Goal: through an Activity, or directly?**
§31's example routes Habit through an Activity ("Meditation" Activity + "Meditation 15min/day" Habit). §22's example links a Habit directly to a Goal ("200 Hours English" ← "English 30min/day" Habit) with no Activity mentioned.
**Resolution:** both are valid and not in conflict — a Habit can link to Goals directly, exactly like an Activity does, via the same many-to-many mechanism (§21's `GoalActivityLink`, generalized to a `source_type ∈ {activity, habit}`). A Habit optionally also points at an Activity (the "what"); that's a separate, orthogonal relationship from its Goal links (the "why it matters"). No contradiction, just two independent edges that happen to share one example screen in the concept.

**B3. Materializing recurring occurrences.**
§18 (Overdue), §25 (Rescheduled applies per-instance) require that a *specific occurrence* of a recurring Activity/Task/Habit can carry its own status, independent of the rule. The concept doesn't say whether occurrences are pre-generated rows or computed on the fly.
**Resolution:** lazy materialization. Occurrences are computed on demand from the `RecurrenceRule` for display (Plan/Calendar, Today) with no row in existence yet. The first time a user *acts* on a specific occurrence (completes it, reschedules it, marks it skipped/missed), that one occurrence gets a concrete row. This avoids generating years of empty rows for every recurring item while still letting Overdue/Rescheduled/Missed be tracked per instance once they matter. Reasoning shown in full because this is exactly the kind of decision §77 asks to surface rather than silently pick.

**B4. What identifies a "duplicate" for idempotency (§60)?**
**Resolution:** every `sessions` row's ID is a client-generated UUID, created at the moment of the user action (not server-assigned). That UUID *is* the idempotency key: sync is always an upsert keyed on it, so a retried submission (network retry, double-tap before the first request lands) resolves to the same row rather than a second one. No separate idempotency-token table needed.

**B5. Progress Events as a client-writable entity.**
§24 describes "Stepwise creates one Progress Event" from a user action, which could be read as the client constructing and syncing a `progress_events` row itself.
**Resolution:** the client never writes `progress_events` directly. It writes `sessions` + `session_metric_values`; a Postgres trigger (server-side) derives `progress_events` from those, joined against `goal_activity_links`, deterministically and idempotently (re-processing the same `session_metric_values` row yields the same fan-out, not a duplicate). This removes an entire category of sync-duplication risk — there is nothing else that could double-write a Progress Event, because nothing but the trigger ever writes one.

**B6. Percentage/Project goal math — RESOLVED (closes OQ-1). Explicit `progressMode` on Goal.**
§11's Goal "types" (Cumulative/Quantity/Distance/Financial/Target Value/Frequency/Percentage) conflate two different things: the *unit/shape* of the number (hours, km, €, kg, a raw count) and *how its current value is computed*. The second axis needed its own name and explicit handling instead of one universal formula.
**Resolution:** Goal gets an explicit `progressMode` field, orthogonal to its unit: `METRIC` (sum of a linked Metric via `goal_activity_links` — Cumulative/Quantity/Distance/Financial/Frequency-as-lifetime-count all use this), `TARGET_VALUE` (latest reading of a linked metric, not a sum — Weight-style goals), `FREQUENCY` (a windowed per-period count against a target, e.g. "3×/week" — distinct from `METRIC` because it needs a rolling window, not a lifetime sum), `MILESTONES` (weighted milestone completion — the Percentage/Project goal's primary mode: `Goal Progress = Σ(milestone.progress × milestone.weight) / Σ(milestone.weight)`, where a Milestone is either binary — 0 or 100% of its weight — or carries its own progress percentage), `TASKS` (a fallback for Percentage/Project goals with no Milestones: a weighted completion ratio over that Goal's/Project's Tasks, using each Task's Execution-status-derived percentage per §29, weighted by an optional per-Task effort/weight field, default equal), and `MANUAL` (a user-entered value — the *only* input for that Goal, not a competing second number alongside an automatically computed one).
A Goal has exactly one `progressMode`, chosen at creation with a sensible default (an hours/distance/financial-shaped goal defaults to `METRIC`; a target-style goal defaults to `TARGET_VALUE`; a project-style goal defaults to `MILESTONES` if it has any, else `TASKS`, else `MANUAL`) and changeable under "More options" (§12, §38). **`MANUAL` does not violate §62**: §62 forbids a *duplicated, independently-editable* total for a fact that's otherwise derivable — `MANUAL` mode is for the case where no other source of truth exists at all, so the stored value is the sole input, not a second copy of something Sessions/Milestones/Tasks already determine. A Goal is never in more than one mode at once, and modes are never silently blended.
*Downstream impact*: Goals (creation form gains a mode field/default), Milestones (gain `weight` and either `isBinary` or `progressPercent`), Tasks (gain an optional `weight`/effort field used only by the `TASKS` fallback), Pace/Forecast (§48–49's "required rate toward a deadline" framing applies naturally to `METRIC`/`TARGET_VALUE`/`FREQUENCY`; for `MILESTONES`/`TASKS`/`MANUAL` goals, Pace/Forecast either don't apply or need mode-specific math — deferred to the Calculation Engine Specification, not solved here). No impact on Today, Activities, Sessions, Life Areas/Life Balance (a Life Area's "Goals progressing: N/M" count just needs "is this Goal's progress moving," which works regardless of mode), History, Search, Offline Sync, Recurrence, or Notifications.

**B7. Sync conflict resolution — RESOLVED (closes OQ-2). Hybrid, not blanket last-write-wins.**
Blanket timestamp-based last-write-wins across every entity was too coarse for progress-affecting data, but full CRDT/field-merge is unjustified complexity for a single-owner-per-account product. Rewritten in full in §9 below; summary: **low-risk scalar metadata** (title, description, notes, icon, color, some settings) resolves via silent version-checked last-write-wins. **Progress-affecting entities** (Sessions/SessionMetricValues, materialized recurrence occurrences) use the same version-check to *detect* a conflict, but instead of silently discarding the losing write, the losing device's write is preserved as a non-destructive "conflict copy" rather than deleted — safe-by-default, with recovery possible, without requiring a blocking merge UI for what should be a rare event (this is single-owner data; true concurrent edits of the same Session on two devices are expected to be uncommon). **New Sessions never conflict with each other** just because they affect the same Goal — each is an independent canonical fact identified by its own client-generated UUID; Goal Progress is a view recomputed after both land, not a field either Session competes to set. **Deletes** are tombstones (unchanged from the original design). **ProgressEvents/aggregates never participate in conflict resolution at all** — see B8, they either don't exist as a stored, syncable entity or are purely derived, so there's nothing to conflict.
*Downstream impact*: Sync Architecture (§9, rewritten below), the ADR "Conflict resolution" row (updated below), Testing Architecture (§24 — conflict tests now need to cover both tiers: metadata silent-resolve, and Session conflict-copy preservation). No impact on Today, Goals, Activities, Habits, Progress, Analytics, Pace, Forecast, Life Areas, Life Balance, Search, Recurrence's rule engine itself, or Notifications; History gains a forward-looking note that a future UX pass may want to surface conflict copies, which is a UX/Navigation Specification concern, not resolved here.

**B8. Does `ProgressEvent` need to exist as a separate persisted, canonical table? — RESOLVED. No — demoted to a derived view/query, not stored.**
§64 lists `ProgressEvent` as a domain entity, and the Phase-1.5 data model (`/DATA_MODEL.md`) made it a trigger-maintained table. Asked to evaluate rather than keep it by default: a trigger-maintained table is *an* implementation of "derived from Sessions," but not the only one, and it reintroduces exactly the kind of thing that can drift from its source if the trigger logic ever has a bug — a materialized copy that must stay in sync with `session_metric_values ⋈ goal_activity_links`, rather than being that join.
**Resolution:** `ProgressEvent` is **not a stored table**, backend or client. It remains a *conceptual* entity (the fact "this session's metric value contributed X to this goal") but is answered by a query/SQL view over `session_metric_values ⋈ goal_activity_links`, computed fresh on read, exactly as Goal progress itself already is (§7, `/DATA_MODEL.md`). This is strictly purer under §62's own logic than a trigger-maintained table: a view can't drift from its inputs because it has no independent state to drift. If a specific query pattern ever needs materialization for performance at scale (a single account is expected to reach thousands, not millions, of Sessions over years — not an early concern), the upgrade path is a Postgres `MATERIALIZED VIEW` behind the same read interface, which changes nothing about what any client or calculation sees.
*Downstream impact*: every place that referenced "the `progress_events` table" (§9 Sync Architecture, §12 Progress Event Architecture, §15 Analytics Architecture, the ADR) is updated below to say "derived on read from `session_metric_values`/`goal_activity_links`" instead. No behavioral change to Goals/Activities/Tasks/Habits/Sessions/Progress/Analytics/Pace/Forecast/Life Areas/Life Balance/History/Search (all already consumed "Goal progress" as a view, never the raw table, so their contract is unchanged). Offline Sync is simplified, not complicated: there was already no client sync of `progress_events` (B5), so removing it as a *backend-stored* table too removes one internal backend table to reason about, with zero client-facing change. Recurrence/Notifications unaffected.

**B9. Recurrence occurrence identity across devices — refined (not new; sharpens B3).**
B3 established lazy materialization but not how two devices avoid materializing *two different rows* for what is logically the *same* occurrence (e.g. one device completes "Boxing, Sep 16" offline while another reschedules that same date before either has synced).
**Resolution:** a virtual (not-yet-materialized) occurrence's identity is **deterministic**, not randomly assigned at materialization time — computed as a stable hash of `(recurrenceRuleId, occurrenceDate)` (a UUIDv5-style derivation: namespace = the rule's UUID, name = the ISO occurrence date). Two devices that independently decide to materialize "the same" occurrence compute the *same* UUID and therefore land on the existing sync upsert-by-UUID mechanism (§9/B4) instead of creating two rows — reusing the idempotency mechanism already built for Sessions rather than inventing a second one. Full semantics (exactly which occurrence-level states force materialization, and the occurrence row's relationship to the Session it produces on completion) are detailed in the Canonical Data Model / Recurrence Specification per the user's direction, not re-derived here.
*Downstream impact*: Scheduling/Recurrence Architecture (§13, rewritten below) and Today Aggregation (§14, one clarifying addition). No impact on Goals, Activities (as entities), Progress, Analytics, Pace, Forecast, Life Areas, Life Balance, History, Search, or Notifications beyond what B3 already touched.

---

## 1. Executive Architecture Summary

Native Android app (Kotlin, Jetpack Compose, Material 3) on an offline-first, layered architecture: Compose UI → ViewModel (StateFlow) → domain Use Cases → Repository → Room (local, operational) with a background Sync Engine reconciling against Supabase (Postgres, canonical, cross-device). Calculations (execution rate, pace, forecast, Life Balance) are pushed to a dedicated, pure-Kotlin Calculation Engine module mirrored by Postgres views server-side, so the same math runs identically on-device (for instant offline stats) and server-side (as the cross-device-consistent canonical answer). Hilt provides DI at the Android/app layer; domain and data-model modules stay framework-agnostic to keep a future Kotlin Multiplatform path to iOS open, per the user's stated Android-first-then-iOS sequencing. A Jetpack Glance Home Screen Widget (§19) is a first-class, canonical part of the product — a presentation-only layer over the same Use Cases, with no execution logic or state of its own.

## 2. Architecture Decisions

See the ADR table at the end of this document for the full list with alternatives/reasons/risks. Headline decisions: native Kotlin/Compose (not React Native — supersedes the Phase 1 decision); Supabase over Firebase (relational model fits the calculation engine; Firestore's aggregation pattern conflicts with §62); Hilt for the Android layer with a framework-agnostic domain/data core; Room as local operational store; a hybrid conflict-resolution strategy — silent version-checked last-write-wins for low-risk metadata, version-checked-with-preserved-conflict-copy for progress-affecting entities, and no conflict at all for independently-identified new Sessions (§9, B7); Goal progress computed via an explicit `progressMode` rather than one universal formula (§6, B6); `ProgressEvent` as a derived view, never a stored table (§7/§12, B8); reminder scheduling hidden behind a domain-owned `ReminderScheduler` interface so Android/Play policy changes never touch domain code (§18).

## 3. Technology Stack

- **Language/UI**: Kotlin, Jetpack Compose, Material 3, Navigation Compose.
- **State**: ViewModel + StateFlow (one ViewModel per screen/feature; UI state as a single immutable data class per screen, per Compose best practice for stable recomposition).
- **Concurrency**: Kotlin Coroutines + Flow throughout; `Dispatchers.IO` for Room/network, `Dispatchers.Default` for calculation work.
- **Local persistence**: Room (SQLite), DataStore (Preferences) for lightweight settings (units, language, theme, last-sync timestamps).
- **Background work**: WorkManager (sync engine worker, reminder fallback scheduling, periodic recomputation if ever needed).
- **DI**: Hilt (Android/app/feature layers). Domain and data-model modules use plain constructor injection with no DI annotations, so they can be wired by Hilt on Android today and by Koin or manual wiring in a Kotlin Multiplatform shared module later without change.
- **Backend**: Supabase — Postgres, Auth, Realtime, Row Level Security, Storage (for any file/image needs), Edge Functions (billing verification, account-deletion cascade, RTDN webhook handling).
- **Networking**: `supabase-kt` (Supabase's Kotlin Multiplatform client) where it covers the need (Auth, Postgrest, Realtime); plain Retrofit/OkHttp + kotlinx.serialization as a fallback for anything `supabase-kt` doesn't yet cover well — decided per-feature at implementation time, not locked here.
- **Push/Crash/Analytics**: Firebase Cloud Messaging (push) and Firebase Crashlytics — used alongside Supabase for data. These are orthogonal concerns; there's no reason to avoid Google's mature, free tooling for them just because the data backend is Supabase.
- **Billing**: Google Play Billing Library (current major version — verify at implementation time; do not hardcode a version here).

## 4. Application Layers

```
Compose UI  →  ViewModel (StateFlow)  →  Use Case (domain)  →  Repository (interface)
                                                                     │
                                              ┌──────────────────────┴──────────────────────┐
                                              ▼                                              ▼
                                       Room (local store)                          Sync Engine ↔ Supabase
```

- **UI (Compose)**: renders state, forwards user intents to the ViewModel. Never touches Room or network types directly.
- **ViewModel**: exposes `StateFlow<ScreenState>`, calls Use Cases, has zero knowledge of Room/Supabase.
- **Use Case (domain)**: one class per meaningful action ("CompleteSession", "CreateGoal", "GetTodayAgenda") — orchestrates one or more Repositories, applies domain rules (e.g. "Quick Done assumes Actual = Planned"), and is where the Calculation Engine gets invoked for anything that needs derived numbers.
- **Repository**: an interface owned by the domain layer, implemented in the data layer. The ViewModel/Use Case layer only ever sees the interface — swapping Room for something else, or Supabase for something else, never touches domain or UI code.
- **Room**: the local operational source of truth (see §7 for the precise meaning of that phrase).
- **Sync Engine**: mediates between Room and Supabase; owns the outbox, retry, and conflict-resolution logic (§9).

## 5. Module Architecture

Multi-module, organized by dependency direction rather than mirroring the feature list one-to-one:

```
:app                      composition root — Hilt setup, NavHost assembly, applies all feature modules

:core:common              dispatchers, Result/AppError types, date/time utilities, DomainEventBus (§19.5) — pure Kotlin
:core:model               domain models (Goal, Activity, Session, ...) — pure Kotlin, no Android/Room/network types
:core:calculation         Calculation Engine: execution rate, pace, forecast, Life Balance math — pure Kotlin, heavily unit-tested
:domain                   Use Cases + Repository interfaces — depends on :core:model, :core:calculation

:core:database            Room entities/DAOs/migrations + mappers to/from :core:model — implements repository read/write for local data
:core:network             Supabase/Postgrest client, DTOs + mappers to/from :core:model
:core:sync                outbox, conflict resolution, WorkManager workers — depends on :core:database + :core:network
:data                     Repository implementations — depends on :domain (interfaces), :core:database, :core:network, :core:sync;
                           this is the ONLY module allowed to depend on both :core:database and :core:network at once

:core:designsystem        Compose theme, Material 3 tokens, shared components — depends on nothing but Compose
:core:notifications       implements the domain-owned ReminderScheduler interface (§18) using whatever concrete
                          Android mechanism is appropriate (AlarmManager/WorkManager/exact-alarm-with-fallback) + FCM wrapper —
                          domain and feature modules depend only on the ReminderScheduler interface (declared in :domain),
                          never on this module's concrete types

:feature:today, :feature:goals, :feature:plan, :feature:progress,
:feature:activities, :feature:habits, :feature:vision (Vision/Life Areas/Life Plan/Life Balance),
:feature:tasks-inbox, :feature:search, :feature:archive, :feature:settings,
:feature:onboarding-auth, :feature:quickadd, :feature:widget (§19 — Glance Home Screen Widget)
                           each depends on :domain, :core:model, :core:designsystem, :core:common —
                           NEVER on :core:database, :core:network, or :data directly
                           (:feature:widget included — this is what makes a second execution path uncompilable, §19.2)
```

The example above is Stepwise's actual module set, not a generic template — it follows §70's screen list, merged where screens obviously share one feature module's domain (e.g. Vision/Life Plan/Life Area/Life Balance all live under `:feature:vision` since they're one strategic-layer concern per §5–8) and split out where a concern is genuinely cross-cutting (Search and Archive touch every entity, so they're their own modules rather than duplicated per-feature).

## 6. Domain Architecture

`:core:model` holds the entities enumerated in `/PRODUCT_CANON.md` §64 (User, Vision, LifeArea, Goal, Milestone, Project, Task, Activity, Habit, Schedule/RecurrenceRule, Session, ProgressEvent, Metric, MetricValue, GoalActivityLink, Skill, Reminder, WeeklyReview, Insight/Recommendation) as plain Kotlin data classes — no Room `@Entity`, no network `@Serializable` on the same class. `:core:database` and `:core:network` each have their own representations and map to/from `:core:model` at their boundary. This is deliberate duplication in exchange for a real guarantee: a Room schema migration or a backend DTO change can never silently change what the domain layer — and therefore every ViewModel and every calculation — thinks a Goal *is*.

Two status axes live on `Goal`/`Task` as separate enums per concept §13/§25, never merged into one: `LifecycleStatus` (Active/Paused/Completed/Archived) and `ExecutionStatus` (Done/Partial/Missed/Rescheduled/InProgress/Cancelled) — the former describes the Goal/Task itself, the latter describes a specific Session/occurrence.

`Goal` carries an explicit `progressMode: METRIC | TARGET_VALUE | FREQUENCY | MILESTONES | TASKS | MANUAL` (B6) — one mode per Goal, never blended; `Milestone` carries `weight` + (`isBinary` or `progressPercent`) to support the `MILESTONES` mode's weighted formula; `Task` carries an optional `weight`/effort field used only by the `TASKS` fallback mode. Exact field types belong to the Canonical Data Model Specification.

`Task.plannedResult` is a genuine Task-owned field, but **`Task` has no independently-editable actual-execution field** (B1). Actual execution values live only on `Session` (`source_type = task` for a Task's own completions); anything the UI shows as "Task Actual" is a read projection from the linked Session(s), never a second place that value can be entered.

Reminders are scheduled through a domain-owned `ReminderScheduler` interface (declared in `:domain`, implemented in `:core:notifications`, §18) — domain and feature code never reference `AlarmManager`, `WorkManager`, or FCM types directly, so a change in Android/Play's scheduling policy is a `:core:notifications` implementation change, not a domain-layer one.

## 7. Persistence Architecture

Four distinct layers, per the explicit ask to separate them — this is the answer to "local operational source of truth vs. domain event source of truth vs. backend persistence vs. derived analytics":

- **Domain event source of truth**: the append-oriented fact of what happened — a `Session` + its `SessionMetricValue`s, identified by a client-generated UUID. This *is* the fact; it means the same thing whether it currently lives only on-device or has reached the backend.
- **Local operational source of truth (Room)**: what the UI actually reads and writes for instant, offline-capable operation. Mirrors the domain events locally, plus may hold a locally-materialized cache of frequently-read numbers (e.g. "this Goal's current progress" for the Goals screen) purely for read performance — that cache is explicitly non-canonical and rebuildable from local Sessions at any time; nothing downstream treats it as authoritative.
- **Backend persistence (Supabase/Postgres)**: the durable, cross-device copy of the same domain events (`sessions`, `session_metric_values`, and structural entities like `goals`/`activities`), plus the canonical Postgres views (goal progress, rollups, pace, forecast — see `/DATA_MODEL.md`) computed *directly* from those events on read — **`ProgressEvent` is one of these views, not a stored table** (B8); nothing sits materialized between the raw events and the numbers a client reads. This is the arbiter when devices disagree, and what a new device/reinstall bootstraps from.
- **Derived analytics**: anything computed *from* the above for display, on-device or from a backend view — never itself an independent fact. If a local cached number and the backend's view momentarily disagree (sync lag), the backend view wins and the local cache is refreshed to match once sync completes; the UI should show a subtle "syncing" affordance rather than silently presenting stale derived numbers as final.

No entity keeps an independently-editable running total anywhere in this stack (§62) — the local cache above is a read-optimization, not a second place a fact can be entered or corrected.

## 8. Offline-First Strategy

Every core action (§59: marking a Session, creating a Task, editing History) writes to Room synchronously and returns immediately — the UI never waits on a network round trip for a normal action. That local write also enqueues an outbox entry (§9). Reads for Today/Goals/Plan/Progress come from Room (via Flow) so the screens render instantly from whatever was last synced plus anything done locally since, without waiting for connectivity. A slim "sync status" indicator (not a blocking spinner) reflects outbox depth/last successful sync.

## 9. Sync Architecture

### 9.0 Transaction boundaries, command idempotency, and the concurrency model (generalized from the Widget pipeline, §19.3)

Per the Security/Transaction/Concurrency standard, this generalizes what §19.3–19.4 already established for one command to every critical multi-entity command in the app.

**Transaction boundaries.** Every critical command (e.g. `CompleteOccurrenceCommand`) defines, up front: its canonical writes, its derived writes, failure behavior, retry behavior, and rollback/recovery behavior. Canonical local state changes execute atomically in one Room transaction where the data logically belongs to one operation — e.g. completing a Task must never leave `Task = DONE` with no Session, or a Session created with its occurrence left un-transitioned. Secondary derived effects (recalculated rollups, the locally-materialized read cache of §7) may be recomputed *after* commit, because they are recoverable projections, not canonical facts — recomputing them from the canonical write is always safe, unlike leaving the canonical write itself half-done.

**Command idempotency.** Every mutating command has a stable identity (already established for Sessions via the client-generated UUID, B4; for occurrences via the deterministic hash, B9; for the Widget's local upsert, §19.4). A network retry, a double-tap, a Widget retry, or process recreation replaying the same command must resolve to the same logical action, never a second one. This is not a special case for any one surface — it's a property every command's identity scheme must have by construction.

**Concurrency model — confirms B7, does not change it.** "Universal last-write-wins is forbidden" was already this project's own conclusion (B7/ADR-006) before the Security/Transaction/Concurrency standard restated it — cross-checked here explicitly, no new decision needed. Restated with the standard's own categories, which map directly onto what §9's bullets below already do:
- **Independent creates** (two independently-created Sessions): both persist; never treated as competing for one slot (already true — each Session is its own canonical fact, B7).
- **Same-entity concurrent edit**: revision/version-checked, not blind overwrite (already true — the version-check + conflict-copy design, B7).
- **Delete**: tombstone semantics, so a deleted row can't resurrect from a stale offline device syncing late (already true, §9 below).
- **Derived data**: never an authoritative participant in a merge — always recomputed from canonical facts after the canonical write resolves (already true, ADR-004).

**Single-writer semantics where necessary.** For state where concurrent writes are especially dangerous — Timer state, entitlement/subscription state (§19 Billing), server-controlled security flags, authoritative account state — exactly one layer owns the write, and no other layer independently creates a competing truth. Timer already satisfies this by construction (§19.8: the Session row is the only place Timer state is written, by whichever `StartTimerUseCase`/`StopTimerUseCase` call happens to run). Entitlement state's single writer is the backend (`subscriptions` table, written only by the Play RTDN Edge Function and server-side purchase verification, §19 Billing) — the app never writes its own belief about subscription status.

### 9.1 Outbox, retries, versioning

- **Local-first writes**: every mutation lands in Room first, in the same transaction as an outbox row describing it (entity type, entity id, operation, a snapshot of the row, `attempt_count`, `last_error`).
- **Dirty state**: syncable rows carry `sync_status: synced | pending | conflict` and `local_updated_at`; the outbox is the authoritative work list, `sync_status` is a derived display convenience.
- **Client-generated IDs**: every syncable entity's primary key is a UUID generated at creation time, in both Room and Postgres — this is what makes sync an idempotent upsert rather than an insert that could double under retry (§60, resolved in B4 above).
- **Tombstones**: deletes set `deleted_at` rather than physically removing the row, both locally and on the backend; the tombstone syncs like any other change, and a device that was offline during the delete finds out about it instead of resurrecting the row. Physical purge happens after a retention window once all of the user's devices have acknowledged the tombstone (tracked via each device's last-synced-at watermark).
- **Retries/backoff**: a WorkManager periodic + expedited worker drains the outbox; WorkManager's built-in exponential backoff policy handles retry timing; the outbox itself is Room-backed so it survives process death/reboot without WorkManager's own persistence doing double duty.
- **Server acknowledgement**: a successful upsert response clears the outbox entry and flips `sync_status` to `synced`; a rejected write (validation failure, not a transient network error) surfaces as a user-visible "sync issue" rather than retrying forever.
- **Timestamps & versioning**: every syncable row has a server-assigned `updated_at` (Postgres trigger, `now()` on write) and a monotonically incremented `version` integer. Clients never use their own wall clock for conflict ordering — client timestamps are display-only ("you did this at 7pm"), never used to resolve a conflict, because client clocks can drift or be wrong.
- **Conflict detection**: a client pushes its last-known `version` alongside a write; if the server's current `version` has moved past that, it's a conflict.
- **Conflict resolution — hybrid, not blanket last-write-wins (B7, closes OQ-2)**:
  - **Low-risk scalar metadata** (title, description, notes, icon, color, most Settings fields) on any entity: silent version-checked last-write-wins keyed on the server's `updated_at`. No user-visible effect, no recovery path needed — losing a stale label edit is a non-event.
  - **Progress-affecting entities** (`sessions`/`session_metric_values`, materialized recurrence occurrences): the same version check detects the conflict, but the losing device's write is **not discarded** — it's kept as a non-destructive "conflict copy" (a separate row referencing the entity it collided with) rather than deleted, so nothing a user recorded ever silently vanishes. The server's `updated_at`-newer write still becomes the entity's current value (so the app has one clear answer to show), but the loser is recoverable rather than gone. This is deliberately short of a CRDT/merge system — justified because Stepwise is single-owner-per-account and a true same-moment edit of the same Session on two devices is expected to be rare, not because losing data is considered acceptable.
  - **New Sessions never conflict with each other**, even when both affect the same Goal: each has its own client-generated UUID and is an independent canonical fact; there is no shared field they're competing to set. Goal progress is a view recomputed once both have synced, not a value either Session writes.
  - **Structural entities** (Goals, Activities, Habits, Projects, Milestones): same metadata-tier last-write-wins as above for their fields — they're single-owner configuration, not progress data.
- **Editing the same entity on multiple devices**: covered by the tiers above — metadata resolves silently; a genuine concurrent Session edit resolves deterministically (newest `updated_at` wins) with the losing edit preserved as a conflict copy rather than a merge prompt blocking the user.
- **Recurring data**: only materialized occurrence rows and the `RecurrenceRule` itself sync; computed (not-yet-materialized) occurrences are a deterministic function of the rule and never need to be transmitted. A materialized occurrence's ID is itself deterministic — a stable hash of `(recurrenceRuleId, occurrenceDate)`, not a random UUID (B9) — so two devices that independently materialize "the same" logical occurrence converge on one row via the same upsert-by-UUID mechanism as Sessions, rather than needing a separate dedup step.
- **Progress Event duplication**: not applicable — `ProgressEvent` isn't a stored, syncable entity at all (B8). The client syncs `sessions`/`session_metric_values` only; "Goal progress" is computed by a query/view over those plus `goal_activity_links`, fresh on every read, so there is nothing shaped like a Progress Event that could ever be duplicated.
- **Aggregates never sync as authoritative state**: Goal progress, rollups, pace, forecast, and Life Balance figures are always recomputed from synced canonical facts — locally for offline reads, from the backend's views when online — never themselves pushed or pulled as a value to trust.
- **Clock drift**: irrelevant to correctness by construction, since conflict resolution never uses client clocks.
- **Failed sync recovery**: the Room-backed outbox is the recovery mechanism — on next app start or connectivity change, WorkManager resumes draining it from wherever it left off.

## 10. Backend Architecture

Supabase: Postgres (schema per `/DATA_MODEL.md`, to be extended with the sync metadata columns above in the Canonical Data Model doc), Auth (email/password + Google Sign-In), Realtime (used sparingly — mainly to let a second open device pick up a change without waiting for its own next poll, not as the primary sync transport, which is the pull/push outbox model above), Row Level Security as the sole authorization boundary (the server never trusts a client-supplied `user_id`; RLS derives it from the authenticated JWT on every query), Edge Functions for anything that must run with elevated trust: Play purchase-token verification, RTDN webhook handling, account-deletion cascade.

## 11. Authentication

Email/password and Google Sign-In (via Credential Manager, not the deprecated legacy Google Sign-In SDK), both backed by Supabase Auth. Refresh token stored via Android Keystore-backed encrypted storage (`androidx.security.crypto`); silent refresh on app start. A previously-authenticated session keeps working offline (cached JWT + local Room data) — the app should not force a re-login just because it's offline. Logout clears local encrypted tokens and offers a choice to also clear the local Room cache (relevant on shared devices) or keep it for a faster next login. Multiple devices are a normal case (same account, several phones/tablets), not an edge case — this is exactly why §9's conflict handling exists.

## 12. Progress Event Architecture

Precise statement (per explicit instruction, to remove any reading that could conflict with offline-first):

> User clients create and modify canonical execution entities — `Session` and its `SessionMetricValue`s. They never directly author an arbitrary, authoritative `ProgressEvent` record. Progress effects are computed **locally**, immediately, from those canonical entities for offline UX. `ProgressEvent` is not retained as a separate persisted entity (B8) — where a "progress event" view of the data is needed, it is deterministically derived and validated by trusted application/backend logic, computed fresh from `session_metric_values` and `goal_activity_links` rather than stored as its own row, whether that computation happens on-device or on the server.

Concretely, the offline flow the user specified:

1. User completes a Boxing Session (`Actual = 1h`) with no connectivity.
2. Room saves the `Session` + `SessionMetricValue` locally, synchronously — this is the canonical execution entity, written immediately, offline-capable by construction (§8).
3. `:core:calculation` immediately computes the domain effect **locally** — joining the local `goal_activity_links` to find every Goal this Session's metrics feed, and updating the local read cache (§7) accordingly. The UI shows "Boxing 72h → 73h" instantly. This local computation is optimistic and non-authoritative — it exists purely so the UI never has to wait on the network.
4. The outbox (§9) queues the `Session`/`SessionMetricValue` for sync.
5. Once connectivity returns, they sync to Supabase as an idempotent upsert (client-generated UUID).
6. The backend answers "Goal progress"/"Progress Event" queries via the same kind of derivation (a view/query over `session_metric_values ⋈ goal_activity_links`) — this is the authoritative, cross-device answer.
7. The client's local cache reconciles against the backend's view on next sync; if they ever transiently disagreed (sync lag, or a conflict-copy scenario per B7), the backend's view wins and the local cache is refreshed to match.

The on-device (Kotlin, `:core:calculation`) and backend (Postgres query/view) implementations of this join-and-aggregate logic must be kept behaviorally identical — this is exactly why `:core:calculation` gets the deepest test coverage in the whole app (§24 Testing Architecture), and why B8 rejected a stored, trigger-maintained `progress_events` table on the backend: keeping the backend side a pure, stateless view (rather than a table something writes to) is what makes "identical to the Kotlin implementation" a checkable property instead of "identical to whatever the trigger happened to leave behind."

## 13. Scheduling / Recurrence Architecture

One `Schedule`/`RecurrenceRule` engine serves Task, Activity, and Habit (§30, §33) — an owner-polymorphic table (`owner_type`, `owner_id`, `rule`), where `rule` supports: daily, weekly, selected weekdays, every-N-days, monthly, and a bounded custom form; start date; optional end date.

**Virtual vs. materialized occurrences (B3, sharpened by B9):**
- A **virtual occurrence** is not a row at all — it's the result of evaluating a `RecurrenceRule` against a calendar date, computed on read, purely for display (Today, Plan/Calendar). Today and Plan/Calendar must be able to show virtual occurrences arbitrarily far into the future with no persisted row backing them.
- An occurrence is **materialized** (gets a real row) the moment it needs to carry individual state — completion (Done/Partial/Missed/Rescheduled/Skipped), an edit to that one instance, a reminder-specific override, or any other exception to the rule.
- **Stable, deterministic identity**: a materialized occurrence's ID is a stable hash of `(recurrenceRuleId, occurrenceDate)` — not a randomly generated UUID chosen at materialization time. This guarantees that if two devices independently decide "this occurrence needs a row" (one completes it offline, another reschedules the same date before either has synced), they compute the *same* ID and converge on one row through the ordinary upsert-by-UUID sync path, rather than producing two rows for one logical occurrence.
- Completing a materialized occurrence creates/links the same `Session` any other completion would (§12) — the occurrence row carries the per-instance schedule state (rescheduled-to-date, skipped, etc.), the Session carries the execution fact, and they reference each other.
- Full field-level semantics (the occurrence table's exact shape, its relationship to `Session`) belong to the Canonical Data Model / Recurrence Specification, not this document.

Today's agenda (§14) and Plan/Calendar both call the same occurrence-resolution function — which merges virtual and materialized occurrences transparently — so there is exactly one place this logic lives, not one per screen.

## 14. Today Aggregation Architecture

Today's data comes from several sources (scheduled Task occurrences, planned Activity occurrences, Habit occurrences, deadlines, Overdue items) but the UI must receive one unified model. A `GetTodayAgenda` Use Case in `:domain` is responsible for merging these into a single ordered list of `TodayItem` (a `:core:model` type with a `kind` discriminator — task/activity/habit/overdue) before the ViewModel ever sees it; ranking uses each item's Goal Impact Score (§50) where present. Each `TodayItem` is backed by either a virtual or a materialized occurrence (§13) transparently — `GetTodayAgenda` resolves that distinction, the UI never sees it. The UI (`:feature:today`) only ever consumes `List<TodayItem>` — it has no idea that four different domain concepts (or two different occurrence representations) fed into it, which is what keeps Today's screen code simple despite the aggregation being non-trivial (mirrors §2's "complex inside, simple outside" principle at the code level, not just the UX level).

## 15. Analytics Architecture

- **On demand (not cached)**: anything cheap to compute from an already-indexed query — a single Goal's current progress, a single Activity's this-week total.
- **Cached (locally materialized, rebuildable)**: numbers read very frequently relative to how often they change — Today's agenda, the Goals list's progress bars. Invalidated and recomputed whenever a relevant local write happens (§7's local cache).
- **Computed as SQL views, not app code (server-side)**: period rollups, pace, forecast, execution rate, Life Balance figures, and Goal progress itself — computed by Postgres views over `sessions`/`session_metric_values`/`goal_activity_links` directly (no intermediate `progress_events` table — B8), per `/DATA_MODEL.md`, so they're correct by construction rather than by an app-level recompute step.
- **Incrementally updated**: none of the above need incremental/streaming update logic — because nothing is a running total that gets incremented, everything is either a cheap direct query or a view recomputed from the (indexed) event log, the "how do we keep an incremental counter correct" problem simply doesn't arise. This is a direct consequence of the §62 source-of-truth decision, not a separate design.

## 16. Life Balance Architecture

Attention/Execution/Goal Progress/Trend (§8, §63) are computed and surfaced as four separate fields on a `LifeAreaBalance` view/model, never combined. Same period parameterization (Week/Month/Year/All Time) as every other analytics surface — one query shape, filtered by `life_area_id` instead of `goal_id`/`activity_id`.

## 17. Search Architecture

Global Search (§56) needs to query across Goal/Activity/Task/Project/Habit/History titles and, later, notes/descriptions. Room supports FTS4/FTS5 virtual tables; a `search_index` FTS table populated by triggers (or a lightweight sync-time indexer) on the underlying entities keeps search local-first and instant, consistent with the rest of the offline-first design — no server round trip needed for search.

## 18. Notifications

**Domain abstraction (per explicit instruction — domain must not know about AlarmManager/WorkManager/FCM):** `:domain` declares a `ReminderScheduler` interface —

```
interface ReminderScheduler {
    suspend fun schedule(reminder: Reminder)
    suspend fun cancel(reminderId: ReminderId)
    suspend fun rescheduleAll()   // e.g. after boot, or after a policy fallback change
}
```

— and every Use Case that sets/cancels a reminder (from Task/Habit creation or editing, §16/§22) depends only on this interface. `:core:notifications` is the sole implementation and owns every concrete decision below; nothing above it needs to change if Android or Play policy around scheduling changes.

- **Local reminders**: `:core:notifications`'s current implementation choice — `AlarmManager` exact alarms where the OS/policy allows it, falling back to an inexact `WorkManager`-scheduled check where exact alarms aren't available (Android 12+ restricts `SCHEDULE_EXACT_ALARM`). **This is exactly the kind of detail the `ReminderScheduler` interface exists to isolate**: the concrete mechanism is re-verified against current Play/Android policy at implementation time (OQ-3 — an implementation-time external verification requirement, not an open product question; the product unambiguously wants reminders, per §16/§22) and can change without touching `:domain` or any feature module.
- **Remote push (FCM)**: reserved for server-initiated, cross-device notifications (re-engagement, future sync-driven notices) — not required for local reminders to work. Register an FCM token per device at auth time regardless, so it's available whenever a remote-push feature is built, without a later migration. Also implemented behind `ReminderScheduler`/a sibling interface, not called directly from domain code.
- **Notification channels**: split by category (Task reminders, Habit reminders, Weekly Review ready, Insights/Recommendations) so a user can mute one category without muting all.
- **Runtime permission**: `POST_NOTIFICATIONS` (Android 13+) requested contextually — when the user sets their first reminder — not on first app launch.

## 19. Android Home Screen Widget Architecture

Stepwise ships a full Android Home Screen Widget as a first-class part of the product, per explicit instruction — not experimental, giving "Посмотреть → Выполнить → Отметить" (§2) a presence outside the app. The single rule governing everything below: **the Widget is a presentation/integration layer over the exact same domain Use Cases the App uses — it is structurally incapable of having its own execution logic or its own source of truth**, enforced the same way every other module boundary in this document is enforced: by which modules `:feature:widget` is and isn't allowed to depend on (§27).

### 19.1 Technology

Jetpack Glance — the current Google-recommended approach for Compose-style App Widgets, built on the classic `RemoteViews`/`AppWidgetProvider` host framework. **Glance's API surface, `updatePeriodMillis` minimums, `ActionCallback` execution time budget, and any target-SDK-specific widget constraints must be verified against live Android documentation immediately before implementation** — this is the same category of requirement as OQ-3, tracked as OQ-4 below, since both Glance itself and Android's widget-hosting rules evolve and neither is safe to assume from training-era knowledge. Glance is entirely a `:feature:widget`-internal detail — no other module references Glance types.

### 19.2 Module placement

New module: `:feature:widget`, following the exact same feature-module rule as every other feature (§5, §27) — depends on `:domain`, `:core:model`, `:core:designsystem` (a Glance-compatible token subset; Glance cannot render full Compose Material3 components, so `:feature:widget` consumes shared colors/spacing/type-scale tokens rather than `:core:designsystem`'s Compose components directly), `:core:common`. **No dependency on `:core:database`, `:core:network`, or `:data`** — this is what makes "the Widget cannot create a second execution path" a compile-time fact rather than a code-review convention. Contains `StepwiseGlanceWidget` (`GlanceAppWidget`), `StepwiseWidgetReceiver` (`GlanceAppWidgetReceiver`), one `ActionCallback` per Quick Action, and `WidgetRefreshCoordinator` (§19.5).

### 19.3 Canonical execution pipeline

Exactly the pipeline requested, and exactly the App's own pipeline — one path, not two:

```
Widget "Boxing ✓" tap (ActionCallback)          App "Boxing ✓" tap (ViewModel)
                     \                                /
                      ▼                              ▼
                   CompleteOccurrenceUseCase (:domain) — the one entry point
                                     │
                                     ▼
                         Room transaction (Session upsert, §7/§9)
                                     │
                                     ▼
               local recalculation (:core:calculation) → Goal/Activity/Today/Analytics
                                     │
                     ┌───────────────┴───────────────┐
                     ▼                                ▼
         DomainEvent.DataChanged published     outbox entry queued (§9)
                     │                                │
                     ▼                                ▼
      WidgetRefreshCoordinator (:feature:widget)   later backend sync
                     │
                     ▼
           GlanceAppWidget.update() → Widget re-renders
```

`ActionCallback` and the ViewModel are both callers of `CompleteOccurrenceUseCase`, never implementers of completion logic themselves. §27's module graph makes a second path physically impossible to compile, not merely discouraged.

### 19.4 Idempotency — local, not only at sync

B4 established sync-time idempotency (client-generated UUID upsert). Widget double-tap sharpens the requirement: correctness must hold **locally and instantly**, before any sync happens — a double-tap that briefly shows "+2h" and is only "corrected" later by sync is still a bug. **Resolution**: `CompleteOccurrenceUseCase`'s Room write is itself an upsert keyed on a Session ID deterministically derived from the occurrence being completed — extending B9's deterministic-identity pattern one step further, rather than a fresh random UUID per invocation. Invoking the Use Case twice for the same occurrence (two rapid Widget taps, a Widget tap racing an App tap, any combination) resolves to one row at the local Room layer, by construction, before sync is even involved. A light UI-level debounce (disable the control while the callback is in flight) is added as defense in depth, not as what correctness depends on. This tightening applies uniformly to the App too — it is a strict improvement to the existing pipeline, not a Widget-only special case.

### 19.5 Refresh strategy — event-driven, not polling (new: `DomainEventBus`)

A Glance widget doesn't continuously observe a `Flow` the way a Compose screen does — it redraws when something explicitly calls `GlanceAppWidget.update()`. Satisfying "event-driven refresh, no unnecessary polling" needs one genuinely new, small piece of architecture, flagged explicitly per the anti-error standard rather than folded in silently: a **`DomainEventBus`** (a `SharedFlow<DomainEvent>` wrapper, defined in `:core:common`, since both `:domain` Use Cases and `:core:sync` need to publish to it without depending on each other, and `:core:common` is already a dependency-free leaf both can reach).

- Mutating Use Cases (`CompleteOccurrenceUseCase`, `EditSessionUseCase`, `DeleteSessionUseCase`, `StartTimerUseCase`/`StopTimerUseCase`, …) publish `DomainEvent.DataChanged(entityType, id)` after a successful local commit.
- `:core:sync` publishes `DomainEvent.SyncCompleted` after a pull/push cycle — this is what makes a remote-originated change (another device, or a server-side recompute) reach the Widget.
- `WidgetRefreshCoordinator` (`:feature:widget`) subscribes to both and calls `update()` only on the affected widget instances (scoped once §19.10's configuration is read).
- **Day-boundary coverage**: nothing above fires from the passage of time alone, so Glance's own coarse periodic update (`updatePeriodMillis`, Android's built-in floor is 30 minutes) stays as a low-frequency safety net so Today's virtual occurrences never go stale for long even if an event-driven trigger is somehow missed — defense in depth, not the primary mechanism, and battery-cheap at that frequency.
- Purely additive: every existing Use Case keeps its current behavior and gains one line (publish an event) after already succeeding.

### 19.6 Widget as projection, never source of truth

Glance's own `GlanceStateDefinition` (the small serialized blob Glance persists between updates so it can redraw without a full recompute) is a rendering cache internal to the widget framework, write-only from the domain's perspective — nothing reads it back as authoritative. Every render is produced fresh from `:domain` Use Case reads (`GetTodayAgendaUseCase`, `GetActiveTimerUseCase`) at update time. There is no separate Widget database.

### 19.7 Today aggregation and recurring occurrences — shared, not duplicated

The Widget's Today view calls the exact same `GetTodayAgendaUseCase` (§14) the App's Today screen calls — same virtual/materialized occurrence resolution (§13), same Goal Impact ranking. Because materialized-occurrence identity is deterministic (B9 — a hash of `recurrenceRuleId` + date), Widget-triggered and App-triggered materialization of "the same" logical occurrence converge on one row regardless of which surface acts first — there is no separate Widget-side Today or recurrence algorithm to keep in sync with the App's.

### 19.8 Timer — authoritative state (clarifies an existing status, adds no new entity)

A running Timer is not a new domain concept: it **is** a `Session` in `IN_PROGRESS` execution status (§25's concept-level status list already defines this; Timer is simply the first feature to fully exploit it). `StartTimerUseCase(activityId)` writes a `sessions` row with `status = IN_PROGRESS`, `startedAt = now()` — if an in-progress Session already exists for that Activity, it's resumed/shown rather than a second one created. `StopTimerUseCase(sessionId)` computes `actual = now() − startedAt` and transitions the Session to `Done`/`Partial` via the same completion path as any other occurrence (§19.3). Elapsed time is **computed at render time**, never persisted as an incrementing counter — the same "no independently mutable running total" principle (§62) applied to Timer specifically, and exactly what makes "authoritative state survives process death/reboot/app close" trivially true: it's a timestamp already in Room, not in-memory state that can be lost. Because App and Widget both read the same `GetActiveTimerUseCase` `Flow<ActiveTimer?>` over that one Session row, a timer started on either surface is the same timer on the other by construction, not by special-casing.

### 19.9 Widget sizes

Glance's `SizeMode.Responsive`/`Exact` selects among composables per size bucket (Small/Medium/Large, per the requested sketch) — all fed by the same `TodayAgenda`/`ActiveTimer` read models; only how much is rendered and which Quick Actions show differs per size. No pixel-perfect layout is locked here, but the architecture is size-agnostic by construction: adding a size is a new Glance composable, never a new data path.

### 19.10 Configuration and multiple instances

Android's AppWidget framework natively keys instances by `appWidgetId`. A `WidgetConfiguration` (`appWidgetId`, `scope: TODAY | FOCUS_ONLY | ACTIVITY(id) | GOAL(id)`, `itemCount`, `enabledQuickActions`) is stored locally in Room, **not synced to the backend** — a widget's placement is a per-device, per-instance arrangement, not account data with cross-device meaning (a widget on a tablet and one on a phone are physically different placements; syncing "scope" between them would be actively wrong). `:feature:widget` looks up its own `appWidgetId`'s configuration on every render. V1 ships a single implicit default (`scope = TODAY`, no configuration UI) — the schema already supports per-instance configuration, so adding a configuration screen later needs no data-model change. Because configuration is stored and read independently per `appWidgetId`, multiple instances cannot mix state by construction (AC9).

### 19.11 Privacy

A per-account Settings field (extending existing Settings, concept §58 / this doc §3) governs how much detail the Widget shows — e.g. hiding a Financial or Weight goal's exact numbers even though the same data is fully visible inside the authenticated App. `:feature:widget`'s render step reads this setting before deciding detail level. The full granular picker isn't built now, but the read seam (Settings → render decision) exists from the start so tightening privacy later doesn't require restructuring the render path.

### 19.12 Analytics

Every analytics event carries a `source: APP | WIDGET` metadata field. **`source` must never appear in any domain calculation branch** — completion math, Goal progress, Pace/Forecast, Life Balance are all defined without reference to where the action originated (§19.3 already guarantees this structurally, since both surfaces call the identical Use Case); `source` exists purely for product analytics, not domain semantics.

### 19.13 Affected-consumer inventory (preflight, per the anti-error standard)

| Consumer | Impact |
|---|---|
| Today | None — Widget reuses `GetTodayAgendaUseCase` unchanged (§19.7) |
| Quick Add | None — Widget's `+ Add` deep-links into the existing Quick Add flow |
| Tasks / Activities / Habits | None — completed via the same Use Cases; B1 (Task's Session-is-canonical rule) applies identically |
| Sessions | No new fields for Widget's sake; `IN_PROGRESS` status (already defined, concept §25) is now actively used by Timer (§19.8) |
| ProgressEvent | None — still a view (B8), computed identically regardless of completion source |
| Recurrence | None — same deterministic occurrence identity (B9) resolves identically from either surface |
| Timer | New: formalized as `StartTimerUseCase`/`StopTimerUseCase`/`GetActiveTimerUseCase` over an `IN_PROGRESS` Session — clarifies an existing status, adds no new persisted entity |
| Room | New: local-only `WidgetConfiguration` table (not synced) |
| Domain Use Cases | New: `StartTimerUseCase`, `StopTimerUseCase`, `GetActiveTimerUseCase`; existing `CompleteOccurrenceUseCase` gains the local-idempotency tightening (§19.4), applying to App and Widget alike |
| Offline Sync | None structurally — Widget writes use the same Room→outbox path; in-progress Sessions sync like any other Session |
| Idempotency | Tightened (§19.4): enforced at the local Use Case/Room layer, not only at sync |
| Analytics | Gains a `source` metadata field (§19.12), explicitly excluded from domain logic |
| Navigation / Deep Links | Reuses existing Navigation Compose deep-link destinations — no new navigation graph |
| Notifications | None — orthogonal; `ReminderScheduler` (§18) unaffected |
| Progress / Pace / Forecast / Life Areas / Life Balance / History / Search | None — unaffected, since nothing about how a Session is created changes what it means once persisted |
| Widget refresh | New (§19.5): `DomainEventBus`, `WidgetRefreshCoordinator` |

No conflicts found with already-approved architecture. The two genuinely new pieces are `DomainEventBus` (§19.5) and the local-idempotency tightening (§19.4) — both additive, both flagged explicitly rather than folded in silently. Timer (§19.8) is not new domain modeling, only the first full use of an already-approved status value.

### 19.14 New interfaces / Use Cases required

- `StartTimerUseCase(activityId)`, `StopTimerUseCase(sessionId)`, `GetActiveTimerUseCase(activityId?): Flow<ActiveTimer?>` — `:domain`.
- `WidgetConfigurationRepository` — interface in `:domain`, implemented in `:data`/`:core:database` (local-only, no `:core:network` involvement, matching the existing repository pattern).
- `DomainEventBus` — defined in `:core:common`; `:domain` Use Cases and `:core:sync` both publish to it.
- `WidgetRefreshCoordinator` — `:feature:widget`-internal, subscribes to `DomainEventBus`.
- No new Repository interface is needed for completion itself — `CompleteOccurrenceUseCase` (already specified) is reused as-is.

### 19.15 Widget Acceptance Criteria ↔ Test Strategy (1:1)

| # | Acceptance Criterion | Test Strategy |
|---|---|---|
| AC1 | App `Done` and Widget `Done` use one domain logic | Unit test invoking `CompleteOccurrenceUseCase` from a simulated ViewModel call-site and a simulated `ActionCallback` call-site; assert byte-identical resulting `Session` row and identical Goal-progress delta — backed by the §27 module-graph rule that makes a second path uncompilable |
| AC2 | Widget `Done` works offline | Sync-layer test with network disabled: invoke the Widget completion path, assert immediate local Room update plus a queued outbox entry — reusing the existing offline test harness (§24), parameterized for a Widget-originated call |
| AC3 | Double-tap never creates a duplicate Session | Idempotency test: invoke `CompleteOccurrenceUseCase` twice in rapid succession for the same occurrence; assert exactly one `Session` row exists (§19.4) — extends the existing idempotency test category |
| AC4 | App and Widget show identical Today state | Contract test: `:feature:today`'s ViewModel and `:feature:widget`'s `WidgetRefreshCoordinator` both collect the same `GetTodayAgendaUseCase` emission in a shared-fixture test and are asserted to render equal `TodayAgenda` values |
| AC5 | A virtual recurring occurrence has one stable identity in App and Widget | Unit test computing the deterministic occurrence ID from two independent call sites, asserting equality; integration test materializing "from Widget" then "from App" for the same occurrence, asserting one resulting row (B9) |
| AC6 | Session edit/delete correctly updates the Widget | Edit/delete a Session; assert `DomainEvent.DataChanged` is published; assert `WidgetRefreshCoordinator` receives it and invokes `update()` (spy/mock on the Glance update call) |
| AC7 | Background sync correctly updates the Widget | Simulate `DomainEvent.SyncCompleted` from a fake `:core:sync`; assert the same refresh path fires and rendered state reflects newly-pulled data |
| AC8 | A Timer started from the Widget is the same Timer after opening the App | Start via the Widget's `StartTimerUseCase` call site; read `GetActiveTimerUseCase`'s `Flow` from a simulated App-side collector; assert identical `sessionId`/`startedAt` (§19.8) |
| AC9 | Multiple Widget instances never mix configuration/state | Create two `WidgetConfiguration` rows for two distinct `appWidgetId`s with different scopes; assert each instance renders only from its own configuration and that updating one never mutates the other's stored row |
| AC10 | Widget never becomes an independent source of truth | Architectural test (module-dependency-graph assertion) asserting `:feature:widget` has no dependency edge to `:core:database` or `:core:network` — a compile-time-enforced invariant, the strongest form this test can take |

## 20. Billing Architecture

Prepares the plumbing without committing to a monetization model, per `/PRODUCT_CANON.md` ("decided later, possibly ads"):
- Google Play Billing Library for purchase flow.
- **Never trust the client's local billing state as entitlement** — a purchase token is verified server-side (a Supabase Edge Function calling the Play Developer API), and the result is written into a `subscriptions` table (status: active/grace_period/on_hold/cancelled/expired) that the app reads as the actual entitlement source of truth.
- Google Play Real-time Developer Notifications (RTDN) → an Edge Function keeps `subscriptions` current without requiring the app to be open.
- Restore purchases: re-query Play Billing and re-verify server-side (covers reinstalls/new devices).
- Grace period/account hold/cancellation/expiration map directly onto Play's own subscription lifecycle states via RTDN — no separate state machine to invent.

## 21. Security

- Supabase Auth JWTs; refresh token in Keystore-backed encrypted storage, never plain `SharedPreferences`.
- HTTPS everywhere; no cleartext traffic permitted (Android network security config).
- **RLS is the real authorization boundary** — every table's policies derive `user_id` from the authenticated JWT; the app is never trusted to say who it is.
- Local Room database relies on Android's app-private storage sandbox for this data's sensitivity level; SQLCipher-based at-rest encryption of the local DB is a reasonable future hardening step, not a day-one requirement.
- Sensitive logs: never log tokens, email, or personal values (weight, financial goal amounts) even in debug builds — an explicit logging facade with an allowlist of loggable fields, not "log the object."
- Crash reporting: scrub PII before it reaches Crashlytics — no raw user content in breadcrumbs/stack traces.
- Secure export: data-export files are written to app-private/cache storage and shared via a scoped `FileProvider` intent, never to public external storage.
- Account deletion: a real cascade delete (or anonymization) across Supabase tables and any Crashlytics/analytics-linked identifiers, not a soft "disabled" flag — required both by `/docs/play-store-checklist.md` and by the account-deletion note in this doc's Billing/Auth sections.

## 22. Performance

Paging 3 (via Room) for History/Session lists rather than loading full history into memory; `LazyColumn` throughout Compose lists; DB indexes on `(user_id, date)` for `sessions`/`tasks` and on the FK columns used in `goal_activity_links`/`session_metric_values` joins; aggregate queries expressed in SQL (`@Query` with `SUM`/`GROUP BY`), never pulled into Kotlin and summed in memory; multi-table writes (Session + MetricValues + outbox entry) wrapped in a single Room transaction; Compose state kept immutable/stable to avoid unnecessary recomposition; Room `Flow` queries scoped narrowly per screen rather than one broad query multiple screens subscribe to and over-recompose from; calculation work dispatched off the main thread (`Dispatchers.Default`/WorkManager), never blocking Compose or a Flow collector.

## 23. Error Handling

A sealed `AppError` hierarchy (`Domain`, `Database`, `Network`, `Auth`, `Sync`, `Billing`, `Validation` subtypes) that the Repository/Use Case layer maps raw exceptions into at the boundary where they're first caught. ViewModels expose UI state built from `AppError`, never a raw `Throwable` — the UI renders by error *category* ("couldn't sync, will retry automatically" for any `Network`/`Sync` error), with the underlying raw exception logged once at the point it was caught, not re-logged at every layer it passes through.

## 24. Testing Architecture

Unit tests (pure Kotlin, JVM, no Android dependency) for `:core:calculation` and `:domain` — fast, exhaustive, table-driven per Goal type (cumulative sum, target-value latest-reading, percentage/milestone-weighted, frequency-per-period; edge cases: zero sessions, a session dated before the goal existed, Cancelled sessions excluded from denominators per §29). Repository tests against fakes. Room DAO tests via `Room.inMemoryDatabaseBuilder`. Migration tests via `MigrationTestHelper`, one per version bump, run in CI on every schema change. Sync tests simulating offline-then-reconnect against a fake backend. Conflict tests covering both tiers of the hybrid strategy (B7/§9): metadata conflicts resolve silently to the newer `updated_at`; a Session/occurrence conflict resolves deterministically *and* preserves the losing write as a recoverable conflict copy rather than discarding it — both asserted explicitly, since "silently correct" and "silently lossy" must never be confused in a test that only checks the winning value. **Idempotency tests are explicitly first-class**: submit the same client-generated Session id twice (simulating a retry/double-tap) and assert exactly one Progress Event fan-out results — this is called out separately because it's the single most product-critical invariant in the app (§24/§60/§68) and the easiest kind of bug to ship silently. ViewModel tests asserting `StateFlow` emissions from fake Use Cases. Compose UI tests for the highest-value flows (Today's one-tap complete, Quick Add). A small number of true end-to-end tests (sign up → create Goal → complete Activity → see Progress update) — expensive, kept few, high value. Widget-specific test strategy (including an architectural test enforcing `:feature:widget`'s module isolation) is specified 1:1 against acceptance criteria in §19.15, not duplicated here.

## 25. Database Migration Strategy

Room: every schema change ships an explicit `Migration` object; `fallbackToDestructiveMigration()` is never used in a production build — this app is explicitly designed to hold years of personal history, and a destructive migration would violate the entire premise of Personal Progress History (§52). Every migration gets a `MigrationTestHelper` test before it ships. Backend: versioned SQL migration files (the existing `/supabase/migrations/` pattern), additive-first — a released app version must keep working against the schema for at least one release cycle after a migration, so drops/renames are staged (add new, dual-write/dual-read if needed, migrate, remove old) rather than done in one step. Rollout: Play Console staged/percentage rollout is safe specifically because migrations are additive-first, so old and new client versions can coexist against the same backend schema during a rollout window.

## 26. Observability

A single structured-logging facade (e.g. Timber) with tagged categories (`sync`, `calculation`, `auth`) so logs are filterable per concern. Firebase Crashlytics for crash reporting (used alongside Supabase for data — orthogonal concerns, no conflict in using both). Analytics-events SDK choice deferred until the monetization/growth strategy is decided (`/PRODUCT_CANON.md`), but the event-emission seam in the domain layer should exist from day one so wiring a provider later doesn't require touching every feature. A debug-only "sync diagnostics" screen (outbox depth, last sync time, last error) — genuinely important for a solo developer debugging real-world sync issues that won't reproduce easily on a dev machine. Debug/verbose logs gated behind `BuildConfig.DEBUG`; never log PII even in debug builds.

## 27. Dependency Rules

- UI (Compose) never imports a Room or Supabase type — only `:core:model`/domain types.
- Feature modules depend on `:domain`, `:core:model`, `:core:designsystem`, `:core:common` — **not** on `:core:database`, `:core:network`, or `:data`. This is enforced at the Gradle level (no dependency edge exists), not just by convention.
- `:domain` depends only on `:core:model` and `:core:calculation` — repository *interfaces* live here, implementations do not.
- `:data` is the only module allowed to depend on both `:core:database` and `:core:network` — it's where the interfaces from `:domain` get implemented and wired.
- `:core:database` and `:core:network` never depend on each other directly; `:core:sync` mediates.
- `:domain` and every feature module depend only on the `ReminderScheduler` interface (§18) — never on `AlarmManager`, `WorkManager`, or FCM types directly; only `:core:notifications` may import those.
- `:feature:widget` follows the same rule as every other feature module — `:domain`, `:core:model`, `:core:designsystem`, `:core:common` only, no exception for it. This single rule is the entire enforcement mechanism behind §19's "no second execution path, no second source of truth."
- `DomainEventBus` (§19.5) lives in `:core:common` specifically so `:domain` (which publishes) and `:core:sync` (which also publishes) never need to depend on each other to share it; `:feature:widget` (which subscribes) reaches it through the same `:core:common` dependency every feature module already has.
- Only `:feature:widget` may import Glance types (`GlanceAppWidget`, `ActionCallback`, etc.) — no other module references them.
- `:core:model`, `:core:calculation`, `:core:common` are plain Kotlin/JVM modules wherever possible (no Android SDK dependency) — this is what keeps a future Kotlin Multiplatform iOS path realistic without a rewrite of the app's actual logic.
- `:app` is the only module allowed to see everything (composition root).

## 28. Architecture Risks

- **supabase-kt maturity**: the Kotlin Multiplatform Supabase client is younger than Firebase's Android SDK; a gap in a needed feature could force a fallback to raw Retrofit/Postgrest calls for that feature (already anticipated in §3, not a blocker, but worth tracking).
- **Hybrid conflict resolution** (B7) is a deliberate simplification for progress-affecting data (version-checked, conflict-copy-preserving, not a full merge) — correct for single-owner data, insufficient if Stepwise ever adds shared/collaborative goals; would need real CRDT-style merging at that point.
- **Exact alarm policy drift**: Android's exact-alarm permission model has tightened before and may again; the `ReminderScheduler` abstraction (§18) contains the blast radius to `:core:notifications`, but the concrete mechanism still needs a live policy check at implementation time (OQ-3).
- **Widget hosting constraints**: Glance/RemoteViews impose real limits (a coarse `updatePeriodMillis` floor, a short `ActionCallback` execution budget, size/complexity ceilings on what a widget can render) that must be checked against current Android documentation before implementation (OQ-4); `:feature:widget`'s isolation (§19.2, §27) keeps any surprise here contained to one module.
- **New cross-cutting refresh mechanism**: `DomainEventBus` (§19.5) is a new piece of shared infrastructure introduced specifically for widget refresh; low risk (additive, one small module) but worth tracking as the first thing of its kind in the architecture, and a candidate other features (e.g. future live cross-device notices) may reuse rather than duplicate.
- **Two-vendor operational surface** (Supabase + Firebase for push/crash) means two dashboards/two things that can have an outage, in exchange for not reinventing push/crash tooling that Firebase already does well.
- **Local-cache/backend-view divergence window**: during sync lag, a locally-cached derived number can transiently disagree with the backend's canonical view; the UI must communicate "syncing," not silently show a number that later changes without explanation.

## 29. Recommended Implementation Sequence

1. **Canonical Data Model Specification** — extends `/DATA_MODEL.md` with the concrete Room schema, Postgres schema, and the sync metadata columns (`id: UUID`, `updated_at`, `version`, `deleted_at`, `sync_status`) introduced here.
2. **Calculation Engine Specification** — the exact formulas per Goal type, pace/forecast math, and the Kotlin/SQL parity requirement from §12/§15.
3. **UX / Navigation Specification** — screen-by-screen detail on top of `/docs/scope-of-work.md`'s feature list and this document's module boundaries, including the Widget's per-size layouts (§19.9) and its configuration UI (§19.10).
4. **Offline & Sync Specification** — the detailed outbox schema, WorkManager worker design, and conflict-resolution edge cases beyond what §9 establishes at the architecture level.
5. **Google Play Release Specification** — building on `/docs/play-store-checklist.md`, now informed by the concrete Billing/notification/account-deletion architecture above.
6. **Implementation.**

---

## ARCHITECTURE DECISION RECORD

| Decision | Choice | Alternatives | Reason | Risks | Reversible? |
|---|---|---|---|---|---|
| Client platform | Native Android — Kotlin + Jetpack Compose | React Native/Expo (Phase 1 decision, now superseded); Flutter | Explicit user direction; best fit for deep platform integration (exact alarms, Play Billing, notification channels) that a cross-platform layer would fight | Loses the earlier RN code-sharing angle with the parked Next.js web app; iOS needs its own build later | Hard — a real rewrite to change later, so this is being locked deliberately now |
| Backend | Supabase (Postgres + Auth + Realtime + RLS) | Firebase; custom backend | Relational model fits the multi-metric/multi-goal calculation engine and the §62 view-based source-of-truth requirement; Firestore's standard aggregation pattern (denormalized counters via Cloud Functions) is the exact anti-pattern §62/§68 forbid | `supabase-kt` less mature than Firebase's Android SDK; smaller ecosystem | Medium — Postgres is a portable, low-lock-in standard |
| DI | Hilt at the Android/app/feature layer; framework-agnostic (plain constructor injection) in `:core:model`/`:core:calculation`/`:domain` | Koin everywhere; Hilt everywhere | Compile-time safety and best Compose/Navigation/WorkManager integration where it matters most; keeping the shared core DI-framework-free preserves a realistic Kotlin Multiplatform path for iOS later | Two DI approaches to understand; contributors must respect the module boundary | Android-layer choice is reversible; the domain-layer discipline is what protects future flexibility |
| Local database | Room | SQLDelight; Realm | Official Jetpack library, best Compose/Flow/WorkManager integration, mature migration tooling | SQLDelight would be more directly portable to a future KMP shared data layer | Medium — swappable later, but costly |
| Push & crash reporting | Firebase Cloud Messaging + Crashlytics, alongside Supabase for data | Fully Supabase-adjacent tooling for both | FCM/Crashlytics are mature, free, and orthogonal to the data-backend choice | Two vendors instead of one | Reversible independently of the data model |
| Conflict resolution | Hybrid: silent version-checked last-write-wins for low-risk metadata; version-checked with a preserved, non-destructive "conflict copy" for progress-affecting entities (Sessions, materialized occurrences); independent new Sessions never conflict at all | Blanket last-write-wins for everything (original proposal, rejected); field-level merge; CRDTs | Blanket LWW risked silently discarding real progress data; full CRDT is unjustified complexity for single-owner-per-account usage; the hybrid gives safety (nothing lost) without a mandatory merge UI | A genuine same-moment edit on two devices still resolves to one "current" value automatically (the loser is recoverable, not surfaced by default) | Upgradeable later (e.g. to a blocking merge UI) without a schema change, since version/timestamp columns and the conflict-copy record already exist |
| Occurrence materialization | Lazy, with **deterministic occurrence identity** (hash of `recurrenceRuleId` + date) — materialize only on first interaction, converging cross-device on one row | Pre-generate all future occurrences; lazy materialization with random IDs (rejected — would let two devices create duplicate rows for the same logical occurrence) | Avoids unbounded row growth while guaranteeing cross-device convergence via the same upsert-by-UUID mechanism already used for Sessions | Slightly more complex read path (merge rule-computed + materialized rows) | Reversible |
| `ProgressEvent` persistence | Not a stored table anywhere (client or backend) — a view/query over `session_metric_values ⋈ goal_activity_links`, computed fresh on read | A Postgres-trigger-maintained `progress_events` table (Phase 1.5 data model); client-synced `progress_events` | A view can't drift from its inputs because it has no independent state; strictly purer under §62 than a materialized table that must stay in sync with a trigger | None material at current expected scale (thousands, not millions, of Sessions per account) | Reversible — a materialized view is a drop-in upgrade behind the same read interface if scale ever demands it |
| Goal progress calculation | Explicit `progressMode` per Goal (`METRIC`/`TARGET_VALUE`/`FREQUENCY`/`MILESTONES`/`TASKS`/`MANUAL`) instead of one formula for all "goal types" | One universal progress formula inferred from Goal type (original proposal, rejected as underspecified for Percentage/Project goals) | §11's Goal types conflate unit/shape with computation method; Percentage/Project goals have no single natural formula (weighted milestones vs. weighted tasks vs. manual are genuinely different modes) | `MANUAL` mode must be kept from silently competing with a computed value for the same Goal (addressed explicitly in B6) | Additive — new modes can be added later without touching existing ones |
| Reminder scheduling | Domain-owned `ReminderScheduler` interface; `:core:notifications` picks the concrete Android mechanism | Domain/feature code calling AlarmManager/WorkManager/FCM directly | Isolates Android/Play policy churn (exact-alarm restrictions especially) to one module; OQ-3 becomes an implementation-time detail behind a stable interface, not a domain-architecture risk | None — pure abstraction, no behavior change | Fully reversible — an interface, not a structural commitment |
| Widget technology | Jetpack Glance, in a `:feature:widget` module with zero dependency on `:core:database`/`:core:network` (§19.1–19.2) | Classic RemoteViews/AppWidgetProvider directly; a hypothetical separate "Widget sync" system | Glance is Google's current recommended approach and fits the Compose-style codebase; the module-boundary rule (not the UI framework choice) is what actually guarantees no second execution path | Glance's API surface and Android's widget-hosting limits change over time — must be verified live before implementation (OQ-4) | Presentation-layer choice, reversible without touching domain/data |
| Widget refresh | Event-driven via a new `DomainEventBus` (§19.5), with a coarse periodic Glance update as a day-boundary safety net | Continuous polling; a Widget-specific push mechanism | Matches the explicit "no unnecessary polling" requirement; reuses the same Use Case success path instead of inventing Widget-specific plumbing | One new cross-cutting piece to maintain | Additive/reversible — a different pub/sub could replace it without touching Use Cases beyond the publish call |
| Timer modeling | A Timer is a `Session` in `IN_PROGRESS` status; elapsed time computed at render time, never persisted as a counter (§19.8) | A separate `Timer`/`ActiveTimer` table with its own running/mutable state | No new domain entity needed — the concept's `IN_PROGRESS` status (concept §25) already covers it; avoids exactly the independently-mutable running total §62 forbids | None material | N/A — a modeling clarification of already-approved status values, not a new commitment |

## OPEN QUESTIONS

- **OQ-1 (Percentage/Project goal math) — CLOSED.** Resolved by the explicit `progressMode` decision (B6): `MILESTONES` → weighted milestones, falling back to `TASKS` → weighted task completion, with `MANUAL` as a distinct, non-competing, explicitly-chosen mode. No longer open.
- **OQ-2 (conflict resolution sign-off) — CLOSED.** Resolved by the hybrid strategy (B7, §9, ADR): silent last-write-wins for metadata, version-checked-with-preserved-conflict-copy for progress-affecting entities, no conflict at all for independent new Sessions. No longer open.
- **OQ-3 — Exact alarm / reminder scheduling policy. Retained, but reframed: an implementation-time external verification requirement, not an unresolved product question.** The product unambiguously wants Task/Habit reminders (§16/§22) — that's settled. What's *not* settled, and can't be settled from the concept or from today's date, is which concrete Android/Play mechanism satisfies that requirement at the moment reminders are actually built, because `SCHEDULE_EXACT_ALARM`/`USE_EXACT_ALARM` policy has changed before and may again. The `ReminderScheduler` interface (§18, B-adjacent, ADR) exists specifically so this check can happen at implementation time — against Play's then-current published policy — without touching domain architecture either way. Nothing here is blocked on it.
- **OQ-4 — Glance / Android Home Screen Widget hosting constraints. Same category as OQ-3: an implementation-time external verification requirement, not an unresolved product question.** The product unambiguously wants a full Home Screen Widget (§19) — that's settled. What needs a live check immediately before implementation, because it changes over time and isn't safe to assume: Jetpack Glance's current API surface, the minimum `updatePeriodMillis` Android enforces, `ActionCallback`'s execution time budget, and any target-SDK-specific widget restrictions. `:feature:widget`'s isolation (§19.2, §27) means this check touches one module, not domain architecture.

No other genuinely open questions remain against the Master Product Concept as written.
