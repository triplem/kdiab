# Health Check Report — jwt-test-guard (#1588)

## No runtime environment to health-check

kdiab has no continuously-running production environment (project.md: no-running-prod). There is no
`/healthz`/`/readyz` endpoint to probe post-deploy because nothing is deployed — the pipeline
terminates at GHCR image publish. The "health" of this change is therefore assessed at two points:

## 1. Pre-publish health (verified)
- All 9 backend modules build + test green with the change (build-and-test stage).
- The guard's own behaviour is proven by `SecurityConfigTest`: a correctly-configured service (prod
  default, or test with opt-in+secret) starts; a misconfigured one (`jwt.test=true` without opt-in)
  fails fast — the intended safe state.

## 2. Publish health (in flight)
- PR #1642 CI gate is the health signal; must be fully green before merge.
- On merge, image-publish success for all 9 backends (`docker-publish.yml`) + kdiab-common JAR publish
  (`ci-common-publish.yml`) is the delivery health check.

## Runtime health semantics of the guard (for whoever runs the images)
- **Healthy:** service boots with the Keycloak-JWKS verifier (prod default, `jwt.test` unset). No change
  from today.
- **Intended-unhealthy (fail-fast):** if `jwt.test=true` without `JWT_ALLOW_TEST_MODE`, the service
  refuses to start and logs `jwt.test=true is not permitted unless jwt.allowTestMode=true …`. A crash
  loop here is a **configuration** signal, not a code defect — remediate the env (see rollback-runbook).

## Forward hook (if prod is introduced later)
Add an auth-accept readiness probe asserting (a) a real Keycloak token is accepted and (b)
`JWT_ALLOW_TEST_MODE` is absent in the running environment. Documented placeholder only.
