<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is maintained by the orchestrator during stage execution. Add observations at the gate ritual, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-08-19T07:26:00Z — Intent framed as a NEW feature-scope intent (not a continuation): the active intent `logback-jsonencoder` was already complete; #1606 is a distinct auth-touching subject under epic #1603. User explicitly confirmed feature scope at the offer gate.
- 2026-08-19T07:26:30Z — Architect grounding: the whole JWT verification path is centralized in `kdiab-common/plugins/Security.kt` (shared by all 8 backends). It uses TWO auth0 libs — `com.auth0:java-jwt` (verification, via the ktor `jwt("auth-jwt")` DSL from `ktor-server-auth-jwt`) AND `com.auth0.jwk:jwks-rsa` (`JwkProviderBuilder` for JWKS fetch). `buildPrincipal` reads claims (sub, roles, allowed_patients, timezone, audience) through java-jwt's `Payload`/`JWTCredential` API. Test mode uses `Algorithm.HMAC256`; prod uses JWKS RS256 with cache/rate-limit/leeway. So replacing java-jwt implies replacing the ktor jwt provider itself with a custom AuthenticationProvider + a jackson-free verifier/JWKS source — materially larger than a one-line dependency swap.

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-08-19T07:40:00Z — Q4 answered "preserve all EXCEPT config keys" (A,B,C,D not E): operator-facing `jwt.*` config-key/env-var churn is permitted if nimbus needs it. Application Design (2.6) must enumerate exactly which config keys change (if any) and flag operator coordination — docker-compose env, `.env`, keycloak-realm, and an ops/user-guide "config change" note. Token format stays Keycloak's, so no forced user re-login regardless.
- 2026-08-19T07:40:30Z — Q3 answered "replace jwks-rsa too": confirm during Reverse-Engineering / Application Design that `com.auth0.jwk:jwks-rsa` actually pulls jackson on runtimeClasspath (per the project rule "verify runtimeClasspath before shedding a dep"), and that Nimbus `RemoteJWKSet` is itself jackson-free (it uses `json-smart`, not jackson) — the DoD "jackson fully off runtimeClasspath" depends on both.
