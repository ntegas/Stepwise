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
| 1.6.3 | Done | Canonical documentation restructured to the full set below; `DEVELOPMENT_PROTOCOL.md` (50 code-centralization rules) and the Security/Transaction/Concurrency standard (`docs/security/*`, plus Transaction/Concurrency folded into `ARCHITECTURE.md`) added. |
| 1.7 | Done | Master Autonomous Development Plan reviewed; root-level canonical file layout confirmed as canonical over the plan's `docs/`-nested alternative (Option A). `ROADMAP.md` and `DEVELOPMENT_LOG.md` created; `ANTI_ERROR_STANDARD.md` §0 (read-first) and §10 (periodic retrospective) added; S0-S3 Security Review Levels added to `SECURITY_ARCHITECTURE.md`; Skill Graph/RPG guardrail added to `PRODUCT_CANON.md`; old `ARCHITECTURE.md` §29 superseded by `ROADMAP.md` (sync-metadata column list preserved into `DATA_MODEL.md` first); `CLAUDE.md` and `DEVELOPMENT_PROTOCOL.md` updated to point at the new files. |
| DEV-000 | Done | Repository & Architecture Preflight — see findings below. No owner-level blocker found; proceeding automatically into DEV-001 per standing instruction. |
| DEV-001+ | Not started | Full sequence now tracked in `/ROADMAP.md`, not here — this table stops enumerating individual DEV tasks to avoid two places tracking the same sequence and drifting apart. |

## What exists right now

- **Product**: `PRODUCT_CANON.md` — Master Product Concept, complete and current, including the Skill Graph/RPG guardrail.
- **Architecture**: `ARCHITECTURE.md` — canonical and detailed (native Android/Kotlin/Compose, offline-first, Supabase, Widget, security posture, testing strategy, ADR, open questions). §29 (old implementation sequence) marked superseded by `ROADMAP.md`.
- **Data model**: `DATA_MODEL.md` — conceptual entities, relationships, and sync metadata columns; the concrete Room/Postgres schema is DEV-004/DEV-005's job, not yet written.
- **Process**: `ANTI_ERROR_STANDARD.md` (now with §0 read-first and §10 periodic retrospective), `DEVELOPMENT_PROTOCOL.md`, `ROADMAP.md` (DEV-000...DEV-037 + operating rules), `DEVELOPMENT_LOG.md` (historical journal) — all current.
- **Security**: `docs/security/` — current, including S0-S3 Security Review Levels in `SECURITY_ARCHITECTURE.md`.
- **Not yet populated** (by design, per explicit instruction — do not fill ahead of their phase): `CALCULATION_ENGINE.md`, `DESIGN_SYSTEM.md`.
- **Code**: none Android yet. `/apps/web` (Next.js, Phase 0) and `/supabase/migrations` (Phase 0 schema) exist but are both superseded/parked — neither is the active build target. No `/android` Gradle project, no `.github/` CI workflows exist yet.

## DEV-000 — Repository & Architecture Preflight findings

Audited the actual repository state (not assumed) on 2026-09-15, branch `claude/magical-rubin-2bdra8`:

- **Branches/working state**: single branch, all canonical-doc work committed through `6f11c14` at audit time; this round's changes (ANTI_ERROR_STANDARD.md §0/§10, ROADMAP.md, DEVELOPMENT_LOG.md, etc.) are the pending commit that closes DEV-000.
- **Gradle/Android**: no `.gradle`/`build.gradle*`/`AndroidManifest.xml` anywhere in the repo — confirmed by direct filesystem search, not inferred. There is no Android project to inventory modules/package structure/dependencies/domain models/Room/repositories/navigation/Compose UI/tests against; DEV-001 starts from nothing here, not from a legacy structure to reconcile.
- **CI**: no `.github/` directory — no workflows exist yet. DEV-001/DEV-002 is where the first GitHub Actions Android CI gets built, per the Environment & Verification Debt framework in `/ROADMAP.md`.
- **Backend**: `/supabase/migrations/0001_init.sql` (Phase 0 schema) exists but predates the corrected source-of-truth data model in `/DATA_MODEL.md` (no independently-mutable totals) — it is superseded conceptually and will not be reused as-is; DEV-004/DEV-007 write the real schema against current canon.
- **Obsolete artifacts**: `/apps/web` (Next.js) and `/packages/domain` (shared TS types) are Phase 0 artifacts, explicitly parked (not deleted — the user's own instruction was to keep them, not discard). No React Native/Expo code was ever actually scaffolded — `docs/android-stack.md`/`docs/android-architecture.md` are documentation-only relics of that decision, already marked superseded.
- **Duplicated responsibilities**: none found — there is no application code yet for two implementations to exist in.
- **Unfinished/generated code, hardcoded assumptions**: none found in the (nonexistent) Android codebase. One documentation-level item worth flagging as technical debt, not a blocker: root `.gitignore` currently only covers `node_modules/`, `.next/`, `out/`, `.env`, `.env.local`, `.DS_Store`, `*.log` — it does not yet exclude Android-specific secret-bearing file patterns (`*.pem`, `*.key`, keystore files, `google-services.json` if used, signing configs) required by `AI_CODE_SECURITY.md`. Not a defect today (no such files exist yet), but must be extended as part of DEV-001/DEV-002 before any such file is ever created, not after.
- **Security configuration**: nothing to audit yet at the code level; `docs/security/*` (policy layer) is current and ahead of code, which is the intended order.

**Conclusion**: no owner-level blocker. Nothing to preserve from a prior Android build (there isn't one) and nothing to unwind. DEV-001 (Development Foundation) starts clean, scoped exactly to `/ROADMAP.md`'s DEV-001 description — Gradle conventions, module boundaries, Hilt, dependency governance, environment configuration, `Clock`, IDs, application errors, logging, coroutine conventions, architectural contracts, repository interfaces where justified. No speculative infrastructure beyond that.

## VERIFICATION DEBT

None yet — no Android-specific code exists to require verification. This section will begin accumulating entries starting with the first Android-SDK-dependent work (expected at DEV-003 Design System Foundation onward, per the `ENVIRONMENT LIMITATION` recorded in `/ROADMAP.md`). Format going forward:

```text
DEV-0NN
- <specific check> — NOT VERIFIED — ANDROID SDK REQUIRED
```

A DEV task is not considered production-verified while a required Android-specific check for it remains listed here as outstanding.

## Open items requiring a decision (not blocking further doc work, but blocking implementation)

- OQ-1, OQ-2 closed. OQ-3 (exact alarms) and OQ-4 (Glance/widget hosting) are implementation-time external verification requirements, not open product questions — see `ARCHITECTURE.md`'s Open Questions section.
- B8 (`ProgressEvent` as a derived view, not a stored table) was flagged for explicit user confirmation since it went beyond direct transcription of an instruction — see the chat response for that revision round; not revisited since, stands as accepted.

## Current gate

DEV-000 found no owner-level blocker (see findings above). Per the user's explicit standing instruction, the project **proceeds automatically into DEV-001** without waiting for further confirmation. `/ROADMAP.md` §"Checkpoints" identifies where independent external review is expected (starting DEV-004) — the project does not otherwise pause between DEV tasks.
