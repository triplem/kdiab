# Component Dependency — Jackson-free JWT Verification (#1606)

Dependency matrix, data flow, and shared resources for the components in `components.md`. Traces to
`../requirements-analysis/requirements.md` and `../user-stories/stories.md`; grounded in
`../../../codekb/kdiab-bkp/architecture.md` and `../../../codekb/kdiab-bkp/component-inventory.md`;
governed by `../practices-discovery/team-practices.md`.

## Dependency Matrix

| Component | Depends on | Nature |
|---|---|---|
| `configureSecurity()` | `JwtConfig`, `TokenVerifier` (impl chosen by `isTest`), `JwtAuthenticationProvider` | wiring at startup |
| `JwtAuthenticationProvider` | `TokenVerifier`, `ClaimsToPrincipalMapper`, Ktor `AuthenticationContext`, `ErrorResponse`, `securityLogger`, `callId` | sync, per-request |
| `JwksTokenVerifier` | Nimbus `DefaultJWTProcessor` + `JWKSource` (RS256), `JwtConfig` | sync; owns JWKS cache |
| `HmacTokenVerifier` | Nimbus `DefaultJWTProcessor` + symmetric key, `JwtConfig` | sync |
| `ClaimsToPrincipalMapper` | `UserPrincipal`, `Role`, `kotlin.uuid.Uuid` | pure function |
| `JwtConfig` / `readJwtConfig` | Ktor `ApplicationEnvironment` config | startup read |
| `TestTokenMinter` (test) | Nimbus `SignedJWT` + `MACSigner` | test-only |

**Removed dependencies:** `io.ktor:ktor-server-auth-jwt`, `com.auth0:java-jwt`,
`com.auth0.jwk:jwks-rsa` (and transitively jackson). **Kept:** `io.ktor:ktor-server-auth` (base).
**Added:** `com.nimbusds:nimbus-jose-jwt`.

## Data Flow (per request)

```
[Client] --Bearer JWT--> authenticate("auth-jwt")
     |
     v
JwtAuthenticationProvider.onAuthenticate
     | token? --no--> challenge(NO_TOKEN) --> 401 + log
     v yes
TokenVerifier.verify(token)
     |  Rejected(reason) --> challenge(reason) --> 401 + log(reason)
     v Verified(claims)
ClaimsToPrincipalMapper.mapToPrincipal(claims)
     |  null --> challenge(INVALID_CLAIMS) --> 401 + log
     v UserPrincipal
context.principal(principal) --> route handler
```
<!-- Text fallback: provider gets the token (else NO_TOKEN challenge); verifier returns Verified(claims) or Rejected(reason); mapper turns claims into UserPrincipal (else INVALID_CLAIMS challenge); success sets the principal and the route runs. Every challenge path emits 401 + the enriched TOKEN_REJECTED log. -->

## Communication Patterns

- All interactions are **synchronous, in-process** function calls — no async, no events, no new I/O
  except the JWKS fetch, which Nimbus performs lazily/cached inside `JWKSource` (same network behaviour
  as today's `JwkProviderBuilder`).

## Shared Resources

- **JWKS cache** — one per process, inside the Nimbus `JWKSource` (RS256 path only). Replaces the
  `com.auth0.jwk` cache; same size/TTL/rate-limit intent. Thread-safe (Nimbus handles concurrency).
- **`securityLogger`** — the existing kotlin-logging logger; the only new usage is the enriched
  `TOKEN_REJECTED` line.
- **`UserPrincipal`, `Role`, `ErrorResponse`** — existing shared types, unchanged.

## Reversibility

Easy to reverse: the change is contained to `kdiab-common/plugins/Security.kt` (+ per-service test
minters + build files). Reverting is a `git revert` of one PR. The `TokenVerifier` seam also makes the
verifier implementation independently swappable later (e.g. back to a library) without touching the
provider or mapper.
