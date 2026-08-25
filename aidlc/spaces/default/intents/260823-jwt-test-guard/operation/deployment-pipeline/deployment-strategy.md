# Deployment Strategy — jwt-test-guard (#1588)

## Strategy: publish-only, ride the existing pipeline

There is no running production environment and no traffic to shift, so canary / blue-green /
metric-triggered strategies are **N/A** (project.md: no-running-prod, publish-only). The strategy is:

1. **Merge** the PR to `main` after the full CI gate is green (team rule).
2. **Publish** — `docker-publish.yml` builds/pushes all 9 backend images to GHCR with immutable tags;
   `ci-common-publish.yml` publishes the kdiab-common JAR; `release.yml` cuts a **patch** semantic
   release.
3. **Consume** — whoever runs the images pulls the new immutable tag. Because production configs never
   set `jwt.test`, the guard is inert in normal operation (defence-in-depth); no rollout coordination.

## Safety characteristics specific to this change

- **Fail-safe by design:** if a consuming deployment *were* misconfigured with `JWT_TEST=true` (and no
  `JWT_ALLOW_TEST_MODE`), the new image **refuses to start** rather than run the insecure HMAC verifier.
  That is the intended behaviour — a loud, safe failure at boot, surfaced in container logs.
- **No coordinated release:** the change is independently shippable in one merge (NFR-conformant); it
  does not gate on any other item.
- **Rollout order irrelevant:** the guard is per-process and identical across all images (logic in
  kdiab-common); replicas/services can update in any order.

## Verification at publish time (smoke)

The "smoke test" for a publish-only pipeline is the CI gate itself (all suites + Kover ≥80% green) plus
image build success for all 9 services. There is no post-deploy health probe because there is no deploy
target; the auth-accept smoke test is a **forward hook** (see cd-config.md) for when prod exists.

## Abort / hold conditions

Do not merge (and therefore do not publish) if any CI check is failing or pending, or if the nightscout
build is red for a reason other than the known **#1614** stale-cache race (CI builds from a clean
checkout, so #1614 must not appear there — if it does, treat it as a real failure and hold).
