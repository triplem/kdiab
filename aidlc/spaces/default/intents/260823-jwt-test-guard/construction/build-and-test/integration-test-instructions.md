# Integration & E2E Test Instructions — jwt-test-guard (#1588)

## What changed at these tiers
The integration-test and e2e-test source sets across the services also mint test JWTs via
`MapApplicationConfig(... "jwt.test" to "true" ...)`, so they received the same
`"jwt.allowTestMode" to "true"` affirmation. No integration/e2e **logic** changed — only the test
harness config gained the opt-in.

## Compile check (fast, infra-free)
Because these tiers may require external infra (Postgres/Keycloak/testcontainers), the propagation is
first validated by **compiling** the integration/e2e source sets — this catches any malformed edit
without needing a running stack:
```bash
for m in kdiab-analyze kdiab-calc kdiab-carbs kdiab-measures kdiab-nightscout kdiab-treatments kdiab-users; do
  ( cd "$m" && ./gradlew compileIntegrationTestKotlin compileE2eTestKotlin --console=plain ) || echo "COMPILE FAILED: $m"
done
```

## Full run (CI / local with infra)
```bash
cd <module> && ./gradlew integrationTest   # JUnit5, shouldRunAfter test
cd <module> && ./gradlew e2eTest           # Kotest, shouldRunAfter integrationTest
```
These run authoritatively in GitHub Actions CI on the deployment-execution PR, where the required
infra is provisioned.

## Interop note — kdiab-nightscout
`kdiab-nightscout` `BaseNightscoutTest.kt` (integration) received the opt-in. FIND-DEBT-001 separately
notes nightscout ships 0 e2e tests; that is out of scope for this security patch and unaffected.
