# DEVELOPMENT_LOG.md — Stepwise Historical Journal

This is an append-only record of what actually happened during development: completed stages, significant decisions and the reasoning behind them, verifications actually performed (and their results), problems found, and retrospective findings (`/ANTI_ERROR_STANDARD.md` §10).

This is **not** a mirror of git history (the commit log already covers "what changed, when") and **not** a per-file change log. An entry here is for something worth knowing without reading every commit: a phase completed, a conflict surfaced and resolved, a verification that passed or failed, a lesson learned. Entries are appended in chronological order and never rewritten — a correction to an earlier entry is a new entry, not an edit to the old one.

---

## Phase 0 — Concept → Data Model → Web Scaffold (completed)

Repository started empty. Produced the initial concept capture, functional analysis, data model, and Supabase schema (`supabase/migrations/0001_init.sql`), plus a Next.js web app skeleton (`apps/web`) with a shared TypeScript domain package (`packages/domain`). Stack decision: Supabase (Postgres + Auth + REST/Realtime + RLS) as a client-agnostic backend, so the same schema could later serve a native Android client without rework.

Verification performed: Supabase migration applied cleanly; manual spot-check that a `sessions` insert produced a `progress_events` row and updated `goals.current_value` via trigger; Next.js app built and ran, auth flow worked, four placeholder tabs rendered.

## Phase 1 — Android Direction Analysis (completed)

User clarified the first shipped client is a **native Android app** for the Google Play Store, not the parked web app. Produced `/docs/android-stack.md` and `/docs/android-architecture.md` recommending React Native/Expo (later superseded — see Phase 1.5), `/docs/play-store-checklist.md` (Play Console requirements verified live via web search: target API level, closed-testing requirement for new personal developer accounts, Data Safety form, content rating, store listing assets), and `/docs/scope-of-work.md` mapping all concept sections to concrete screens/features for user sign-off before any implementation.

Retrospective finding at the time: the React Native/Expo recommendation was driven partly by this sandbox's lack of Android Studio/SDK, not purely by product fit — flagged as a recommendation to confirm, not a locked decision.

## Phase 1.5 — Native Android Pivot + Full Architecture Specification (completed)

User reversed the stack decision: native Kotlin/Jetpack Compose, not React Native/Expo, for tighter platform integration and a cleaner path to a future Kotlin Multiplatform iOS build. This required redoing the Android-specific architecture analysis from scratch rather than adapting the Expo-era docs, which are now marked superseded (`docs/android-stack.md`, `docs/android-architecture.md` — historical only).

Produced the full `ARCHITECTURE.md` (originally `docs/android-architecture-specification.md`): consistency audit (§0), architecture decisions, technology stack (Hilt over Koin, Supabase retained over Firebase/custom), module architecture, domain/persistence/offline-sync/backend/auth/progress-event/scheduling/today/analytics/life-balance/search/notifications architecture, billing, security, performance, error handling, testing, migration strategy, observability, dependency rules, risks, and an Architecture Decision Record + Open Questions section (OQ-1 through OQ-4).

A six-point revision round closed OQ-1 (Goal `progressMode` enum), OQ-2 (hybrid sync conflict resolution — ADR-006), and OQ-3 (`ReminderScheduler` interface — ADR-009), and resolved three further clarifications, including the decision (flagged at the time as an independent judgment call requiring explicit confirmation, not a transcription) to demote `ProgressEvent` from a persisted table to a view/query over `session_metric_values ⋈ goal_activity_links` (ADR-008).

Retrospective findings from this phase, since folded into `/ANTI_ERROR_STANDARD.md` §7:
- An off-by-one section-reference bug (`§22` vs the correct `§23`/later `§24` for Testing Architecture) was found only because of a deliberate post-edit regression pass, not caught at edit time. Lesson: cross-reference regression-checking after any renumbering is not optional.
- Inserting the Widget section (§19) and renumbering §19-28 to §20-29 turned several existing `§25` references ambiguous between this document's own new "Database Migration Strategy" section and the Master Product Concept's own §25 ("Execution Statuses"). Found via deliberate grep, fixed by disambiguating wording. Lesson: a renumbering pass must explicitly grep for the old numbers across the whole repo, not just within the edited file.

## Phase 1.6 — Home Screen Widget Architecture (completed)

User specified a full Android Home Screen Widget requirement (19 numbered points) with an explicit affected-consumer checklist and 10 acceptance criteria. Produced `ARCHITECTURE.md` §19: Jetpack Glance, an isolated `:feature:widget` module with zero dependency on `:core:database`/`:core:network` (the module-boundary rule that structurally prevents a second execution/source-of-truth path), the canonical execution pipeline reused from the app, local idempotency, `DomainEventBus`-driven refresh (no polling), per-widget `WidgetConfiguration` (local-only, not synced), a 17-row affected-consumer inventory, and a 10-row Acceptance-Criteria↔Test-Strategy table (AC1-AC10) satisfying `/ANTI_ERROR_STANDARD.md` §6.

## Phase 1.7 — Code Architecture Centralization + Security/Transaction/Concurrency Standards (completed)

User supplied two large rule sets in quick succession: 50 numbered code-centralization rules (module boundaries, no duplicated logic, dependency-version-catalog discipline, canonical-documentation-file requirements) and 54 security/transaction/concurrency rules. Restructured the canonical documentation set to root-level files (`CLAUDE.md`, `PRODUCT_CANON.md`, `ARCHITECTURE.md`, `DATA_MODEL.md`, `DEVELOPMENT_PROTOCOL.md`, `ANTI_ERROR_STANDARD.md`, `PROJECT_STATE.md`, `CALCULATION_ENGINE.md`/`DESIGN_SYSTEM.md` stubs, `ADR/`, `docs/security/*`), confirmed by explicit user decision over the Master Plan's alternative `docs/`-nested layout (see Phase 1.8).

Created `docs/security/SECURITY_ARCHITECTURE.md`, `THREAT_MODEL.md` (scaffold, populated per-subsystem in its own phase), `SECURITY_TEST_MATRIX.md`, `DEPENDENCY_POLICY.md`, `INCIDENT_RESPONSE.md`, `AI_CODE_SECURITY.md`.

## Phase 1.8 — Master Autonomous Development Plan Review + DEV-000 Preflight (in progress)

User supplied a "Master Autonomous Development Plan" nesting canonical docs under `docs/` and a 38-task roadmap (DEV-000 through DEV-037). This directly conflicted with the just-established root-level layout from Phase 1.7. Per `/ANTI_ERROR_STANDARD.md` §4, the conflict was surfaced explicitly (CONFLICT / CURRENT CANON / NEW PLAN / DOWNSTREAM IMPACT / OPTION A / OPTION B / RECOMMENDATION) rather than resolved silently; user confirmed Option A (keep root-level layout as canonical, treat the Master Plan as corrected by it).

Twelve-point decision round followed, resolving remaining open items: confirmed `ROADMAP.md` (not `DEVELOPMENT_PROTOCOL.md`) as the home for the DEV-000...037 sequence and process rules; confirmed a new S0-S3 Security Review Level scheme for `SECURITY_ARCHITECTURE.md` (S3 = security-critical, requires independent review, never self-certified); confirmed a Skill Graph/RPG-mechanics guardrail for `PRODUCT_CANON.md` (no such system without a new explicit product decision); added the standing "read `ANTI_ERROR_STANDARD.md` first" rule and this periodic-retrospective mechanism (`/ANTI_ERROR_STANDARD.md` §0 and §10, this entry being the first retrospective-adjacent log entry produced under that new rule).

**Environment limitation confirmed and recorded** (not worked around, per explicit user instruction): this sandbox has JDK 21 and Gradle 8.14.3 but no Android SDK, and the official SDK distribution host `dl.google.com` is blocked by the outbound proxy's organization policy (confirmed via direct `curl` — 403 CONNECT tunnel failure — and via the proxy's own `/__agentproxy/status` allowlist, which does not include Google's SDK hosts). Recorded verbatim as:

```
ENVIRONMENT LIMITATION:
Android SDK unavailable in current Claude execution environment.
```

Decision: GitHub Actions becomes the authoritative independent Android verification environment starting DEV-001/DEV-002 (Gradle build, Android compilation, unit tests, lint, static analysis initially; Room/migration tests, instrumentation tests, security checks, and release build verification added later, as needed). Every Android-specific claim is labeled `VERIFIED` or `NOT VERIFIED — ANDROID SDK REQUIRED`; a `VERIFICATION DEBT` section is tracked in `PROJECT_STATE.md` per DEV-task until CI closes each debt item.

DEV-000 itself (repo/architecture preflight) is being executed and documented as part of this phase; see `PROJECT_STATE.md` for its findings and current status.

### Retrospective — this round

- **What was attempted**: apply the user's 12-point decision round to seven canonical documents in one pass (`ANTI_ERROR_STANDARD.md`, this file, `ROADMAP.md`, `SECURITY_ARCHITECTURE.md`, `PRODUCT_CANON.md`, `CLAUDE.md`, `DEVELOPMENT_PROTOCOL.md`, `DATA_MODEL.md`, `ARCHITECTURE.md`) plus execute and document DEV-000.
- **What went wrong / had to be redone**: the mandatory post-edit regression grep (`/ANTI_ERROR_STANDARD.md` §7) found a latent, pre-existing instance of the exact same bug class as the earlier `§25` ambiguity: `ARCHITECTURE.md`'s own internal `§29` ("Recommended Implementation Sequence," now superseded) collided in number with the Master Product Concept's own `§29` ("Execution Calculation"), and three prose references inside `ARCHITECTURE.md` (in B6 and in §24 Testing Architecture) meant the *concept's* §29/§24/§60/§68, not the document's own section numbers, but weren't marked as such. This predates this round's edits — it wasn't introduced by this pass — but was only caught now because §29 was being touched anyway. Fixed by disambiguating to "concept §NN" at each site, and by updating `CALCULATION_ENGINE.md`/`DESIGN_SYSTEM.md`'s stale pointers from `ARCHITECTURE.md §29` (now superseded) to the correct `ROADMAP.md` DEV-task.
- **Durable rule change**: none new beyond `/ANTI_ERROR_STANDARD.md` §0 and §10 added this round (the user's direct request, not an independently discovered lesson). The `§29` finding is exactly the kind of case §7 already covers (cross-reference regression after a renumbering/supersession) — it confirms that existing rule is doing its job, rather than requiring a new one. Worth noting for a future retrospective: any document that reuses the Master Product Concept's own numbering scheme for its own top-level sections is at structural risk of this exact collision recurring — flagged here as something to watch, not yet enough of a repeated pattern to warrant a new numbered rule on its own.

## DEV-001 — Development Foundation (completed)

First real code in the repository. Scoped tightly to what DEV-001 actually justifies (see `PROJECT_STATE.md`'s DEV-001 findings for the full breakdown): a root Gradle project (settings, version catalog, wrapper) plus one module, `:core:common` — `Clock`/`SystemClock`, `IdGenerator`/`UuidIdGenerator`, the `AppError`/`AppResult` sealed hierarchies, a `Logger` contract, and `DispatcherProvider`/`StandardDispatcherProvider`, with `FakeClock`/`FakeIdGenerator`/`TestDispatcherProvider` as shared `testFixtures`. 10 JVM unit tests were written and actually executed (not asserted) — 10/10 passing on a cold `./gradlew clean build`.

Every other module in ARCHITECTURE.md §5's target graph, Hilt, repository interfaces, and dev/staging/prod environment configuration were explicitly deferred to the DEV task that actually justifies them (see PROJECT_STATE.md) rather than built speculatively — this is a direct application of ROADMAP.md's "do not create speculative infrastructure" instruction, not an oversight.

Two genuine findings from actually running the build (not just writing it):
- `kotlinx-datetime` 0.7+ deprecated `kotlinx.datetime.Clock`/`Instant` in favor of `kotlin.time.Clock`/`Instant` (kotlin-stdlib) — the first compile attempt failed on `Unresolved reference 'System'` because the old symbol no longer exists; fixed by switching `Clock`/`SystemClock`/`FakeClock` to the stdlib types.
- Kotlin 2.4.20's Gradle plugin warned that Gradle 8.14.3 (the sandbox's installed version) is below its minimum-supported 8.14.4 floor. Fixed properly — bumped the wrapper to Gradle 8.14.5 (confirmed reachable and fetchable) rather than suppressing the warning or downgrading Kotlin, and stayed on the 8.x line rather than jumping to the current 9.7.1 given the still-unverified Android Gradle Plugin compatibility question that a future Android module will raise.

Retrospective note: this is exactly the kind of thing the environment-verification framework anticipated — a pure-Kotlin/JVM module can be genuinely built and tested in this sandbox (Maven Central and the Gradle Plugin Portal are both reachable even though `dl.google.com` is not), so `VERIFIED` here is a real, executed claim, not a placeholder. No `NOT VERIFIED — ANDROID SDK REQUIRED` entries were needed for DEV-001 because nothing Android-SDK-dependent was built — that starts at DEV-003.

## DEV-002 — Security & Quality Foundation (in progress at time of writing)

Wired `detekt` (1.23.8) and `ktlint` (14.2.0) into the root `build.gradle.kts`'s `subprojects` block, so every module — current and future — inherits the same static-analysis/formatting config automatically, per `DEVELOPMENT_PROTOCOL.md` rule 42's "one formatter, one static-analysis config" requirement. Running `ktlintCheck` for the first time against the DEV-001 code found real style violations (multi-parameter constructors not one-per-line, one misordered import) — fixed with `./gradlew ktlintFormat`, then re-verified clean. `./gradlew build` now runs compile + tests + `ktlintCheck` + `detekt` as one command.

Wrote the first GitHub Actions workflow (`.github/workflows/ci.yml`): build/test/lint/static-analysis, a PR-only dependency-review job, and a secret-scan job.

Two genuine findings from verifying dependencies before use (`DEPENDENCY_POLICY.md`), not assumed from memory:
- `gitleaks-action@v2` stops working entirely on 2026-09-16 (tomorrow, relative to when this was written) because GitHub is removing the Node 20 runtime its old build depends on — `@v3` is required. Caught by checking current status live rather than using a remembered version.
- `gitleaks-action` (even `@v3`) requires a paid license for scanning more than one repository under an *organization* account (free for a personal account). Rather than gamble on which kind of account this repository ends up under, the secret-scan job runs the underlying open-source `gitleaks` CLI (MIT-licensed) directly via its official `ghcr.io/gitleaks/gitleaks` container image — sidesteps the licensing question entirely rather than working around it.

Action versions (`actions/checkout@v7`, `actions/setup-java@v6`, `gradle/actions/setup-gradle@v6`, `actions/dependency-review-action@v5`) were all verified live on 2026-09-15 before use, matching the "verify before implementation" instruction from the environment/decisions round — none were assumed from training data.

**First CI run confirmed** (run [34935726894](https://github.com/ntegas/Stepwise/actions/runs/34935726894), commit `842e7db`, checked via the GitHub Actions API rather than assumed): `build` (compile + unit tests + ktlintCheck + detekt) succeeded, `secret-scan` (gitleaks) succeeded, `dependency-review` correctly skipped (push event, not a pull request — that job is intentionally PR-only). DEV-002 is closed.

Retrospective note: this is the first time in the project that a claim of "the CI workflow works" was checked against an actual run rather than left as "should work once it runs" — the workflow was pushed, then its result was read back via `mcp__github__actions_get`/`actions_list` before declaring the task done, closing the loop the `NOT VERIFIED — CI EXECUTION REQUIRED` label exists for.

## DEV-003 — Design System Foundation (in progress at time of writing)

The first DEV task needing the Android Gradle Plugin, and it immediately surfaced a real structural problem: adding `:core:designsystem` (needs `com.android.library`) to the same Gradle build as `:core:common` broke `:core:common`'s previously-VERIFIED build too, because Gradle configures the whole project tree by default regardless of which module's task is requested — an unresolvable AGP plugin (`dl.google.com` blocked in this sandbox) failed everything, not just the Android module. Fixed by (1) declaring AGP/Compose plugins only inside `:core:designsystem`'s own build script, never at root even as `apply false` (the root project is always configured, so anything unresolvable there is fatal to every module), and (2) adding `org.gradle.configureondemand=true` to `gradle.properties`, which is what actually lets Gradle skip configuring `:core:designsystem` when only `:core:common`'s task is requested. Verified empirically, not assumed: `:core:common:build` (10/10 tests) passes cleanly with both fixes in place.

Second real, dated finding: AGP moved to a 9.x major version line; AGP 9.4.0 (current stable, checked against the official Android Developers release notes on 2026-09-15) requires Gradle ≥9.6.0, well past DEV-001's 8.14.5 pin. Before bumping, checked whether this would break the existing detekt/ktlint setup — a known Gradle-9 compatibility issue had been reported against detekt 1.23.8 with an older Kotlin version — and verified empirically rather than assumed either way: the full `:core:common` build (10/10 tests) still passes clean under Gradle 9.6.0, with only a harmless upstream detekt deprecation warning (scheduled for removal in Gradle 10, not 9).

Built `:core:designsystem` (tokens: Color/Type/Spacing/Shape/Elevation/Motion, `StepwiseTheme`; 10 generic reusable components) per `DESIGN_SYSTEM.md`'s now-filled-in content. Deliberately deferred `StepwiseGoalCard`/`StepwiseTaskRow`/`StepwiseActivityRow` since they'd need to guess at domain-model shapes DEV-004 hasn't defined yet. `compileSdk 36` was set because `docs/play-store-checklist.md`'s target-API requirement became mandatory on 2026-08-31 — already in effect, not a future deadline, checked against the date rather than assumed still-pending. `minSdk 26` is a stated, revisable technical default, reasoned the same way DEV-001's Kotlin/Gradle version pins were, not a product decision requiring escalation.

Regression check while touching `DESIGN_SYSTEM.md` found one more genuinely stale cross-reference (same bug class as the earlier `§25`/`§29` cases): its old scaffold cited `DEVELOPMENT_PROTOCOL.md` "rule C3–C4" for accessibility, off by one against the document's real current numbering (23–24). Checked the similar "A4/A5/A7/D2"-style citations elsewhere in the repo against the actual numbering too, to see if this was systemic — they were all correct, so this was an isolated slip, not a repeat of the renumbering-collision pattern.

Nothing in `:core:designsystem` can be verified locally at all, not even `ktlintCheck` — Gradle fails at AGP plugin resolution before ktlint's task graph is even built, since `dl.google.com` is unreachable from this sandbox. The CI workflow was extended with `android-actions/setup-android@v4` to preempt the well-known unaccepted-SDK-license pitfall on GitHub-hosted runners; this entry will be updated once the actual CI run is checked.
