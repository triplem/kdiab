# Library Landscape — Jackson-free JWT Verification (#1606)

**Lean market-research** for an internal refactor: this is a **library / build-vs-buy** analysis,
not a product competitive analysis. Traces to the intent statement
(`../intent-capture/intent-statement.md`), whose definition-of-done is *jackson off the runtime
classpath across all services (closes epic #1603)* while preserving authentication behaviour
exactly. Candidates recorded per user selection: nimbus-jose-jwt, a custom verifier, and a
ktor-native-jwks + jackson-free verifier. (`jjwt` was explicitly excluded.)

## Evaluation Criteria

Derived from the intent statement's success metrics and constraints:

1. **Jackson-free** — must not pull jackson onto `runtimeClasspath` (the whole point).
2. **New transitive footprint** — how many *other* runtime deps it adds (each is new attack surface / SBOM entry).
3. **Crypto maturity** — audited, well-maintained signature verification (RS256 via JWKS + HMAC test path).
4. **Behaviour-parity effort** — how much of `Security.kt`'s current behaviour (claim extraction, JWKS cache/rate-limit/leeway, HTTPS check, HMAC test mode, 401/TOKEN_REJECTED) it can preserve with least risk.
5. **Review burden** — how much security-critical code *we* own and must review.

## Candidate Comparison

| Candidate | Jackson-free? | New runtime deps | Crypto maturity | Review burden | Notes |
|---|---|---|---|---|---|
| **nimbus-jose-jwt** (Connect2id) | Yes | Adds `net.minidev:json-smart` (+ `accessors-smart`) | High — the reference JVM JOSE lib; used by Spring Security; regular releases | Low — we call a maintained verifier; `RemoteJWKSet` handles key fetch/rotation/caching | Swaps one transitive JSON lib (jackson) for another (json-smart). json-smart is smaller but has had its own CVEs; still, DoD (jackson-free) is met. Requires a **custom Ktor `AuthenticationProvider`** since we drop `ktor-server-auth-jwt`. |
| **Custom verifier** | Yes | **Zero** — reuses `kotlinx.serialization` (already on classpath) + JDK `java.security.Signature` (SHA256withRSA) + `javax.crypto.Mac` (HmacSHA256) + existing Ktor HttpClient for JWKS | Medium — JDK crypto primitives are solid, but *we* assemble signature check + `exp`/`nbf`/`aud`/`iss` validation + base64url + JWKS key selection/rotation | **High** — we own all security-critical validation logic; needs the most thorough security review + tests | Smallest possible footprint: jackson-free **and** json-smart-free. Strongest fit for the "minimize deps" spirit of epic #1603, at the cost of owning crypto glue code. |
| **Ktor-native jwks + jackson-free verifier** | Partial/uncertain | Depends | n/a | Medium | Ktor's `jwt`/`jwks` auth support is built on `ktor-server-auth-jwt`, which **is** the java-jwt (jackson) path. There is no first-class jackson-free ktor JWT provider today, so in practice this collapses into "custom `AuthenticationProvider`" (the custom option) or "nimbus behind a custom provider". Recorded for completeness; likely folds into one of the other two at design time. |

<!-- Text fallback: nimbus = low review burden but adds json-smart; custom = zero new deps but high review burden (we own crypto); ktor-native jwks realistically collapses into custom-or-nimbus because ktor's JWT support IS the java-jwt path being removed. -->

## Strengths / Weaknesses Summary

- **nimbus-jose-jwt** — *Strength:* mature, low-risk crypto, minimal code we own. *Weakness:* trades jackson for json-smart (DoD still met, but not a pure dep reduction); forces a custom Ktor provider anyway since `ktor-server-auth-jwt` goes away.
- **Custom verifier** — *Strength:* zero new runtime deps (jackson-free AND json-smart-free), best embodies epic #1603's minimize-surface goal, reuses libraries already present. *Weakness:* we own security-critical verification; highest review + test burden; must get key-rotation, leeway, audience/issuer/expiry checks exactly right.
- **Ktor-native jwks** — *Strength:* least disruption to auth plumbing in principle. *Weakness:* no jackson-free ktor-native JWT provider exists; not independently viable — folds into custom or nimbus.

## Recommendation (advisory — Feasibility/ADR decides)

Both **nimbus** and **custom** are viable and both meet the DoD. Per the user (Q1), the decision
stays **open** into Feasibility (1.3) and is settled in the Application Design ADR (2.6) on
concrete evidence: exact transitive deps each adds (`gradle dependencyInsight`), the security-review
burden of owned crypto, and behaviour-parity risk. The realistic short list is **two options**
(nimbus vs custom); the ktor-native path collapses into them.
