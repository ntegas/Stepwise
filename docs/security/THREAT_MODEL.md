# THREAT_MODEL.md — Scaffold

Lightweight threat modeling is required before implementing each security-critical subsystem listed below (`SECURITY_ARCHITECTURE.md`), per rule: define assets, actors, trust boundaries, entry points, abuse cases, and mitigations, at minimum. **Not yet populated per-subsystem** — each subsystem's threat model is written when that subsystem is actually designed (Canonical Data Model / Offline & Sync / Google Play Release specs, per `PROJECT_STATE.md`), not invented ahead of it.

## Required threat models (one section each, to be filled in its own phase)

1. **Auth** — sign-up/sign-in, token issuance/refresh, session revocation, account recovery.
2. **Sync** — the outbox/conflict-resolution pipeline (`ARCHITECTURE.md` §9), cross-device data exposure.
3. **Supabase/RLS** — per-table policy correctness, service-role isolation.
4. **Billing** — entitlement verification, purchase-token spoofing, RTDN webhook trust.
5. **Widget external actions** — forged/stale/replayed intents, privacy exposure on the home screen (`ARCHITECTURE.md` §19.11, §19.16 in `SECURITY_ARCHITECTURE.md`).
6. **Notifications** — action spoofing, deep-link entity ownership.
7. **Import/Export** — malformed import files, export data leakage.
8. **Account deletion** — completeness of cascade delete/anonymization across Supabase and any analytics-linked identifiers.
9. **Backup/restore** — what Android Backup may copy, restore-path integrity.

## Format for each, once written

- **Assets**: what's worth protecting in this subsystem.
- **Actors**: legitimate users, the untrusted-client assumption (`SECURITY_ARCHITECTURE.md` — trust boundaries), any external services.
- **Trust boundaries**: where data crosses from a less-trusted to a more-trusted context.
- **Entry points**: every place external input reaches this subsystem.
- **Abuse cases**: concrete attack attempts (see `SECURITY_TEST_MATRIX.md` for the corresponding tests — this file names the case, that one names the test).
- **Mitigations**: what in `SECURITY_ARCHITECTURE.md` / `ARCHITECTURE.md` addresses each abuse case, and any gap found.
