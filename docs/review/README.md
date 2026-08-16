# kdiab — Technology & Domain Review

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
| Tech debt | [tech-debt.md](./tech-debt.md) | FIND-DEBT-001…008 | High-discipline codebase; residual debt, with resolved items correctly excluded |
| Modernization | [modernization.md](./modernization.md) | FIND-MOD-001…005 | Modern stack; the real question is nine-service shape + observability |

## At a glance

- **30 actionable findings**, **0 Critical** (no confirmed patient-harm-certain dose bug), **5 High**
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
review" intent. GitHub-issue materialization is **deferred** (ADR-RVW-005, `gh`-gated); the ready-to-open
queued issue set is recorded in [BACKLOG.md](./BACKLOG.md).
