# Smoke Test Results — U1 Jackson-free JWT (#1606)

Consumes `../deployment-pipeline/deployment-strategy.md`,
`../deployment-pipeline/cd-config.md`,
`../environment-provisioning/environment-inventory.md`,
`../../construction/build-and-test/build-test-results.md`.

## No live smoke test executed (N/A — no running environment)

"Deployment is not done until smoke passes" presumes a running deployment to smoke-test. #1606 has no
running production environment (`environment-inventory.md`), and the deployment (image publish) has not
been executed (document-only decision — see `deployment-log.md`). Therefore **no live post-deploy smoke
test was run**, and none is pending as part of this stage.

## Executed verification = CI (the standing smoke-equivalent)

The verification authority for #1606 is CI, and its **local pre-flight** was executed and recorded in
`build-test-results.md`. This is the executed evidence that stands in for a live smoke test:

| Verification | Executed where | Result (pre-flight, from build-test-results.md) |
|---|---|---|
| Behavioural parity — full accept/reject negative-path matrix | unit + integration `:check` | ✅ green, 9/9 modules |
| `UserPrincipal` extraction (`sub`, `roles`, `allowed_patients`, `timezone`) preserved | unit tests | ✅ preserved |
| `401` `ErrorResponse` body + `TOKEN_REJECTED` log unchanged | unit tests | ✅ byte-for-byte |
| Cross-service auth (one forwarded token accepted by every upstream) | `e2e.yml` (runs in CI on push/PR) | ⏳ runs on push (branch not yet pushed) |
| Supply-chain (jackson/java-jwt/jwks-rsa absent from runtimeClasspath) | `dependencyInsight` | ✅ verified (AC-1/AC-8) |

The cross-service e2e (`e2e.yml`) is the closest thing to a live multi-service smoke and will execute
automatically when the branch is pushed / PR opened (per the maintainer runbook in `deployment-log.md`).

## Deferred live smoke test (ready to run when a running env exists)

The concrete per-service auth accept/reject smoke test is fully specified in
`../deployment-pipeline/rollback-runbook.md` § "Smoke test (deferred)": for each service, a valid
Keycloak token must return 2xx AND a tampered/expired token must return `401` with the standard body
and a `TOKEN_REJECTED` log, canary (`kdiab-measures`) first. It becomes the deploy health gate the
moment a running environment is introduced (`cd-config.md` forward hooks) — until then it stays
deferred by design, not omitted.

## Result

**No live smoke executed (N/A).** Standing verification is CI (parity/coverage/supply-chain green
pre-flight; e2e runs on push). Live auth smoke deferred to a future running environment.
