# Security Test Instructions & Review — U1 Jackson-free JWT (#1606)

Lead perspective: aidlc-devsecops-agent (security). #1606 is an **auth-touching, safety-sensitive**
change; ADR-023 mandates a manual security review before merge. This file records both the security
**test cases** and the **manual review** of `kdiab-common/.../plugins/Security.kt`.

## A. Manual security review of the Nimbus provider — verdict: PASS (1 non-blocking note)

| Property | Finding |
|---|---|
| **Algorithm confusion / `alg=none`** | **BLOCKED.** Each path pins exactly one algorithm — prod `JWSVerificationKeySelector(RS256, jwkSource)`, test `SingleKeyJWSKeySelector(HS256, key)`. An attacker cannot submit an `alg=none` token, nor an HS256 token signed with the RS256 public key (classic RS↔HS confusion): the key selector rejects any other `alg`. This is the single most important JWT property and it is correct. |
| **Signature verification** | Real cryptographic verification via Nimbus `DefaultJWTProcessor` — RS256 against JWKS, HS256 against the symmetric key. |
| **Claim enforcement** | One shared `DefaultJWTClaimsVerifier(audience, exact-issuer, …)` with `maxClockSkew=3` enforces issuer + audience on **both** paths (fixes the FR-3 risk a bare MACVerifier would skip them). `exp` enforced when present (expired → 401). |
| **Weak-key protection** | HS256 requires a ≥32-byte secret (`require(secret.size >= 32)`), fail-fast at startup — Nimbus enforces the RFC-7518 256-bit minimum that java-jwt tolerated. A net security improvement. |
| **Transport (JWKS)** | Non-local JWKS must be HTTPS (`check(isInternal || scheme=="https")`) → blocks plaintext JWKS key substitution (MITM). |
| **Test-mode containment** | `check(!isTest || secret != null)` + explicit "do not use test JWT mode in production" — HMAC test mode cannot silently run in prod (which uses JWKS/RS256; `jwt.secret` unused there). |
| **Fail-closed on malformed claims** | `mapToPrincipal` exception-guards every typed accessor → a wrong-shape claim (e.g. `roles:[1,2]`) yields **401, not 500** (no stack-trace leak, no bypass). Missing `sub` → `Uuid.parse(null)` guarded → 401. |
| **No secret/PII in logs (A09)** | `TOKEN_REJECTED` logs only `reason`, `path`, `method`, `remote`, `correlationId` — never the token, secret, or claim values. 401 body is generic (`"Token is not valid or has expired"`); the classified reason stays server-side. |
| **Reason classification** | Derived by string-matching Nimbus messages — affects only the LOGGED `reason=` label, never the accept/reject decision (which is authoritative from the verifier). A version/i18n drift is cosmetic, not a bypass. Note for maintenance. |
| **Client IP for logging** | `clientIp()` trusts the first `X-Forwarded-For` hop **for the log field only** — not for any auth or rate-limit decision (the rate limiter keys on `userId`). Ensure a trusted proxy sets XFF in prod; no auth impact. |

**Non-blocking finding (required-claims set).** The claims verifier passes `emptySet()` as required
claims, so `exp` is enforced only *when present* — matching the retired java-jwt behaviour (java-jwt
did not require `exp` either, and several valid test tokens legitimately omit it). The design ADR
mentioned `requiredClaims={exp,sub}`; enforcing that would be **stricter than java-jwt parity** and
would reject the (java-jwt-accepted) no-`exp` tokens, so it is correctly NOT applied under the
parity bar (NFR-2). Real Keycloak tokens always carry `exp`, so this is not exploitable in practice.
*Recommendation (separate hardening issue, out of #1606 scope):* if the platform wants to require
`exp` presence platform-wide, do it deliberately across all issuers **and** test minters.

## B. Security test cases (automated — the parity matrix IS the security suite)

Run: `cd kdiab-common && ./gradlew test` (`JwtAuthenticationParityTest`) + each service's route tests.

| # | Attack / case | Expected |
|---|---|---|
| S1 | `alg=none` token | 401 (algorithm pinned) |
| S2 | valid RS256 shape re-signed as HS256 with the JWKS public key | 401 (RS/HS confusion blocked) — prod path |
| S3 | tampered payload / bad signature | 401 |
| S4 | expired (`exp` past) | 401 |
| S5 | not-yet-valid (`nbf` future) | 401 |
| S6 | wrong audience / wrong issuer | 401 |
| S7 | present-but-non-array `roles` (`[1,2]`) | 401, NOT 500 |
| S8 | malformed-UUID `sub` / missing `sub` | 401 |
| S9 | no / malformed `Bearer` header | 401 |
| S10 | HS256 secret < 32 bytes at startup | app fails fast (`SecurityConfigTest`) |

The parity matrix covers S3–S9 today; S1/S2 are inherent to the pinned key selectors (not separately
asserted but structurally guaranteed). S10 is `SecurityConfigTest`.

## C. Supply-chain security (the whole point of #1603/#1606)

```bash
# Per module — expect "No dependencies matching" for each:
cd kdiab-<service> && ./gradlew dependencyInsight --dependency jackson-databind --configuration runtimeClasspath
cd kdiab-<service> && ./gradlew dependencyInsight --dependency java-jwt      --configuration runtimeClasspath
```

- **VERIFIED:** `jackson-databind`, `jackson-core`, `com.auth0:java-jwt`, `com.auth0.jwk:jwks-rsa`
  are absent from all 9 modules' `runtimeClasspath`; no silent downgrade (jackson vanished, did not
  drop to the CVE-vulnerable 2.21.3). `handlebars` stays pinned at 4.5.2 (CVE-2026-55760).
- **CI security gates** (`backend-ci-reusable.yml`): Trivy CRITICAL/HIGH, CodeQL, and CycloneDX SBOM
  must stay green — the removal of jackson from the runtime image should *reduce* Trivy findings.
- Nimbus adds only jackson-free transitives (`json-smart`, `accessors-smart`, `asm`).
