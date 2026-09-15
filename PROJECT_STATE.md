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
| DEV-003 | Done | Design System Foundation — see findings below. `:core:designsystem` written (tokens + 10 generic components) and **confirmed CI-green** after 14 real CI iterations — most spent on a genuine Gradle plugin-resolution bug misdiagnosed for a long time as an AGP-version problem (see findings). This sandbox can no longer build any part of the repo locally at all (accepted trade-off, see VERIFICATION DEBT) — CI is now the sole source of truth. |
| DEV-004+ | Not started | Full sequence now tracked in `/ROADMAP.md`, not here — this table stops enumerating individual DEV tasks to avoid two places tracking the same sequence and drifting apart. |

## What exists right now

- **Product**: `PRODUCT_CANON.md` — Master Product Concept, complete and current, including the Skill Graph/RPG guardrail.
- **Architecture**: `ARCHITECTURE.md` — canonical and detailed (native Android/Kotlin/Compose, offline-first, Supabase, Widget, security posture, testing strategy, ADR, open questions). §29 (old implementation sequence) marked superseded by `ROADMAP.md`.
- **Data model**: `DATA_MODEL.md` — conceptual entities, relationships, and sync metadata columns; the concrete Room/Postgres schema is DEV-004/DEV-005's job, not yet written.
- **Process**: `ANTI_ERROR_STANDARD.md` (now with §0 read-first and §10 periodic retrospective), `DEVELOPMENT_PROTOCOL.md`, `ROADMAP.md` (DEV-000...DEV-037 + operating rules), `DEVELOPMENT_LOG.md` (historical journal) — all current.
- **Security**: `docs/security/` — current, including S0-S3 Security Review Levels in `SECURITY_ARCHITECTURE.md`.
- **Not yet populated** (by design, per explicit instruction — do not fill ahead of their phase): `CALCULATION_ENGINE.md`.
- **`DESIGN_SYSTEM.md`** — now filled in and CI-verified (DEV-003).
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

Scoped per `/ROADMAP.md`'s DEV-003 description ("build semantic color tokens, typography, spacing, shapes, elevation, motion, light/dark theme, core reusable components... do not redesign every screen yet"). This is the first DEV task needing the Android Gradle Plugin, and it took **14 real CI iterations** to go green — most of that chasing a misdiagnosis, not fighting 14 independent bugs. Recorded here in full, honestly, per `ANTI_ERROR_STANDARD.md` §10 — the length and the wrong turn are exactly what a retrospective is for, not something to smooth over. Full push-by-push detail is in `DEVELOPMENT_LOG.md`; this is the organized summary.

**What actually happened, grouped by real cause (not 14 flat steps):**

1. **Iteration 1 — unrelated CI setup bug.** `android-actions/setup-android@v4` (added preemptively) tries to install a long-removed legacy SDK package and fails outright. Removed — GitHub-hosted runners already ship a working, license-accepted Android SDK, so the step was solving a problem that didn't exist.

2. **Iterations 2–8 — a genuine, sustained misdiagnosis.** The recurring error `org.jetbrains.kotlin.android ... already on the classpath with an unknown version` (and, separately, a `KotlinAndroidTarget`/`BaseVariant` crash) was read as AGP 9.4.0's new "built-in Kotlin" support being broken. Tried, in order: removing the explicit `kotlin-android` plugin, removing `kotlin-compose` too, confirming the crash persisted with neither applied (seemingly proving it was AGP's own bug), then `android.builtInKotlin=false` and `android.newDsl=false` (the documented opt-out pair) — the crash went away but the *original* classpath error came back identically. This was not wasted effort — downgrading to AGP 8.13.2 at the end of this chain (to escape the theory entirely) is what later exposed iteration 12's real, unrelated Compose-BOM finding — but the AGP-9-built-in-Kotlin theory itself was wrong throughout.

3. **Iterations 9–11 — the real plugin-resolution bug.** Root `build.gradle.kts` only ever declared `kotlin-jvm` as `apply false` (for `:core:common`). `org.jetbrains.kotlin.jvm` and `org.jetbrains.kotlin.android` are different plugin IDs backed by the same underlying Kotlin Gradle Plugin artifact — resolving `kotlin-android` for the first time inside `:core:designsystem`'s own `plugins{}` block collided with it, independent of AGP version (confirmed by reproducing the identical error under plain AGP 8.13.2). Fixing it required declaring **every** plugin used anywhere in the build — `kotlin-android`, `kotlin-compose`, and finally `android.library` (real AGP) itself — as `apply false` at root, the standard Android Studio multi-module convention every earlier iteration had deviated from specifically to keep `:core:common` locally buildable in this sandbox (`dl.google.com` blocked). That deviation is what caused the whole bug class. **This is a real, permanent trade-off, not a temporary one**: this sandbox can no longer build any part of this repository locally at all — see VERIFICATION DEBT below.

4. **Iteration 12 — a real, separate version-floor finding.** With plugin resolution finally clean, AGP 8.13.2 turned out to be flatly incompatible with Compose BOM `2026.08.00` anyway: its own dependencies (`androidx.compose.animation:animation-core-android:1.12.0` and others) require AGP ≥9.1.0 and `compileSdk` ≥37 — caught via a real CI AAR-metadata failure, not guessed. Moved back to **AGP 9.4.0** (the version iterations 2–8 spent so long fighting for the wrong reason) plus `compileSdk 37`.

5. **Iterations 13–14 — ordinary compile/lint fixes, nothing mysterious.** First real compilation surfaced a JVM-target mismatch (Java pinned to 17, Kotlin defaulting to 21 — fixed with `kotlin { jvmToolchain(17) }`, the same pattern `:core:common` uses); 47 detekt findings and 31 ktlint findings, both dominated by the same well-known Compose-vs-JVM-linter friction (`FunctionNaming`/`LongParameterList` expecting camelCase/short signatures, Compose using PascalCase components with many parameters) — fixed via `config/detekt/detekt.yml` (`ignoreAnnotated: ["Composable"]`) and a root `.editorconfig` (`ktlint_function_naming_ignore_when_annotated_with = Composable`), both now inherited by every future module automatically. The remaining findings were real: `MagicNumber` on color/easing literals (fixed via `ignorePropertyDeclaration` plus extracting 4 named-argument color values into named constants) and `MatchingDeclarationName` on 3 files (fixed via renames — `Spacing.kt`→`StepwiseSpacing.kt`, `Motion.kt`→`StepwiseMotion.kt` — and splitting `BottomNavigation.kt` so `StepwiseNavigationItem` has its own file).

**Built and now genuinely CI-verified**:
- `:core:designsystem` module: `com.android.library` 9.4.0 + Compose, `compileSdk 37`, `minSdk 26` (a stated, revisable technical default, not a product decision).
- Full token set + `StepwiseTheme` and 10 generic reusable components — see `/DESIGN_SYSTEM.md` for the complete breakdown, including which components (`StepwiseGoalCard`/`StepwiseTaskRow`/`StepwiseActivityRow`) were deliberately deferred to DEV-004+ because they'd need to guess at domain-model shapes that don't exist yet.

**Regression check found and fixed one more thing while touching `DESIGN_SYSTEM.md`**: its old scaffold cited `DEVELOPMENT_PROTOCOL.md` "rule C3–C4" for accessibility — off by one against the document's actual current numbering (rules 23–24, not 22–23). Checked the sibling "A4/A5/A7/D2"-style citations elsewhere in the repo against the real numbering too — those were all correct, so this was an isolated slip, not a systemic renumbering bug.

**Verification status**: `:core:designsystem` — compilation, ktlint, and detekt — is **VERIFIED**, confirmed via a real green GitHub Actions run (run [34966319372](https://github.com/ntegas/Stepwise/actions/runs/34966319372), commit `5ae16a5`, checked via the Actions API, not assumed). See `DEVELOPMENT_LOG.md` for the full iteration-by-iteration account.

**Conclusion**: no owner-level blocker. DEV-003 is closed. Proceeding into DEV-004 (Canonical Data Model) — flagged in `/ROADMAP.md` as a major architectural checkpoint, larger and more consequential than DEV-000–003, so it gets a higher care/effort level and a full read of `ARCHITECTURE.md`/`DATA_MODEL.md` before any code.

## VERIFICATION DEBT

```text
DEV-003 — CLOSED
- :core:designsystem compilation (AGP/Compose) — VERIFIED (CI run 34966319372)
- ktlint/detekt over :core:designsystem's Kotlin sources — VERIFIED (same run)

New, permanent, sandbox-only limitation (not itself a defect — see DEV-003 findings
iteration 9-11 for why):
- This sandbox can no longer run ANY Gradle task locally, for ANY module, including
  :core:common — root build.gradle.kts must declare android.library apply-false
  (required once any Android module coexists with a pure-JVM one), and the root
  project is always configured regardless of which task is requested, so this
  triggers an AGP-resolution attempt against dl.google.com (blocked here) on every
  invocation. Real GitHub Actions CI has full internet access and is unaffected —
  CI is now the sole source of truth for this entire repository's build, not just
  the Android-specific parts of it.
```

A DEV task is not considered production-verified while a required check for it remains listed here as outstanding.

## Open items requiring a decision (not blocking further doc work, but blocking implementation)

- OQ-1, OQ-2 closed. OQ-3 (exact alarms) and OQ-4 (Glance/widget hosting) are implementation-time external verification requirements, not open product questions — see `ARCHITECTURE.md`'s Open Questions section.
- B8 (`ProgressEvent` as a derived view, not a stored table) was flagged for explicit user confirmation since it went beyond direct transcription of an instruction — see the chat response for that revision round; not revisited since, stands as accepted.

## Current gate

DEV-000 through DEV-003 all found no owner-level blocker (see findings above). Per the user's explicit standing instruction, the project **proceeds automatically between DEV tasks** without waiting for further confirmation on each one. `/ROADMAP.md` §"Checkpoints" identifies where independent external review is expected — **DEV-004 is the first such checkpoint**, flagged as a major architectural checkpoint bigger than DEV-000–003, so it is announced with a higher recommended care/effort level and a full read of `ARCHITECTURE.md`/`DATA_MODEL.md` before any code, rather than started the same way as DEV-001–003.
