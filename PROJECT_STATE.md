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
| DEV-000 | Done | Repository & Architecture Preflight — see `/DEVELOPMENT_LOG.md`. No owner-level blocker found; proceeded automatically into DEV-001 per standing instruction. |
| DEV-001 | Done | Development Foundation — see findings below. Root Gradle project + `:core:common` (Clock, IdGenerator, AppError/AppResult, Logger, DispatcherProvider), genuinely built and unit-tested locally. Hilt, repository interfaces, and every other module deferred with stated reasons (see below); no owner-level blocker. |
| DEV-002 | Done | Security & Quality Foundation — see findings below. ktlint + detekt static analysis wired into the default `build`/`check` lifecycle; first GitHub Actions CI workflow (build/test/lint/static-analysis, dependency review, secret scan) — confirmed green on its first real run. |
| DEV-003 | In progress | Design System Foundation — see findings below. `:core:designsystem` written (tokens + 10 generic components); the Android Gradle Plugin bump this required (AGP 9.4.0 → Gradle 9.6.0) is verified against `:core:common` locally, but `:core:designsystem` itself is NOT VERIFIED locally (AGP unresolvable in this sandbox) pending its first CI run. |
| DEV-004+ | Not started | Full sequence now tracked in `/ROADMAP.md`, not here — this table stops enumerating individual DEV tasks to avoid two places tracking the same sequence and drifting apart. |

## What exists right now

- **Product**: `PRODUCT_CANON.md` — Master Product Concept, complete and current, including the Skill Graph/RPG guardrail.
- **Architecture**: `ARCHITECTURE.md` — canonical and detailed (native Android/Kotlin/Compose, offline-first, Supabase, Widget, security posture, testing strategy, ADR, open questions). §29 (old implementation sequence) marked superseded by `ROADMAP.md`.
- **Data model**: `DATA_MODEL.md` — conceptual entities, relationships, and sync metadata columns; the concrete Room/Postgres schema is DEV-004/DEV-005's job, not yet written.
- **Process**: `ANTI_ERROR_STANDARD.md` (now with §0 read-first and §10 periodic retrospective), `DEVELOPMENT_PROTOCOL.md`, `ROADMAP.md` (DEV-000...DEV-037 + operating rules), `DEVELOPMENT_LOG.md` (historical journal) — all current.
- **Security**: `docs/security/` — current, including S0-S3 Security Review Levels in `SECURITY_ARCHITECTURE.md`.
- **Not yet populated** (by design, per explicit instruction — do not fill ahead of their phase): `CALCULATION_ENGINE.md`, `DESIGN_SYSTEM.md`.
- **Code**: a real Gradle project exists at the repo root (`settings.gradle.kts`, `gradle/libs.versions.toml`, wrapper pinned to Gradle 8.14.5) with one module, `:core:common` (pure Kotlin/JVM — Clock, IdGenerator, AppError/AppResult, Logger, DispatcherProvider), built and unit-tested (10/10 passing) in this sandbox without any Android SDK dependency. No `:app`, no Android module, no `.github/` CI workflows exist yet — see DEV-001 findings below for what was deferred and why. `/apps/web` (Next.js, Phase 0) and `/supabase/migrations` (Phase 0 schema) remain superseded/parked.

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

## DEV-001 — Development Foundation findings

Scoped per `/ROADMAP.md`'s DEV-001 description and its own "do not create speculative infrastructure" constraint. Anti-duplication search first (`/ANTI_ERROR_STANDARD.md` §1): DEV-000 already confirmed no existing Gradle/Android project anywhere in the repo, so nothing to search for or reuse — this is a from-scratch foundation, not a reconciliation.

**Built and verified now:**
- Root Gradle project: `settings.gradle.kts` (declares only modules that exist — see below), `gradle/libs.versions.toml` (one version catalog, per `DEVELOPMENT_PROTOCOL.md` rule 37), root `build.gradle.kts`, `gradle.properties`, and a wrapper pinned to **Gradle 8.14.5** (bumped from the sandbox's installed 8.14.3 after the real build surfaced a genuine Kotlin-2.4.20-requires-≥8.14.4 warning — fixed by upgrading the wrapper, not by suppressing the warning or downgrading Kotlin; 8.x rather than the current 9.7.1 line, to stay on the version family Android Gradle Plugin actually supports once it's introduced).
- `:core:common` — pure Kotlin/JVM module (no Android dependency, per ARCHITECTURE.md §27, so it stays realistic for a future Kotlin Multiplatform iOS path): `Clock`/`SystemClock` (rule 11), `IdGenerator`/`UuidIdGenerator` using `kotlin.uuid.Uuid` rather than `java.util.UUID` (keeps the module JVM-only-dependency-free), `AppError`/`AppResult` sealed hierarchies (rule 18, matching the exact Domain/Validation/Database/Network/Auth/Sync/Billing naming), `Logger` interface (rule 44 — contract only; a concrete Android sink is added when `:app` exists), `DispatcherProvider`/`StandardDispatcherProvider` (io/default/main/unconfined, per ARCHITECTURE.md §3). Test fixtures (`FakeClock`, `FakeIdGenerator`, `TestDispatcherProvider`) live in a `testFixtures` source set so later modules reuse them instead of writing their own (rule 41).
- **10 JVM unit tests, genuinely executed in this sandbox, 10/10 passing, 0 skipped** (`./gradlew clean build` from a cold cache, wrapper-fetched Gradle 8.14.5, zero compiler warnings). This is real `VERIFIED` status, not an assertion — see the commit for the exact command run.

**Deliberately deferred, with reasons (not silently dropped):**
- **Every other module** in ARCHITECTURE.md §5's target graph (`:core:model`, `:core:calculation`, `:domain`, `:core:database`, `:core:network`, `:core:sync`, `:data`, `:core:designsystem`, `:core:notifications`, `:app`, every `:feature:*`) — each would be an empty shell with no real content until its own DEV task (`:core:model`/`:core:calculation`/`:domain` → DEV-004; `:core:designsystem` → DEV-003; `:core:database` → DEV-005; `:data` → DEV-006; `:core:network` → DEV-007; `:core:sync` → DEV-008; `:core:notifications` → DEV-027; `:app` + first `:feature:*` → DEV-015). Creating them now would be exactly the "speculative infrastructure" DEV-001 says not to build.
- **Hilt** — Hilt applies at the Android/`:app`/feature layer (ARCHITECTURE.md §3), and no such module exists yet; adding it now would mean a dependency with nothing consuming it, which `DEPENDENCY_POLICY.md` itself rules out ("nothing gets added... without actual necessity"). Its version will be selected and pinned in the version catalog when `:app` is created (DEV-015).
- **Repository interfaces** — "where justified" per DEV-001's own wording; none are justified yet because no domain entities exist to type them against (that's DEV-004's Canonical Data Model). Adding them now would mean guessing at shapes DEV-004 might change.
- **Android-flavored Gradle convention plugins / `build-logic`** — with a single module, there is nothing yet to share conventions across; introduced when a second JVM/Android module exists (tracked here, not silently dropped).
- **Environment configuration (dev/staging/prod separation)** — genuinely belongs with DEV-002 (Security & Quality Foundation), since it's inseparable from the secrets/build-variant policy that task establishes; doing it now ahead of that policy would risk redoing it.

**Verification status**: `:core:common` is **VERIFIED** (built and tested in this sandbox — see above). No `NOT VERIFIED — ANDROID SDK REQUIRED` entries yet, because nothing Android-SDK-dependent was created this task; that starts at DEV-003.

**Conclusion**: no owner-level blocker. Proceeding into DEV-002 (Security & Quality Foundation) per standing instruction.

## DEV-002 — Security & Quality Foundation findings

Scoped per `/ROADMAP.md`'s DEV-002 description. Much of "security documentation, trust boundaries, secret policy, dependency policy" already existed from Phase 1.7 (`docs/security/*`) — this task is about the *tooling* that enforces those policies in code, not re-documenting them.

**Built and verified now:**
- **Static analysis + formatting**: `detekt` (`io.gitlab.arturbosch.detekt`, 1.23.8 — the last stable release; detekt 2.0 exists only as an alpha under new `dev.detekt` coordinates, checked live rather than assumed, so the pin stays on 1.x) and `ktlint` (`org.jlleitschuh.gradle.ktlint`, 14.2.0), both wired once in the root `build.gradle.kts`'s `subprojects` block so every current and future module inherits them automatically (`DEVELOPMENT_PROTOCOL.md` rule 42 — one formatter, one static-analysis config). No bespoke detekt rule-set file yet — its default ruleset is enough until a real deviation is needed.
- Running these for real against `:core:common` found genuine style violations in the DEV-001 code (multi-parameter constructors not one-per-line, one misordered import) — fixed via `./gradlew ktlintFormat`, not by hand-editing to game the checker. `./gradlew build` now runs compile + unit tests + `ktlintCheck` + `detekt` as one command, confirmed via a cold `clean build`.
- **First GitHub Actions CI workflow** (`.github/workflows/ci.yml`): a `build` job (checkout, JDK 21, `gradle/actions/setup-gradle`, `./gradlew build`), a `dependency-review` job (`actions/dependency-review-action`, PR-only, minimum permissions), and a `secret-scan` job (official `ghcr.io/gitleaks/gitleaks` image run directly via Docker, sidestepping `gitleaks-action`'s per-organization license question entirely — see below). Action versions (`actions/checkout@v7`, `actions/setup-java@v6`, `gradle/actions/setup-gradle@v6`, `actions/dependency-review-action@v5`) were verified live on 2026-09-15, not recalled from training data — re-verify before assuming they're still current at a later date.
- **A genuine, dated finding from that verification pass**: `gitleaks-action` requires a paid license for scanning more than one repo under an *organization* account (free for personal-account repos) — checked before use rather than added blind, per `DEPENDENCY_POLICY.md`. Sidestepped by running the underlying open-source `gitleaks` CLI (MIT-licensed) directly via its official container image instead of the Action wrapper, which avoids the licensing question regardless of how this repository is ever hosted.

**Deliberately deferred, with reasons:**
- **Production/debug build-variant separation** — a real Gradle/Android build-type concept that needs an actual Android application module to attach to; premature before `:app` exists (DEV-015). The *policy* (`docs/security/SECURITY_ARCHITECTURE.md`'s environment-isolation rule) already covers what it must satisfy once built.
- **Dev/staging/prod environment configuration** — same reasoning; there's no build variant or backend environment yet to configure.
- **Security test foundation** — the JUnit5 + `testFixtures` pattern from DEV-001 already is that foundation; no security-specific subsystem (auth, RLS, billing) exists yet to write a security test against.
- **Per-subsystem threat modeling** — `THREAT_MODEL.md`'s own stated policy is to write each subsystem's threat model when that subsystem is actually designed (DEV-007 Auth, DEV-008 Sync, etc.), not ahead of it; that policy stands, nothing new needed here.
- **A bespoke detekt rule-set / dependency-vulnerability database scan (OWASP Dependency-Check style)** — GitHub's native `dependency-review-action` (PR-time) plus GitHub's own Dependabot alerts (a repository setting, not something this session can toggle) already cover the CI-gate requirement `SECURITY_TEST_MATRIX.md` names; a heavier local SCA tool is added only if a real gap in that coverage shows up.

**Verification status**: **VERIFIED**, both locally and on GitHub. Locally: ktlint found and fixed real violations, detekt and the full `build` passed clean on a cold run. On GitHub (run [34935726894](https://github.com/ntegas/Stepwise/actions/runs/34935726894), commit `842e7db`, checked via the Actions API, not assumed): the `build` job (compile + unit tests + ktlintCheck + detekt) succeeded, the `secret-scan` job (gitleaks) succeeded, and `dependency-review` correctly skipped (it only runs on `pull_request` events — this was a push, so skipping was the intended behavior, not a failure).

**Conclusion**: no owner-level blocker. DEV-002 is closed.

## DEV-003 — Design System Foundation findings

Scoped per `/ROADMAP.md`'s DEV-003 description ("build semantic color tokens, typography, spacing, shapes, elevation, motion, light/dark theme, core reusable components... do not redesign every screen yet"). This is the first DEV task needing the Android Gradle Plugin, which triggered a real, unplanned compatibility problem worth recording in full.

**A genuine structural finding, found and fixed before writing any design-system code**: adding `:core:designsystem` (which needs `com.android.library`) to the same multi-project Gradle build as `:core:common` broke `:core:common`'s previously-VERIFIED build too — Gradle configures every project in the tree by default regardless of which module's task is requested, so an unresolvable AGP plugin (`dl.google.com` is blocked in this sandbox) failed the whole build, not just the Android module. Fixed two ways, both necessary:
1. AGP/Compose plugins are declared only inside `core/designsystem/build.gradle.kts` itself, never at root (even as `apply false`) — the root project is always configured, so anything unresolvable there breaks everything.
2. `org.gradle.configureondemand=true` added to `gradle.properties` — without it, Gradle still configures every subproject (including `:core:designsystem`) even when only `:core:common`'s task is requested. Verified empirically: `:core:common:build` (10/10 tests) passes cleanly with this in place, `:core:designsystem` fails immediately at plugin resolution as expected — exactly the isolation needed to keep local pure-JVM verification working alongside an Android module this sandbox can't build.

**A second real compatibility fact, checked live rather than assumed**: AGP jumped to a 9.x major version line; AGP 9.4.0 (current stable, checked against the official Android Developers release notes on 2026-09-15) requires Gradle ≥9.6.0 — well past the 8.14.5 pinned in DEV-001. The wrapper was bumped to Gradle 9.6.0, and — since a known Gradle-9-compatibility issue was reported against detekt 1.23.8 (with an older Kotlin version) — this was verified empirically against `:core:common`'s existing build before proceeding, not assumed either way: 10/10 tests still pass, only a harmless upstream detekt deprecation warning (removal scheduled for Gradle 10, not 9) appears.

**Built (all `NOT VERIFIED — ANDROID SDK REQUIRED` locally — see below)**:
- `:core:designsystem` module: `com.android.library` + Compose, `compileSdk 36` (mandatory for new submissions since 2026-08-31 per `docs/play-store-checklist.md` — already in effect, not a future deadline), `minSdk 26` (a stated, revisable technical default, not a product decision — reasoned the same way the Kotlin/Gradle version pins were in DEV-001).
- Full token set + `StepwiseTheme` and 10 generic reusable components — see `/DESIGN_SYSTEM.md` for the complete breakdown, including which components (`StepwiseGoalCard`/`StepwiseTaskRow`/`StepwiseActivityRow`) were deliberately deferred to DEV-004+ because they'd need to guess at domain-model shapes that don't exist yet.
- CI workflow extended with `android-actions/setup-android@v4` ahead of the Gradle build step, to avoid the well-known unaccepted-SDK-license pitfall on GitHub-hosted runners — not yet confirmed necessary or sufficient until the first real CI run.

**Regression check found and fixed one more thing while touching `DESIGN_SYSTEM.md`**: its old scaffold cited `DEVELOPMENT_PROTOCOL.md` "rule C3–C4" for accessibility — off by one against the document's actual current numbering (rules 23–24, not 22–23). Checked the sibling "A4/A5/A7/D2"-style citations elsewhere in the repo (`CALCULATION_ENGINE.md`, `docs/security/SECURITY_ARCHITECTURE.md`, `ADR/ADR-005`) against `DEVELOPMENT_PROTOCOL.md`'s real numbering too — those were all actually correct, so only the one reference was fixed, not a systemic renumbering bug like the earlier §25/§29 cases.

**Verification status**: everything in `:core:designsystem` is `NOT VERIFIED — ANDROID SDK REQUIRED` until the first CI run is checked (this sandbox cannot resolve AGP at all — not even `ktlintCheck` can run against this module locally, since Gradle fails at plugin resolution before ktlint's task graph is even built). `:core:common` remains genuinely `VERIFIED` under the new Gradle 9.6.0 + `configureondemand` setup. See `DEVELOPMENT_LOG.md` for the CI check's outcome once performed.

## VERIFICATION DEBT

```text
DEV-003
- :core:designsystem compilation (AGP/Compose) — NOT VERIFIED — ANDROID SDK REQUIRED
- ktlint/detekt over :core:designsystem's Kotlin sources — NOT VERIFIED — ANDROID SDK REQUIRED
  (cannot run at all locally: Gradle fails at AGP plugin resolution before ktlint's
  task graph is built, so even non-Android-specific lint checks are blocked)
```

A DEV task is not considered production-verified while a required Android-specific check for it remains listed here as outstanding.

## Open items requiring a decision (not blocking further doc work, but blocking implementation)

- OQ-1, OQ-2 closed. OQ-3 (exact alarms) and OQ-4 (Glance/widget hosting) are implementation-time external verification requirements, not open product questions — see `ARCHITECTURE.md`'s Open Questions section.
- B8 (`ProgressEvent` as a derived view, not a stored table) was flagged for explicit user confirmation since it went beyond direct transcription of an instruction — see the chat response for that revision round; not revisited since, stands as accepted.

## Current gate

DEV-000 and DEV-001 both found no owner-level blocker (see findings above). Per the user's explicit standing instruction, the project **proceeds automatically into DEV-002** without waiting for further confirmation. `/ROADMAP.md` §"Checkpoints" identifies where independent external review is expected (starting DEV-004) — the project does not otherwise pause between DEV tasks.
