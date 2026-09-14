# ADR-014: Local Database — Room

**Status**: Accepted

**Context**: Needed a local operational data store (`ARCHITECTURE.md` §7) supporting offline-first writes, `Flow`-based reactive reads, and long-term migration safety (years of personal history, per `PRODUCT_CANON.md` §52).

**Decision**: Room (SQLite).

**Alternatives considered**: SQLDelight (more directly portable to a future Kotlin Multiplatform shared data layer); Realm.

**Reason**: Official Jetpack library, best Compose/`Flow`/WorkManager integration, mature migration tooling (`MigrationTestHelper`, explicit `Migration` objects — see `ARCHITECTURE.md` §25).

**Consequences / Risks**: SQLDelight would ease a future KMP data-layer share; Room's Android-specific integration is traded for that portability today.

**Reversibility**: Medium — swappable later, but costly given how central it is.

See `ARCHITECTURE.md` §3, §25.
