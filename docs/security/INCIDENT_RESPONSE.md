# INCIDENT_RESPONSE.md

## Incident readiness (must be possible before it's needed)

The architecture must support all of the following without a rebuild:
- Revoke credentials.
- Disable a vulnerable backend function.
- Rotate secrets.
- Force a minimum app version if a serious issue requires it.
- Disable a risky feature.
- Invalidate sessions.
- Patch the server independently of an Android app release (a real advantage of most logic living in Supabase/Postgres/Edge Functions rather than only on-device).

## Updateability

No security mechanism is built such that it can't be safely updated. Cryptographic algorithms, backend checks, feature controls, and policy mechanisms are all replaceable without a full app rebuild — this is a design constraint on how those pieces are built, checked at the time each is designed (`SECURITY_ARCHITECTURE.md`), not an afterthought.

## Periodic security audit

Before each production release, run a dedicated security audit against the *current* versions of: OWASP MASVS, Android security recommendations, Google Play security requirements, Supabase production/security guidance, dependency vulnerabilities, and current target-SDK behavior. Security recommendations change — no specific API or library version is permanently declared "safe" in this document; requirements are re-checked live before major releases (same discipline as OQ-3/OQ-4 in `ARCHITECTURE.md`).

## When an incident happens (to be filled in as the relevant infrastructure exists)

This section is intentionally not populated with a specific runbook yet — a runbook (who does what, in what order) depends on infrastructure (Edge Functions, RTDN, CI) that hasn't been built. Populate this once that infrastructure exists, rather than writing a plausible-sounding but untested procedure now.
