# THREAT_MODEL.md

Lightweight threat modeling is required before implementing each security-critical subsystem listed below (`SECURITY_ARCHITECTURE.md`), per rule: define assets, actors, trust boundaries, entry points, abuse cases, and mitigations, at minimum. Each subsystem's threat model is written when that subsystem is actually designed, not invented ahead of it. **Auth** and **Supabase/RLS** were written at DEV-007 (`ROADMAP.md`), since that task designs both together — Supabase's RLS boundary only means anything in terms of who `auth.uid()` resolves to, so the two can't be threat-modeled independently of each other. **Sync** is written at DEV-008, the task that actually designs the outbox/conflict-resolution pipeline. The rest remain scaffolded below until their own phase.

## Auth

- **Assets**: the authenticated session itself (access + refresh tokens — possession of a valid access token *is* possession of that user's entire account, because every other boundary in this app, RLS included, derives identity from it); account credentials (password, Google identity link); the account's existence/email (recon value — confirms a person uses Stepwise).
- **Actors**: the legitimate account owner; Supabase Auth (the trusted token issuer — `SECURITY_ARCHITECTURE.md`'s one authority for "who you are"); the Android client (untrusted environment per `SECURITY_ARCHITECTURE.md` — trust boundaries: a user can root/modify/intercept their own device, which this threat model treats as "attacker has full control of their own client," not a privileged position); an attacker who has obtained another user's device, a backup, or a leaked token; a phishing/social-engineering attacker targeting credentials directly, outside the app.
- **Trust boundaries**: (1) client ⇄ Supabase Auth, at sign-up/sign-in/token-refresh — the point an unauthenticated request becomes an authenticated identity; (2) the stored-token boundary on-device — once issued, a refresh token is only as safe as its local storage; (3) client ⇄ RLS-protected Postgres — every subsequent query carries the JWT forward; this is `auth.uid()`'s only input, so Auth's own correctness is a precondition for every RLS guarantee in the **Supabase/RLS** section below, not a separate concern from it.
- **Entry points**: the sign-up form (email/password), the sign-in form, the Google Sign-In / Credential Manager flow, the token-refresh call made silently on app start, any password-recovery flow, and the locally-stored refresh token itself (an entry point for an attacker with device/backup access, not for the network attacker).
- **Abuse cases and mitigations**:
  - *Credential stuffing / brute-force sign-in* → Supabase Auth's own rate limiting (`SECURITY_ARCHITECTURE.md` — rate limiting and abuse control); the client never implements its own competing throttle that could be bypassed by calling the API directly.
  - *Stolen/leaked refresh token (device compromise, backup extraction)* → token stored via Android Keystore-backed encrypted storage (`androidx.security.crypto`), never plaintext `SharedPreferences`, never logged (`SECURITY_ARCHITECTURE.md` — logging security); excluded from Android Backup unless proven safe (`SECURITY_ARCHITECTURE.md` — backup security — default is exclude); logout invalidates the session server-side, not just locally, so a captured-then-logged-out token stops working.
  - *A modified/rooted client forges its own `user_id` or claims elevated entitlement* → irrelevant to Auth itself but the reason Auth and RLS are one threat model: identity for every downstream check comes from the JWT Auth issued, never a client-supplied field (`SECURITY_ARCHITECTURE.md` — trust boundaries, mass assignment protection).
  - *Replay of a captured access token after logout/expiry* → short-lived access tokens + server-side session revocation on logout (`ARCHITECTURE.md` §11); a revoked/expired token fails Auth's own verification before it ever reaches RLS.
  - *Account enumeration via differing sign-up/sign-in/recovery error messages* → deferred to implementation-time verification against Supabase Auth's current default error responses (not a design gap this document should guess at); tracked as a VERIFICATION DEBT item, not silently assumed safe.
  - *Offline session reuse after revocation* — a device that was signed out remotely (lost/stolen-device flow) keeps using its cached JWT while offline → accepted residual risk, not a gap: `ARCHITECTURE.md` §11 explicitly requires offline sessions to keep working from a cached JWT, so revocation only takes effect the next time that device reaches the network and Auth rejects the stale token. No local-only enforcement can close this without breaking the offline-first requirement itself.
  - *A rooted/root-able device (Magisk-equivalent) runs code as the Stepwise app's own UID* → accepted residual risk, added explicitly during DEV-007 review (adversarial reviewer) rather than left implicit: Android Keystore's access control is UID-based, not possession-based, so code executing as the app's UID can call the same decrypt path `EncryptedPrefsSessionManager` itself uses and read the refresh token in plaintext — hardware-backed (StrongBox/TEE) key storage doesn't change this, since only the *decrypt operation* needs to run as the right UID, which root grants. This is a platform-wide limitation of `EncryptedSharedPreferences`/Keystore, not a defect in this implementation; impact is bounded by the same server-side-revocation mitigation above (a stolen device's token is still only good until the real owner logs out remotely or it expires).
  - *A captured, valid, unexpired Google ID token is replayed to mint a new Stepwise session* → `SupabaseAuthClient.signInWithGoogleIdToken` is a thin pass-through with no client-side trust decision (signature/`aud`/`iss`/`exp` validation genuinely happens server-side at Supabase Auth), but as implemented it sets no PKCE-style `nonce` binding the token to this specific sign-in attempt, so Supabase can only apply Google's standard token checks, not single-use/replay-binding to *this* flow. Obtaining a still-valid token requires Credential Manager/device-level compromise, not a bare network attacker — same order of risk as the rooted-device entry above. Tracked as VERIFICATION DEBT, not fixed silently: whether supabase-kt 3.9.0's `IDToken` builder DSL exposes a settable `nonce` at all could not be confirmed from this sandbox (no cached dependency jars, no network access to inspect the library); resolve by either wiring Credential Manager's `GetGoogleIdOption.setNonce` through end-to-end once verified available, or recording this explicitly as accepted residual risk the way the other two entries here are.

## Supabase/RLS

- **Assets**: every row of user data across all tables in `DATA_MODEL.md` (Vision/LifeArea/Goal/.../Session/SessionMetricValue/...) — the whole point of RLS is that none of it is readable or writable by anyone but its owner; the service-role credential, which bypasses RLS entirely and must never reach a client.
- **Actors**: the authenticated owner (should read/write only their own rows); another authenticated Stepwise user, User B, attempting to reach User A's rows (the primary adversary this section defends against — single-tenant-per-account, §69, means there is no legitimate cross-user access at all, unlike a multi-tenant app with sharing); an unauthenticated caller hitting the Postgrest API directly; Edge Functions, which hold the service role and are the only context trusted to bypass RLS.
- **Trust boundaries**: the RLS policy itself is the boundary — not the client, not the repository layer, not the UI (`SECURITY_ARCHITECTURE.md` — RLS deny-by-default: "never relying on UI or repository-layer filtering as the actual boundary"). A second boundary sits between ordinary authenticated requests (anon/authenticated Postgrest role, RLS-bound) and the service role (RLS-exempt, Edge-Functions-only).
- **Entry points**: every table's SELECT/INSERT/UPDATE/DELETE via Postgrest, called either through the Supabase client SDK or (by a sufficiently motivated attacker) directly against the REST API with a captured JWT; nested-ownership tables reached through a parent (e.g. `milestones`/`goal_activity_links` via their owning `goal_id`, `session_metric_values` via its owning `session_id`, `recurrence_occurrences` via its owning `recurrence_rule_id`) are a second entry point per table, not covered by a naive `user_id = auth.uid()` check if the table itself carries no `user_id` column. A polymorphic reference column (`goal_activity_links.source_id`, `sessions.source_id`, `schedules.owner_id` — each spanning Task/Activity/Habit, so none can carry a single FK) is a *third* entry point per table: RLS alone only checks the row's own declared owner, not that the thing it polymorphically points at belongs to that same owner (closed at DEV-007 with an explicit ownership-consistency trigger per reference — see `supabase/migrations/0002_canonical_schema.sql`'s own header for the confirmed exploit this closes).
- **Abuse cases** (see `SECURITY_TEST_MATRIX.md` rows 1–3/5/8 for the corresponding executable tests):
  - *User A reads/updates/deletes User B's row directly by ID* → denied by a `USING`/`WITH CHECK` policy comparing `auth.uid()` to the row's own `user_id` (or, for a table with no direct `user_id`, to its parent's `user_id` via a subquery — `milestones`, `goal_activity_links`, `session_metric_values`, `recurrence_occurrences`) on every table, every operation, no exceptions.
  - *User A links A's own row to something B actually owns, via a polymorphic reference RLS can't see* → confirmed exploitable at DEV-007 (adversarial reviewer): `goal_activity_links.source_id`/`sessions.source_id`/`schedules.owner_id` are each a bare `uuid` spanning 3 possible owner tables, so no single FK — and RLS checks on these tables never followed that reference to confirm its *actual* owner matched. Closed with an explicit `BEFORE INSERT OR UPDATE` trigger per affected table (`check_goal_activity_link_ownership`/`check_session_source_ownership`/`check_schedule_owner_ownership`, `0002_canonical_schema.sql`) that resolves the reference's real owner and rejects the write outright on mismatch — defense in depth alongside RLS, not a replacement for it.
  - *A client-supplied `user_id` on INSERT names someone else* → `WITH CHECK (auth.uid() = user_id)` rejects the write outright rather than silently reassigning it — the row is simply never created, not created-with-a-corrected-owner (correcting it would still mean trusting client input for something security-relevant).
  - *RLS enabled on a table but no policy defined* → Postgres's own default (RLS enabled + zero policies = deny-all) already fails closed; still verified per-table rather than assumed, since a reversed or missing `WITH CHECK` on an otherwise-present policy is a realistic authoring mistake that "RLS is enabled" alone wouldn't catch.
  - *A tombstoned (soft-deleted) row is written to again, or a stale offline device resurrects it* → the row's ownership policy still applies to a tombstoned row exactly as to a live one (a delete is `deleted_at` being set, not a different row with different rules, `ADR-006`) — RLS governs *who* can touch the row, the sync/merge logic in `ARCHITECTURE.md` §9 governs *whether a given write should win*; this section's job is only to confirm RLS doesn't accidentally open a gap for cross-user rows once a `deleted_at` is set.
  - *Service-role key reaches a client build, log, or repository* → covered structurally, not just by policy: the key is never referenced from any Android/Kotlin source, `BuildConfig` field, or committed file (`SECURITY_ARCHITECTURE.md` — service role protection; `AI_CODE_SECURITY.md` — AI context secret protection); it exists only in Edge Function runtime configuration.
  - *`Metric` (the one table with no `user_id`, per `DATA_MODEL.md`) is written to by an ordinary user* → it is a shared global reference catalog by design; RLS policy is read-only for the `authenticated` role and write-only via migration/service role, not "every user's own copy" — stated explicitly here so it isn't mistaken for a missed owner-column case during review.

## Sync

Written at DEV-008 (`ROADMAP.md`), closing the HIGH dependency DEV-007's adversarial reviewer
flagged against this section before it existed (see the superseded note previously here, now
resolved below). Covers the outbox/push pipeline added this task
(`:core:sync`/`:data/sync`/`0003_sync_engine.sql`); pull/multi-device merge does not exist yet
(see the scope note under Abuse cases) and will need this section revisited when it's added.

- **Assets**: the same per-row data the **Supabase/RLS** section protects, now in flight between
  devices — an in-transit outbox entry, a `conflict_copies` record (itself a second, append-only
  copy of a progress-affecting row's contested payload); each row's `version`/`updated_at`
  columns, whose integrity every compare-and-swap conflict decision in `ARCHITECTURE.md`
  §9.1/ADR-006 depends on.
- **Actors**: the legitimate owner syncing from one or more of their own devices (the realistic
  multi-device case ADR-006 designs for — not another user, since RLS already excludes User B
  from these rows entirely, same actor model as **Supabase/RLS**); a stale/offline device of the
  owner's own, replaying a push whose earlier success acknowledgement never arrived; a
  modified/rooted client (`SECURITY_ARCHITECTURE.md` — untrusted-client assumption) crafting a
  Postgrest call directly rather than through `:core:sync`'s own logic.
- **Trust boundaries**: the same client ⇄ Postgrest boundary as **Supabase/RLS** — sync adds no
  new network boundary, only a new write *pattern* (version-checked upsert vs. compare-and-swap)
  across that existing one. A second, purely-local boundary sits inside the outbox itself: between
  "a write queued but not yet confirmed by the server" and "a write this device knows landed at a
  specific server version" — `PushOutcome.Success.confirmedVersion` is what moves a row from the
  first state to the second, and nothing downstream (a second local edit, a later CAS) can be
  trusted until that round-trip actually happens.
- **Entry points**: every `upsert`/`compareAndSwapUpdate` call `PostgrestSyncClient` makes
  (same Postgrest REST surface as **Supabase/RLS**, just two specific write shapes instead of the
  general case); the `conflict_copies` INSERT path specifically, since it is this task's one new
  table; the free-form `tableName: String` carried on every `OutboxEntry` from
  `OutboxEntryEntity` through to the transport call.
- **Abuse cases**:
  - *A stale device's retry blindly resurrects an already-tombstoned row, or overwrites a newer
    edit from another device, via the no-conflict-detection `upsert` path* → **confirmed
    exploitable, demonstrated live against Postgres during this task's own review (adversarial
    reviewer) — fixed, not merely documented**: an earlier draft hardcoded `expectedVersion = null`
    at the one production call site, so every write for an entity took the blind-upsert path
    forever, defeating the entire compare-and-swap design. Fixed by having
    `PushOutcome.Success` carry the server-confirmed `confirmedVersion`, and having
    `SessionRepositoryImpl` look up each row's existing local version before enqueueing, so only a
    genuinely-never-seen-before row gets `expectedVersion = null`. See `SyncEngine.kt`'s own KDoc
    and the `SyncEngineTest` scenario named for this exact exploit.
  - *A forged `version`/`updated_at` in the write body beats the compare-and-swap `version` filter*
    → `enforce_sync_metadata()` (`0003_sync_engine.sql`) overwrites both server-side on every
    INSERT/UPDATE regardless of what the client sent, so a client can neither claim a version
    higher than the truth nor set its own `updated_at`; proven against real Postgres in
    `sync_engine_adversarial_test.sql` (forged-value-discarded assertions on both a metadata-tier
    and a progress-affecting table).
  - *A `conflict_copies` row's embedded payload JSON claims a different `user_id` than the row's
    own `user_id` column* → RLS only checks the column, never the JSON blob's nested content, so a
    modified client/direct Postgrest call could otherwise insert a self-owned row whose embedded
    payload names a different user entirely. **Confirmed, live-reproduced (adversarial reviewer,
    MEDIUM) — fixed structurally**: `ownerUserId` is threaded as its own trusted field end-to-end
    (`OutboxEntry` → `ConflictCopyRecord` → the row actually written), never parsed back out of
    the payload, closing the mismatch possibility at the design level rather than needing a new
    enforcement trigger to catch it after the fact.
  - *A plain `INSERT` retry of an already-landed write errors on a duplicate key, or a replayed
    push is otherwise not idempotent* → `upsert` (not `insert`) is used for every write that has no
    confirmed prior version, specifically so repeating an already-successful push is a no-op, not
    an error (`SyncEngine.kt`'s own KDoc); proven in `SyncEngineTest`.
  - *A future repository enqueues an arbitrary/wrong table name, reaching Postgrest with no
    compiler-enforced set of valid tables* → `SyncEngine.push` now rejects any `tableName` outside
    a fixed allowlist mirroring `0003_sync_engine.sql`'s own trigger-application list before
    calling the transport at all (added this task, architecture reviewer, LOW/IMPROVEMENT — not an
    exploit demonstrated against today's two wired-up tables, a closed door before a second one is
    wired in).
  - *`conflict_copies` is read, modified, or deleted by anyone other than its own owner, including
    the owner trying to alter their own conflict history* → cross-user SELECT denied by RLS;
    **no UPDATE/DELETE policy exists at all**, so even the owner cannot change or remove a written
    conflict copy (Postgres's RLS default-deny with no matching policy, deliberate — an immutable
    historical record, same reasoning `0003_sync_engine.sql`'s own header gives); proven in
    `sync_engine_adversarial_test.sql`.
  - *Known gap, disclosed rather than silently narrowed — no pull/multi-device-merge path exists
    yet* → this task implements push only (`:core:sync`'s `push`/the outbox/`OutboxDrainer`); there
    is no inbox/pull mechanism, so "two devices converge on the same state" and "a deleted row
    doesn't come back via a stale device's own local cache" are correct only for the push-side
    half of the story tested here (one device's write reaching the server correctly), not yet
    verified end-to-end through an actual pull on a second device. Tracked explicitly in
    `/PROJECT_STATE.md`'s DEV-008 findings, not assumed solved by this section.
- **Mitigations**: see each abuse case above; the general pattern is the same as
  **Supabase/RLS** — the server (Postgres trigger, RLS, CHECK constraint) is the actual boundary
  for every property these abuse cases describe, never client-side logic alone, with `:core:sync`'s
  own correctness (the version round-trip, the allowlist) as defense in depth on top of that
  server-side enforcement rather than a replacement for it.

## Remaining threat models (one section each, to be filled in its own phase)

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
