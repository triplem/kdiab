# Build-vs-Buy — Jackson-free JWT Verification (#1606)

The one genuinely relevant market-research output for this internal refactor. Traces to the
intent statement (`../intent-capture/intent-statement.md`): DoD = jackson off `runtimeClasspath`,
behaviour preserved exactly, security review + full auth e2e required. **Decision status: OPEN**
per user (Q1 = "keep both in play") — this artifact frames the decision; Feasibility (1.3) and the
Application Design ADR (2.6) settle it on evidence.

## Options on the Table

Both options require the same plumbing change — drop `ktor-server-auth-jwt` (java-jwt) and
`com.auth0.jwk:jwks-rsa`, and install a **custom Ktor `AuthenticationProvider`** in
`kdiab-common/plugins/Security.kt`. They differ only in what verifies the token inside that provider.

### Option A — BUY: adopt `nimbus-jose-jwt`
- **What:** Nimbus `DefaultJWTProcessor` + `RemoteJWKSet` (RS256, JWKS fetch/cache/rotation) and a `MACVerifier` for the HMAC test path; map Nimbus `JWTClaimsSet` → `UserPrincipal`.
- **Adds to runtime classpath:** `com.nimbusds:nimbus-jose-jwt`, transitively `net.minidev:json-smart` (+ `accessors-smart`). Jackson-free ⇒ **DoD met**.
- **Owned code:** thin — the provider wiring + claim mapping. Crypto/JWKS handled by Nimbus.
- **Review burden:** low (audited library does the crypto).

### Option B — BUILD: custom verifier
- **What:** verify with JDK `java.security.Signature("SHA256withRSA")` and `javax.crypto.Mac("HmacSHA256")`; fetch JWKS via the existing Ktor `HttpClient`; parse JWT + JWKS with **`kotlinx.serialization`** (already on the classpath); implement `exp`/`nbf`/`aud`/`iss`/leeway checks and JWKS `kid` selection + caching ourselves.
- **Adds to runtime classpath:** **nothing** (jackson-free *and* json-smart-free).
- **Owned code:** substantial — all security-critical validation is ours.
- **Review burden:** high (this is exactly the code a security review must scrutinize).

## Decision Framework

| Criterion | Weight | Favors |
|---|---|---|
| Jackson off `runtimeClasspath` (DoD) | must-have | **Tie** — both satisfy it |
| Minimize *net* new deps (epic #1603 spirit) | high | **Build** (zero new) over Buy (adds json-smart) |
| Crypto correctness / low risk in an auth path | high | **Buy** (audited Nimbus) over Build (we own it) |
| Security-review + test effort | medium | **Buy** (less owned code) |
| Long-term maintenance | medium | **Buy** (Nimbus tracks JOSE spec/CVEs) vs Build (we patch) |
| Behaviour parity with current `jwks-rsa` caching | medium | **Buy** (`RemoteJWKSet` mirrors it closely) |

## Recommendation (advisory)

**Lean BUY (nimbus-jose-jwt)** as the default, because this is a **safety-sensitive auth path** on
a T1D platform and the intent explicitly mandates a security review — trading a smaller *owned*
crypto surface for one extra (jackson-free) transitive dep is the lower-risk exchange. The
**BUILD** option remains attractive on pure dependency-minimization grounds and should be kept as a
documented alternative; choose it only if Feasibility finds json-smart itself carries unacceptable
CVE exposure or the team prefers zero net new deps and accepts the review burden.

**Hand-off to Feasibility (1.3):** decide on evidence — run `gradle dependencyInsight` to confirm
(a) exactly what each option adds/removes and (b) that json-smart is not itself a CVE liability;
weigh the security-review burden of owned crypto against one audited dependency. Record the final
choice as an ADR (`ADR-{USR|common}-NNN` or `ADR-NNN`) in Application Design.
