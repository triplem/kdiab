# Intent Capture — Clarifying Questions

**Intent:** #1606 — replace `com.auth0:java-jwt` with a jackson-free JWT verification (Refs #1603)
**Scope:** feature · **Depth:** Standard · **Space:** default

> Fill in each `[Answer]:` tag. Every question ends with `X. Other (please specify)`.
> For multi-select questions, list all letters that apply, e.g. `[Answer]: A, C, E`.

---

## Q1 — What is the definition of "done" / the primary driver?

`com.auth0:java-jwt` is on the runtime classpath because `ktor-server-auth-jwt` needs it, and it transitively pulls jackson. Which is the real success bar?

- A. **jackson fully off the runtime classpath** across all services — this is the last open piece of epic #1603, and "done" = `gradle dependencyInsight` shows no jackson on `runtimeClasspath`
- B. **Remove `com.auth0:java-jwt` specifically** (regardless of whether jackson lingers via some other consumer)
- C. **Both** — remove java-jwt AND verify jackson is gone from the JWT path; if another consumer still pulls jackson, note it but keep this issue scoped to the JWT path
- D. Reduce attack surface / dependency count generally; exact library less important than "no auth0 JWT libs"
- X. Other (please specify)

[Answer]: A — jackson fully off the runtime classpath across all services (closes epic #1603); done = `gradle dependencyInsight` shows no jackson on `runtimeClasspath`. **Mode:** guided (2026-08-19)

---

## Q2 — Which replacement approach do you want?

- A. **`nimbus-jose-jwt`** (recommended) — the de-facto jackson-free JWT/JOSE library on the JVM; supports RS256 (JWKS) + HMAC, mature security track record
- B. A **custom minimal verifier** (hand-rolled RS256/HMAC + JWKS fetch) — smallest dependency footprint, but we own the crypto/validation code
- C. Another jackson-free JWT library (please name it under Other)
- D. **Leave the library choice to Application Design** — decide once we model the verification path in detail
- X. Other (please specify)

[Answer]: A — `nimbus-jose-jwt` (jackson-free; supports RS256/JWKS + HMAC). **Mode:** guided (2026-08-19)

---

## Q3 — Does the JWKS provider (`com.auth0.jwk:jwks-rsa`) also need replacing?

The current code fetches JWKS via `com.auth0.jwk:JwkProviderBuilder` — a **separate** auth0 library from java-jwt that also transitively pulls jackson. If jackson must fully leave (Q1=A/C), this must go too.

- A. **Yes — replace it** (e.g. with Nimbus `RemoteJWKSet` + caching), because otherwise jackson stays on the classpath
- B. **No — only `java-jwt` is in scope**; leave `jwks-rsa` in place for now (open a follow-up if jackson lingers)
- C. **Investigate in Application Design** — confirm whether `jwks-rsa` actually pulls jackson before deciding scope
- X. Other (please specify)

[Answer]: A — replace `jwks-rsa` too (Nimbus `RemoteJWKSet` + caching); required so jackson fully leaves the classpath. **Mode:** guided (2026-08-19)

---

## Q4 — Which current behaviours MUST be preserved exactly? (select all that apply)

The issue says preserve `UserPrincipal` extraction and JWKS validation exactly. Confirm the full behaviour-preservation set:

- A. **Claim extraction unchanged** — `sub`→userId, `roles`, `allowed_patients`, `timezone`, `audience` map to `UserPrincipal` identically (incl. the "no roles ⇒ reject", "bad UUID ⇒ reject", audience-check rules in `buildPrincipal`)
- B. **Both signing paths** — prod JWKS **RS256** (Keycloak) and test-mode **HMAC256** (`jwt.test=true`) remain supported
- C. **JWKS hardening preserved** — cache (size/TTL), rate-limit, `acceptLeeway`, and the HTTPS-required-for-non-local check
- D. **Error/challenge behaviour preserved** — 401 + `ErrorResponse`, and the `security_event=TOKEN_REJECTED` structured log line on rejection
- E. **Config keys unchanged** — `jwt.audience`, `jwt.domain`, `jwt.realm`, `jwt.jwksUrl`, `jwt.secret`, `jwt.test` (no env-var churn for operators; no forced user re-login — token format is Keycloak's, unchanged)
- X. Other / some of these are negotiable (please specify)

[Answer]: A, B, C, D — preserve claim mapping, both RS256+HMAC signing paths, JWKS hardening, and 401/TOKEN_REJECTED error behaviour exactly. **NOT E**: some `jwt.*` config-key / env-var churn is acceptable if nimbus needs it (operator coordination + docs note required; no forced user re-login regardless — token format unchanged). **Mode:** guided (2026-08-19)

---

## Q5 — What is the intended rollout shape?

`configureSecurity()` lives once in `kdiab-common`; all 8 backends install it.

- A. **Single shared change in `kdiab-common` Security.kt**, atomic across all 8 backends, one feature branch + one PR (`Closes #1606`) — expected given the shared library
- B. **Phased per-service** migration (e.g. prove on one service, then roll out)
- C. Shared change, but behind a **feature flag / config toggle** so old and new verifiers can coexist during rollout
- X. Other (please specify)

[Answer]: A — single shared change in `kdiab-common` Security.kt, atomic across all 8 backends, one feature branch + one PR (`Closes #1606`). **Mode:** guided (2026-08-19)

---

## Q6 — What is the verification bar before merge? (select all that apply)

This is auth-touching and safety-sensitive; the issue mandates full auth e2e + security review.

- A. **Full auth e2e** — all services' auth e2e tests green (valid/expired/wrong-audience/missing-roles/HMAC-test-mode tokens all behave identically to today)
- B. **Security review** — `/security-review` (or equivalent) on the diff, no unmitigated HIGH/CRITICAL
- C. **Dependency proof** — `gradle dependencyInsight --dependency jackson-databind --configuration runtimeClasspath` confirms jackson (and java-jwt) gone from the relevant classpath, per the project's "verify runtimeClasspath before shedding a dep" rule
- D. **Standard quality gate** — `./gradlew check` (tests + Detekt + Kover ≥80%) green on every affected module + `kdiab-common publishToMavenLocal`
- E. **All CI green on the PR** — every backend + kdiab-ui build passes before merge (no force-pin downgrade re-introduces a jackson CVE)
- X. Other (please specify)

[Answer]: A, B, C, D, E — all five gates: full auth e2e, security review (no unmitigated HIGH/CRITICAL), dependencyInsight proof jackson/java-jwt gone, `./gradlew check` (Kover ≥80% + Detekt), and all CI green on the PR. **Mode:** guided (2026-08-19)
