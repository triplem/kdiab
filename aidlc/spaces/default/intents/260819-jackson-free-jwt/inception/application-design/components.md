# Components — Jackson-free JWT Verification (#1606)

Design of the replacement auth-verification components in `kdiab-common`. Traces to
`../requirements-analysis/requirements.md` and `../user-stories/stories.md`; grounded in
`../../../codekb/kdiab-bkp/architecture.md` and `../../../codekb/kdiab-bkp/component-inventory.md`;
governed by `../practices-discovery/team-practices.md`. All components live in
`kdiab-common/plugins/` (package `org.javafreedom.kdiab.common.plugins`) unless noted; route wiring in
the 8 services (`authenticate("auth-jwt")`) is unchanged.

## Component Overview

| Component | Type | Responsibility | Replaces |
|---|---|---|---|
| `JwtConfig` | data holder | Read + validate `jwt.*` config (audience, domain, realm, test, secret, jwksUrl) + the HTTPS-required-for-non-local check | the inline `environment.config` reads in `configureSecurity()` |
| `TokenVerifier` | interface (port) | `verify(token): JWTClaimsSet?` — verify signature + issuer/audience/expiry(+leeway), return claims or null | the ktor `jwt{} verifier(...)` |
| `JwksTokenVerifier` | adapter | RS256 production path: Nimbus `DefaultJWTProcessor` + `JWKSource` (JWKSourceBuilder cache/rate-limit/retry) + `DefaultJWTClaimsVerifier` | `JwkProviderBuilder` + java-jwt `verifier(provider, domain)` |
| `HmacTokenVerifier` | adapter | HMAC256 test path (`jwt.test=true`): Nimbus `DefaultJWTProcessor` with `ImmutableSecret` HS256 key + the SAME `DefaultJWTClaimsVerifier` (re-adds issuer+audience checks) | java-jwt `JWT.require(HMAC256).withAudience().withIssuer()` |
| `ClaimsToPrincipalMapper` | pure function | Map Nimbus `JWTClaimsSet` → `UserPrincipal?` with the exact `buildPrincipal` rules | `buildPrincipal(JWTCredential, ...)` |
| `JwtAuthenticationProvider` | Ktor `AuthenticationProvider` | Parse Bearer header → `TokenVerifier.verify` → `ClaimsToPrincipalMapper.map` → `context.principal(...)` or `context.challenge{}` (401 + `ErrorResponse` + `TOKEN_REJECTED` log) | the ktor `jwt("auth-jwt")` provider |
| `configureSecurity()` | Application extension | Wire config → verifier → provider → `authentication { register(JwtAuthenticationProvider) }` | same-named function (rewritten) |
| `TestTokenMinter` (test-fixtures) | test util | Mint HMAC256 `SignedJWT` via Nimbus `MACSigner` for `jwt.test=true` suites | `com.auth0.jwt.JWT.create().sign(HMAC256)` in every service's tests |

## Component Responsibilities & Boundaries

- **`JwtConfig`** owns config parsing + the startup HTTPS check (`check(isInternal || https)`), preserving the exact non-local predicate (`localhost` / `127.0.0.1` / host without `.`). Pure; no Nimbus/Ktor.
- **`TokenVerifier`** is the seam that makes prod vs test swappable and keeps Nimbus out of the mapper. Both adapters share ONE `DefaultJWTClaimsVerifier` config (required issuer=`jwt.domain`, audience=`jwt.audience`, required `exp`, `maxClockSkew = JWT_ACCEPT_LEEWAY`) so issuer/audience/expiry/leeway behave identically on both paths — closing the FR-3 "MACVerifier doesn't check issuer/audience" gap by construction.
- **`ClaimsToPrincipalMapper`** is a pure function (no framework deps) reproducing every `buildPrincipal` reject rule. **All Nimbus typed-claim access is exception-guarded** (`runCatching{…}.getOrNull()`) because Nimbus accessors *throw* `ParseException` on a present-but-wrong-shape claim while java-jwt's `getClaim(…).asList/.asString` return *null* — the guard reproduces the null-on-mismatch semantics. So: `roles` present-but-non-array → treated as empty → reject; `subject` → UUID (reject on non-UUID); `allowed_patients` → skip unparseable; `timezone` non-string → default `"UTC"`; `audience` list.
- **`JwtAuthenticationProvider`** owns the Ktor integration + the challenge. It is the ONLY component that touches `AuthenticationContext`. The `challenge` reproduces FR-6's **HTTP contract exactly** — `respond(401, ErrorResponse(401, "Token is not valid or has expired"))` — and emits an **enriched** security log (approved refinement, Q4=B+D): `security_event=TOKEN_REJECTED reason=<r> path=… method=… remote=<xff-or-remoteHost> correlationId=…`, where `reason ∈ {no-token, malformed, bad-signature, expired, wrong-audience, wrong-issuer, invalid-claims}` and `remote` is the first `X-Forwarded-For` IP (fallback `remoteHost`). The `reason` is derived from the failure point: `TokenVerifier` distinguishes Nimbus `ParseException`→malformed, `BadJOSEException`→bad-signature, `BadJWTException` message→expired/wrong-audience/wrong-issuer; a null header→no-token; a successful verify with null map→invalid-claims.
- **`UserPrincipal`** (unchanged data class) and route-level `authenticate("auth-jwt")` are untouched — no service code changes.

## Design Notes

- **Why a custom provider, not `bearer`:** verified against `ktor-server-auth-jvm-3.5.0` — the bearer provider's Config has no `challenge` method, so it cannot reproduce FR-6. A custom `AuthenticationProvider` (on the same `ktor-server-auth` base) supports `context.challenge{}`. See `decisions.md` ADR-023.
- **Single shared change:** all components are in `kdiab-common`; the 8 services inherit them via `configureSecurity()`. `TestTokenMinter` is the one component that fans out into each service's test sources (per Q1=migrate).
