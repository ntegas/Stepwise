# ADR-003: Offline-First Architecture

**Status**: Accepted

**Context**: `PRODUCT_CANON.md` §59 requires core actions (marking a Session, creating a Task, editing History) to work with zero connectivity, syncing once connectivity returns — stated as a day-one architectural constraint, not later hardening.

**Decision**: Every core action writes to Room synchronously and returns immediately; the UI never waits on a network round trip for a normal action. Reads for Today/Goals/Plan/Progress come from Room via `Flow`. A background Sync Engine (outbox pattern) reconciles with Supabase independently of the UI thread.

**Alternatives considered**: Network-first with local caching as a fallback (rejected — makes the common case, not the exception, depend on connectivity).

**Reason**: Matches the explicit product requirement directly; also the only design compatible with the Widget (`ARCHITECTURE.md` §19) needing to complete an action with no network at all.

**Consequences / Risks**: Requires the full offline-sync machinery (outbox, idempotency, conflict resolution — ADR-006) to exist before any "just call the API" shortcut is safe to build.

**Reversibility**: Structural — this is a foundational layering choice (`ARCHITECTURE.md` §4, §7, §8), not a swappable detail.
