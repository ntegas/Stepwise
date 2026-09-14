# SECURITY_ARCHITECTURE.md

Security is a cross-cutting architectural requirement for domain, database, sync, backend, authentication, UI, widgets, notifications, billing, CI/CD, dependencies, and AI-generated code — not a phase after development (`DEVELOPMENT_PROTOCOL.md` §40). Standards referenced: OWASP MASVS/MASTG, Android Security Best Practices, NIST SSDF, secure-by-default, least privilege, zero implicit trust, defense in depth.

Sibling documents: `THREAT_MODEL.md`, `SECURITY_TEST_MATRIX.md`, `DEPENDENCY_POLICY.md`, `INCIDENT_RESPONSE.md`, `AI_CODE_SECURITY.md`.

## Trust boundaries

**The Android client is an untrusted environment**, including our own official app build. A user can modify the APK, run a rooted/emulated environment, intercept calls, alter the local database, call the Supabase API directly, or replay requests. Backend authorization must never be based on a client-asserted claim (`"userId": "123"`, `"premium": true`) — the backend establishes identity and authorization context itself, from the authenticated request, never from client-supplied fields.

## Supabase RLS — deny by default

Every user-data table has RLS **enabled**, default-deny, with a policy that explicitly ties each row to the authenticated identity (never relying on UI or repository-layer filtering as the actual boundary). Every new table gets its own RLS test covering SELECT/INSERT/UPDATE/DELETE, including an explicit User A → User B access attempt. See ADR-002 for why Supabase/Postgres RLS was chosen as this boundary in the first place.

## Service role protection

Supabase service-role credentials never appear in the APK, the repository, a production client `BuildConfig`, AI context, or logs. The service role exists only in the trusted backend environment (Edge Functions).

## Secrets management

No hardcoded API secrets, private keys, signing credentials, database passwords, service-role keys, OAuth client secrets, or CI tokens. Secrets live in the appropriate secure environment/secret manager; the repository contains only safe public configuration values. See `AI_CODE_SECURITY.md` for the AI-context-specific extension of this rule (`.gitignore` is not sufficient on its own).

## Authentication and authorization

- **Authentication** (who you are) and **authorization** (what you may do) are separate — a successful login does not imply the right to perform any given operation.
- Short-lived authentication tokens, secure refresh lifecycle, logout invalidation, account deletion, session revocation where applicable, rate limiting, and secure recovery are all required.
- Android Credential Manager / passkeys are the preferred modern mechanism where they fit the product requirements, alongside email/password and Google Sign-In (`ARCHITECTURE.md` §11).
- **Authorization is enforced at the backend/database layer.** A client-side check (`if (currentUser.id == ownerId)`) may improve UX; it is never a security boundary. RLS is the actual boundary.

## Local data security

Classify local data (e.g. `PUBLIC` / `INTERNAL` / `USER_PRIVATE` / `SECRET`). No sensitive credentials in plaintext. Credential/key material uses Android Keystore and system secure mechanisms — never a homegrown scheme.

## Cryptography policy

No custom encryption algorithms, homegrown key derivation, static encryption keys baked into the APK, hardcoded IVs/nonces, or obsolete crypto. Only platform or widely-vetted libraries/algorithms. Cryptographic code is security-critical by definition — an AI-generated cryptographic implementation is never accepted without separate, explicit review.

## Network security

Production traffic uses secure transport; cleartext HTTP is forbidden outside explicitly isolated development scenarios. Network Security Configuration is explicit. Never add a globally permissive trust manager, never disable certificate validation to "fix" a connection error, and never ship `TrustAllCertificates` / an always-true `HostnameVerifier` in production code.

## Exported Android components

Every `Activity`/`Service`/`Receiver`/`ContentProvider` is checked against the default: **do not export without a reason.** Every exported entry point is reviewed for authentication, authorization, input validation, intent-spoofing resistance, and data leakage.

## Deep link security

Deep links are untrusted input. Route, entity ID, ownership, allowed action, and authentication state are all validated — a deep link must never open or modify a resource the current user doesn't own.

## Widget security

The Widget (`ARCHITECTURE.md` §19) is an external presentation surface: its actions cannot bypass the domain/security pipeline (§19.3 already guarantees this structurally — there is no privileged write path that belongs to the Widget alone). Additionally checked: forged intents, stale `PendingIntent`s, replay, duplicate action (covered by command idempotency, `ARCHITECTURE.md` §9.0/§19.4), and privacy exposure (§19.11 — what detail level the Widget is allowed to show).

## Notification action security

Notification actions use the same canonical commands as every other surface (`DEVELOPMENT_PROTOCOL.md` A4/D2) — never accept an arbitrary entity ID from a notification action and perform a privileged operation without checking ownership/state first.

## Input validation and injection defense

All external data is untrusted: UI input, deep links, notifications, backend data, imported files, restored backups, server responses. Validation happens at the appropriate trust boundary. No SQL built by string concatenation — Room parameter binding / prepared queries on-device; the backend likewise never builds dynamic SQL from untrusted input without safe parameterization.

## Mass assignment protection

A backend mutation never blindly accepts an arbitrary client object and writes every field. Security-sensitive fields (`ownerId`, `role`, `entitlement`, `serverVerified`, `createdByTrustedSystem`, subscription state) are never client-settable — the set of writable fields is always an explicit allowlist.

## Rate limiting and abuse control

Backend-sensitive operations (authentication, account recovery, billing verification, any future invitations, expensive backend operations) get rate limiting, replay protection, abuse detection, and server-side validation.

## Play Integrity

The architecture supports Google Play Integrity for risk-sensitive server interactions, as one signal in defense-in-depth — never the sole authentication mechanism. Integrity verdicts are verified on the backend; verification logic and keys never live in the client.

## Logging security

Never logged: access tokens, refresh tokens, passwords, private keys, complete authorization headers, or unnecessary sensitive personal data. Release logs are minimized; debug logs never ship to production automatically. See `ARCHITECTURE.md` §26 for the general logging/observability design this constrains.

## Screenshot / clipboard policy

No unusual restriction needed for Stepwise's ordinary data today, but the architecture must allow protecting a genuinely sensitive screen later. Secrets/auth data are never placed on the clipboard or in visible UI.

## Backup security

Decide explicitly what Android Backup may copy. Tokens, private keys, or other sensitive security material are never included if doing so creates risk.

## Database invariants — defense in depth

Critical rules are enforced twice where justified: once in domain validation, once as a database constraint (required IDs, uniqueness, FK relationships, non-negative values where applicable, duplicate-logical-occurrence prevention per B9's deterministic identity). Database constraints are the last line of defense against corruption, not a substitute for domain validation.

## Fail secure, minimum privilege, environment isolation, release signing

- **Fail secure**: an inability to verify access rights means deny/retry, never "allow so the app works."
- **Minimum privilege**: every module, backend function, database role, CI job, GitHub token, and API credential gets only what it needs.
- **Environment isolation**: dev/staging/production are separated; a dev client never accidentally operates with production administrative credentials; production data is never used as ordinary development fixtures.
- **Release signing**: production signing keys are protected separately; AI tools never have access to signing private keys; signing material never lives in the Git repository.

## Feature flags are not authorization

A hidden UI element does not protect a backend operation. Every privileged operation has server-side authorization regardless of what the client's feature flags show.
