# ADR-007: Explicit Goal `progressMode` Instead of One Universal Formula

**Status**: Accepted

**Context**: `PRODUCT_CANON.md` §11's Goal "types" (Cumulative/Quantity/Distance/Financial/Target Value/Frequency/Percentage) conflate the *unit/shape* of a number with *how its current value is computed*. Percentage/Project goals in particular had no single natural formula.

**Decision**: Goal carries an explicit `progressMode`: `METRIC` (sum of a linked metric via `goal_activity_links`), `TARGET_VALUE` (latest reading, not a sum), `FREQUENCY` (a windowed per-period count), `MILESTONES` (weighted milestone completion — `Σ(milestone.progress × milestone.weight) / Σ(milestone.weight)`), `TASKS` (a fallback: weighted task-completion ratio when a Goal has no Milestones), `MANUAL` (a user-entered value — the *sole* input for that Goal, never a second number competing with a computed one). Exactly one mode per Goal, chosen at creation with a sensible default, changeable under "More options."

**Alternatives considered**: One universal progress formula inferred from Goal type (the original approach, rejected as underspecified for Percentage/Project goals).

**Reason**: `MANUAL` mode does not violate ADR-004/§62, because it's the only input for that Goal's progress, not a duplicate of something Sessions/Milestones/Tasks already determine.

**Consequences / Risks**: Pace/Forecast math applies naturally to `METRIC`/`TARGET_VALUE`/`FREQUENCY` but needs mode-specific treatment for `MILESTONES`/`TASKS`/`MANUAL` — deferred to `CALCULATION_ENGINE.md`.

**Reversibility**: Additive — new modes can be added later without touching existing ones.

See `ARCHITECTURE.md` §0 (B6), §6, and `DATA_MODEL.md`. Closes OQ-1.
