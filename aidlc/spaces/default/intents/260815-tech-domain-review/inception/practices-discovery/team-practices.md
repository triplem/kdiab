# Team Practices — kdiab

Descriptive, team-voice synthesis of how this team works, discovered from codebase
evidence (RE codekb + committed `.claude/rules/*` + org/team/project memory + git
history) and confirmed at the practices-discovery interview. Five sections matching
the `aidlc-team.md` headings; promoted to the team rule layer on affirmation.

## Way of Working

Trunk-based development on `main` with short-lived feature branches named
`<type>/<issue>-<description>` (type ∈ feature | fix | bug | chore | docs | refactor),
one branch per issue/story, PR-per-change. Merges to `main` use **merge-commits, not
squash** — this overrides the org squash default so that per-commit `Closes #N` issue
linkage is preserved (evidenced: every recent merge is a `Merge pull request #NNNN`).
Direct commits to `main` are git-hook-blocked; every change traces to a GitHub issue
(`Closes #N` / `Refs #N`) and follows Conventional Commits (Angular preset) driving
semantic-release. Parallel agents collaborate via git worktrees on the same branch.

## Walking Skeleton

Skip the walking-skeleton ceremony for this work. The platform is an established,
running nine-service system, so improvement/review Bolts are incremental work on an
existing codebase with no thin end-to-end slice left to bootstrap. (Genuinely
greenfield features added later would still run the skeleton per the org default.)

## Testing Posture

Tests are a first-class deliverable, written alongside the code (classic test-after,
not strict TDD). Each backend service ships a three-tier suite — unit (JUnit 5 +
MockK + H2), integration (JUnit 5, runs after unit), and e2e (Kotest, runs after
integration); the frontend uses Vitest + Playwright. Minimum **80% line coverage on
all code across every scope**, enforced in CI before merge via Kover `koverVerify`
(Kotlin services) and Vitest thresholds (frontend); a coverage miss blocks the PR.
This strengthens the org default (which floors 80% only for enterprise/feature) so the
floor also holds for poc/refactor/workshop work.

## Deployment

Deploy-on-merge to `main`: `docker-publish.yml` builds and publishes all images on
every push to `main`, and semantic-release computes the version bump from Conventional
Commits. All nine backends plus kdiab-ui must be green across the full gate — tests,
Kover coverage, Detekt (SARIF), SonarCloud, CodeQL, Trivy CRITICAL/HIGH, and SBOM —
before any release; no production deploy proceeds with a failing or pending check.

## Code Style

Kotlin is idiomatic (prefer `val` over `var`, `when` over if-else chains, the right
scope function) and linted by Detekt with per-module config + baseline (composite
`includeBuild` makes each module its own Gradle root; no ktlint is applied).
TypeScript is strict-mode with ESLint + typescript-eslint (strict) and named exports.
Apply **SOLID** (single responsibility, open/closed via extension, Liskov-safe
subtypes, segregated interfaces, dependency inversion — inject abstractions) and
**DRY** (extract duplicated logic into the shared library and Gradle convention
plugins rather than per-service copies). Use structured logging (kotlin-logging +
Logback JSON; Pino for TS) with `X-Correlation-ID` tracing; never log secrets or PII.
