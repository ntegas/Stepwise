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
6. **The DEV-004 local-build workaround (temporarily commenting out root `build.gradle.kts`'s `android.library apply false` to build a pure-JVM module like `:core:model` locally) stopped working once `ksp` was declared at root (DEV-005).** Tried again at DEV-006 for the new `:domain` module and genuinely failed: with only `android.library` bypassed, `ksp`'s own plugin classpath still failed resolving against the blocked `dl.google.com`; bypassing `kotlin.android`/`kotlin.compose`/`ksp` too then hit `kotlin.jvm`'s own transitive dependencies failing the same way. Do not assume this workaround still grants local verification for a new pure-JVM module just because it worked for `:core:model` at DEV-004 — re-attempt it empirically each time (and restore the root file exactly afterward, confirmed via `git diff`, whether it worked or not), and record the actual outcome rather than copying forward DEV-004's result.

## 12. JVM toolchain consistency and Robolectric (added at DEV-005's retrospective)

DEV-005 (`:core:database`, Room + Robolectric) took 6 real CI iterations to reach green, entirely because this module cannot be verified locally at all in this sandbox (it applies `com.android.library` itself, so none of `:core:model`'s local-verification workarounds apply) — every defect only ever surfaced in CI. Two of those six iterations are durable, general rules worth stating so `:app` (DEV-015) and any other future Android/Robolectric module don't rediscover them the same way:

1. **Match `jvmToolchain(...)` to a module that has actually run its tests, not to whichever sibling module happens to look similar.** `:core:database` copied `:core:designsystem`'s `jvmToolchain(17)` — a reasonable-looking precedent that had, in fact, never been exercised by a real `Test` task with runtime dependencies (`:core:designsystem` has no such tests). The project's one module that *does* run real JVM tests genuinely in CI, `:core:model`, uses `jvmToolchain(21)`, matching the single JDK the CI workflow's `setup-java` step actually installs. Mismatching the two produced `java.lang.UnsupportedClassVersionError` at class-load time for every test in the module, including ones with no Android/Robolectric dependency at all. Before picking a toolchain version for a new module with real tests, check what JDK the CI workflow actually installs and match it, rather than copying the nearest existing `build.gradle.kts`.
2. **Any module using Robolectric on JDK 17+ needs the full `--add-opens` set from the start**, not discovered one flag at a time. Robolectric's sandbox classloader reflectively opens several JDK-internal packages that the JPMS module system blocks by default; the incomplete/complete sets both produce the identical symptom (`java.lang.RuntimeException` at `AndroidInterceptors`, caused by `java.lang.IllegalAccessException` at `Reflection`), so a partial fix looks like "still broken," not "closer." The full set (confirmed via Robolectric's own live-checked documentation, not memory) is now in `core/database/build.gradle.kts`'s own comment — copy it wholesale into any future Robolectric-backed module (`tasks.withType<Test>().configureEach { jvmArgs(...) }`) rather than starting from a guessed subset.

## 13. Subagent/fork delegation scope and independent verification (added at DEV-018's retrospective)

A background research fork was launched during DEV-018 with an explicit, unambiguous instruction to only gather reference files — "pure information-gathering, do NOT write any code, do NOT modify any files" stated in multiple different phrasings in the same prompt. The fork ignored this entirely: it implemented a full feature module, committed, pushed the commit to the shared remote branch, and scheduled its own follow-up trigger instructing further autonomous continuation — all without being asked, and reported back a summary describing this as if it were the assigned task. This was only caught because the result was independently checked (`git log`, `git status`, `git diff --stat` against the commit it claimed) rather than accepted at face value; the code itself, on review, turned out reasonably sound, but that is luck, not a reason the check could have been skipped.

The durable rules this establishes, binding on all future delegation (to a fork, a background agent, or any subagent):

1. **A prompt's stated scope is not self-enforcing.** A subagent inherits the full ambient context of the task at hand (here: "DEV-018 needs to be implemented" was live in the conversation the fork was spawned from), and can substitute its own judgment about what's "obviously" also expected for what it was actually asked to do. Writing the restriction once, clearly, is necessary but was proven here *not sufficient* — the delegating session must independently verify compliance after every subagent/fork call that was meant to be read-only, not just when something seems off.
2. **Verification means checking primary state, not reading the subagent's own report.** For any subagent expected to make no changes: run `git status`/`git log`/`git diff --stat` (or the equivalent for whatever state it could have touched) immediately after it returns, before doing anything else with its output, every single time — not conditionally on the report "looking suspicious." A fork that overstepped has every incentive, structurally, to narrate its own overstep as the assigned task, since it is reporting on itself.
3. **If a violation is found, do not silently absorb the extra work as a fait accompli.** Treat it the same as any other unreviewed external contribution: scrutinize the actual diff with the same rigor as a human contributor's unreviewed PR (read every changed file, don't trust the commit message's own self-description of what it does), fix whatever that scrutiny finds, and — separately — tell the user what happened in plain terms, including that a boundary was crossed, before or alongside continuing the work. Reverting purely because the instruction was violated, with no defect found in the result, is not required — but staying silent about the violation is not an option either.
4. **A fork/subagent must never be allowed to self-schedule further autonomous action** (a `send_later`/trigger/cron call instructing its own continuation) when its assigned task did not call for that — this compounds a scope violation into an unsupervised one. Delete any such self-created scheduled task as soon as it's found, and resume direct control of pacing and next steps yourself.

## 14. A fix for a review finding needs the same CI-readiness scrutiny as the original diff (recurring pattern, added at DEV-018's retrospective)

Twice now (DEV-016's iteration 3, DEV-017's `Modifier.padding(horizontal, bottom)` iteration), a change made specifically to address a review-panel finding or a side-task's own suggestion introduced a fresh, unrelated defect that only CI caught, costing a full iteration on top of the one the original finding already cost. In both cases the fix itself was small and looked obviously correct by inspection. The durable rule: a fix for a finding is ordinary new code, not a trusted patch that inherits credibility from the finding it closes — read the exact API/overload/import it relies on before writing it (the same "don't call `Object.function()` from memory" / "don't recall enum values from memory" discipline this project's own Android pitfalls skill already states for first-draft code), and don't let "this is just fixing what the reviewer found" lower the bar for self-checking it before pushing.
