# CI Pipeline — Clarifying Questions

> Stage 3.7 (CI Pipeline), enterprise scope. Consumes `code-summary.md`, `build-and-test-summary.md`,
> `build-test-results.md`. For this recommendations-only / assessment intent the CI target is the
> `docs/review/*.md` deliverable, not deployable software.

## Context-determined answers (not asked — resolved from project state)

These follow deterministically from `team.md` and the existing `.github/workflows/`, so they are
recorded, not put to the user:

| Question | Answer | Source |
|---|---|---|
| Q1. CI tool in use? | **GitHub Actions** | repo `.github/workflows/` (docker-publish, CodeQL, Trivy, SonarCloud) |
| Q2. Branch strategy? | **Trunk-based**; short-lived `<type>/<issue>-<desc>` feature branches; **merge-commit, not squash** (preserves `Closes #N`) | `team.md` § Way of Working |
| Q3. Artifact repositories? | **n/a** — a Markdown review ships no build artifact; the "artifact" is the committed `docs/review/` tree | assessment intent |
| Q4. Commit/release convention? | Conventional Commits (Angular), semantic-release on `main` | `team.md` § Deployment |

## Decisions put to the user

- **[Answer]: A** — Q5. Scope of the review-docs CI.
  - A. Full docs-verification workflow — run all build-and-test checks (schema, contiguity,
    traceability, phase-authority, dead-links, recommendations-only, no-secrets) as a merge gate on
    `docs/review/**` changes. **(Recommended)**
  - B. Minimal — a single runnable verification script documented in `ci-config.md`, no workflow gate.
  - C. Out of scope — document the checks as manual steps only.
  - X. Other (specify).

- **[Answer]: A** — Q6. Install vs recommend (recommendations-only invariant).
  - A. **Install** a live `.github/workflows/review-verify.yml` in the repo now. **(User-selected.)**
  - B. **Recommend only** — author the workflow YAML inside `ci-config.md` as a design artifact; the
    maintainer installs it if/when they choose.
  - X. Other (specify).

## Resolution

Q5 = A (full verification workflow) and Q6 = A (install live) were put to the user via the harness
structured-question channel. Selected answers drive `ci-config.md` and `quality-gates.md`.

**Consequence recorded (Q6 = A):** installing `.github/workflows/review-verify.yml` + `docs/review/verify.py`
adds review-verification tooling to the repo. This is a deliberate, user-authorized action at the
ci-pipeline gate. The review's **recommendations-only invariant remains intact where it matters** — it
is scoped to *kdiab application / service* code (`kdiab-*/`, root build files, service config): the
verifier changes **no** kdiab service. The two new files are the review's own CI harness, not a change
to the system under review.
