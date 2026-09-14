# PROJECT_STATE.md — Current Phase & Status

Live status tracker. This is the one place build-order/phase history lives — other documents point here rather than keeping their own copy, so they can't drift apart (`/ANTI_ERROR_STANDARD.md` §1).

## Phase history

| Phase | Status | Summary |
|---|---|---|
| 0 | Done | Original 48-section concept analysis, first data model, Supabase schema (superseded), Next.js web app skeleton (parked), README. |
| 1 | Done | Android pre-development analysis against the original concept — stack decision (React Native/Expo, since superseded), Play Store checklist, first scope-of-work draft. |
| 1.5 | Done | Concept replaced with the Master Product Concept (§1–77) — Vision/Life Areas/Life Balance strategic layer, multi-goal/multi-metric Sessions, explicit source-of-truth/idempotency rules. Data model, functional analysis, architecture, and scope-of-work reconciled against it. |
| 1.6 | Done | Platform decision changed to native Android (Kotlin + Jetpack Compose), superseding React Native/Expo. Canonical `ARCHITECTURE.md` written: consistency audit, layered/module architecture, offline-first & sync design, backend/security/testing, ADR. |
| 1.6.1 | Done | User-directed revision: Goal `progressMode`, hybrid sync conflict resolution, `ReminderScheduler` abstraction, `ProgressEvent` demoted to a derived view (not a stored table), tightened Task/Session rule, deterministic recurrence-occurrence identity. Closed OQ-1/OQ-2, reframed OQ-3. |
| 1.6.2 | Done | Android Home Screen Widget added as a canonical, first-class requirement — new `:feature:widget` module, `DomainEventBus`, local-idempotency tightening, Timer modeled as an `IN_PROGRESS` Session, full affected-consumer inventory and 10 acceptance criteria ↔ test mappings. OQ-4 added (Glance/widget-hosting live verification). |
| 1.6.3 | In progress | Canonical documentation restructured to the full set below; `DEVELOPMENT_PROTOCOL.md` (50 code-centralization rules) and the Security/Transaction/Concurrency standard (`docs/security/*`, plus Transaction/Concurrency folded into `ARCHITECTURE.md`) added. |
| 2 | Not started | Canonical Data Model Specification → Calculation Engine Specification → UX/Navigation Specification → Offline & Sync Specification → Google Play Release Specification → Implementation, in that order (`ARCHITECTURE.md` §29). No MVP cut — the full concept is the build target. |

## What exists right now

- **Product**: `PRODUCT_CANON.md` — Master Product Concept, complete and current.
- **Architecture**: `ARCHITECTURE.md` — canonical and detailed (native Android/Kotlin/Compose, offline-first, Supabase, Widget, security posture, testing strategy, ADR, open questions).
- **Data model**: `DATA_MODEL.md` — conceptual entities and relationships; the concrete Room/Postgres schema with sync metadata is Phase 2's Canonical Data Model Specification, not yet written.
- **Process**: `ANTI_ERROR_STANDARD.md`, `DEVELOPMENT_PROTOCOL.md` — both current.
- **Security**: `docs/security/` — current as of the Security/Transaction/Concurrency standard.
- **Not yet populated** (by design, per explicit instruction — do not fill ahead of their phase): `CALCULATION_ENGINE.md`, `DESIGN_SYSTEM.md`.
- **Code**: none yet. `/apps/web` (Next.js, Phase 0) and `/supabase/migrations` (Phase 0 schema) exist but are both superseded/parked — neither is the active build target. No `/android` Gradle project exists yet.

## Open items requiring a decision (not blocking further doc work, but blocking implementation)

- OQ-1, OQ-2 closed. OQ-3 (exact alarms) and OQ-4 (Glance/widget hosting) are implementation-time external verification requirements, not open product questions — see `ARCHITECTURE.md`'s Open Questions section.
- B8 (`ProgressEvent` as a derived view, not a stored table) was flagged for explicit user confirmation since it went beyond direct transcription of an instruction — see the chat response for that revision round.

## Current gate

Per the user's explicit instruction: **do not begin the Canonical Data Model Specification or any implementation without confirmation.** This file reflects the state as of the most recent documentation pass; check the accompanying chat response for that pass's specific READY/NOT READY verdict before proceeding.
