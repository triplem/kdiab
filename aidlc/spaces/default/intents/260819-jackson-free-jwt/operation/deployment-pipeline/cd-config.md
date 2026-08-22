# CD Configuration — U1 Jackson-free JWT (#1606)

Consumes `../../construction/ci-pipeline/ci-config.md`,
`../../construction/ci-pipeline/quality-gates.md`.

> **Upstream note.** This stage's frontmatter also declares `deployment-architecture` and
> `cicd-pipeline` (both produced by `infrastructure-design`, stage 3.4). **Stage 3.4 was skipped**
> for #1606 — there is no cloud infrastructure to design (AWS is forbidden per `project.md`; the
> deployable surface is GHCR container images). Neither `deployment-architecture.md` nor
> `cicd-pipeline.md` exists; the CD design below is sourced from `ci-config.md` + `quality-gates.md`
> and the platform's real GitHub-native release workflows.

## Decision: use the existing publish-on-merge pipeline unchanged

#1606 is a dependency-swap (Nimbus JWT provider replacing `com.auth0:java-jwt`, jackson force-pin
removed). It ships as new/changed **application code and build config** — the identical set of nine
container images the platform already builds and publishes. The platform's GitHub-native release
pipeline already covers every affected image. **No CD workflow file is added or modified for #1606**
— mirroring the same "existing pipeline covers it" conclusion `ci-config.md` reached for CI.

This is a **publish-to-registry** pipeline, not a deploy-to-environment pipeline. Per the deployment
questions (Q1), **there is no continuously-running production environment today**: the pipeline's
terminal step is publishing immutable images to GHCR, which are then consumed ad hoc (self-hosting,
demos, local `docker compose` / `podman`). There is therefore no environment-promotion matrix, no
canary/blue-green stage, no post-deploy smoke stage, and no metric-based rollback trigger to
configure — those would be inventions with no target. This document records the pipeline that
actually exists and the forward hooks for when a running environment is introduced.

## The pipeline that ships #1606 (existing, unchanged)

| Stage | Workflow | Role for #1606 |
|---|---|---|
| Build + verify | `backend-ci-reusable.yml` (via the 8 `ci-<service>-backend.yml` + `ci-common-publish.yml`) | `:check :koverVerify :buildFatJar :cyclonedxBom` + Detekt SARIF + SonarCloud + Trivy CRITICAL/HIGH + SBOM. Gate before any publish. |
| SAST | `codeql-backend.yml` | CodeQL over the changed Kotlin, incl. the new `Security.kt` Nimbus provider. |
| Cross-service verify | `e2e.yml` | Auth flows across services with one forwarded token. |
| **Publish gate** | `docker-publish.yml` job `gate` | On `workflow_run(completed, main)`, queries every check-run on the head SHA; publishes **only if all are `success`/`skipped`/`neutral`**. Blocks on any pending/failed check. |
| **Publish** | `docker-publish.yml` jobs `publish-backends` (8× matrix) + `publish-ui` | Builds each fat JAR + image, pushes to `ghcr.io/<owner>/kdiab-<image>` tagged `latest` (default branch), `v{version}` (semver), `sha-<short>`. |
| Version | `release.yml` | semantic-release computes the version bump from the Conventional Commit (`fix(auth): …` → patch). |

<!-- Text fallback: PR CI (build+verify, CodeQL, e2e) must be green; on merge to main the
docker-publish gate re-confirms all check-runs passed, then publishes 9 immutable images to GHCR
tagged latest + v{version} + sha-<short>; semantic-release tags the version. No deploy step follows. -->

## Environment promotion

| Tier | Exists today? | Gate |
|---|---|---|
| PR / feature branch | Yes | Full CI (`backend-ci-reusable.yml`, CodeQL, e2e) must be green — from `quality-gates.md`. |
| `main` (registry) | Yes | The `docker-publish.yml` `gate` job — all check-runs green — plus the team merge gate (merge-commit with `Closes #1606`, manual security sign-off on the Nimbus provider per ADR-023). |
| Running production | **No — not provisioned** | N/A today. Forward hook below. |

There is no dev→staging→prod promotion because only the first two tiers exist. "Promotion to prod"
is, today, "an operator pulls a published tag when they choose to run the platform."

## Approval workflow for release

- **Automated gate**: `docker-publish.yml` `gate` job — deterministic, blocks publish until every CI
  check-run on the commit is green (no `--admin` bypass; team practice from `quality-gates.md`).
- **Human gate**: the PR merge itself is the human approval. For #1606 specifically, ADR-023 mandates
  a **manual security sign-off** on the safety-sensitive Nimbus auth path before merge — this is the
  release approval that matters for an auth change. The build-and-test security review is PASS; a
  human should countersign.

## Feature-flag strategy

**None — not applicable.** JWT verification is a fleet-wide correctness property, not a
progressively-rollout-able feature. A token either verifies or it does not; there is no meaningful
"dark launch" of an auth-verification library swap. The safety comes from *behavioural parity*
(ADR-023: identical accept/reject matrix, identical `UserPrincipal` extraction, identical `401`
body) proven in CI, not from a runtime flag. No CloudWatch Evidently / AppConfig / LaunchDarkly layer
exists or is introduced.

## Database migrations in the deployment

**None.** #1606 touches no schema — no Liquibase changelog is added. There is no forward-only /
backward-compatible migration concern for this change.

## Forward hooks (when a running production environment is later introduced)

Recorded so this decision is revisited deliberately, not by accident. If/when a continuously-running
prod is provisioned:

1. Add a `deploy` workflow triggered on `docker-publish.yml` success (or on `release: published`)
   that rolls the new image tag onto the environment.
2. Add the **auth post-deploy smoke test** described in `rollback-runbook.md` § "Smoke test (deferred)"
   as the deploy's health gate — "deployment is not done until smoke passes."
3. Add a **metric-based rollback trigger** (auth `401`/`403` spike on previously-valid tokens, or any
   `5xx` on the auth path) wired to the observability stage (4.4) outputs.
4. Prefer **redeploy-prior-immutable-tag** for fast auth-outage recovery (the prior `sha-`/`v{version}`
   tags already sit in GHCR) with the `git revert` PR as the permanent fix — the fast path that Q2's
   "no redeploy" answer defers only because nothing is running yet.

Until then, the rollback path is source-level only — see `rollback-runbook.md`.
