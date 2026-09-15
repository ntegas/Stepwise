# ANTI_ERROR_STANDARD.md — Stepwise Audit & Change-Management Process

This formalizes the process already used across this project's architecture work (Phase 1.5 concept reconciliation, the Android Architecture Specification's consistency audit, the Widget addition, the centralization-rules pass). It is binding on all future work, human or AI. See also `/docs/security/AI_CODE_SECURITY.md` for the security-specific extension of this standard (rules 28, 33–36 of the Security/Transaction/Concurrency standard).

## 0. Read this file first

This file is read in full before starting any task in this repository — analysis, documentation, or code, no exceptions for tasks that look small or routine. The point is to catch a mistake before it's made by having the relevant rule already in view, not to re-derive it afterward from a retrospective. If a task is already in progress when this file is updated with a new rule, the new rule applies from that point forward in the same task.

## 1. Before creating anything new — anti-duplication search

Before adding a new service, use case, repository, helper, formatter, calculation, or component: search the repo for an existing canonical implementation first. A new implementation is justified only when the search comes back empty, and the search itself (what was searched, what was found) is worth stating in the change summary, not just the conclusion.

## 2. Before changing anything architecturally significant — consistency audit

1. **Requirements inventory**: list the architecturally-binding requirements the change touches, traced to their source (a `/PRODUCT_CANON.md` §, an existing ADR, a prior decision).
2. **Find real ambiguities/tensions**, not just restate the request. Where the new instruction is genuinely underspecified against what already exists, say so explicitly rather than picking silently.
3. **Resolve what can be resolved from existing canon** with stated reasoning; escalate what can't (see §4 below) instead of guessing.

## 3. Affected-consumer inventory

For every change, check impact against — at minimum — the list that's proven relevant so far: Today, Goals, Activities, Tasks, Habits, Sessions, ProgressEvent, Recurrence, Timer, Room, Domain Use Cases, Offline Sync, Idempotency, Analytics, Navigation/Deep Links, Notifications, Progress/Pace/Forecast/Life Areas/Life Balance, Search, Widget refresh. "No impact" is a valid, expected answer for most of these on most changes — the point is to have checked, and to say so, not to force every change to touch everything.

## 4. Never change approved architecture silently

If implementing something requires changing already-decided product concept or architecture:
1. Explain the problem.
2. Show the affected functions/entities and their consumers (§3).
3. Propose options.
4. Wait for the decision-maker's choice.
5. Only then change the architecture — and update every document that stated the old decision, not just the newest one.

This applies equally to a human-directed change and to a change an AI concludes is necessary mid-implementation.

## 5. Flag new cross-cutting pieces explicitly

When satisfying a requirement needs a genuinely new piece of shared infrastructure (not just reusing/extending something that already exists), say so explicitly and explain why the existing pieces don't cover it — never fold something new in as if it had always been there. (Precedent: `DomainEventBus` in `/ARCHITECTURE.md` §19.5 was introduced this way, not silently.)

## 6. Acceptance Criteria ↔ Tests = 1:1

For any substantial change, each acceptance criterion gets exactly one named test strategy, and every test strategy traces back to a criterion — no criteria without a test, no tests that don't map to a stated criterion. (Precedent: `/ARCHITECTURE.md` §19.15.)

## 7. Diff-only + regression verification after editing canonical docs

After editing a canonical document:
- Confirm the diff touches only what was intended (`git diff --stat`, then read the full diff) — no accidental edits elsewhere.
- Re-check cross-references: renumbering a section, renaming a file, or moving a doc breaks every place that pointed at the old number/path. Grep for the old identifier across the repo before considering the change done.
- Re-read the changed sections once fully, adversarially, looking for a self-contradiction the edit might have introduced (precedent: the `§25` ambiguity found and fixed during the Widget insertion, where the document's own renumbered section collided with a Master Product Concept section reference).

## 8. Report format for significant changes

When a change is substantial enough to warrant it, the response accompanying it states: what sections/files changed, the affected-consumer inventory, new interfaces/entities introduced (flagged per §5), any conflicts found with existing architecture (or "none found"), and a READY / NOT READY status — not just a description of the new content.

## 9. AI-specific extensions

- An AI's own claim that its implementation is "secure" / "correct" / "tested" is not evidence — see `/docs/security/AI_CODE_SECURITY.md`.
- Never weaken, delete, or skip a failing test to reach a green build without stating the reason separately and getting it reviewed.
- A new dependency an AI proposes is verified independently before use (real package, real maintainer, license, vulnerability history) — never installed just because it was suggested.

## 10. Periodic Retrospective

At meaningful intervals (a completed development stage, a checkpoint in `ROADMAP.md`, or whenever asked), review recent work for: what was attempted, what failed or had to be redone, why it happened, and what durable rule — if it had already been in force — would have prevented it.

- The retrospective event itself (what was reviewed, what was found — including "nothing new found") is recorded in `DEVELOPMENT_LOG.md`, since that file is the historical record of what happened and when.
- If the review surfaces a genuinely new durable rule not already covered by an existing section here, it is added to this file as a new numbered item — `DEVELOPMENT_LOG.md` records that the lesson was found; this file is what makes it binding on future work.
- A retrospective that finds nothing new is still worth recording — it confirms the standard is currently sufficient, which is itself useful history.

## 11. Gradle multi-module plugin resolution (added at DEV-003's retrospective)

DEV-003 spent 8 of its 14 CI iterations misdiagnosing a generic Gradle plugin-resolution error as an AGP-version-specific bug (full account in `DEVELOPMENT_LOG.md`/`PROJECT_STATE.md`). The durable rules that would have prevented it:

1. A Gradle error reading "\[plugin id\] ... already on the classpath with an unknown version, so compatibility cannot be checked" is, first and foremost, a signal that some plugin ID used in the build is being resolved for the first time in a subproject without a matching `apply false` declaration at the root — **not** evidence of an AGP-version-specific bug. Check this before any other theory, especially for Kotlin Gradle Plugin variants that share one underlying artifact (`org.jetbrains.kotlin.jvm`, `.android`, `.multiplatform`, `.plugin.compose`): if any one of them is applied anywhere in the build, every one of them that's used anywhere else must also be declared `apply false` at root, with the same version.
2. Once any Android module (`com.android.library`/`com.android.application`) exists alongside a pure-JVM one, AGP itself must also be declared `apply false` at root, alongside the Kotlin plugins — a Kotlin Android Gradle plugin's own classes statically reference AGP classes, so declaring the Kotlin plugin without also declaring AGP produces a `NoClassDefFoundError` at root, a different symptom of the same missing-declaration gap. The two travel together; there's no partial version of this fix.
3. In a network-restricted sandbox where `dl.google.com` is unreachable, rule 2 has a real cost: it means the root project's mere configuration (which happens on every Gradle invocation, for every task, regardless of which module it targets) attempts to resolve AGP and fails — so no Gradle task can be run locally at all once any Android module exists, not just Android-specific ones. This is a genuine, permanent trade-off in this specific environment (recorded as VERIFICATION DEBT in `PROJECT_STATE.md`), not a defect to keep re-solving — and it is **not** a general concern outside a network-restricted sandbox like this one; a project with normal internet access hits none of this.
4. Before assuming a downgrade of an already-adopted major dependency (e.g. AGP) is safe, check what the *other* already-adopted dependencies (e.g. a Compose BOM version) require of it — a Compose BOM's own transitive dependencies can carry their own minimum-AGP/minimum-`compileSdk` floor, surfaced only as a real AAR-metadata build failure, not a version-catalog conflict. DEV-003 downgraded AGP for 3 iterations before this surfaced.
5. Any Compose module needs both `config/detekt/detekt.yml`'s `ignoreAnnotated: ["Composable"]` overrides (for detekt's `FunctionNaming`/`LongParameterList`) and the root `.editorconfig`'s `ktlint_function_naming_ignore_when_annotated_with = Composable` — expected, well-known friction between JVM-oriented linter defaults and Compose's PascalCase-function-as-component convention, not a real code-quality problem. Both already exist project-wide as of DEV-003, so a future Compose module should inherit them automatically — verify that assumption holds when one is actually added (DEV-015 `:app`, DEV-028 Widget), rather than re-deriving this from scratch.
