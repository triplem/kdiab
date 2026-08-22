# Intent Statement — Jackson-free JWT Verification

**Intent:** #1606 — `feat(auth): replace com.auth0:java-jwt with a jackson-free JWT verification`
**Epic:** Refs #1603 (remove jackson from the runtime classpath)
**Scope:** feature · **Depth:** Standard · **Space:** default · **Record:** `260819-jackson-free-jwt`

---

## Problem Statement

The eight kdiab Kotlin/Ktor backends authenticate every request with a JWT verified by a
single shared plugin, `kdiab-common/plugins/Security.kt` (`configureSecurity()`). That plugin
depends on **two Auth0 libraries**:

- `com.auth0:java-jwt` — the verifier, reached through Ktor's `jwt("auth-jwt")` DSL
  (`ktor-server-auth-jwt`). Claim extraction in `buildPrincipal` uses java-jwt's
  `JWTCredential`/`Payload` API.
- `com.auth0.jwk:jwks-rsa` — `JwkProviderBuilder`, which fetches and caches the Keycloak JWKS.

Both libraries transitively pull **jackson** onto the runtime classpath. Jackson is the
last remaining dependency epic #1603 set out to remove: the logback JSON-encoder swap
(#1605) and the unused `ktor-server-openapi`/Swagger path (#1607) already shipped, leaving
the JWT authentication path as the sole surviving jackson consumer that the epic tracks.

Jackson carries a recurring stream of `jackson-databind` CVEs (the epic already had to
force-pin jackson to avoid re-introducing CVE-2026-54512/54513 via a silent downgrade).
Every jackson version on the runtime classpath is attack surface the platform must monitor
and patch. Because the JWT path is auth-critical and safety-sensitive (this is a Type 1
Diabetes platform), the replacement must be behaviour-identical and independently verified.

## Target Customer

| Beneficiary | How they benefit |
|---|---|
| **Platform maintainers** (primary) | One fewer transitively-pulled, CVE-prone library to monitor, patch, and force-pin. Closing epic #1603 removes jackson from the runtime classpath entirely, simplifying the supply-chain/SBOM story across all 8 services. |
| **Security / DevSecOps** | Smaller attack surface; the `gradle dependencyInsight` proof becomes a durable regression guard against jackson creeping back. |
| **Operators** | No behavioural change to authentication; at most a documented, coordinated `jwt.*` config-key change (env/compose/keycloak) surfaced up-front. |
| **End users** (patients, doctors, admins) | **No visible change and no forced re-login** — token format stays Keycloak's; the intent is that authentication behaves exactly as today. |

The "customer" here is internal (the platform's own supply-chain health), so the value is
reduced security/maintenance risk rather than a new user-facing capability.

## Success Metrics

Measurable outcomes that define "done" (all must hold):

1. **Jackson gone from the runtime classpath** — `gradle dependencyInsight --dependency jackson-databind --configuration runtimeClasspath` (and the jackson-core/annotations coordinates) returns no result on every affected module. This is the primary acceptance test and closes epic #1603.
2. **`com.auth0:java-jwt` and `com.auth0.jwk:jwks-rsa` removed** from the dependency graph (both Auth0 JWT libraries gone).
3. **Behaviour-identical authentication** — full auth e2e green across all services: valid, expired, wrong-audience, missing-roles, malformed-UUID-subject, and HMAC-test-mode (`jwt.test=true`) tokens all behave exactly as they do today (same accept/reject outcome, same 401 + `ErrorResponse`, same `security_event=TOKEN_REJECTED` log line). `UserPrincipal` (userId, roles, allowedPatients, audiences, timezone) is extracted identically, including the JWKS RS256 cache / rate-limit / `acceptLeeway` / HTTPS-required hardening.
4. **Security review passes** — `/security-review` (or equivalent) on the diff with no unmitigated HIGH/CRITICAL findings (we are replacing a security-critical verification path).
5. **Quality gate green** — `./gradlew check` (tests + Detekt + Kover ≥80%) on every affected module, `kdiab-common publishToMavenLocal` works, and **all CI checks green** on the PR (no force-pin removal silently downgrades jackson back into a CVE).

## Initiative Trigger

**Why now:** This is the final open sub-issue of epic #1603 — the runtimeClasspath investigation
(learned 2026-08-19) proved jackson was pulled by three consumers: logback-jackson (fixed by
#1605), `ktor-server-openapi`/Swagger (fixed by #1607), and `com.auth0:java-jwt` via JWT auth
(this issue, #1606). With the first two shipped, #1606 is the last step to eliminate jackson —
and its force-pin — from the platform. The driver is **tech-debt / security-hardening**, not a
market or feature deadline.

## Initial Scope Signal

- **Scope:** `feature` (confirmed by the user). Although the change is behaviour-preserving
  (refactor-like), it is materially larger than the sibling dep-drops #1605/#1607: replacing
  java-jwt means replacing the Ktor `jwt` provider itself with a custom `AuthenticationProvider`
  built on `nimbus-jose-jwt`, re-implementing both the RS256/JWKS and HMAC-test signing paths,
  and porting claim extraction off java-jwt's `Payload` API — all in an auth-critical,
  safety-sensitive path that mandates full e2e + a security review. The `feature` stage set and
  Standard depth fit that risk profile.
- **Blast radius:** one shared file (`kdiab-common/plugins/Security.kt`) consumed by all 8
  backends — a single atomic change and one PR.
- **Chosen technical direction (from intent capture):** replace **both** Auth0 libraries with
  Nimbus (`nimbus-jose-jwt` for verification, `RemoteJWKSet` for JWKS), preserving all
  authentication behaviour exactly; a bounded `jwt.*` config-key change is permitted if Nimbus
  requires it, with operator coordination and a documentation note.
- **Out of scope:** removing jackson consumers outside the JWT path (none remain per epic #1603);
  any change to token issuance / Keycloak realm / claim contents; any change to `UserPrincipal`
  semantics or the `canAccess` authorization logic.
