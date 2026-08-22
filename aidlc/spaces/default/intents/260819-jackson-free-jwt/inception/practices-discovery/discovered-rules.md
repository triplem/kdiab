# Discovered Rules — #1606 (Jackson-free JWT)

Agent-facing corrective rules. **Re-run status:** every rule below was **already affirmed 2026-08-16**
and is already present in `aidlc/spaces/default/memory/project.md`. This run promotes **NO new rules**
(re-appending would duplicate). Listed here only as the applicable rule set for #1606. Evidence:
`../../../codekb/kdiab-bkp/` (`code-quality-assessment.md`, `dependencies.md`, `technology-stack.md`,
`architecture.md`, `code-structure.md`, `business-overview.md`).

## Mandated

(Already affirmed — not re-promoted this run)

- ALWAYS create a feature branch before staging; never work on `main` directly.
- ALWAYS merge to `main` with a merge-commit (never squash) to preserve `Closes #N`.
- ALWAYS pass the full gate before opening/merging a PR: `./gradlew check` (tests + Detekt + Kover ≥80%) for Kotlin; `npm run build` + lint + test for kdiab-ui.
- ALWAYS keep line coverage ≥80% on new/modified code.
- ALWAYS wait for every GitHub Actions check to be green before merging.
- ALWAYS reference a GitHub issue and follow Conventional Commits.
- ALWAYS delete both remote and local feature branch immediately after merge.
- ALWAYS validate a dependency-shedding change against the full **runtimeClasspath** (`gradle dependencyInsight`) before merging, and never remove a version force-pin without checking conflict resolution won't downgrade to a CVE-vulnerable version. *(Directly governs #1606's force-pin removal.)*

## Forbidden

(Already affirmed — not re-promoted this run)

- NEVER commit directly to `main` (git-hook enforced).
- NEVER squash-merge — drops the per-commit `Closes #N` linkage.
- NEVER merge a PR with a failing or still-running CI check; never bypass with `--admin`.
- NEVER hardcode credentials/secrets/API keys; never log secrets or raw PII. *(Directly governs the JWT verifier + its logging.)*

## New Rules This Run

**None.** #1606 surfaced no new durable team/project practice — the governing rules
(dependencyInsight-before-shedding, security-review, merge-commit, coverage floor) already exist.
The one #1606-specific safety choice (risk-first characterization tests for a security-critical swap)
is an intent-local decision recorded in scope-definition, not a standing rule.
