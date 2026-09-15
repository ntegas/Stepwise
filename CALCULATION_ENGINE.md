# CALCULATION_ENGINE.md — Scaffold

**Not yet populated.** Real content (exact formulas, edge-case rules, the Kotlin/SQL parity contract) is written during `/ROADMAP.md`'s DEV-019 (Progress Calculation Engine), building on DEV-004's Canonical Data Model, per `/PROJECT_STATE.md`. This file exists now only so the canonical documentation set (`DEVELOPMENT_PROTOCOL.md` §47) is complete, per explicit instruction to wait rather than invent formulas ahead of that phase.

## What this document will cover, per `DEVELOPMENT_PROTOCOL.md` rule A5

- Goal Progress — per `progressMode` (`METRIC`, `TARGET_VALUE`, `FREQUENCY`, `MILESTONES`, `TASKS`, `MANUAL`) — see `ARCHITECTURE.md` §0 (B6) for the mode decision; exact formulas belong here.
- Planned vs Actual / Execution rate — the Done=100%/Partial=actual÷planned/Missed=0%/Cancelled-excluded/Rescheduled rule (`PRODUCT_CANON.md` §29) as an exact, testable formula.
- Pace and Forecast.
- Goal Impact scoring.
- Activity and Life Area statistics (Attention/Execution/Goal Progress/Trend, kept separate per `PRODUCT_CANON.md` §63).
- Life Balance aggregation.
- Period comparisons and Week/Month/Year/All-Time aggregation.
- The Kotlin (`:core:calculation`) ↔ Postgres (SQL views) parity requirement — both implementations must agree, and this document is where their shared contract is specified precisely enough to test.

## Until this phase

Do not invent or approximate any of the above in code or in other documents. Where a formula is referenced elsewhere (e.g. `PRODUCT_CANON.md` §29's execution-rate example), treat it as illustrative of the *rule*, not as this document's final specification.
