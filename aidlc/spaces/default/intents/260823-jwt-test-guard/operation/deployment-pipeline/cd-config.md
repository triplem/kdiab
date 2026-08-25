# CD Config — jwt-test-guard (#1588 / FIND-SEC-001)

> **Publish-to-registry model.** kdiab has no continuously-running production environment; the delivery
> pipeline terminates at **GHCR image publish** + semantic-release. This change ships on the existing
> pipeline — no new CD infrastructure is introduced.

## Existing pipeline this change rides

| Workflow | Trigger | Output |
|---|---|---|
| `.github/workflows/docker-publish.yml` | push to `main` (+ release) | Builds & pushes all backend images to `ghcr.io/<owner>/kdiab-<image>` with immutable tags (`latest`, `v{version}`, `sha-<short>`). |
| `.github/workflows/release.yml` | push to `main` | Monorepo **semantic-release**: computes the version bump from Conventional Commits and tags the release. |
| `.github/workflows/ci-common-publish.yml` | push to `main` | Publishes the **kdiab-common** JAR (this change modifies `kdiab-common`, so this must succeed — per the quality-gate "verify publish before merge" rule). |

## Change-specific pipeline facts

- **Blast radius on publish:** the guard lives in `kdiab-common`, which every backend includes → on
  merge, `ci-common-publish.yml` republishes the common JAR and `docker-publish.yml` rebuilds/republishes
  **all 9 backend images**. Expected and normal for a shared-library change.
- **Version bump:** the change is a security fix → Conventional Commit `fix(security): …` → **patch**
  release via semantic-release. No `BREAKING CHANGE` (production default behaviour is unchanged; only a
  misconfigured `jwt.test=true` deployment is affected, and none exists).
- **New config surface:** one new optional env var, **`JWT_ALLOW_TEST_MODE`** (HOCON `jwt.allowTestMode`,
  default `false`). It is **not** required by any production deployment and MUST NOT be set there. Only
  CI/test environments set it (alongside `JWT_TEST=true`). No secret, no infra change.

## Authoritative gate before publish

`docker-publish.yml`/`release.yml` run only after the PR's full GitHub Actions gate is green (tests +
Kover ≥80% + Detekt + Trivy + CodeQL + SonarCloud + SBOM across all 9 backends). Per team rule, no
merge — and therefore no publish — on a failing or pending check.

## Forward hooks (if a running prod is introduced later)

Clearly out of scope today (no deploy target exists). When a prod environment lands, add: a deploy
workflow consuming the immutable GHCR tags; an auth-accept smoke test asserting a real Keycloak JWT is
accepted and that `JWT_ALLOW_TEST_MODE` is absent; and a metric/rollback trigger. Until then these are
documented placeholders, not built.
