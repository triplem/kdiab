# Design Decisions (ADRs) — Jackson-free JWT Verification (#1606)

Records the architecture decisions. Traces to `../requirements-analysis/requirements.md`,
`../user-stories/stories.md`; grounded in `../../../codekb/kdiab-bkp/architecture.md` and
`../../../codekb/kdiab-bkp/component-inventory.md`; governed by
`../practices-discovery/team-practices.md`. The canonical `docs/adr/ADR-023-…adoc` file (Q2=A) is
authored in Code Generation from ADR-023 below.

## ADR-023 — Jackson-free JWT verification via Nimbus + a custom Ktor AuthenticationProvider

**Status:** Accepted (design). **Context:** `ktor-server-auth-jwt` transitively pulls `com.auth0:java-jwt`
+ `com.auth0.jwk:jwks-rsa` → jackson, the last jackson consumer on the runtime classpath (epic #1603).
Verified via `dependencyInsight` on kdiab-common + kdiab-measures. The change must preserve JWT auth
behaviour exactly.

**Decision:** Drop `ktor-server-auth-jwt`; keep `ktor-server-auth` (base); add `com.nimbusds:nimbus-jose-jwt`.
Implement a **custom `AuthenticationProvider`** (`JwtAuthenticationProvider`) that verifies tokens with
Nimbus `DefaultJWTProcessor` — RS256 via a cached `JWKSource` (prod) and HS256 via a symmetric key
(`jwt.test=true`) — using **one shared `DefaultJWTClaimsVerifier`** (issuer + audience + `exp` + 3s
clock-skew) so both paths enforce identical claims. Map Nimbus `JWTClaimsSet` → `UserPrincipal` with the
exact current `buildPrincipal` rules. Route wiring `authenticate("auth-jwt")` is unchanged.

**Consequences:**
- Jackson (+ both Auth0 libs) leave the runtime classpath → DoD met, epic #1603 closes.
- Adds `nimbus-jose-jwt` (+ `json-smart`, `accessors-smart`, `asm`) — all jackson-free.
- The shared `DefaultJWTClaimsVerifier` **fixes by construction** the FR-3 risk that a bare `MACVerifier`
  wouldn't check issuer/audience — the test (HMAC) path now enforces them via the same verifier.
- We own a small amount of security-critical wiring (provider + verifier config) → mandatory security review.
- Contained to `kdiab-common` (+ per-service test minters + build files); reversible via one `git revert`.
- **Claim-access parity (reviewer must-fix, resolved):** Nimbus typed accessors (`getStringListClaim`/`getStringClaim`) *throw* `ParseException` on a present-but-wrong-shape claim, whereas java-jwt's `getClaim(…).asList/.asString` return *null*. `ClaimsToPrincipalMapper` therefore exception-guards every typed access (`runCatching{…}.getOrNull()`) so a shape mismatch is treated as absent — reproducing today's null-on-mismatch → identical accept/reject (esp. FR-4's present-but-non-array `roles`). Without this guard a `"roles":[1,2]` token would 500 instead of 401.
- **Documented deviation — test-mode clock skew 0→3s:** today only the prod (JWKS) path sets `acceptLeeway(3)`; the HMAC test path uses java-jwt's default 0s. The one shared `DefaultJWTClaimsVerifier` applies `maxClockSkew=3s` to *both*. Harmless (3s), but noted as an intentional, tiny parity deviation on the test path.
- **FR-5 JWKS caching/rate-limit is a semantic re-model, not a 1:1 port:** auth0's `cached(size=10, ttl=24h)` + `rateLimited(bucket=10, refill 1/min)` (token bucket) maps to Nimbus `cache(ttl, refreshTimeout)` + `rateLimited(minInterval)` (min-interval-between-reloads; whole-set cache, no size cap). "Preserve rate-limiting" = preserve the *intent* (bounded JWKS refetch), not an exact-curve equivalence — no test should assert the exact bucket behaviour.

**Alternatives Rejected:**
- **Ktor `bearer` provider (scope-definition Q1a):** rejected — verified against `ktor-server-auth-jvm-3.5.0`
  that `BearerAuthenticationProvider$Config` has **no `challenge`** method, so it cannot reproduce FR-6's
  401 `ErrorResponse` body + `TOKEN_REJECTED` log. (This superseded the earlier bearer choice on hard evidence.)
- **Custom hand-rolled verifier (build):** rejected at Feasibility — for a safety-sensitive auth path an
  audited JOSE library (Nimbus) minimizes owned crypto; the one extra transitive (`json-smart`) is acceptable.
- **jjwt with a non-jackson serializer:** rejected — swaps jackson for gson/orgjson; no advantage over Nimbus.

## ADR-023a — Test token minting migrates to Nimbus MACSigner

**Context:** Every service's tests mint HMAC tokens via `com.auth0.jwt.JWT.create()` (java-jwt), reached
transitively through the main `ktor-server-auth-jwt`; dropping it breaks test compilation platform-wide (FR-10).
**Decision (Q1=A):** rewrite each service's test minter to a Nimbus `SignedJWT` + `MACSigner` helper
(`TestTokenMinter`), removing java-jwt from **test** scope too. **Consequences:** java-jwt is fully gone
(main *and* test) — cleanest end state; touches every service's test sources (bounded, mechanical).
**Alternatives Rejected:** `testImplementation(java-jwt)` (keeps java-jwt+jackson on `testRuntimeClasspath`
only — DoD-valid but leaves the lib); a shared kdiab-common test-fixture (more refactoring than #1606 warrants —
notee: could be a later DRY follow-up).

## ADR-023b — TOKEN_REJECTED log enriched; HTTP contract preserved (FR-6 refinement)

**Context:** the user asked to revisit `TOKEN_REJECTED`. Today one identical line + 401 fires for every
failure case. **Decision (Q4=B+D):** keep the **HTTP response byte-for-byte identical**
(`401 ErrorResponse(401,"Token is not valid or has expired")`) and the log prefix identical, but **enrich the
security log** with `reason=<no-token|malformed|bad-signature|expired|wrong-audience|wrong-issuer|invalid-claims>`
and make `remote=` proxy-aware (first `X-Forwarded-For`, fallback `remoteHost`). **Consequences:** better
incident triage; no client-visible change; a documented, intentional deviation from strict FR-6 parity — Build
& Test asserts the `reason=` per negative-path case; the generic 401 message is retained (don't leak *why* to
the caller). `remote=` via XFF is best-effort (spoofable unless behind a trusted proxy — kdiab is). Read XFF
directly in the challenge (no app-wide `XForwardedHeaders` plugin — keeps the change contained).
**Alternatives Rejected:** strict 1:1 reproduction (loses the triage win the user wants); excluding
no-token from TOKEN_REJECTED (a semantic change the user did not select).

## ADR-023c — Jackson force-pin: remove only the two jackson lines

**Context:** `kdiab.kotlin-base` pins jackson-core, jackson-databind, **and handlebars (CVE-2026-55760)**.
**Decision (FR-8):** in the same PR, gated on a clean platform-wide sweep, remove **only** the two jackson
constraint lines; **retain the handlebars pin**. **Consequences:** epic #1603 closes without re-opening a
HIGH CVE. **Alternatives Rejected:** removing the whole constraints block (would strip the handlebars pin —
the exact force-pin-downgrade trap the project rule forbids); keeping all pins (leaves a now-inert jackson pin).

## Reversibility Summary

| Decision | Reversibility |
|---|---|
| ADR-023 (Nimbus + custom provider) | Easy — one file, one revert; `TokenVerifier` seam isolates the impl |
| ADR-023a (test minter migration) | Easy — per-service test helper edits |
| ADR-023b (log enrichment) | Trivial — log-line-only change |
| ADR-023c (force-pin) | Trivial — build-file constraint lines |

## Review

**VERDICT: NOT-READY**

Reviewed as the §12a architecture reviewer against the actual APIs the design commits to,
not just the prose. I disassembled the real `ktor-server-auth-jvm-3.5.0` JAR from the Gradle
cache and the real `com.nimbusds:nimbus-jose-jwt-10.0.1` JAR (fetched from Maven Central), and
cross-checked every claimed method/constructor against `kdiab-common/plugins/Security.kt` and
`ErrorResponse.kt`. The architecture is sound and the Ktor half is fully implementable — but the
claims→UserPrincipal parity model rests on a factually wrong statement about Nimbus claim-accessor
behaviour, and that wrong statement is load-bearing for FR-4 (behaviour preservation), which is the
whole point of this security-sensitive change. That is a blocker.

**What verified out correctly (no action needed):**

- **Custom `AuthenticationProvider` is the right call and is API-correct.** Confirmed
  `AuthenticationProvider` is `public abstract` with ctor `(Config)` and `abstract suspend onAuthenticate(AuthenticationContext)`; `Config` has a `protected Config(String)` ctor; `AuthenticationConfig.register(AuthenticationProvider)` exists. `class JwtAuthenticationProvider(cfg: Config) : AuthenticationProvider(cfg)` + `jwtAuth(...) { register(...) }` all resolve.
- **The bearer-rejection is factually justified.** `BearerAuthenticationProvider$Config` has `authenticate`/`authHeader`/`authSchemes`/`realm` but **no `challenge`** — verified. ADR-023's alternatives-rejected reasoning holds on hard evidence.
- **The challenge mechanism is real.** `AuthenticationContext.challenge(Object, AuthenticationFailedCause, Function3<AuthenticationProcedureChallenge, ApplicationCall, Continuation, *>)` exists; `AuthenticationProcedureChallenge.complete()` exists; the real `BearerAuthenticationProvider` uses exactly this shape with `AuthenticationFailedCause.NoCredentials.INSTANCE`. `context.principal(...)` exists. `parseAuthorizationHeader(ApplicationRequest)` exists for reading the bearer token.
- **Every Nimbus type/method the design names exists in 10.0.1:** `JWKSourceBuilder.create(URL)` + `.cache(long,long)` + `.rateLimited(long)` + `.retrying(boolean)` + `.build()`; `DefaultJWTProcessor` with `setJWSKeySelector`/`setJWTClaimsSetVerifier`/`process(String, C)`; `JWSVerificationKeySelector(JWSAlgorithm, JWKSource)`; `SingleKeyJWSKeySelector(JWSAlgorithm, Key)`; `ImmutableSecret`; `MACSigner`; `SignedJWT`.
- **`DefaultJWTClaimsVerifier(String requiredAudience, JWTClaimsSet exactMatchClaims, Set<String> requiredClaims)` is a real constructor** and `maxClockSkew` is a settable property (`setMaxClockSkew(int)` → Kotlin `.apply { maxClockSkew = 3 }` works). Putting `issuer(cfg.domain)` in the exact-match set does enforce exact issuer match, and `requiredClaims=setOf("exp","sub")` enforces presence. The "one shared claims verifier fixes the FR-3 MACVerifier gap by construction" design is correct and is the strongest part of the design.
- **`ErrorResponse(401, "…")` matches** the real `ErrorResponse(code: Int, message: String, correlationId: String? = null)`.

**BLOCKING findings (must fix before this proceeds):**

- **[MUST-FIX] The `ClaimsToPrincipalMapper` parity model is built on a false premise about Nimbus and will diverge from `buildPrincipal` on malformed claims — a real accept/reject / status-code regression.** `component-methods.md` (l.76-77) and `components.md` (l.27) both assert *"`getStringListClaim` returns null for missing OR non-array `roles` → empty → reject."* Disassembly of `JWTClaimsSet` proves this is only *half* true:
  - Present-but-**scalar** (`"roles":"admin"`) → `getListClaim` returns null → `getStringListClaim` returns null. ✅ matches original.
  - Present-but-**array-of-non-strings** (`"roles":[1,2]` or `[{…}]`) → `getStringArrayClaim` does a `checkcast` to `String`, catches the `ClassCastException`, and **throws `ParseException("The roles claim is not a list / JSON array of strings")`**. ❌ It does **not** return null.
  The current `buildPrincipal` uses java-jwt `getClaim("roles").asList(String::class.java)` which returns **null** for that same shape → `emptyList()` → empty roles → reject (401). If `mapToPrincipal` calls `getStringListClaim` unguarded, the `ParseException` propagates out of the mapper — the token is *not* mapped to a rejected-401 the way today's code does; depending on where it surfaces it becomes an unhandled 500 or a different path. **Same defect applies to `getStringClaim("timezone")` (throws `"… claim is not a String"` if `timezone` is e.g. numeric; original `.asString()` returns null → defaults `"UTC"` → accept) and to `getStringListClaim("allowed_patients")`.** FR-4/AC-4.1 explicitly names "present-but-not-a-JSON-array roles" as a parity case, so this is in scope and currently mis-specified. **Fix:** the design must state that `mapToPrincipal` wraps every Nimbus typed claim accessor in `runCatching{…}.getOrNull()` (or try/`ParseException`) so a shape mismatch is treated as absent (→ default/UTC or → empty→reject), exactly reproducing java-jwt's null-on-mismatch semantics. Without this the "identical accept/reject" bar (NFR-2) is not met.

**NON-BLOCKING findings (tighten before/in Functional Design + Build-and-Test):**

- **`reason=` derivation is more fragile than the design implies, and has unmapped cases.** Disassembly of `DefaultJWTClaimsVerifier` shows the only distinct messages are: `"Expired JWT"`, `"JWT before use time"` (nbf), `"JWT missing required audience"`, `"JWT audience rejected: …"`, `"JWT missing required claims: …"`, and the generic exact-match failure `"JWT <name> claim has value …, must be …"` (this is what a **wrong issuer** produces — there is *no* dedicated "wrong issuer" string). So: (a) message-parsing for WRONG_ISSUER must match the generic exact-match message keyed on the `iss` claim, not a literal "issuer" string; (b) the `RejectionReason` enum has **no bucket for `nbf`/before-use** nor for `missing required claims` — both currently fall through with no defined `reason`. Since `reason=` is an intentional, non-contract log refinement (Q4=B+D; the 401 body is fixed) this does **not** affect parity, but Build-and-Test's "assert `reason=` per negative-path case" needs the mapping table pinned to these exact strings, and the enum needs an explicit fallback (e.g. `invalid-claims`) for nbf / missing-claims.
- **Catch ordering is load-bearing and unstated.** `BadJWTException extends BadJOSEException extends Exception`, and `process()` throws `ParseException`, `BadJOSEException`, `JOSEException`. The `verify` implementation MUST catch `BadJWTException` before `BadJOSEException` or every claims failure collapses into BAD_SIGNATURE. The prose lists them in the right order but a note that ordering is mandatory would prevent a silent regression.
- **Test-mode clock-skew changes 0 → 3s (minor parity drift, undocumented).** Today the HMAC test path (`JWT.require(HMAC256).withAudience().withIssuer()`) sets **no** `acceptLeeway`, so java-jwt default leeway = 0s; only the prod JWKS path calls `acceptLeeway(3)`. The design applies the *same* `DefaultJWTClaimsVerifier` (`maxClockSkew=3`) to both paths, so test mode gains 3s of skew tolerance it does not have today. Harmless (arguably better) but it is a behaviour change against a "preserve exactly" bar and should be noted as an intentional deviation in the ADR, not silent.
- **JWKS rate-limit is a semantic re-model, not a 1:1 port — worth an explicit ADR note.** Today: `rateLimited(bucketSize=10, refillRate=1/min)` — a token bucket (burst 10, refill 1/min). Nimbus `rateLimited(long)` is a **minimum-interval-between-JWKS-reloads** model (a different algorithm). FR-5 says "preserve rate-limiting"; the design's `JWK_RATE_LIMIT_MS` constant maps intent but not mechanism. Call this out so no one asserts exact parity of the throttling curve. (Cache `size` from the old `cached(maxSize=10, …)` also has no Nimbus equivalent — Nimbus caches the whole JWK set, not N entries; benign, but state it.)
- **`challenge("auth-jwt", …)` second argument is a placeholder.** The real signature needs a concrete `AuthenticationFailedCause` (the `…` in `component-methods.md` l.94). Specify `NoCredentials` for the no-token path and `InvalidCredentials` for verify/claims failures, mirroring the real bearer provider — otherwise it won't compile.
- **Cross-references and structure are clean.** No broken component IDs; the data-flow diagram matches the method contracts; `components.md`/`component-methods.md`/`component-dependency.md`/`services.md` agree with each other and with `Security.kt`. No circular dependencies (`configureSecurity` → verifier + provider → mapper; mapper is a pure leaf). Removed/kept/added dependency lists are internally consistent. FR-1/FR-8's "jackson dead everywhere" remains correctly *conditional* on the Build-and-Test sweep (incl. `ktor-server-swagger`), so it is not a design blocker.

**Must-fix summary (NOT-READY):** the single blocker is the `ClaimsToPrincipalMapper` finding — the design must specify exception-guarded Nimbus claim access so present-but-wrong-shape claims (`roles`/`timezone`/`allowed_patients`) reproduce java-jwt's null-on-mismatch → identical accept/reject, and must correct the two artifacts that state `getStringListClaim` "returns null for non-array." Once that is folded in (a one-paragraph correction to `component-methods.md` + `components.md`, plus a line in ADR-023), the design is implementable; the five non-blocking items are precision fixes for Functional Design / Build-and-Test.
