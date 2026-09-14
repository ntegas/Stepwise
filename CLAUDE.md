# Stepwise — Session Entry Point

This file is auto-loaded at the start of every Claude Code session opened against this repository. It is intentionally short — it exists to point at the canonical documents below, not to duplicate them. Read the ones relevant to the task before acting; do not re-derive a decision that already lives in one of them.

## Read before acting

- **`/PRODUCT_CANON.md`** — the product: the Master Product Concept, the non-negotiable UX principle, the concept-change process. Start here for "what is Stepwise and why."
- **`/ARCHITECTURE.md`** — the canonical, detailed technical architecture: stack, layers, modules, offline-first/sync, backend, the Home Screen Widget, testing, the Architecture Decision Record, open questions.
- **`/DATA_MODEL.md`** — entities, fields, relationships, the automatic-cascade/source-of-truth rule.
- **`/DEVELOPMENT_PROTOCOL.md`** — binding rules for how code in this repo must be structured (centralization, no duplicated logic, module boundaries, etc.).
- **`/ANTI_ERROR_STANDARD.md`** — the required process before changing anything architecturally significant: consistency audit, affected-consumer inventory, anti-duplication search, how to surface a conflict instead of silently resolving it.
- **`/PROJECT_STATE.md`** — current phase, what's done, what's next. Check this before assuming where the project is.
- **`/CALCULATION_ENGINE.md`, `/DESIGN_SYSTEM.md`** — scaffolded; real content (formulas, tokens) arrives in their own phase per `/PROJECT_STATE.md`. Do not invent content into these ahead of that phase.
- **`/ADR/`** — one file per significant architecture decision. Check here before revisiting something that looks already-decided.
- **`/docs/security/`** — `SECURITY_ARCHITECTURE.md`, `THREAT_MODEL.md`, `SECURITY_TEST_MATRIX.md`, `DEPENDENCY_POLICY.md`, `INCIDENT_RESPONSE.md`, `AI_CODE_SECURITY.md`. Security is cross-cutting, not a post-development pass — read the relevant file for whatever layer you're touching (domain, sync, backend, auth, widget, notifications, billing, CI/dependencies).
- `/docs/functional-analysis.md`, `/docs/scope-of-work.md`, `/docs/play-store-checklist.md` — supporting breakdowns, referenced from the canonical docs above.
- `/docs/android-stack.md`, `/docs/android-architecture.md` — superseded (React Native/Expo era). Historical only; never build against them.

## The one rule that overrides convenience

Per `/ANTI_ERROR_STANDARD.md` and `/PRODUCT_CANON.md`'s concept-change process: if implementing something requires changing an already-decided piece of product concept or architecture, **do not change it silently**. Show the conflict, the affected consumers, the options, and wait for a decision.
