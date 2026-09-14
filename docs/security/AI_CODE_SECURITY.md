# AI_CODE_SECURITY.md

Extends `/ANTI_ERROR_STANDARD.md` with rules specific to AI-generated code and AI access to this repository. Binding on any AI (this session included) working on Stepwise.

## AI context secret protection

`.gitignore` is not sufficient — an AI coding agent has access to a large amount of repo/context beyond what's tracked in git. Secret-containing files are excluded from AI context as well as from version control. At minimum: `.env`, `.env.*`, `*.pem`, `*.key`, keystore files, service-account files, production credentials, signing secrets. An AI agent never requests or reads a production secret without a proven, stated necessity — "it might be useful" is not sufficient justification.

## AI-generated code is untrusted until verified

An AI's own claim that its implementation is "secure," "correct," or "tested" is not evidence. An AI does not have the authority to certify the security of its own code. Security-critical areas (auth, authorization, crypto, RLS, billing, input validation, security boundaries — see `SECURITY_TEST_MATRIX.md`) require an independent verification strategy: the same AI must not be the sole author of both the implementation and the tests offered as proof it's safe.

## Hallucinated dependencies

Any dependency an AI proposes is checked before use, per `DEPENDENCY_POLICY.md`: does the package actually exist, is it from an official source, does it have a real maintainer/repository/release history, what's its vulnerability history, is it actually necessary, what's its license and current version. Never auto-install a package solely because an AI suggested it.

## Test tampering is forbidden

An AI must never delete a failing test, weaken an assertion, or change expected security behavior solely to get a green build. If an existing test genuinely needs to change, the AI states the reason separately, as its own explicit point — never folds it silently into an unrelated change.

## No security bypass "fixes"

None of the following are acceptable as a "temporary fix" for something that isn't working, ever: disabling TLS validation, disabling RLS, using the service role from a client, making a database table public, exporting an Android component unnecessarily, disabling auth, a wildcard CORS policy adopted without analysis, granting broad permissions, or bypassing certificate checks. Any of these is treated as a security regression, full stop — not a pragmatic shortcut, regardless of how it's framed in a commit message or PR description.

## Debug backdoor prevention

No production-accessible debug login, master password, universal admin account, hidden auth bypass, test endpoint, unrestricted debug menu, hardcoded premium unlock, or developer override. Debug-only functionality is compiled/packaged/configured separately from production builds, not gated by a runtime flag alone.

## Relationship to `/ANTI_ERROR_STANDARD.md`

This document is the security-specific instance of that standard's general rules: §1 (anti-duplication) applies to "don't reinvent crypto/auth"; §4 (never change approved architecture silently) applies with extra force to security boundaries — a security-relevant architecture change always goes through the full show-the-conflict-first process, no exceptions for "it was just a quick fix."
