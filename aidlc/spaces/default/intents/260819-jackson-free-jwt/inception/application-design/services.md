# Services — Jackson-free JWT Verification (#1606)

Service-level view. Traces to `../requirements-analysis/requirements.md` and
`../user-stories/stories.md`; grounded in `../../../codekb/kdiab-bkp/architecture.md` and `../../../codekb/kdiab-bkp/component-inventory.md`
(kdiab-common is the shared library in the component inventory); governed by
`../practices-discovery/team-practices.md`. This change adds **no new deployable service** — it
rewrites a shared plugin that all 8 backend services install.

## Service Definitions

| Service | Role in #1606 | Change |
|---|---|---|
| **kdiab-common** (shared library, not deployable) | Owns `configureSecurity()` + all new verification components | All production code changes live here (one file / package) |
| **kdiab-measures / profiles / treatments / analyze / carbs / calc / nightscout / users** | Install `configureSecurity()` via `CommonPlugins` at startup | **No production code change**; only their **test** token-minters migrate to `TestTokenMinter` (Nimbus) |
| **Keycloak** (external) | Issues RS256 JWTs + serves JWKS | Unchanged — verification-only change |

## Lifecycle & Orchestration

- **Startup (per service):** `Application.module()` → `configureSecurity()` reads `JwtConfig`, runs the
  HTTPS-required check (fail-fast, unchanged), constructs the verifier once
  (`JwksTokenVerifier` in prod; `HmacTokenVerifier` when `jwt.test=true`), and registers
  `JwtAuthenticationProvider("auth-jwt")`. The Nimbus `JWKSource` (cached/rate-limited/retrying) is
  created once at startup and reused for the process lifetime — mirroring today's single
  `JwkProviderBuilder`.
- **Per request:** Ktor routes protected by `authenticate("auth-jwt") { … }` (unchanged) invoke
  `JwtAuthenticationProvider.onAuthenticate` → verify → map → `principal` or challenge. No orchestration
  across services; each verifies locally. JWT-forwarding services (analyze, calc) still forward the same
  Bearer token to upstreams unchanged (the multi-audience token is accepted by each via the audience
  check in the shared claims verifier).
- **JWKS refresh:** handled inside Nimbus `JWKSource` (cache TTL + rate-limited refresh on unknown
  `kid`), replacing `com.auth0.jwk`'s cache/rate-limit. No app-level scheduling.

## Communication Contracts

- **Inbound:** `Authorization: Bearer <jwt>` — unchanged wire contract. Success → route handler with
  `UserPrincipal`; failure → `401 ErrorResponse(401,"Token is not valid or has expired")` — unchanged.
- **Outbound (to Keycloak JWKS):** GET `${jwt.domain}/protocol/openid-connect/certs` (or `jwt.jwksUrl`)
  over HTTPS via Nimbus's default `ResourceRetriever` (connect/read timeouts). Same endpoint as today.
- **Config contract (operators):** `jwt.audience/domain/realm/jwksUrl/secret/test` — **unchanged**
  (design confirms Nimbus needs no new keys; FR-7 realm/config change is a no-op for this design, so
  US-5 does not fire). No forced end-user re-login.

## Scaling / Operational Characteristics

- Stateless per request; the only shared state is the in-process JWKS cache (same as today). No new
  network hop, no new datastore, no new container. Horizontal scaling characteristics unchanged.
- **Security-log change (Q4=B+D):** the `TOKEN_REJECTED` line gains `reason=` and a proxy-aware
  `remote=` (first `X-Forwarded-For`, fallback `remoteHost`). The OTEL→Loki pipeline ingests the line
  as-is; the added `reason=` key is a superset and does not break existing parsers (log fields are
  key=value; new keys are additive).
