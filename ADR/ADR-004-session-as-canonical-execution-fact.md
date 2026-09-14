# ADR-004: Session as the Canonical Execution Fact; No Independently-Mutable Totals

**Status**: Accepted

**Context**: `PRODUCT_CANON.md` §62 (source of truth) and §68 forbid independently-maintained totals that can desync from reality (the concrete example given: `Goal=74h` while `Activity=73h` while `Month total=72h40m`). An early data model (Phase 0/1.5) had `goals.current_value` as a trigger-updated column — exactly the pattern §62 warns against.

**Decision**: `Session` (plus its `SessionMetricValue`s) is the one canonical execution fact. Every other number — Goal progress, Activity/Life Area rollups, period aggregates, Pace, Forecast — is a view/query computed from Sessions on read, never a stored, independently-editable column. This also resolved a real ambiguity (`ARCHITECTURE.md` §0, B1): `Task.plannedResult` is a genuine Task-owned field, but `Task` has no independently-editable actual-execution field — actual values live only on `Session`.

**Alternatives considered**: Cached/trigger-maintained totals per entity (the original Phase 0/1.5 approach, rejected on exactly this ADR's grounds).

**Reason**: A cached running total is precisely the kind of number that can drift from its source; a view cannot drift because it has no independent state.

**Consequences / Risks**: Every derived number is computed on read rather than pre-aggregated — acceptable at this product's expected scale (see ADR-008 for the specific `ProgressEvent` case), revisit only if profiling ever shows otherwise.

**Reversibility**: A materialized view is a drop-in performance upgrade later behind the same read interface, without weakening the principle.

See `ARCHITECTURE.md` §7 (the four-layer source-of-truth model: domain event / local operational / backend persistence / derived analytics) and `DATA_MODEL.md`.
