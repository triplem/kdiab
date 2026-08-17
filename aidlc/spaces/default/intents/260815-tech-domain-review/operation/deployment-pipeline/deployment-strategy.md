# Deployment Strategy — Review Deliverable & Recommendations

> Stage 4.1 (Deployment Pipeline), enterprise scope. Companion to `cd-config.md`. Defines *how* the
> deliverable and each recommendation move to their next tier, the sequencing, and the promotion gates.
> Recommendations-only intent: infra-heavy strategies (blue/green, canary-by-metric, feature flags via
> AppConfig) are **N/A** and are replaced by their GitHub-native analogues.

## Strategy selection

| Deployment object | Chosen strategy | Why not blue/green or canary |
|---|---|---|
| The `docs/review/**` deliverable | **Recreate (single-shot PR merge)** | Markdown is idempotent and instantly reversible by `git revert`; there is no running service to keep warm, so a second colour buys nothing. |
| Each recommendation (finding → fix) | **Phased-but-pull** (Q3 = A), executed one-issue-at-a-time on the platform's existing pipeline | The classic canary "shift 10% of traffic" has no analogue for a code change that either ships or doesn't; risk is managed by *ordering* (safety-first) and per-issue CI gates, not by traffic splitting. |

## Track A — deploying the deliverable

1. **Working → Staged:** author/edit under `docs/review/**` on a feature branch; run `verify.py` locally.
2. **Staged → Published:** open a PR; `review-verify.yml` runs the 10 gates from `quality-gates.md`. With
   Q2 = A the check is **required**, so merge is blocked until green. On merge the deliverable is
   Published on `main`.
3. **Cadence:** the deliverable is released **once** as a whole, then amended incrementally (a corrected
   finding, a new phase note) via the same PR path. No coordinated multi-file release is ever needed
   (NFR-2 / NFR-5).

## Track B — deploying recommendations (phased-but-pull)

The ROADMAP bands are the **default value ordering**, not hard release gates. The maintainer pulls the
next item by value/convenience; the pipeline enforces only real dependencies.

### Phase bands (from `ROADMAP.md` — the single phase authority, ADR-RVW-006)

| Band | Analogue | Contents | Target |
|---|---|---|---|
| **Near** | first canary burst | 5 quick-wins + Must clinical-safety (SEC-001, CLIN-002/013/010/001, DEBT-005) | ~1–2 weeks of bursts (~8–10 maintainer-days) |
| **Mid** | progressive rollout | Should security + tech-debt + domain data-model (19 items) | ~1–2 months of bursts |
| **Long** | full rollout, value-gated | Could modernization + structural data-model (5 items) | quarter-scale, only if value still holds |

### Sequencing rules

- **Default order:** Near → Mid → Long by value density. The maintainer MAY pull any item early.
- **Hard dependency (enforced):** `FIND-CLIN-014` (server-side IOB + stacking warning) must not ship
  before `FIND-CLIN-001` (make `activeIob` required) — the only cross-item gate in the whole backlog.
- **Safety floor:** clinical-safety findings keep ordering priority within a band (the review's severity
  discipline, ADR-RVW-004: Critical is reserved for patient-safety).
- **No batch release:** every finding is independently shippable — one feature branch per issue,
  merge-commit (not squash), ≥80% coverage on new/changed code, green CI before merge (team practices).

### Feature-flag / dark-launch analogue

There is no runtime flag service. **The deferred queued-issue set IS the dark-launch mechanism:** a
finding is "dark" (documented, not acted on) until its sub-issue is opened (Q1 = D → sub-issues created
on-demand). Opening the issue is the "flag flip" that moves a finding from **Published** (in the docs) to
**Tracked** (live work). This gives progressive delivery without any flag infrastructure.

## Approval workflow into "production" (a fix merging to main)

Each recommendation's fix rides the **kdiab platform's existing gate**, which this strategy does not
re-invent — it defers to it:

| Finding class | Extra approval before merge |
|---|---|
| Clinical-safety (CLIN-*) | Domain sanity check via `/doctor-t1d-review` (and `/patient-t1d-review` for UX-facing changes) in addition to the standard CI gate |
| Security / regulatory (SEC-*) | Security review; the two regulatory flags (SEC-004 MDR/SaMD, SEC-005 GDPR-vs-MDR) are **decisions**, not code — they gate on a documented determination, not a PR |
| Data-model / tech-debt / modernization | Standard team gate (tests + Kover ≥80% + Detekt + CodeQL + Trivy + SonarCloud all green) |

## Promotion metrics & thresholds

| Transition | Metric | Pass threshold |
|---|---|---|
| Staged → Published (deliverable) | `verify.py` result | exit 0 (10/10 gates) |
| Published → Tracked (finding) | maintainer pull decision | n/a (human) |
| Tracked → Implemented (fix) | platform CI | all required checks green; ≥80% coverage on new/changed code |

## Prerequisites checklist (before first use)

- [ ] Branch protection: add `Verify review deliverable integrity` as a required check on `main` for
      `docs/review/**` (Q2 = A).
- [ ] Epic materialized as tracking anchor at Deployment Execution (4.3), behind confirmation (Q1 = D).
- [ ] `gh` CLI authenticated for the maintainer (OQ-1 / A-2 gate) before any issue creation.
