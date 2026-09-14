# ADR-010: Widget Technology — Jetpack Glance

**Status**: Accepted

**Context**: `PRODUCT_CANON.md` (as extended by explicit instruction) requires a full Android Home Screen Widget as a first-class, canonical product feature, not experimental.

**Decision**: Jetpack Glance, isolated to a new `:feature:widget` module with zero dependency on `:core:database`/`:core:network` (same feature-module rule as every other feature).

**Alternatives considered**: Classic `RemoteViews`/`AppWidgetProvider` directly; a hypothetical separate "Widget sync" system (rejected outright — would violate ADR-004/the single-execution-pipeline rule).

**Reason**: Glance is Google's current recommended approach and fits the Compose-style codebase. Note explicitly: it's the module-boundary rule, not the choice of UI framework, that actually guarantees no second execution path (see ADR-011 and `ARCHITECTURE.md` §19.2–19.3).

**Consequences / Risks**: Glance's API surface and Android's widget-hosting limits (update frequency floor, `ActionCallback` execution budget) change over time — must be verified against live documentation immediately before implementation (OQ-4, not assumed here).

**Reversibility**: Presentation-layer choice, reversible without touching domain/data.

See `ARCHITECTURE.md` §19.1–19.2.
