# ADR-005: Lazy Recurrence Materialization with Deterministic Occurrence Identity

**Status**: Accepted

**Context**: `PRODUCT_CANON.md` §18/§25 require that a specific occurrence of a recurring Task/Activity/Habit can carry its own status (Overdue, Rescheduled, Missed) independent of the recurrence rule, without pre-generating years of empty rows, and without two devices independently creating duplicate rows for the same logical occurrence.

**Decision**: A **virtual occurrence** is computed on read from the `RecurrenceRule` for display (Today, Plan/Calendar) with no row in existence. An occurrence is **materialized** (gets a real row) only the moment it needs individual state (completion, edit, reschedule, skip, exception). Its ID is a **deterministic hash of `(recurrenceRuleId, occurrenceDate)`**, not a randomly generated UUID — so two devices independently materializing "the same" logical occurrence converge on one row via the ordinary sync upsert-by-UUID mechanism (ADR-006/the idempotency design), rather than needing a separate deduplication step.

**Alternatives considered**: Pre-generating all future occurrences (rejected — unbounded row growth); lazy materialization with random IDs per instance (rejected — would let two devices create duplicate rows for the same logical occurrence).

**Consequences / Risks**: Slightly more complex read path (merging rule-computed and materialized rows) — isolated to one Recurrence Engine component (`DEVELOPMENT_PROTOCOL.md` A7), not reimplemented per screen.

**Reversibility**: Reversible.

See `ARCHITECTURE.md` §13 and §0 (B3, B9).
