# CD Configuration — Review Deliverable Delivery

> Stage 4.1 (Deployment Pipeline), enterprise scope, Operation phase. Lead: aidlc-pipeline-deploy-agent.
> This is a **recommendations-only / assessment intent**: the "deployable artifact" is the committed
> `docs/review/*.md` set (10 documents, 30 actionable findings) plus its GitHub-issue projection. There
> is no application binary, container, or cloud environment to deploy — the kdiab platform's own CD
> (`docker-publish.yml`, semantic-release, the nine backend pipelines) is mature and **out of scope**,
> and the project rules forbid AWS/Bedrock, so there is no CloudWatch Evidently / AppConfig to configure.
> Classic CD concepts are therefore mapped to their honest GitHub-native analogues below.

## Upstream inputs

| Consumed artifact | Exists? | Used for |
|---|---|---|
| `construction/ci-pipeline/ci-config.md` | yes | The CI shape this CD pipeline promotes *from* — `review-verify.yml` + `verify.py`, path-filtered to `docs/review/**`. |
| `construction/ci-pipeline/quality-gates.md` | yes | The 10 blocking gates (G1–G10) that define "green"; this CD design turns that green into a **required** promotion gate. |
| `construction/{unit}/infrastructure-design/deployment-architecture.md` | **no — stage skipped** | N/A. `infrastructure-design` (3.4) was skipped for this assessment intent (no infra to design). No deployment architecture exists to consume. |
| `construction/{unit}/infrastructure-design/cicd-pipeline.md` | **no — stage skipped** | N/A. Same skip. The CD pipeline is defined here directly from the CI config + the deliverable, not from an infrastructure-design hand-off. |

## What "deployment" means here — two delivery tracks

```
Track A — the deliverable                 Track B — the findings
+---------------------------+             +----------------------------------+
| docs/review/*.md authored |             | 30 findings in BACKLOG.md        |
|         |                 |             |         |                        |
|         v                 |             |         v                        |
| PR -> review-verify.yml   |  (Q2=A)     | Epic materialized on GitHub      |  (Q1=D)
|   REQUIRED check green     |------+      |   (tracking anchor, this run)    |
|         |                 |      |      |         |                        |
|         v                 |      |      |         v                        |
| merge to main = PUBLISHED |      |      | sub-issue opened on-demand as    |
+---------------------------+      |      |   each finding is pulled          | (Q3=A)
                                   |      |         |                        |
                                   |      |         v                        |
                                   |      | feature branch -> CI -> merge    |
                                   |      |   (kdiab platform's existing CD) |
                                   +----->|   = recommendation "in prod"      |
                                          +----------------------------------+
```
<!-- Text fallback: Track A promotes the docs deliverable itself — authored markdown goes through a PR
that must pass the required review-verify.yml check, then merges to main (published). Track B promotes
individual findings — the epic is materialized now as a tracking anchor, sub-issues are opened on demand
as findings are pulled, and each pulled finding follows the kdiab platform's existing feature-branch ->
CI -> merge pipeline to reach "production" (implemented). -->

## Promotion tiers (environment analogue)

There are no dev/staging/prod servers. The promotion tiers are **states of the deliverable**:

| Tier | Classic analogue | Concrete state | Promotion gate into this tier |
|---|---|---|---|
| **Working** | dev | Uncommitted / feature-branch edits to `docs/review/**` | Local `python3 docs/review/verify.py` exit 0 |
| **Staged** | staging | Open PR touching `docs/review/**` | `review-verify.yml` green (10/10 checks) |
| **Published** | production | Merged on `main` | PR approved + **required** status check green (Q2=A) |
| **Tracked** | released / GA | Finding is a live GitHub issue under the epic | Maintainer pulls the finding (Q3 phased-but-pull) |
| **Implemented** | in-service | Finding's fix merged to `main` via the platform pipeline | The platform's own CD gate (Kover ≥80%, Detekt, CodeQL, Trivy, SonarCloud all green) |

## Promotion gate — make the deliverable check load-bearing (Q2 = A)

`review-verify.yml` currently *runs* on `docs/review/**` PRs but is **not a required status check**, so a
red run does not block merge — an advisory gate, which "is not a gate" (pipeline-deploy key principle 4).
The CD design **recommends** promoting it to load-bearing:

- **One-time setup (deployment prerequisite):** add the job `Verify review deliverable integrity` to the
  required status checks for the branch-protection rule covering `main`, scoped to changes under
  `docs/review/**`. (Repo Settings → Branches → branch protection rule → Require status checks to pass.)
- **Effect:** a PR that drops a finding, breaks a link, or fails any of the 10 gates in `quality-gates.md`
  cannot merge. The gate becomes the enforced boundary between **Staged** and **Published**.
- **Least privilege preserved:** unchanged from `ci-config.md` — `permissions: contents: read`,
  `timeout-minutes: 5`, path-filtered so it never runs on backend/UI PRs.
- **Not auto-applied:** branch-protection edits are a repo-admin action; this design documents the exact
  setting but does not change repo settings itself (recommendations-only posture).

## Issue-materialization config — epic-only trigger (Q1 = D)

Per ADR-RVW-005 the GitHub-issue projection was deferred (`gh`-gated, OQ-1). The maintainer chose to
**materialize the epic only** now as a tracking anchor, with sub-issues created on demand:

| Object | When created | How |
|---|---|---|
| **Epic** (`review`, `epic` labels) | At Deployment Execution (4.3), **behind an explicit confirmation gate** | `gh issue create` with a body carrying the full 30-row backlog as text (so nothing is lost before sub-issues exist) + links to the five theme docs |
| **Sub-issues** (~29) | On demand, as each finding is pulled (Q3) | `gh issue create` + native `addSubIssue` GraphQL to link under the epic, per `.claude/rules/github-issue-management.md` |

- **Repo conventions honoured:** native `addSubIssue`, **no assignee at creation** (assign on start of
  work), reuse-first labels (`area:*`, `severity:*`, `quick-win`), dedup rules from unit U10 (AR-001
  cross-referenced not re-filed; closed #1082 gets no issue; v3 HISTORY debt gets a new issue, not a
  reattach to the closed #894–898).
- **Deferred boundary intact:** creating the epic is a *partial* un-park. The bulk of the queued set
  stays deferred; the actual `gh` mutation happens only at 4.3 with a human confirmation (outward-facing,
  hard-to-reverse), never silently in this design stage.
- **CODEOWNERS:** not adopted (Q2 = A, not C) — the solo maintainer is the only reviewer, so a CODEOWNERS
  file would add ceremony without adding a second approver.

## Interaction with existing CI/CD

- **Path isolation (unchanged):** the `docs/review/**` filter means this delivery pipeline never runs on
  backend or UI PRs, and the nine backend + kdiab-ui pipelines never run on docs-only PRs.
- **Two independent release cadences:** the deliverable (Track A) is released by a docs PR; each
  recommendation (Track B) is released by the platform's own semantic-release-driven pipeline once its
  fix merges. They never block each other.
- **No secrets, no registry, no deploy target:** the pipeline publishes markdown into git and issues into
  the tracker; there is no artifact registry, container push, or environment credential to manage.
