# SECURITY_TEST_MATRIX.md

## Abuse case tests (not just the happy path)

Each row gets an actual test once the relevant subsystem exists — this matrix is the checklist so none are forgotten, not a claim that they're implemented yet.

| # | Abuse case | Expected result |
|---|---|---|
| 1 | User A attempts to read User B's Session | Denied by RLS, not just hidden by the client |
| 2 | User A attempts to UPDATE User B's Goal | Denied by RLS |
| 3 | A modified client sends `premium: true` | Ignored — entitlement is server-verified only (`SECURITY_ARCHITECTURE.md` — mass assignment protection, `ARCHITECTURE.md` §19 Billing) |
| 4 | The same completion command is submitted repeatedly (retry, double-tap, replay) | Exactly one logical effect (`ARCHITECTURE.md` §9.0 command idempotency) |
| 5 | A deleted Session arrives from a stale offline device | Tombstone wins — the row does not resurrect (`ARCHITECTURE.md` §9, ADR-006) |
| 6 | A deep link contains another user's entity ID | Denied — ownership checked before the resource opens (`SECURITY_ARCHITECTURE.md` — deep link security) |
| 7 | A notification action is triggered multiple times | Same idempotency guarantee as row 4 |
| 8 | A client attempts to change its local `ownerId` | Has no effect — the backend derives identity from the authenticated session, never a client-supplied field |

Extend this table as new subsystems are designed — each `THREAT_MODEL.md` entry should produce new rows here.

## CI security gates

Every PR runs, where applicable: build, unit tests, lint/static analysis, security static analysis (SAST), dependency vulnerability scan (SCA), secret scan, dependency diff/review, migration tests, and RLS/security tests when the backend changes. A critical security finding blocks merge. See `DEVELOPMENT_PROTOCOL.md` §43 for the general CI/CD pipeline this extends, and `DEPENDENCY_POLICY.md` for the dependency-specific gates.

## Security regression tests

Every security vulnerability found after launch gets a regression test on fix, where technically possible, so the same class of error can't silently return.

## Independent verification for security-critical code

For auth, authorization, crypto, RLS, billing, and input validation: the same AI must not be the sole author of both the implementation and the tests presented as proof of its safety. See `AI_CODE_SECURITY.md` for the full rule and the related test-tampering prohibition.
