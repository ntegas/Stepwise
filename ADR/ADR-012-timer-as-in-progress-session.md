# ADR-012: Timer Modeled as a Session in `IN_PROGRESS` Status

**Status**: Accepted

**Context**: A running Timer needed one authoritative state shared across App and Widget, surviving process death/reboot/app close, with elapsed time that can't be lost.

**Decision**: A Timer is not a new domain entity — it **is** a `Session` in `IN_PROGRESS` execution status (already defined at the concept level, `PRODUCT_CANON.md` §25). `StartTimerUseCase` writes a `sessions` row with `status = IN_PROGRESS`, `startedAt = now()`; `StopTimerUseCase` computes `actual = now() − startedAt` and transitions the Session via the normal completion path. Elapsed time is computed at render time, never persisted as an incrementing counter.

**Alternatives considered**: A separate `Timer`/`ActiveTimer` table with its own running/mutable state (rejected — would be exactly the independently-mutable running total ADR-004/§62 forbids, and would need its own cross-device consistency logic that a plain Session row gets for free).

**Reason**: No new domain entity needed; a timestamp already in Room trivially survives process death/reboot, unlike in-memory running state.

**Consequences / Risks**: None material — this is a modeling clarification of an already-approved status value, not a new commitment.

**Reversibility**: N/A.

See `ARCHITECTURE.md` §19.8.
