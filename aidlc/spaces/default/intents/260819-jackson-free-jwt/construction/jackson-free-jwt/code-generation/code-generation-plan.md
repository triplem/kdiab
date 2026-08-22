# Code Generation Plan — U1 Jackson-free JWT Verification (#1606)

Traces to `../../../inception/units-generation/unit-of-work.md`,
`../../../inception/requirements-analysis/requirements.md`, and the application design
(ADR-023 + component/method specs). Developer-agent lead. Actual code goes to the workspace root;
this plan + `code-summary.md` are the record artifacts.

## Preconditions

- **Branch:** create `feat/1606-jackson-free-jwt` off `main` (never commit to main — git-hook enforced).
- **Nimbus version:** pin `com.nimbusds:nimbus-jose-jwt:10.0.1` (the version the §12a reviewer verified the API against: `DefaultJWTClaimsVerifier(String, JWTClaimsSet, Set<String>)` + `maxClockSkew`; `JWKSourceBuilder.create(URL).cache(long,long).rateLimited(long).retrying(boolean)`; `JWSVerificationKeySelector`, `SingleKeyJWSKeySelector`, `ImmutableSecret`, `MACSigner`, `SignedJWT`).

## Change surface (measured)

- **1 production file:** `kdiab-common/src/main/kotlin/org/javafreedom/kdiab/common/plugins/Security.kt`.
- **2 build files:** `gradle/libs.versions.toml`, `build-logic/src/main/kotlin/kdiab.kotlin-base.gradle.kts`.
- **25 test files** across all 8 services importing `com.auth0.jwt` (JWT.create test-minters) — see the list in the intent record. **⚠ decision impact:** 25 duplicated migrations strongly favours a **single shared Nimbus `TestTokenMinter`** (scope-Q1 option C) over per-service in-place rewrites (option A). Re-confirm with the user (see checkpoint) — A means 25 near-identical edits; C means one helper + 25 call-site swaps to it.
- **1 new doc:** `docs/adr/ADR-023-jackson-free-jwt-verification.adoc`.

## Task-ordered implementation (risk-first, T1→T8)

### T1 — Characterization/parity tests (before the swap)
- Add a `kdiab-common` unit test that installs `configureSecurity()` on a test Ktor app and asserts the full negative-path matrix (valid, expired, nbf, wrong-audience, wrong-issuer, bad-signature, missing/blank/non-array roles, malformed-UUID sub, missing header, malformed Bearer, HMAC valid+invalid) against the CURRENT impl — green first. Also assert the enriched `TOKEN_REJECTED reason=` per case + proxy-aware `remote=`.
- (Per-service route auth tests already exist and must stay green.)

### T2 — Dependency swap (`gradle/libs.versions.toml`)
- Remove `ktor-server-auth-jwt` from the `ktor-server` bundle (keep `ktor-server-auth`).
- Add `nimbus = "10.0.1"` + `nimbus-jose-jwt = { module = "com.nimbusds:nimbus-jose-jwt", version.ref = "nimbus" }`; add it to `kdiab-common`'s `implementation`.
- If option C: add a `testFixtures`/test dependency exposing `TestTokenMinter`.

### T3 — Nimbus verifier in `Security.kt` (the core)
Rewrite per component-methods.md: `JwtConfig` + `readJwtConfig` (preserve HTTPS-non-local predicate `host.contains('.')`/localhost/127.0.0.1), `TokenVerifier` (`JwksTokenVerifier`, `HmacTokenVerifier`) sharing ONE `DefaultJWTClaimsVerifier` (issuer+audience+exp, `maxClockSkew=3`), **exception-guarded** `ClaimsToPrincipalMapper` (runCatching on every typed accessor — the reviewer must-fix), `JwtAuthenticationProvider` (custom, `context.challenge` with `AuthenticationFailedCause` NoCredentials/InvalidCredentials) emitting `TOKEN_REJECTED reason=<…> remote=<XFF|remoteHost>` + `401 ErrorResponse`, `configureSecurity()`. Catch `BadJWTException` before `BadJOSEException`.

### T4 — Test-minter migration (Nimbus)
Replace `JWT.create().sign(Algorithm.HMAC256(secret))` with Nimbus `SignedJWT`+`MACSigner` across the 25 files (or via one shared `TestTokenMinter.hs256(...)` per option C). Preserve exact claims (sub/roles/allowed_patients/timezone/aud/iss/exp).

### T5 — Realm/config
Verify no `jwt.*`/`config/keycloak-realm.json` change is needed (design expects none). If any, document + PR-flag.

### T6 — Platform-wide sweep + jackson-only force-pin removal
`gradle dependencyInsight --dependency jackson-databind --configuration runtimeClasspath` on all 8 services + kdiab-common (incl. checking `ktor-server-swagger`). If clean everywhere → remove **only** the two jackson `constraints` lines in `kdiab.kotlin-base` (KEEP handlebars). Capture the proof.

### T7 — ADR-023
Author `docs/adr/ADR-023-jackson-free-jwt-verification.adoc` from `../../../inception/application-design/decisions.md`.

### T8 — Release gate
`./gradlew check` per affected module (tests + Detekt + Kover ≥80%) + `kdiab-common publishToMavenLocal`; full auth e2e; security review; all CI green.

## Verification (per requirements ACs)

- AC-1.1/1.2 + AC-8.1: the T6 sweep (jackson/java-jwt/jwks-rsa gone from every runtime classpath; swagger clean).
- AC-2/3/4/5/6 + NFR-2: the T1 matrix stays green after T3 (esp. HMAC-mode wrong-issuer/wrong-audience; present-but-non-array roles → 401 not 500).
- AC-10.1/10.2: all suites compile+green; java-jwt only on testRuntimeClasspath if option A/testImpl chosen (option C removes it entirely from test too if the fixture is Nimbus-only).

## Gated / deferred

- Force-pin removal (T6) is gated on the clean sweep — if a surprise consumer survives, keep the pins + log a follow-up.
- The mandatory security review (T8) is a human/tooling gate before merge.
