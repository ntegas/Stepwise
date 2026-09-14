# DEPENDENCY_POLICY.md

## AI-proposed dependencies are never auto-installed

Any new dependency an AI proposes is independently verified before use: the package actually exists, comes from an official source, has a real maintainer/repository and release history, has no concerning vulnerability history, is actually necessary, and its license and current version are checked. A package is never installed just because an AI suggested it. See `AI_CODE_SECURITY.md` §"Hallucinated dependencies."

## Allowlist preference

Prefer AndroidX, official Google/Kotlin libraries, and established, actively-maintained libraries. A new third-party dependency needs a stated justification; nothing gets added for trivial functionality that a few lines of first-party code would cover. One dependency version catalog is used project-wide (`DEVELOPMENT_PROTOCOL.md` §37) — no module sets a library version independently.

## Software supply chain

Dependencies are pinned through reproducible version management; lock/verification mechanisms are used where supported. Dependency changes are reviewed in every PR. Known vulnerabilities are detected, not discovered by accident.

## Transitive dependency review

Security review covers not just a direct library but its transitive dependencies — a small new dependency can still pull in significant supply-chain surface area.

## Software Composition Analysis (SCA)

Third-party dependencies are checked regularly against known vulnerabilities (see `SECURITY_TEST_MATRIX.md` CI gates). A vulnerable dependency does not stay indefinitely just because the app still compiles.

## SBOM / dependency inventory

A machine-readable dependency inventory (SBOM) is maintained where practically justified, so a new CVE's relevance to Stepwise can be determined quickly.
