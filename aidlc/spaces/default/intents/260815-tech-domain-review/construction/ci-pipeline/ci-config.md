# CI Configuration — Review Deliverable Verification

> Stage 3.7 (CI Pipeline), enterprise scope. Lead: aidlc-pipeline-deploy-agent. Consumes
> `code-summary.md` (per-unit), `build-and-test-summary.md`, `build-test-results.md`. This is a
> recommendations-only / assessment intent, so CI targets the `docs/review/*.md` deliverable — not
> deployable software. The kdiab platform's own CI (backend-ci-reusable, CodeQL, Trivy, SonarCloud,
> docker-publish) is mature and out of scope here.

## What was installed (Q5 = full, Q6 = install live)

Per the user's ci-pipeline gate decisions, two files were added to the repo:

| Path | Role |
|---|---|
| `.github/workflows/review-verify.yml` | GitHub Actions workflow — runs the verifier on `docs/review/**` (and its own) changes, on `pull_request` and `push` to `main` |
| `docs/review/verify.py` | Dependency-free (stdlib) Python harness encoding the build-and-test deliverable-integrity checks; exit 1 on any failure |

Both are **review tooling**, not changes to any kdiab service — see "Recommendations-only scope" below.

## Pipeline shape

- **CI tool:** GitHub Actions (matches the repo's existing `.github/workflows/`).
- **Trigger:** `pull_request` and `push` to `main`, path-filtered to `docs/review/**` and the workflow
  file itself — so it only runs when the review deliverable (or its gate) changes; it never adds load to
  unrelated PRs.
- **Runner / cost:** `ubuntu-latest`, `timeout-minutes: 5`. No build, no services, no network — a
  single `python3 docs/review/verify.py` step. `permissions: contents: read` (least privilege).
- **Dependencies:** none. Ubuntu runners ship Python 3; no `pip install`, no `setup-python` pin.
- **Action pinning:** `actions/checkout` pinned by commit SHA with a version comment, matching the
  repo's supply-chain convention (`@3d3c42e5… # v7.0.1`).

## Verifier checks (`docs/review/verify.py`)

Ten checks, mirroring `build-and-test`'s suite — the durable subset that must hold on every future
change to the deliverable:

1. **presence** — all ten deliverables exist and are non-empty.
2. **schema** — every finding carries the mandated Finding-Record fields (`Recommendation (rewrite):`
   qualifier allowed).
3. **contiguity** — finding IDs are contiguous and unique per area (CLIN 1–14, DATA 1–5, SEC 1–7,
   DEBT 1–8, MOD 1–5).
4. **severity-discipline** — no non-clinical Critical (ADR-RVW-004).
5. **evidence-format** — citations are `path#symbol`/key based, never bare line numbers (ADR-RVW-007).
6. **backlog-traceability** — every actionable finding is in the BACKLOG table, and the "N actionable
   findings" heading matches the row count. *(This is the check that caught FIND-SEC-002.)*
7. **phase-authority** — a finding's `Phase` agrees across theme doc, backlog column, and roadmap band;
   drift fails, aligned to the roadmap band as source of truth (ADR-RVW-006). *(Caught CLIN-010/013.)*
8. **dead-links** — every intra-set `./doc.md` link resolves.
9. **readme-numbers** — 39 total findings, 0 Critical, 5 High.
10. **no-secrets** — no private-key / token / secret patterns committed in the docs.

Run locally exactly as CI does:

```bash
python3 docs/review/verify.py   # exit 0 = green; from repo root
```

## Recommendations-only scope (Q6 = A consequence)

Installing the workflow means this review added **two files** to the repo. That is a deliberate,
user-authorized action at the ci-pipeline gate — and it does **not** breach the review's charter:

- The recommendations-only invariant is about the **system under review**: the review changed **no
  kdiab application, domain, or service source/config** (`kdiab-measures/`, `kdiab-calc/`, …, root
  `build.gradle.kts`, Liquibase, Keycloak realm). That remains true.
- The two new files are the review's own **verification harness** (a CI job + a checker script that
  read `docs/review/`), analogous to adding tests for a document — not a change to the software being
  assessed.
- The recurring gate intentionally **omits** the one-time "no code changed" authoring invariant: once a
  finding is materialized, its implementation PR *will* change kdiab source, and that is correct — so
  baking that invariant into recurring CI would be wrong.

## Interaction with existing CI

- **Path isolation:** the `docs/review/**` filter means `review-verify.yml` never runs on backend or
  UI PRs, and the nine backend + kdiab-ui pipelines never run on docs-only PRs. No cross-interference.
- **Branch protection (recommended, not set here):** to make the gate load-bearing, add
  `Verify review deliverable integrity` to the required status checks for `docs/review/**` — optional
  follow-up for the maintainer.
