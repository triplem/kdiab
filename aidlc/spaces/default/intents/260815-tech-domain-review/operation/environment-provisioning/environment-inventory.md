# Environment Inventory — Review Deliverable Delivery Surface

> Stage 4.2 (Environment Provisioning), enterprise scope. Lead: aidlc-aws-platform-agent (adopted as
> generic **platform / environment-readiness** — this project forbids AWS/Bedrock, so there is **no
> cloud environment**: no VPC, no compute, no IAM, no managed database, no secrets store). Support:
> devsecops (least-privilege + secret hygiene of the delivery surface), compliance (data-classification
> of the deliverable). Consumes `cd-config.md` (the delivery design); the two `infrastructure-design`
> consumes (`deployment-architecture.md`, `infrastructure-services.md`) do **not exist** — that stage
> was skipped for this assessment intent, so the inventory is built from `cd-config.md` + live
> read-only inspection of the repo.

## What "environment" means here

The deployable artifact is the `docs/review/*.md` set + its GitHub-issue projection. Its entire runtime
is **GitHub**: the git repository, the Actions runner that executes the integrity gate, and the `gh`
surface that materializes findings into the tracker. There is nothing to `terraform apply`.

## Inventory

| # | Component | Role | Provisioned by | Status |
|---|---|---|---|---|
| E1 | GitHub repo `triplem/kdiab` | The delivery target — holds `docs/review/**` and receives the issue projection | GitHub (exists) | ✅ ready |
| E2 | GitHub Actions runner (`ubuntu-latest`) | Executes `review-verify.yml` (the integrity gate) | GitHub-hosted | ✅ ready |
| E3 | Python 3 (stdlib only) | Runs `docs/review/verify.py` — no `pip`, no `setup-python` | Pre-installed on the runner | ✅ ready |
| E4 | `.github/workflows/review-verify.yml` | The CI/CD gate job `Verify review deliverable integrity` | Installed at ci-pipeline (3.7) | ✅ present |
| E5 | `docs/review/verify.py` | The 10-check integrity harness | Installed at ci-pipeline (3.7) | ✅ present, 10/10 green live |
| E6 | `gh` CLI authentication | Enables the issue projection at 4.3 | Maintainer token | ✅ authed as `triplem`, scopes cover `repo`/`workflow` |
| E7 | GitHub labels (review taxonomy) | Label the epic + sub-issues | **Created this stage (Q1=B)** | ✅ 10 created + 2 reused |
| E8 | Branch-protection rule on `main` | Makes the integrity gate load-bearing (Q2=A) | Maintainer repo-admin action | ⚠️ NOT set — advisory prerequisite |

## E7 — Label taxonomy (provisioned this stage)

Created live on `triplem/kdiab` (`gh label create --force`, idempotent):

| Label | Color | Purpose |
|---|---|---|
| `review` | `#5319e7` | Marks every review epic/finding issue |
| `quick-win` | `#0e8a16` | The 5 effort-S high-value items |
| `area:clinical-safety` | `#1d76db` | Area tag (blue family = "area" dimension) |
| `area:data-model` | `#0e8a16` | Area tag |
| `area:security` | `#6f42c1` | Area tag |
| `area:tech-debt` | `#795548` | Area tag |
| `area:modernization` | `#00bcd4` | Area tag |
| `severity:high` | `#b60205` | Severity tag (warm gradient = "severity" dimension) |
| `severity:medium` | `#d93f0b` | Severity tag |
| `severity:low` | `#fbca04` | Severity tag |

**Reused (not re-created):** `epic`, `In Progress` (already existed). Two colour dimensions keep the two
tag families visually separable at a glance (blue-ish areas, warm severities).

## E6 — Access & least privilege (devsecops perspective)

- **Auth present:** `gh` is logged in as `triplem`; token scopes include `repo`, `workflow`, `admin:org`.
  `repo` alone covers issue + label + branch-protection operations; the broader scopes are pre-existing
  on the token, not required by this workflow.
- **CI least privilege (unchanged from ci-config):** `review-verify.yml` runs with
  `permissions: contents: read` only, `timeout-minutes: 5`, `actions/checkout` pinned by SHA. It needs
  no write scope — it only reads and verifies.
- **No secrets to provision:** the delivery surface stores markdown in git and issues in the tracker.
  There is no API key, connection string, or credential to inject — nothing for a secrets manager to
  hold, and `verify.py` includes a `no-secrets` check so none is ever committed into the docs.

## Repo topology note

This working clone is `kdiab-bkp`, whose only git remote is a **local** `claude` remote →
`/home/triplem/projects/kdiab` (no GitHub `origin`). The canonical delivery target is the GitHub repo
`triplem/kdiab`, reached directly via `gh -R triplem/kdiab` (confirmed reachable). Deployment Execution
(4.3) must target `triplem/kdiab`, not the local remote.

## Data classification (compliance perspective)

- The deliverable is a **review of** a T1D health platform, but contains **no patient data, PII, or PHI**
  — only code references (`path/File.kt#symbol`), architectural findings, and regulatory *questions*
  (the MDR/SaMD and GDPR flags are flagged as decisions to make, not data handled).
- Therefore the delivery surface has **no data-residency, encryption-at-rest, or DPA obligation** of its
  own. The regulatory findings it *raises* (SEC-004/005/006) concern the kdiab platform, and are tracked
  as findings — they are not compliance obligations of this docs repo.
