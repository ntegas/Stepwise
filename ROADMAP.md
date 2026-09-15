# ROADMAP.md — Stepwise Development Roadmap & Operating Process

This is the single authoritative implementation sequence for Stepwise: the DEV-000...DEV-037 task list, how work moves through it, and the process rules (commit discipline, branch safety, multi-agent coordination, product-owner interaction) that govern *sequencing*, as opposed to `/DEVELOPMENT_PROTOCOL.md`, which governs how code itself must be structured. `DEVELOPMENT_PROTOCOL.md` carries a canonical-reference pointer to this file rather than duplicating the roadmap.

Live position (current task, blockers, decisions made, verification debt) is tracked in `/PROJECT_STATE.md`, not here — this file defines the sequence and the rules of the road; `PROJECT_STATE.md` says where we currently are on it. Historical narrative of what happened at each stage goes in `/DEVELOPMENT_LOG.md`. The audit/change-management process itself (anti-duplication search, consistency audit, affected-consumer inventory, never-change-silently rule, retrospectives) is `/ANTI_ERROR_STANDARD.md` — this file assumes that process and does not restate it.

This document originates from the user's "Master Autonomous Development Plan," corrected by the explicit decisions in the round that also produced `/ANTI_ERROR_STANDARD.md` §0/§10 (see `/DEVELOPMENT_LOG.md`, Phase 1.8): canonical files stay root-level (not nested under `docs/`), and the roadmap lives here rather than inside `DEVELOPMENT_PROTOCOL.md`.

## Operating Mode

Work continuously through the approved DEV sequence below. For every DEV task, the cycle is:

```text
PREFLIGHT → IMPACT ANALYSIS → IMPLEMENTATION → TESTS → REGRESSION → DIFF REVIEW → DOCUMENTATION → COMMIT → NEXT TASK
```

The product owner does not need to manually approve routine implementation details already determined by canonical documentation. Proceeding automatically to the next DEV task is allowed when:

- acceptance criteria are satisfied;
- relevant tests pass;
- no unresolved architectural contradiction exists;
- no security blocker exists;
- no destructive migration/data-loss risk exists;
- no unresolved product-semantic decision is required.

Stop and request a decision when proceeding would require guessing about: product semantics; canonical data ownership; destructive data migration; security/trust boundaries; authorization; subscription/entitlement semantics; major architecture changes; irreversible behavior; or a contradiction between canonical specifications. Never silently choose an interpretation in these cases — surface it per `/ANTI_ERROR_STANDARD.md` §4.

## Environment & Verification Debt

This sandbox has JDK 21 and Gradle 8.14.3 but no Android SDK, and the official SDK distribution host (`dl.google.com`) is blocked by the outbound proxy's organization policy — confirmed by direct connection attempt and by the proxy's own status endpoint, not assumed. This was checked, not accepted as a default: an official-channel install was attempted first (per the product owner's explicit instruction to try before declaring a limitation), using no mirrors, no unofficial archives, no TLS bypass, and no unverified scripts. Recorded verbatim as required:

```text
ENVIRONMENT LIMITATION:
Android SDK unavailable in current Claude execution environment.
```

Consequences:

- Every Android-specific claim is labeled either `VERIFIED` or `NOT VERIFIED — ANDROID SDK REQUIRED` — never asserted as tested/working/passed without actual execution having occurred.
- `PROJECT_STATE.md` carries a **VERIFICATION DEBT** section, per DEV-task, listing exactly which Android-specific checks are outstanding, e.g.:
  ```text
  VERIFICATION DEBT

  DEV-003
  - Compose compilation — NOT VERIFIED

  DEV-005
  - Room Android integration tests — NOT VERIFIED
  ```
  A DEV task is not production-verified while a required Android-specific check for it remains `NOT VERIFIED`.
- **GitHub Actions is the authoritative independent Android verification environment**, built out starting DEV-001/DEV-002 using official/widely-trusted actions, pinned versions where appropriate. Minimally, as relevant code appears: Gradle build, Android compilation, unit tests, lint, static analysis — then later Room/migration tests, instrumentation tests where justified, security checks, and release build verification. Heavy CI jobs are added only when actually needed, not speculatively.
- Division of labor: this sandbox does implementation, pure-JVM tests (`:core:model`, `:core:calculation`, `:domain` — deliberately Android-SDK-free), and local static analysis; GitHub CI does Android SDK compilation and Android-specific checks.
- Development is not blocked entirely by the missing SDK — DEV tasks (and parts of DEV tasks) that can be correctly implemented and verified without it proceed. But a large volume of unverified Android-specific code must not accumulate; a working Android CI becomes a mandatory quality gate once an Android-heavy stage is reached.
- If the environment changes (SDK becomes installable, or CI comes online), this section and the VERIFICATION DEBT list in `PROJECT_STATE.md` are updated to close out debt as it's actually resolved — not preemptively.

## Development Roadmap

### DEV-000 — Repository & Architecture Preflight
Audit the actual repository: branches/current working state, Gradle structure, Android configuration, modules, package structure, dependencies, existing domain models, Room/database, repositories, Supabase/backend, navigation, Compose UI, tests, CI, security configuration, documentation, obsolete React Native artifacts, duplicated responsibilities, unfinished/generated code, hardcoded assumptions, existing technical debt. No broad refactoring during the audit — produce repository-grounded findings only.

### DEV-001 — Development Foundation
Establish only justified foundations: Gradle conventions, module boundaries, Hilt, dependency governance, environment configuration, `Clock`, IDs, application errors, logging, coroutine conventions, architectural contracts, repository interfaces where justified. No speculative infrastructure.

### DEV-002 — Security & Quality Foundation
Establish security documentation, trust boundaries, secret policy, production/debug separation, static analysis, lint, dependency scanning, secret scanning, CI quality gates, security test foundation, dependency policy. Threat-model major attack surfaces.

### DEV-003 — Design System Foundation
Build semantic color tokens, typography, spacing, shapes, elevation, motion, light/dark theme, core reusable components. Representative previews/tests where appropriate. Do not redesign every screen yet.

### DEV-004 — Canonical Data Model *(checkpoint)*
Design and document canonical domain semantics for Vision, Life Area, Goal, Goal Metric, Goal progress mode, Milestone, Project, Task, Activity, Habit, Session, Session metrics, schedules, recurrence, occurrence identity, execution states, relationships, sync metadata where appropriate. Explicitly mark each as CANONICAL / DERIVED / LOCAL STATE / SYNC METADATA — no ambiguous ownership. Major architectural checkpoint.

### DEV-005 — Room Persistence Foundation
Implement entities, relations, DAOs, indexes, converters, domain mappers, transactions, database configuration, migration strategy, database tests. Database entities stay separate from domain models.

### DEV-006 — Repository & Offline Data Layer
Implement repository contracts/implementations, local-first reads, local mutation pipeline, transaction coordination, outbox/sync preparation, error mapping, reactive flows. UI must not depend directly on Room.

### DEV-007 — Authentication & Supabase Foundation
Implement secure authentication, session handling, backend schema, ownership, RLS, authorization, environment isolation. Perform adversarial RLS tests. Security level: **S3**.

### DEV-008 — Sync Engine
Implement stable identities, versions/revisions, outbox, retry, tombstones, merge semantics, conflict detection, idempotency, offline synchronization, multi-device tests. No universal last-write-wins. Security/data-integrity critical.

### DEV-009 — Canonical Execution Engine *(checkpoint)*
Implement shared execution commands/use cases for Done, Partial, Missed, Rescheduled, In Progress, Cancelled, Session creation/edit/delete, occurrence execution, idempotency, transaction boundaries. All execution surfaces must eventually use this engine.

### DEV-010 — Recurrence Engine
Implement canonical recurrence rules, occurrence generation, lazy materialization, exceptions, skip, reschedule, stable logical identity, timezone semantics, duplicate prevention. Extensively test date boundaries.

### DEV-011 — Goals Domain
Implement goal lifecycle, goal types, `progressMode`, metrics, target/deadline, milestones/projects relationships, validation. Keep simple goal creation simple.

### DEV-012 — Activities & Sessions
Implement Activity lifecycle, Activity↔Goals, Session creation/history, multi-metric Sessions, edits/deletes, recalculation effects.

### DEV-013 — Tasks & Habits
Implement tasks, planned values, canonical actual derivation, habit rules, habit→Activity/Session execution, recurrence integration. Avoid duplicated execution logic.

### DEV-014 — Today Engine
Create the canonical Today Aggregator: Focus, tasks, activities, habits, relevant occurrences, overdue. Today UI and Widget will consume this same engine.

### DEV-015 — First Complete UI Vertical Flow *(checkpoint)*
Build one coherent real flow: Today → item → execute → canonical Session → Goal progress update → Progress representation. First major visual/product checkpoint. Use the Design System. Do not bypass domain architecture for UI convenience.

### DEV-016 — Goals UI
Implement complete Goals UX, with progressive disclosure for advanced settings.

### DEV-017 — Plan / Calendar UI
Implement Day/Week/Month/Year views using canonical scheduling/recurrence.

### DEV-018 — Activities / Habits UI
Implement activity/session/habit management and history.

### DEV-019 — Progress Calculation Engine
Implement central calculations for Week/Month/Year/All Time, Activity statistics, Planned vs Actual, Execution, trends, consistency, heatmap/cumulative data where justified.

### DEV-020 — Pace & Forecast
Implement explainable remaining/required pace/current pace/ahead-behind/forecast. Test edge cases: overdue deadlines, zero history, completed goals.

### DEV-021 — My Vision / Life Areas / Life Plan
Implement the strategic planning layer without burdening daily execution.

### DEV-022 — Life Balance *(checkpoint)*
Implement descriptive analytics for Attention, Execution, Goal Progress, Trend. Avoid pseudo-scientific life scoring.

### DEV-023 — Projects & Milestones
Complete complex goal decomposition UX and domain integration.

### DEV-024 — History / Review / Insights
Implement useful historical review and periodic reflection without duplicating analytics.

### DEV-025 — Search / Inbox / Quick Add
Implement fast capture and discovery. Preserve: capture now, organize later.

### DEV-026 — Timer Engine
Implement authoritative timer state. Integrate timer completion into canonical Session creation. Support manual Session entry.

### DEV-027 — Notifications & Reminders
Implement centralized reminder/notification architecture. Verify current Android/Google Play restrictions before selecting restricted mechanisms.

### DEV-028 — Android Home Screen Widget
Implement the production widget, sharing the Today Aggregator, Execution Engine, Timer Engine, and canonical IDs. Support appropriate widget sizes/configurations. Widget must work offline. No duplicate execution architecture.

### DEV-029 — Billing & Entitlements
Implement Google Play subscriptions and entitlement architecture. Security level **S3**. Verify current Billing requirements immediately before implementation.

### DEV-030 — Settings / Account / Export / Delete *(checkpoint)*
Implement settings and account lifecycle including privacy/data operations. Security-sensitive destructive flows require tests.

### DEV-031 — Offline & Conflict Hardening
Perform adversarial offline testing: long offline period, multiple devices, simultaneous independent Sessions, same-Session edits, deletion while another device is stale, retries, duplicate requests, process death, clock/timezone changes. Fix discovered invariants.

### DEV-032 — Performance & Database Optimization
Profile before optimizing: query plans, indexes, Compose recomposition, large histories, recurrence generation, Today aggregation, analytics, widget refresh, sync batches, startup. No speculative optimization.

### DEV-033 — Accessibility / Adaptive UI / Localization
Audit and improve TalkBack, content descriptions, touch targets, contrast, dynamic text, adaptive layouts, screen sizes, orientation where applicable, localization architecture.

### DEV-034 — Full Cybersecurity Audit *(checkpoint)*
Independent security review against current standards: OWASP MASVS/MASTG, Android security, Supabase/RLS, authentication, authorization, network, storage, backup, deep links, exported components, intents, widget, notifications, billing, dependencies, supply chain, secrets, logs, debug/release separation, AI-generated dependency risks, potential backdoors, abuse cases. Inspect actual implementation, not just documentation. Every discovered vulnerability gets: ID, SEVERITY, ATTACK PATH, IMPACT, FIX, REGRESSION TEST, STATUS.

### DEV-035 — Full Regression / Integration / Migration Testing
Test cross-feature invariants, particularly: one action → one execution fact; Session edit → correct recomputation; Session delete → correct reversal; offline → correct synchronization; recurrence → no duplicates; widget → same execution semantics; Goal calculations → same everywhere.

### DEV-036 — Google Play Release Hardening
Recheck current requirements rather than relying on old assumptions: target SDK, permissions, restricted APIs, data safety, privacy, billing, subscriptions, account deletion, signing, Play Integrity where justified, backup, release configuration, obfuscation/minification, crash behavior, production endpoints, debug exclusion.

### DEV-037 — Production Release Candidate Audit *(checkpoint)*
Final independent audit across Product correctness, UX, Architecture, Data integrity, Offline behavior, Sync, Security, Privacy, Billing, Performance, Accessibility, Tests, Migrations, Google Play readiness. Produce a BLOCKER / CRITICAL / HIGH / MEDIUM / LOW / IMPROVEMENT list. Release is allowed only when no unresolved Blocker/Critical issue remains.

## Checkpoints

Expect independent external architecture/product/security review after approximately: DEV-004, DEV-009, DEV-015, DEV-022, DEV-030, DEV-034, DEV-037. Do not wait at these checkpoints unless explicitly instructed to stop — instead, leave the repository in a coherent state, update `PROJECT_STATE.md`, make the checkpoint easy to review, and continue if no blocking decision exists. The reviewer may inspect several completed DEV tasks together.

## Commit Discipline

Make coherent commits. Commit messages identify the DEV task and responsibility, e.g. `DEV-009: add idempotent session completion transaction`. Avoid enormous mixed commits covering unrelated responsibilities. Do not rewrite unrelated history. Do not force-push shared branches unless explicitly authorized.

## Branch Safety

Before changing code: identify the current branch, identify the default/base branch, inspect uncommitted work, and never overwrite another agent's uncommitted changes. If another AI agent may be working in the repository, use clearly isolated branches/worktrees. Never assume exclusive ownership of the entire repository.

## Other AI Agents

Claude is currently the primary implementation engineer. Other agents (e.g. Codex, ChatGPT) may independently review or perform specifically assigned tasks. On encountering changes made by another agent: inspect them, do not blindly revert them, determine their architectural intent, integrate only when compatible, and flag contradictions. One DEV responsibility has one active implementation owner unless parallel workstreams are explicitly declared independent.

## Product Owner Interaction

Do not overwhelm the product owner with implementation details that don't require a decision. At meaningful milestones, summarize: COMPLETED, WHAT USER CAN NOW DO, IMPORTANT ARCHITECTURE DECISIONS, RISKS / BLOCKERS, TEST STATUS, NEXT DEV TASK. Keep detailed engineering state in the repository, not in chat.
