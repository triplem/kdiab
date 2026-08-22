# Integration Test Instructions — U1 Jackson-free JWT (#1606)

Standard test strategy (feature scope). Consumes the code-generation `code-summary.md`.
Framework: JUnit 5 (`integrationTest` suite, `shouldRunAfter test`) + H2 in-memory + Ktor
`testApplication`; e2e uses Kotest (`e2eTest`, `shouldRunAfter integrationTest`).

## How to run

```bash
cd kdiab-<service> && ./gradlew integrationTest        # boundary/cross-component tests
cd kdiab-<service> && ./gradlew e2eTest                # full app startup + real HTTP (Kotest)
cd kdiab-<service> && ./gradlew check                  # test + integrationTest + e2eTest + detekt + kover
```

## Load-bearing integration/e2e coverage for #1606

### 1. Full app startup without jackson (every service)
The `integrationTest`/`e2eTest` suites boot the service via `module()` — which installs
`configureSecurity()` (the Nimbus provider), swagger, serialization, etc. — over H2. A green suite
proves the app **starts and serves real HTTP with jackson absent from the runtime classpath**
(the AC-1/AC-8 supply-chain goal cannot regress the running app).

### 2. End-to-end auth on real routes
Each service's `*ApiTest` (integration) and `*E2ETest` (e2e) mint tokens via the migrated Nimbus
minter and drive real endpoints, asserting the auth decision end-to-end (200/401/403). The
multi-audience analyze tokens (`.audience(listOf(...))`) exercise the JWT-forwarding audience checks.

### 3. HMAC ≥32-byte secret end-to-end (profiles)
`kdiab-profiles` `InsulinApiTest`/`ProfileApiTest` (integration) and the profiles e2e specs mint +
validate with the lengthened `"secret-…"` value, confirming the Nimbus `MACSigner`/`MACVerifier`
round-trip under the new key-length rule.

## Environment

No external services required — Liquibase-bootstrapped H2 in-memory + in-process Ktor. No Postgres,
no Keycloak, no network. JWKS/RS256 (production) is not exercised in tests; the HS256 `jwt.test=true`
path is (with ≥32-byte secrets).

## Coverage target

Kover ≥ 80% on new/modified code; `koverVerify` runs after all three suites in `./gradlew check`.

## Verified so far (this stage / prior)

- `:kdiab-measures:integrationTest :kdiab-measures:e2eTest` — GREEN (full app startup, no jackson).
- `:kdiab-profiles:integrationTest` — GREEN (≥32-byte secret round-trip).
- Remaining services' integration/e2e results in `build-test-results.md`.
