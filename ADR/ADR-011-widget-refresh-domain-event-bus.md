# ADR-011: Widget Refresh via a New `DomainEventBus` (Event-Driven, Not Polling)

**Status**: Accepted

**Context**: A Glance widget doesn't continuously observe a `Flow` the way a Compose screen does — it redraws only when something calls `GlanceAppWidget.update()`. The product requirement was explicit: event-driven refresh, no unnecessary polling.

**Decision**: A new `DomainEventBus` (a `SharedFlow<DomainEvent>` wrapper in `:core:common`, reachable by both `:domain` and `:core:sync` without either depending on the other). Mutating Use Cases publish `DomainEvent.DataChanged` after a successful commit; `:core:sync` publishes `DomainEvent.SyncCompleted` after a pull/push cycle. `WidgetRefreshCoordinator` subscribes and calls `update()` only on affected instances. A coarse periodic Glance update stays as a day-boundary safety net.

**Alternatives considered**: Continuous polling (rejected — explicitly against the stated requirement, and battery-costly); a Widget-specific push mechanism bypassing the existing Use Case success path (rejected — would duplicate plumbing that already exists).

**Note**: This is flagged explicitly as **new** shared infrastructure not previously specified (`ARCHITECTURE.md` §4 Application Layers didn't have an event bus before this), per `ANTI_ERROR_STANDARD.md` §5 — never folded in as if it had always existed.

**Consequences / Risks**: One new cross-cutting piece to maintain; a plausible candidate for reuse by future features needing the same "something changed, redraw a passive surface" pattern.

**Reversibility**: Additive/reversible — a different pub/sub could replace it without touching Use Cases beyond the publish call.

See `ARCHITECTURE.md` §19.5, §27.
