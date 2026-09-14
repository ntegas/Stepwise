# ADR-009: Reminder Scheduling via a Domain-Owned Interface

**Status**: Accepted

**Context**: Task/Habit reminders (`PRODUCT_CANON.md` §16/§22) are unambiguously wanted, but the concrete Android mechanism (exact alarms vs. inexact `WorkManager` fallback) is subject to Android/Play policy that has changed before and may again (OQ-3).

**Decision**: `:domain` declares a `ReminderScheduler` interface (`schedule`, `cancel`, `rescheduleAll`); `:core:notifications` is the sole implementation, picking `AlarmManager`/`WorkManager`/FCM as appropriate. No other module references those Android APIs directly.

**Alternatives considered**: Domain/feature code calling `AlarmManager`/`WorkManager`/FCM directly (rejected — couples domain architecture to a policy surface that changes independently of the product).

**Reason**: Isolates Android/Play policy churn to one module; OQ-3 becomes an implementation-time detail behind a stable interface, not a domain-architecture risk.

**Consequences / Risks**: None — pure abstraction, no behavior change.

**Reversibility**: Fully reversible — an interface, not a structural commitment.

See `ARCHITECTURE.md` §18, §27.
