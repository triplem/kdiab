# Component Methods — Jackson-free JWT Verification (#1606)

Method signatures for the components in `components.md`. Traces to
`../requirements-analysis/requirements.md` (FR-2..FR-6, FR-10) and `../user-stories/stories.md`;
grounded in `../../../codekb/kdiab-bkp/architecture.md` and `../../../codekb/kdiab-bkp/component-inventory.md`
(the existing `Security.kt`/`UserPrincipal`/`ErrorResponse` components these methods replace/reuse);
governed by `../practices-discovery/team-practices.md`. Kotlin, package
`org.javafreedom.kdiab.common.plugins`. Detailed business rules → Functional Design (3.1).

## Result / reason types

```kotlin
enum class RejectionReason(val wire: String) {
    NO_TOKEN("no-token"), MALFORMED("malformed"), BAD_SIGNATURE("bad-signature"),
    EXPIRED("expired"), WRONG_AUDIENCE("wrong-audience"), WRONG_ISSUER("wrong-issuer"),
    INVALID_CLAIMS("invalid-claims")
}

sealed interface VerificationOutcome {
    data class Verified(val claims: JWTClaimsSet) : VerificationOutcome   // Nimbus claims set
    data class Rejected(val reason: RejectionReason) : VerificationOutcome
}
```
Returning a reason (not just `null`) is what enables the enriched `reason=` log (Q4=B+D) without
leaking anything to the client (the 401 body is fixed).

## `JwtConfig`

```kotlin
data class JwtConfig(
    val audience: String, val domain: String, val realm: String,
    val isTest: Boolean, val secret: String?, val jwksUrl: String,
)
fun readJwtConfig(environment: ApplicationEnvironment): JwtConfig   // reads jwt.*; runs the HTTPS-required-for-non-local check (same predicate); check(!isTest || secret!=null)
```

## `TokenVerifier` (port) + adapters

```kotlin
fun interface TokenVerifier { fun verify(token: String): VerificationOutcome }   // token = raw JWT (no "Bearer ")

// RS256 production — Nimbus DefaultJWTProcessor + JWKSource (cached/rate-limited/retrying)
class JwksTokenVerifier(cfg: JwtConfig) : TokenVerifier
//   builds: JWKSourceBuilder.create<SecurityContext>(URL(cfg.jwksUrl))
//              .cache(JWK_CACHE_TTL_MS, JWK_CACHE_REFRESH_MS).rateLimited(JWK_RATE_LIMIT_MS).retrying(true).build()
//           DefaultJWTProcessor<SecurityContext>().apply {
//              jwsKeySelector = JWSVerificationKeySelector(JWSAlgorithm.RS256, jwkSource)
//              jwtClaimsSetVerifier = defaultClaimsVerifier(cfg) }

// HMAC256 test mode — same processor shape, symmetric key, SAME claims verifier
class HmacTokenVerifier(cfg: JwtConfig) : TokenVerifier
//   jwsKeySelector = SingleKeyJWSKeySelector(JWSAlgorithm.HS256, SecretKeySpec(cfg.secret, "HmacSHA256"))
//   jwtClaimsSetVerifier = defaultClaimsVerifier(cfg)   // ← re-adds issuer+audience+exp (fixes FR-3 gap)

// shared, so both paths enforce issuer+audience+exp+leeway identically:
private fun defaultClaimsVerifier(cfg: JwtConfig) =
    DefaultJWTClaimsVerifier<SecurityContext>(
        /* requiredAudience */ cfg.audience,
        /* exactMatch issuer */ JWTClaimsSet.Builder().issuer(cfg.domain).build(),
        /* requiredClaims */ setOf("exp", "sub"),
    ).apply { maxClockSkew = JWT_ACCEPT_LEEWAY_SECONDS }   // = 3, current acceptLeeway
```
`verify` maps Nimbus exceptions → `Rejected(reason)`. **Catch order is load-bearing** (`BadJWTException`
extends `BadJOSEException`, so catch it FIRST or all claims failures collapse into BAD_SIGNATURE):
- `ParseException` → `MALFORMED`
- `BadJWTException` → match on Nimbus's exact messages: `"Expired JWT"` → `EXPIRED`; `"JWT before use time"`
  → `EXPIRED` (nbf; folded into EXPIRED — no separate reason); `"JWT missing required audience"` /
  `"JWT audience rejected: …"` → `WRONG_AUDIENCE`; the generic exact-match `"JWT iss claim has value …, must be …"`
  → `WRONG_ISSUER`; **any other `BadJWTException` (e.g. `"JWT missing required claims: …"`) → fallback `INVALID_CLAIMS`**
- `BadJOSEException` (signature/key selection) → `BAD_SIGNATURE`
- success → `Verified(claims)`

(There is no dedicated Nimbus "wrong issuer" exception type — wrong issuer surfaces via the generic
exact-match message keyed on `iss`. Build & Test pins these exact strings; the `INVALID_CLAIMS` fallback
guarantees no unmapped `BadJWTException` escapes unclassified.)

## `ClaimsToPrincipalMapper`

```kotlin
fun mapToPrincipal(claims: JWTClaimsSet): UserPrincipal?    // null ⇒ INVALID_CLAIMS
//   subject → runCatching{ Uuid.parse(claims.subject) }.getOrNull() ?: return null
//   roles     = claims.stringList("roles").mapNotNull(Role::fromString).toSet() ; empty ⇒ return null
//   allowed   = claims.stringList("allowed_patients").mapNotNull{ runCatching{ Uuid.parse(it) }.getOrNull() }.toSet()
//   timezone  = claims.stringOrNull("timezone") ?: "UTC"
//   audience  = runCatching{ claims.audience }.getOrNull() ?: emptyList()

// CRITICAL parity guards (reviewer must-fix): Nimbus typed accessors THROW ParseException on a
// present-but-wrong-shape claim (e.g. "roles":[1,2] → getStringListClaim throws; "timezone":123 →
// getStringClaim throws), whereas java-jwt's getClaim(...).asList/.asString return NULL for the same.
// So EVERY typed access is exception-guarded to reproduce java-jwt's null-on-mismatch → treat-as-absent:
private fun JWTClaimsSet.stringList(name: String): List<String> =
    runCatching { getStringListClaim(name) }.getOrNull() ?: emptyList()   // missing OR wrong-shape ⇒ []
private fun JWTClaimsSet.stringOrNull(name: String): String? =
    runCatching { getStringClaim(name) }.getOrNull()                      // missing OR wrong-shape ⇒ null
```
Reproduces every `buildPrincipal` rule INCLUDING java-jwt's null-on-type-mismatch: a present-but-non-array
`roles` (FR-4/AC-4.1) → empty → reject (401), NOT an unhandled exception; a non-string `timezone` → default
`"UTC"` → accept, matching `.asString()` today.

## `JwtAuthenticationProvider` (custom `AuthenticationProvider`)

```kotlin
class JwtAuthenticationProvider(cfg: Config) : AuthenticationProvider(cfg) {
    override suspend fun onAuthenticate(context: AuthenticationContext)
    // 1. token = call.request bearer token or → challenge(NO_TOKEN)
    // 2. when(verifier.verify(token)) { Rejected(r) -> challenge(r); Verified(c) ->
    //       mapToPrincipal(c)?.let(context::principal) ?: challenge(INVALID_CLAIMS) }
    class Config(name: String) : AuthenticationProvider.Config(name) {
        lateinit var verifier: TokenVerifier; lateinit var realmName: String
    }
}
fun AuthenticationConfig.jwtAuth(name: String, configure: JwtAuthenticationProvider.Config.() -> Unit)  // register(...)

// the challenge (reproduces FR-6 HTTP contract + enriched log):
// 2nd arg is a concrete AuthenticationFailedCause (NOT a placeholder): NoCredentials for NO_TOKEN,
// InvalidCredentials otherwise — matching how the real BearerAuthenticationProvider challenges.
private fun AuthenticationContext.rejectWith(reason: RejectionReason) {
    val cause = if (reason == RejectionReason.NO_TOKEN) AuthenticationFailedCause.NoCredentials
                else AuthenticationFailedCause.InvalidCredentials
    challenge("auth-jwt", cause) { ch, call ->
        securityLogger.warn { "security_event=TOKEN_REJECTED reason=${reason.wire} path=${call.request.path()} " +
            "method=${call.request.httpMethod.value} remote=${call.clientIp()} correlationId=${call.callId ?: "-"}" }
        call.respond(HttpStatusCode.Unauthorized, ErrorResponse(401, "Token is not valid or has expired"))
        ch.complete()
    }
}
private fun ApplicationCall.clientIp(): String =
    request.header("X-Forwarded-For")?.split(",")?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
        ?: request.local.remoteHost   // best-effort; XFF spoofable unless behind trusted proxy
```

## `configureSecurity()` + test fixture

```kotlin
fun Application.configureSecurity() {
    val cfg = readJwtConfig(environment)
    val verifier = if (cfg.isTest) HmacTokenVerifier(cfg) else JwksTokenVerifier(cfg)
    authentication { jwtAuth("auth-jwt") { this.verifier = verifier; realmName = cfg.realm } }
}

// test-fixtures (per service, Q1=migrate off java-jwt):
object TestTokenMinter {                        // replaces com.auth0.jwt.JWT.create()
    fun hs256(secret: String, audience: String, issuer: String, subject: String,
              roles: List<String>, allowedPatients: List<String> = emptyList(),
              timezone: String? = null, expiresAt: Instant = …): String   // Nimbus SignedJWT + MACSigner
}
```
