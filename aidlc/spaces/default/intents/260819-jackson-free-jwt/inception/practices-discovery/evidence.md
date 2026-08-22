# Practices Discovery — Evidence & Freshness Trail (#1606)

## Approach: Reuse, Not Re-scan

Practices were fully discovered and **affirmed 2026-08-16** during the `tech-domain-review` intent and
persisted to `aidlc/spaces/default/memory/team.md` (5 sections) and `project.md` (Mandated/Forbidden +
learnings). This run is a **re-affirmation**, not a fresh discovery. The four-agent parallel evidence
scan (Step 2) was **skipped** because:

- The codebase has not changed in any way that affects the five practice areas since the affirmation
  (the only intervening backend commits — #1605 logback, #1607 Swagger — are dependency/logging
  changes that do not touch branching, testing, deployment, or code-style conventions).
- The codekb (`../../../codekb/kdiab-bkp/`) — `code-structure.md`, `technology-stack.md`,
  `dependencies.md`, `code-quality-assessment.md`, `architecture.md`, `business-overview.md` — was
  itself verified current for the relevant subsystem in reverse-engineering (2026-08-19).

## What Each Perspective Would Confirm (from existing evidence)

| Perspective | Source (already captured) | Finding (unchanged) |
|---|---|---|
| pipeline-deploy (branching/CI) | git history, `dependencies.md`, `technology-stack.md` | Trunk-based, merge-commit, deploy-on-merge, 18 CI workflows, SHA-pinned actions |
| quality (testing) | `code-quality-assessment.md` | Three-tier tests, Kover 80% floor enforced in CI, test-after |
| developer (code patterns) | `code-structure.md`, `architecture.md` | Hexagonal layers, idiomatic Kotlin, shared lib for cross-cutting concerns |
| devsecops (security/supply-chain) | `dependencies.md`, `code-quality-assessment.md` | Detekt, Trivy/CodeQL/SonarCloud, Dependabot, SBOM, force-pin for CVE remediation |

## Freshness Verdict

**Current.** No re-scan required; no new practice surfaced. `team.md` + `project.md` remain
authoritative and were **not modified** by this run (no promote — nothing new to affirm; re-appending
already-affirmed rules would duplicate them). See `discovered-rules.md` § "New Rules This Run: None".
