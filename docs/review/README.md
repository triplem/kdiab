# kdiab — Technology & Domain Review

> **Version:** v2.0.0 — the deliverable is semantically versioned; see
> [CONVENTIONS.md](./CONVENTIONS.md) § Versioning for the bump rules and
> [Version history](#version-history) below.

A prioritized, evidence-linked "where do I invest next" review of the kdiab T1D platform, produced for
the solo maintainer. **Recommendations only** — every finding flags a concern with concrete code
evidence and pairs any rewrite with an incremental alternative; no code or tests were changed.

## Reading guide (start here)

| If you want… | Read |
|---|---|
| The rules every finding follows (schema, severity, evidence format) | [CONVENTIONS.md](./CONVENTIONS.md) |
| **The one ordered list to work from** | [BACKLOG.md](./BACKLOG.md) |
| What to do *this week* (small, high-value) | [QUICK-WINS.md](./QUICK-WINS.md) |
| The Near / Mid / Long sequence | [ROADMAP.md](./ROADMAP.md) |

## Theme documents (full evidence)

| Theme | Doc | Findings | Headline |
|---|---|---|---|
| Clinical safety | [clinical-safety.md](./clinical-safety.md) | FIND-CLIN-001…014 | Dose math is sound; IOB stacking is unguarded (the top item) |
| Data model | [data-model.md](./data-model.md) | FIND-DATA-001…005 | Schemas are flexible (JSONB) but carb-absorption / calibration gaps remain |
| Security & compliance | [security.md](./security.md) | FIND-SEC-001…007 | Clean ABAC; flag the test-JWT guard and the MDR/SaMD question |
| Tech debt | [tech-debt.md](./tech-debt.md) | FIND-DEBT-001…009 | High-discipline codebase; residual debt (incl. a missing performance-testing tier), with resolved items correctly excluded |
| Modernization | [modernization.md](./modernization.md) | FIND-MOD-001…005 | Modern stack; the real question is nine-service shape + observability |

## At a glance

- **31 actionable findings**, **0 Critical** (no confirmed patient-harm-certain dose bug), **5 High**
  (2 clinical-safety, 2 security, 1 tech-debt), the rest Medium/Low.
- **9 positive verdicts** — items investigated and found sound (recorded so trust is earned, not assumed).
- **100% evidence-linked** (NFR-1): every finding cites a `path/File.kt#symbol`, a config line, or a
  live-verified issue.
- **Currency guard applied** (US-5): codekb-tracked anchors were re-verified against live `main`; two
  stale claims (UI coverage #1082, the #894-#898↔HISTORY reference) were caught and corrected — see the
  live-verification box in [tech-debt.md](./tech-debt.md).

## How to act on a finding

Each backlog row maps to a single, independently shippable change under the team's practices (NFR-5):
one feature branch per issue, merge-commit (not squash), ≥80% coverage on new/changed code, green CI
before merge. No recommendation requires a coordinated multi-item release.

## Status

Produced during Construction on the enterprise-scope AI-DLC workflow for the "technology & domain
review" intent. GitHub-issue materialization is **complete** (2026-08-17): filed as **epic #1562** with
**31 sub-issues (#1563–#1593)**, labelled and linked via `addSubIssue`, no assignees. The as-filed
mapping is recorded in [BACKLOG.md](./BACKLOG.md) § GitHub issues.

## Version history

The deliverable follows [semver](https://semver.org/) (rules in [CONVENTIONS.md](./CONVENTIONS.md) § Versioning):
**MINOR** = a finding added, **MAJOR** = a finding superseded/removed or a severity/phase re-scope,
**PATCH** = wording/evidence/link fix with no finding-set change.

- **v2.0.0** (2026-08-17) — roadmap re-scope: **FIND-DEBT-008** (a `quick-win`-tagged item) moved from the **Mid** band to **Near** so the roadmap opens with the complete top-5 quick-wins set. Finding set unchanged (still 31 actionable); severity/effort/evidence unchanged; the already-filed GitHub issue keeps its `quick-win` label (phase is not projected onto issues). *(MAJOR — roadmap-phase authority change)*
- **v1.1.1** (2026-08-17) — materialized the backlog as GitHub epic #1562 (+ 31 sub-issues #1563–#1593); status/mapping update only, no finding-set change. *(PATCH)*
- **v1.1.0** (2026-08-17) — added FIND-DEBT-009 (no performance/load-testing tier); 30 → 31 actionable findings. *(MINOR)*
- **v1.0.0** (2026-08-17) — initial published deliverable; 30 actionable findings.
