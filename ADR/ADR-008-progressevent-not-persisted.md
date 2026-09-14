# ADR-008: `ProgressEvent` Is Not a Persisted Table

**Status**: Accepted (revises the Phase 1.5 data model)

**Context**: `PRODUCT_CANON.md` §64 lists `ProgressEvent` as a domain entity; the Phase 1.5 data model implemented it as a Postgres-trigger-maintained table. Asked explicitly to evaluate whether a separate persisted entity is needed at all, rather than keeping it by default.

**Decision**: `ProgressEvent` is not a stored table anywhere, client or backend. It remains a conceptual entity — "this session's metric value contributed X to this goal" — answered by a view/query over `session_metric_values ⋈ goal_activity_links`, computed fresh on read, exactly like Goal progress itself (ADR-004).

**Alternatives considered**: A Postgres-trigger-maintained `progress_events` table (the Phase 1.5 approach); a client-synced `progress_events` entity (rejected outright — the client never authors an arbitrary authoritative ProgressEvent, per `PRODUCT_CANON.md` §24's intent).

**Reason**: A view cannot drift from its inputs because it has no independent state to drift — strictly purer under §62 than a materialized table that must stay in sync with trigger logic. Also removes an entire category of sync-duplication risk, since there's nothing shaped like a ProgressEvent for a client to ever double-write.

**Consequences / Risks**: None material at the product's expected scale (thousands, not millions, of Sessions per account over years).

**Reversibility**: A materialized view is a drop-in upgrade behind the same read interface if scale ever demands it.

See `ARCHITECTURE.md` §0 (B8), §7, §12.
