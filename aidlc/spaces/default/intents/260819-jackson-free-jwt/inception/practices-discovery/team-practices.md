# Team Practices — Re-affirmed for #1606 (Jackson-free JWT)

Re-run pre-fill from the already-affirmed practices in `aidlc/spaces/default/memory/team.md` +
`project.md` (affirmed 2026-08-16). Evidence base: the codekb (`../../../codekb/kdiab-bkp/`) —
`code-structure.md`, `technology-stack.md`, `dependencies.md`, `code-quality-assessment.md`,
`architecture.md`, `business-overview.md`. No re-scan (backend auth change doesn't alter any
convention; see `evidence.md`). Each section mirrors the live `team.md` and adds a #1606 note.

## Way of Working

Trunk-based on `main` with short-lived `<type>/<issue>-<desc>` feature branches, one branch per
issue, PR-per-change. Merges use **merge-commits, not squash** (preserves per-commit `Closes #N`).
Direct commits to `main` are git-hook-blocked; every change traces to a GitHub issue and follows
Conventional Commits (Angular preset). **#1606 application:** one branch `feat/1606-jackson-free-jwt`,
one PR `Closes #1606`, merge-commit; parallel agents (if any) via worktrees on that branch.

## Walking Skeleton

Skipped — the platform is an established, running nine-service system; #1606 is incremental work on
existing code with no thin end-to-end slice to bootstrap. **#1606 application:** no skeleton Bolt;
Construction runs the change directly.

## Testing Posture

Three-tier per backend service (unit JUnit5+MockK+H2, integration, e2e Kotest), frontend Vitest+
Playwright. **80% line coverage floor on all new/modified code**, enforced by Kover `koverVerify` in
CI; a coverage miss blocks the PR. Classic test-after, not strict TDD. **#1606 application:** the
change adds **risk-first characterization/parity tests** (scope Q2) — a deliberate, safety-driven
lean toward test-first for this security-critical auth swap — plus full auth e2e as a merge gate.

## Deployment

Deploy-on-merge to `main`: `docker-publish.yml` builds/publishes all images on every push; semantic-
release computes the version bump from Conventional Commits. All nine backends + kdiab-ui must be
green across the full gate (tests, Kover, Detekt/SARIF, SonarCloud, CodeQL, Trivy CRITICAL/HIGH, SBOM)
before any release. **#1606 application:** all-CI-green is a hard merge gate; verify no force-pin
change silently downgrades jackson into a CVE (project rule).

## Code Style

Idiomatic Kotlin (`val` over `var`, `when`, right scope function), Detekt per-module (composite
`includeBuild`, each module its own Gradle root; no ktlint). TypeScript strict + ESLint. SOLID + DRY
(shared logic in the shared library / convention plugins). Structured logging (kotlin-logging +
Logback JSON) with `X-Correlation-ID`; never log secrets/PII. **#1606 application:** the new Nimbus
verifier lives once in `kdiab-common` (DRY); preserve the `security_event=TOKEN_REJECTED` structured
log; run `:kdiab-common:detektMain` (per-module).
