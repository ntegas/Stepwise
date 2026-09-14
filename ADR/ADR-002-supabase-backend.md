# ADR-002: Supabase Backend

**Status**: Accepted

**Context**: Needed one backend serving Android (and later iOS/web), with a relational calculation engine (multi-goal, multi-metric progress fan-out per `PRODUCT_CANON.md` §21/§23) and a single, non-duplicated source of truth (§62).

**Decision**: Supabase — Postgres, Auth, Realtime, Row Level Security.

**Alternatives considered**: Firebase; a custom backend.

**Reason**: The relational model fits the multi-metric/multi-goal calculation engine and the view-based source-of-truth requirement directly. Firestore's standard scaling pattern (denormalized counters maintained via Cloud Functions) is the exact anti-pattern §62/§68 forbid — choosing Firebase would have meant fighting the platform's own idioms to satisfy an already-locked product requirement. A custom backend would mean building and operating auth/API/realtime from scratch with no material benefit over Supabase, which is itself standard Postgres underneath.

**Consequences / Risks**: `supabase-kt` (the Kotlin client) is less mature than Firebase's Android SDK; smaller ecosystem than Firebase.

**Reversibility**: Medium — Postgres is a portable, low-lock-in standard; a future migration to self-hosted Postgres/custom API is realistic without a data-model rewrite.

See `ARCHITECTURE.md` §3, §10, and its Architecture Decision Record for the full comparison.
