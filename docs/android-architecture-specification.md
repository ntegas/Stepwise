# STEPWISE — ANDROID ARCHITECTURE SPECIFICATION

Canonical technical foundation for Stepwise on Android. Source of truth for product behavior remains `/CLAUDE.md` (Master Product Concept, §1–77) — nothing here overrides it. This document supersedes `/docs/android-stack.md` and `/docs/android-architecture.md`, which were written for a React Native/Expo build; the platform decision has changed to **native Android: Kotlin + Jetpack Compose**, per explicit direction. iOS, App Store, and StoreKit are out of scope for this document by instruction.

This is the prerequisite for, in order: Canonical Data Model Specification, Calculation Engine Specification, UX/Navigation Specification, Offline & Sync Specification, Google Play Release Specification, Implementation. None of those should re-derive the decisions made here.

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
| Execution status (Done/Partial/Missed/Rescheduled/In Progress/Cancelled) is a different axis from lifecycle status (Active/Paused/Completed/Archived) | §13, §25 | Domain model — two enums, never conflated |
| One recurrence engine for Task/Activity/Habit | §30, §33 | Scheduling architecture |
| Offline-first: core actions work with zero connectivity | §59 | Every layer — this is the top structural constraint |
| i18n-ready, no hardcoded strings; units/currency user-configurable | §58 | UI layer, resource strategy |
| No networking/CRM features | §69 | Scope boundary — no contact/relationship domain objects, ever |
| No feature is cut/reinterpreted without explaining impact and getting a decision | §77 | This document's own audit discipline, and all future work |

### B. Ambiguities found, and how they're resolved

**B1. Task's own Planned/Actual/status fields vs. Session as canonical record.**
§16 lists Planned/Actual/status directly as Task fields; §22/§24 say a completion creates a Session. Left unresolved, an app could let both be edited independently — exactly the duplicated-total problem §62 forbids.
**Resolution:** a Task's Planned/Actual/status are *projections* of its own linked Session(s), not an independent record. Completing a Task creates a `sessions` row (`source_type = task`) the same way an Activity/Habit does; the Task's displayed Planned/Actual come from a join, never a second editable copy. This keeps Tasks, Activities, and Habits on one uniform completion pipeline instead of Tasks being a special case.

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

**B6. Percentage/Project goal math — genuinely unresolved, see Open Questions (OQ-1).**

---

## 1. Executive Architecture Summary

Native Android app (Kotlin, Jetpack Compose, Material 3) on an offline-first, layered architecture: Compose UI → ViewModel (StateFlow) → domain Use Cases → Repository → Room (local, operational) with a background Sync Engine reconciling against Supabase (Postgres, canonical, cross-device). Calculations (execution rate, pace, forecast, Life Balance) are pushed to a dedicated, pure-Kotlin Calculation Engine module mirrored by Postgres views server-side, so the same math runs identically on-device (for instant offline stats) and server-side (as the cross-device-consistent canonical answer). Hilt provides DI at the Android/app layer; domain and data-model modules stay framework-agnostic to keep a future Kotlin Multiplatform path to iOS open, per the user's stated Android-first-then-iOS sequencing.

## 2. Architecture Decisions

See the ADR table at the end of this document for the full list with alternatives/reasons/risks. Headline decisions: native Kotlin/Compose (not React Native — supersedes the Phase 1 decision); Supabase over Firebase (relational model fits the calculation engine; Firestore's aggregation pattern conflicts with §62); Hilt for the Android layer with a framework-agnostic domain/data core; Room as local operational store; row-level last-write-wins (via server version) for conflict resolution, justified by the product's single-owner-per-account nature.

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

:core:common              dispatchers, Result/AppError types, date/time utilities — pure Kotlin
:core:model               domain models (Goal, Activity, Session, ...) — pure Kotlin, no Android/Room/network types
:core:calculation         Calculation Engine: execution rate, pace, forecast, Life Balance math — pure Kotlin, heavily unit-tested
:domain                   Use Cases + Repository interfaces — depends on :core:model, :core:calculation

:core:database            Room entities/DAOs/migrations + mappers to/from :core:model — implements repository read/write for local data
:core:network             Supabase/Postgrest client, DTOs + mappers to/from :core:model
:core:sync                outbox, conflict resolution, WorkManager workers — depends on :core:database + :core:network
:data                     Repository implementations — depends on :domain (interfaces), :core:database, :core:network, :core:sync;
                           this is the ONLY module allowed to depend on both :core:database and :core:network at once

:core:designsystem        Compose theme, Material 3 tokens, shared components — depends on nothing but Compose
:core:notifications       local reminder scheduling (AlarmManager/WorkManager) + FCM wrapper

:feature:today, :feature:goals, :feature:plan, :feature:progress,
:feature:activities, :feature:habits, :feature:vision (Vision/Life Areas/Life Plan/Life Balance),
:feature:tasks-inbox, :feature:search, :feature:archive, :feature:settings,
:feature:onboarding-auth, :feature:quickadd
                           each depends on :domain, :core:model, :core:designsystem, :core:common —
                           NEVER on :core:database, :core:network, or :data directly
```

The example above is Stepwise's actual module set, not a generic template — it follows §70's screen list, merged where screens obviously share one feature module's domain (e.g. Vision/Life Plan/Life Area/Life Balance all live under `:feature:vision` since they're one strategic-layer concern per §5–8) and split out where a concern is genuinely cross-cutting (Search and Archive touch every entity, so they're their own modules rather than duplicated per-feature).

## 6. Domain Architecture

`:core:model` holds the entities enumerated in `/CLAUDE.md` §64 (User, Vision, LifeArea, Goal, Milestone, Project, Task, Activity, Habit, Schedule/RecurrenceRule, Session, ProgressEvent, Metric, MetricValue, GoalActivityLink, Skill, Reminder, WeeklyReview, Insight/Recommendation) as plain Kotlin data classes — no Room `@Entity`, no network `@Serializable` on the same class. `:core:database` and `:core:network` each have their own representations and map to/from `:core:model` at their boundary. This is deliberate duplication in exchange for a real guarantee: a Room schema migration or a backend DTO change can never silently change what the domain layer — and therefore every ViewModel and every calculation — thinks a Goal *is*.

Two status axes live on `Goal`/`Task` as separate enums per §13/§25, never merged into one: `LifecycleStatus` (Active/Paused/Completed/Archived) and `ExecutionStatus` (Done/Partial/Missed/Rescheduled/InProgress/Cancelled) — the former describes the Goal/Task itself, the latter describes a specific Session/occurrence.

## 7. Persistence Architecture

Four distinct layers, per the explicit ask to separate them — this is the answer to "local operational source of truth vs. domain event source of truth vs. backend persistence vs. derived analytics":

- **Domain event source of truth**: the append-oriented fact of what happened — a `Session` + its `SessionMetricValue`s, identified by a client-generated UUID. This *is* the fact; it means the same thing whether it currently lives only on-device or has reached the backend.
- **Local operational source of truth (Room)**: what the UI actually reads and writes for instant, offline-capable operation. Mirrors the domain events locally, plus may hold a locally-materialized cache of frequently-read numbers (e.g. "this Goal's current progress" for the Goals screen) purely for read performance — that cache is explicitly non-canonical and rebuildable from local Sessions at any time; nothing downstream treats it as authoritative.
- **Backend persistence (Supabase/Postgres)**: the durable, cross-device copy of the same domain events, plus the canonical Postgres views (goal progress, rollups, pace, forecast — see `/docs/data-model.md`) computed from them. This is the arbiter when devices disagree, and what a new device/reinstall bootstraps from.
- **Derived analytics**: anything computed *from* the above for display, on-device or from a backend view — never itself an independent fact. If a local cached number and the backend's view momentarily disagree (sync lag), the backend view wins and the local cache is refreshed to match once sync completes; the UI should show a subtle "syncing" affordance rather than silently presenting stale derived numbers as final.

No entity keeps an independently-editable running total anywhere in this stack (§62) — the local cache above is a read-optimization, not a second place a fact can be entered or corrected.

## 8. Offline-First Strategy

Every core action (§59: marking a Session, creating a Task, editing History) writes to Room synchronously and returns immediately — the UI never waits on a network round trip for a normal action. That local write also enqueues an outbox entry (§9). Reads for Today/Goals/Plan/Progress come from Room (via Flow) so the screens render instantly from whatever was last synced plus anything done locally since, without waiting for connectivity. A slim "sync status" indicator (not a blocking spinner) reflects outbox depth/last successful sync.

## 9. Sync Architecture

- **Local-first writes**: every mutation lands in Room first, in the same transaction as an outbox row describing it (entity type, entity id, operation, a snapshot of the row, `attempt_count`, `last_error`).
- **Dirty state**: syncable rows carry `sync_status: synced | pending | conflict` and `local_updated_at`; the outbox is the authoritative work list, `sync_status` is a derived display convenience.
- **Client-generated IDs**: every syncable entity's primary key is a UUID generated at creation time, in both Room and Postgres — this is what makes sync an idempotent upsert rather than an insert that could double under retry (§60, resolved in B4 above).
- **Tombstones**: deletes set `deleted_at` rather than physically removing the row, both locally and on the backend; the tombstone syncs like any other change, and a device that was offline during the delete finds out about it instead of resurrecting the row. Physical purge happens after a retention window once all of the user's devices have acknowledged the tombstone (tracked via each device's last-synced-at watermark).
- **Retries/backoff**: a WorkManager periodic + expedited worker drains the outbox; WorkManager's built-in exponential backoff policy handles retry timing; the outbox itself is Room-backed so it survives process death/reboot without WorkManager's own persistence doing double duty.
- **Server acknowledgement**: a successful upsert response clears the outbox entry and flips `sync_status` to `synced`; a rejected write (validation failure, not a transient network error) surfaces as a user-visible "sync issue" rather than retrying forever.
- **Timestamps & versioning**: every syncable row has a server-assigned `updated_at` (Postgres trigger, `now()` on write) and a monotonically incremented `version` integer. Clients never use their own wall clock for conflict ordering — client timestamps are display-only ("you did this at 7pm"), never used to resolve a conflict, because client clocks can drift or be wrong.
- **Conflict detection**: a client pushes its last-known `version` alongside a write; if the server's current `version` has moved past that, it's a conflict.
- **Conflict resolution**: row-level last-write-wins keyed on the server's `updated_at`. Deliberately not field-level merge or a CRDT — see ADR row "Conflict resolution" and Open Questions (OQ-2) for the reasoning and the explicit ask for sign-off on this simplification.
- **Editing the same entity on multiple devices**: covered by the above — expected to be rare (single-owner data), resolved by last-write-wins rather than a merge UI.
- **Recurring data**: only materialized occurrence rows and the `RecurrenceRule` itself sync; computed (not-yet-materialized) occurrences are a deterministic function of the rule and never need to be transmitted.
- **Progress Event duplication**: structurally impossible in normal operation — the client never writes `progress_events`, only `sessions`/`session_metric_values`; the server-side derivation trigger is keyed on `session_metric_value_id`, so replays are no-ops (B5 above).
- **Clock drift**: irrelevant to correctness by construction, since conflict resolution never uses client clocks.
- **Failed sync recovery**: the Room-backed outbox is the recovery mechanism — on next app start or connectivity change, WorkManager resumes draining it from wherever it left off.

## 10. Backend Architecture

Supabase: Postgres (schema per `/docs/data-model.md`, to be extended with the sync metadata columns above in the Canonical Data Model doc), Auth (email/password + Google Sign-In), Realtime (used sparingly — mainly to let a second open device pick up a change without waiting for its own next poll, not as the primary sync transport, which is the pull/push outbox model above), Row Level Security as the sole authorization boundary (the server never trusts a client-supplied `user_id`; RLS derives it from the authenticated JWT on every query), Edge Functions for anything that must run with elevated trust: Play purchase-token verification, RTDN webhook handling, account-deletion cascade.

## 11. Authentication

Email/password and Google Sign-In (via Credential Manager, not the deprecated legacy Google Sign-In SDK), both backed by Supabase Auth. Refresh token stored via Android Keystore-backed encrypted storage (`androidx.security.crypto`); silent refresh on app start. A previously-authenticated session keeps working offline (cached JWT + local Room data) — the app should not force a re-login just because it's offline. Logout clears local encrypted tokens and offers a choice to also clear the local Room cache (relevant on shared devices) or keep it for a faster next login. Multiple devices are a normal case (same account, several phones/tablets), not an edge case — this is exactly why §9's conflict handling exists.

## 12. Progress Event Architecture

Client writes `sessions` + `session_metric_values` only (never `progress_events` directly, per B5). A Postgres trigger on `session_metric_values` insert/update/delete:
1. Joins `goal_activity_links` on `(source_type, source_id, metric_id)` to find every Goal that cares about this metric from this Activity/Habit.
2. Writes/updates/removes one `progress_events` row per match.
3. Because Goal progress is a *view* over `progress_events` (§7, `/docs/data-model.md`), the Goal's number is correct the instant the trigger runs — no separate "recompute the goal" step exists to forget.

On-device, the same fan-out logic runs in `:core:calculation` against local Room data immediately after a local write, purely to update the locally-materialized read cache (§7) for instant UI feedback before sync round-trips to the server and back. The two implementations (Kotlin, SQL) must be kept behaviorally identical — this is exactly why `:core:calculation` gets the deepest test coverage in the whole app (§22 Testing Architecture).

## 13. Scheduling / Recurrence Architecture

One `Schedule`/`RecurrenceRule` engine serves Task, Activity, and Habit (§30, §33) — an owner-polymorphic table (`owner_type`, `owner_id`, `rule`), where `rule` supports: daily, weekly, selected weekdays, every-N-days, monthly, and a bounded custom form; start date; optional end date; per-occurrence exceptions (skip/reschedule a single instance without touching the rule). Occurrence resolution (§B3): computed on read for display, materialized into a real row only on first interaction with that specific occurrence. Today's agenda (§14) and Plan/Calendar both call the same occurrence-resolution function — there is exactly one place this logic lives, not one per screen.

## 14. Today Aggregation Architecture

Today's data comes from several sources (scheduled Task occurrences, planned Activity occurrences, Habit occurrences, deadlines, Overdue items) but the UI must receive one unified model. A `GetTodayAgenda` Use Case in `:domain` is responsible for merging these into a single ordered list of `TodayItem` (a `:core:model` type with a `kind` discriminator — task/activity/habit/overdue) before the ViewModel ever sees it; ranking uses each item's Goal Impact Score (§50) where present. The UI (`:feature:today`) only ever consumes `List<TodayItem>` — it has no idea that four different domain concepts fed into it, which is what keeps Today's screen code simple despite the aggregation being non-trivial (mirrors §2's "complex inside, simple outside" principle at the code level, not just the UX level).

## 15. Analytics Architecture

- **On demand (not cached)**: anything cheap to compute from an already-indexed query — a single Goal's current progress, a single Activity's this-week total.
- **Cached (locally materialized, rebuildable)**: numbers read very frequently relative to how often they change — Today's agenda, the Goals list's progress bars. Invalidated and recomputed whenever a relevant local write happens (§7's local cache).
- **Materialized as SQL views, not app code (server-side)**: period rollups, pace, forecast, execution rate, Life Balance figures — computed by Postgres views over `sessions`/`session_metric_values`/`progress_events`, per `/docs/data-model.md`, so they're correct by construction rather than by an app-level recompute step.
- **Incrementally updated**: none of the above need incremental/streaming update logic — because nothing is a running total that gets incremented, everything is either a cheap direct query or a view recomputed from the (indexed) event log, the "how do we keep an incremental counter correct" problem simply doesn't arise. This is a direct consequence of the §62 source-of-truth decision, not a separate design.

## 16. Life Balance Architecture

Attention/Execution/Goal Progress/Trend (§8, §63) are computed and surfaced as four separate fields on a `LifeAreaBalance` view/model, never combined. Same period parameterization (Week/Month/Year/All Time) as every other analytics surface — one query shape, filtered by `life_area_id` instead of `goal_id`/`activity_id`.

## 17. Search Architecture

Global Search (§56) needs to query across Goal/Activity/Task/Project/Habit/History titles and, later, notes/descriptions. Room supports FTS4/FTS5 virtual tables; a `search_index` FTS table populated by triggers (or a lightweight sync-time indexer) on the underlying entities keeps search local-first and instant, consistent with the rest of the offline-first design — no server round trip needed for search.

## 18. Notifications

- **Local reminders** (Task/Habit, §16/§22): `AlarmManager` exact alarms where the OS/policy allows it; Android 12+ restricts `SCHEDULE_EXACT_ALARM`, so the implementation must check the current permission state and fall back to an inexact `WorkManager`-scheduled check where exact alarms aren't available — this is a live policy area (see Open Questions, OQ-3) and must be re-verified against current Play policy before locking the reminder flow.
- **Remote push (FCM)**: reserved for server-initiated, cross-device notifications (re-engagement, future sync-driven notices) — not required for local reminders to work. Register an FCM token per device at auth time regardless, so it's available whenever a remote-push feature is built, without a later migration.
- **Notification channels**: split by category (Task reminders, Habit reminders, Weekly Review ready, Insights/Recommendations) so a user can mute one category without muting all.
- **Runtime permission**: `POST_NOTIFICATIONS` (Android 13+) requested contextually — when the user sets their first reminder — not on first app launch.

## 19. Billing Architecture

Prepares the plumbing without committing to a monetization model, per `/CLAUDE.md` ("decided later, possibly ads"):
- Google Play Billing Library for purchase flow.
- **Never trust the client's local billing state as entitlement** — a purchase token is verified server-side (a Supabase Edge Function calling the Play Developer API), and the result is written into a `subscriptions` table (status: active/grace_period/on_hold/cancelled/expired) that the app reads as the actual entitlement source of truth.
- Google Play Real-time Developer Notifications (RTDN) → an Edge Function keeps `subscriptions` current without requiring the app to be open.
- Restore purchases: re-query Play Billing and re-verify server-side (covers reinstalls/new devices).
- Grace period/account hold/cancellation/expiration map directly onto Play's own subscription lifecycle states via RTDN — no separate state machine to invent.

## 20. Security

- Supabase Auth JWTs; refresh token in Keystore-backed encrypted storage, never plain `SharedPreferences`.
- HTTPS everywhere; no cleartext traffic permitted (Android network security config).
- **RLS is the real authorization boundary** — every table's policies derive `user_id` from the authenticated JWT; the app is never trusted to say who it is.
- Local Room database relies on Android's app-private storage sandbox for this data's sensitivity level; SQLCipher-based at-rest encryption of the local DB is a reasonable future hardening step, not a day-one requirement.
- Sensitive logs: never log tokens, email, or personal values (weight, financial goal amounts) even in debug builds — an explicit logging facade with an allowlist of loggable fields, not "log the object."
- Crash reporting: scrub PII before it reaches Crashlytics — no raw user content in breadcrumbs/stack traces.
- Secure export: data-export files are written to app-private/cache storage and shared via a scoped `FileProvider` intent, never to public external storage.
- Account deletion: a real cascade delete (or anonymization) across Supabase tables and any Crashlytics/analytics-linked identifiers, not a soft "disabled" flag — required both by `/docs/play-store-checklist.md` and by the account-deletion note in this doc's Billing/Auth sections.

## 21. Performance

Paging 3 (via Room) for History/Session lists rather than loading full history into memory; `LazyColumn` throughout Compose lists; DB indexes on `(user_id, date)` for `sessions`/`tasks` and on the FK columns used in `goal_activity_links`/`session_metric_values` joins; aggregate queries expressed in SQL (`@Query` with `SUM`/`GROUP BY`), never pulled into Kotlin and summed in memory; multi-table writes (Session + MetricValues + outbox entry) wrapped in a single Room transaction; Compose state kept immutable/stable to avoid unnecessary recomposition; Room `Flow` queries scoped narrowly per screen rather than one broad query multiple screens subscribe to and over-recompose from; calculation work dispatched off the main thread (`Dispatchers.Default`/WorkManager), never blocking Compose or a Flow collector.

## 22. Error Handling

A sealed `AppError` hierarchy (`Domain`, `Database`, `Network`, `Auth`, `Sync`, `Billing`, `Validation` subtypes) that the Repository/Use Case layer maps raw exceptions into at the boundary where they're first caught. ViewModels expose UI state built from `AppError`, never a raw `Throwable` — the UI renders by error *category* ("couldn't sync, will retry automatically" for any `Network`/`Sync` error), with the underlying raw exception logged once at the point it was caught, not re-logged at every layer it passes through.

## 23. Testing Architecture

Unit tests (pure Kotlin, JVM, no Android dependency) for `:core:calculation` and `:domain` — fast, exhaustive, table-driven per Goal type (cumulative sum, target-value latest-reading, percentage/milestone-weighted, frequency-per-period; edge cases: zero sessions, a session dated before the goal existed, Cancelled sessions excluded from denominators per §29). Repository tests against fakes. Room DAO tests via `Room.inMemoryDatabaseBuilder`. Migration tests via `MigrationTestHelper`, one per version bump, run in CI on every schema change. Sync tests simulating offline-then-reconnect against a fake backend. Conflict tests simulating two devices editing the same row, asserting the documented last-write-wins outcome. **Idempotency tests are explicitly first-class**: submit the same client-generated Session id twice (simulating a retry/double-tap) and assert exactly one Progress Event fan-out results — this is called out separately because it's the single most product-critical invariant in the app (§24/§60/§68) and the easiest kind of bug to ship silently. ViewModel tests asserting `StateFlow` emissions from fake Use Cases. Compose UI tests for the highest-value flows (Today's one-tap complete, Quick Add). A small number of true end-to-end tests (sign up → create Goal → complete Activity → see Progress update) — expensive, kept few, high value.

## 24. Database Migration Strategy

Room: every schema change ships an explicit `Migration` object; `fallbackToDestructiveMigration()` is never used in a production build — this app is explicitly designed to hold years of personal history, and a destructive migration would violate the entire premise of Personal Progress History (§52). Every migration gets a `MigrationTestHelper` test before it ships. Backend: versioned SQL migration files (the existing `/supabase/migrations/` pattern), additive-first — a released app version must keep working against the schema for at least one release cycle after a migration, so drops/renames are staged (add new, dual-write/dual-read if needed, migrate, remove old) rather than done in one step. Rollout: Play Console staged/percentage rollout is safe specifically because migrations are additive-first, so old and new client versions can coexist against the same backend schema during a rollout window.

## 25. Observability

A single structured-logging facade (e.g. Timber) with tagged categories (`sync`, `calculation`, `auth`) so logs are filterable per concern. Firebase Crashlytics for crash reporting (used alongside Supabase for data — orthogonal concerns, no conflict in using both). Analytics-events SDK choice deferred until the monetization/growth strategy is decided (`/CLAUDE.md`), but the event-emission seam in the domain layer should exist from day one so wiring a provider later doesn't require touching every feature. A debug-only "sync diagnostics" screen (outbox depth, last sync time, last error) — genuinely important for a solo developer debugging real-world sync issues that won't reproduce easily on a dev machine. Debug/verbose logs gated behind `BuildConfig.DEBUG`; never log PII even in debug builds.

## 26. Dependency Rules

- UI (Compose) never imports a Room or Supabase type — only `:core:model`/domain types.
- Feature modules depend on `:domain`, `:core:model`, `:core:designsystem`, `:core:common` — **not** on `:core:database`, `:core:network`, or `:data`. This is enforced at the Gradle level (no dependency edge exists), not just by convention.
- `:domain` depends only on `:core:model` and `:core:calculation` — repository *interfaces* live here, implementations do not.
- `:data` is the only module allowed to depend on both `:core:database` and `:core:network` — it's where the interfaces from `:domain` get implemented and wired.
- `:core:database` and `:core:network` never depend on each other directly; `:core:sync` mediates.
- `:core:model`, `:core:calculation`, `:core:common` are plain Kotlin/JVM modules wherever possible (no Android SDK dependency) — this is what keeps a future Kotlin Multiplatform iOS path realistic without a rewrite of the app's actual logic.
- `:app` is the only module allowed to see everything (composition root).

## 27. Architecture Risks

- **supabase-kt maturity**: the Kotlin Multiplatform Supabase client is younger than Firebase's Android SDK; a gap in a needed feature could force a fallback to raw Retrofit/Postgrest calls for that feature (already anticipated in §3, not a blocker, but worth tracking).
- **Last-write-wins conflict resolution** is a deliberate simplification (see OQ-2) — acceptable for single-owner data, wrong if Stepwise ever adds shared/collaborative goals.
- **Exact alarm policy drift**: Android's exact-alarm permission model has tightened before and may again; the reminder implementation needs a live policy check before being locked in, not an assumption baked in now (OQ-3).
- **Two-vendor operational surface** (Supabase + Firebase for push/crash) means two dashboards/two things that can have an outage, in exchange for not reinventing push/crash tooling that Firebase already does well.
- **Local-cache/backend-view divergence window**: during sync lag, a locally-cached derived number can transiently disagree with the backend's canonical view; the UI must communicate "syncing," not silently show a number that later changes without explanation.

## 28. Recommended Implementation Sequence

1. **Canonical Data Model Specification** — extends `/docs/data-model.md` with the concrete Room schema, Postgres schema, and the sync metadata columns (`id: UUID`, `updated_at`, `version`, `deleted_at`, `sync_status`) introduced here.
2. **Calculation Engine Specification** — the exact formulas per Goal type, pace/forecast math, and the Kotlin/SQL parity requirement from §12/§15.
3. **UX / Navigation Specification** — screen-by-screen detail on top of `/docs/scope-of-work.md`'s feature list and this document's module boundaries.
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
| Conflict resolution | Row-level last-write-wins via server `version`/`updated_at` | Field-level merge; CRDTs | Product is single-owner-per-account with rare true concurrent edits; full CRDT complexity isn't justified by the actual usage pattern | A genuine same-moment edit on two devices picks one arbitrarily, silently | Upgradeable later without a schema change, since version/timestamp columns already exist — flagged in Open Questions for explicit sign-off |
| Occurrence materialization | Lazy — materialize a recurring item's occurrence only on first interaction with it | Pre-generate all future occurrences | Avoids unbounded row growth for far-future recurring rules while keeping Overdue/Rescheduled/Missed meaningful per instance | Slightly more complex read path (merge rule-computed + materialized rows) | Reversible |
| Progress Event writes | Server-only, derived by a Postgres trigger from `session_metric_values`; never client-written | Client computes and syncs `progress_events` directly | Removes an entire category of sync-duplication risk by construction | Requires the trigger logic to be correct and well-tested (mirrored by `:core:calculation` for local optimistic display) | Reversible with a migration, but not worth reversing |

## OPEN QUESTIONS

Only the ones that genuinely can't be resolved from the Master Product Concept as written:

- **OQ-1 — Percentage/Project goal math.** §11's "Launch Stepwise — 68%" example doesn't specify what produces the 68%: a weighted average of that Goal's Milestones (§14 shows Milestones with their own percentages), a completion ratio of Tasks/Projects under the Goal, or a value the user sets directly (which would need an explicit, documented exception to §62's "no manually-set totals" rule, since this is the one Goal type where a manual percentage might genuinely make sense). This needs a decision before the Calculation Engine Specification can define this Goal type's formula.
- **OQ-2 — Sign-off on last-write-wins conflict resolution.** This document recommends row-level last-write-wins (§9, ADR) as sufficient given Stepwise's single-owner-per-account usage pattern, explicitly instead of field-level merge or CRDT-style resolution. This is a real scope decision, not something the concept states either way — needs explicit confirmation before the Offline & Sync Specification locks it in, since it's much cheaper to build correctly the first time than to retrofit finer-grained merging later.
- **OQ-3 — Exact alarm permission strategy.** Android's exact-alarm policy (`SCHEDULE_EXACT_ALARM`/`USE_EXACT_ALARM`) has changed before and is the kind of thing that needs a live check against current Play policy at the time reminders are actually implemented, not an assumption locked in today — flagged here so it isn't forgotten, not because the concept is ambiguous about wanting reminders (it isn't — §16/§22 clearly want them).
