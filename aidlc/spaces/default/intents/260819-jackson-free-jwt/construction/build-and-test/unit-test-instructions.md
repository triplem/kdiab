# Unit Test Instructions — U1 Jackson-free JWT (#1606)

Standard test strategy (feature scope). Consumes the code-generation `code-summary.md`.
Framework: JUnit 5 + MockK + kotlin-test + Ktor `testApplication` (in-process H2 where a DB is needed).

## How to run

```bash
cd kdiab-<service> && ./gradlew test                 # one service's unit suite
cd kdiab-common   && ./gradlew test                  # shared lib incl. the JWT parity matrix
```

## Load-bearing unit tests for #1606

### 1. JWT authentication parity matrix (`kdiab-common`) — the core safety net
`JwtAuthenticationParityTest` installs `configureSecurity()` on a test Ktor app and asserts the full
negative-path matrix against the Nimbus provider. Required cases (all must yield the same
accept/reject as the retired `java-jwt` impl):

| Case | Expected |
|---|---|
| valid HS256 token | 200 + `UserPrincipal` populated (sub, roles, allowed_patients, timezone) |
| no Authorization header | 401 |
| malformed `Bearer` | 401 |
| bad signature (wrong key) | 401 |
| expired (`exp` in past) | 401 |
| not-before (`nbf` in future) | 401 |
| wrong audience | 401 |
| wrong issuer | 401 |
| missing/blank `roles` | 401 |
| **present-but-non-array `roles`** (`[1,2]`) | **401, NOT 500** (reviewer must-fix) |
| malformed-UUID `sub` | 401 |

### 2. Per-service route auth unit tests (all 8 services)
Each service's `*RoutesTest` mints HS256 tokens via the migrated Nimbus `SignedJWT`+`MACSigner`
helper and asserts RBAC (`checkReadAccess`/`checkWriteAccess`) — 200 self/admin/allowed-doctor,
403 cross-user/non-allowed-doctor, 401 no-token. These prove the minter migration preserves
token shape (sub/roles/allowed_patients/aud/iss).

### 3. Config-validation unit tests
`kdiab-profiles/SecurityConfigTest` — app FAILS to start when `jwt.test=true` and `jwt.secret` is
omitted; app STARTS with an explicit ≥32-byte secret. (The happy-path secret was lengthened to
satisfy the new Nimbus minimum.)

## Coverage target

Kover ≥ 80% line coverage on new/modified code (`koverVerify`, enforced in `./gradlew check`).
`Security.kt` is the primary new logic and is exercised by the parity matrix + per-service route tests.

## Verified so far (this stage / prior)

- `:kdiab-common:test` (parity matrix) — GREEN.
- `:kdiab-measures:test` (route auth) — GREEN.
- `:kdiab-profiles:test` (incl. `SecurityConfigTest`) — GREEN.
- Full per-module results in `build-test-results.md`.
