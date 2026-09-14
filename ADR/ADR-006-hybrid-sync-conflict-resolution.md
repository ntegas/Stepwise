# ADR-006: Hybrid Sync Conflict Resolution (Not Universal Last-Write-Wins)

**Status**: Accepted (revised from an initial blanket last-write-wins proposal)

**Context**: An initial proposal used row-level last-write-wins for every entity. The user explicitly rejected universal LWW for progress-affecting data, while also rejecting full CRDT/field-level merge as unjustified complexity for a single-owner-per-account product.

**Decision**: Two tiers.
- **Low-risk scalar metadata** (title, description, notes, icon, color, most Settings fields): silent version-checked last-write-wins keyed on the server's `updated_at`.
- **Progress-affecting entities** (Sessions/SessionMetricValues, materialized recurrence occurrences): the same version check detects a conflict, but the losing write is preserved as a non-destructive "conflict copy" rather than discarded — the newer `updated_at` still becomes the current value (one clear answer to show), but nothing a user recorded silently vanishes.
- **Independent new Sessions never conflict with each other**, even when both affect the same Goal — each is its own canonical fact identified by its own client-generated UUID; Goal progress is a view recomputed once both have synced (ADR-004), not a value either Session competes to set.
- Deletes use tombstones. Derived data (aggregates, rollups) never participates in merge as an authoritative value — it's always recomputed from canonical facts.

**Alternatives considered**: Universal last-write-wins (rejected — risks silently discarding real progress data); full CRDT / field-level merge (rejected — unjustified complexity given single-owner-per-account usage).

**Consequences / Risks**: A genuine same-moment edit on two devices still resolves automatically to one current value; the loser is recoverable but not surfaced by default (a future UX pass may want to surface conflict copies — not decided here).

**Reversibility**: Upgradeable later (e.g. to a blocking merge UI) without a schema change, since version/timestamp columns and the conflict-copy record already exist. Insufficient if Stepwise ever adds shared/collaborative goals — would need real CRDT-style merging at that point.

See `ARCHITECTURE.md` §9 (B7) and the Security/Transaction/Concurrency standard folded into the same section.
