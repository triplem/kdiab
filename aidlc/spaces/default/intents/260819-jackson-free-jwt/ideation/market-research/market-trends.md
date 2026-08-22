# Ecosystem Trends — Relevant to Jackson-free JWT (#1606)

Lean scan of the JVM/JOSE ecosystem trends that bear on this internal refactor. Traces to the
intent statement (`../intent-capture/intent-statement.md`) — specifically its trigger
(tech-debt / security-hardening: eliminate jackson and its force-pin) and its DoD
(jackson off `runtimeClasspath`). No market sizing applies (internal change).

## Relevant Trends

1. **Jackson-databind CVE fatigue.** `jackson-databind` has a long, recurring CVE history
   (polymorphic deserialization gadgets, etc.). This project already had to *force-pin* jackson
   to avoid a silent downgrade re-introducing CVE-2026-54512/54513 (project rule, learned
   2026-08-19). The industry trend is to remove jackson from paths that don't need general-purpose
   polymorphic JSON — exactly what epic #1603 is doing.

2. **Dependency-surface minimization / SBOM discipline.** Supply-chain security (SLSA, SBOM,
   Trivy/CodeQL gates — all present in this repo's CI) pushes teams to shed transitive libraries
   they don't strictly need. A JWT verifier does not need a full data-binding JSON library; it
   needs base64url + a small claims parser + signature verification. This favors either a minimal
   JOSE lib (nimbus) or reusing a JSON lib already present (kotlinx.serialization → custom).

3. **`kotlinx.serialization` as the Kotlin-native JSON default.** Ktor + Kotlin projects
   increasingly standardize on `kotlinx.serialization` (kdiab already does, via
   `ContentNegotiation`). This makes a *custom* jackson-free verifier cheaper than it historically
   was — the JSON parsing dependency is already paid for.

4. **Nimbus as the de-facto JVM JOSE library.** `nimbus-jose-jwt` (Connect2id) is the standard
   choice underneath Spring Security's resource-server JWT support and much of the OAuth2/OIDC JVM
   ecosystem. Its `RemoteJWKSet` (key fetch + caching + rotation) directly mirrors what
   `com.auth0.jwk:jwks-rsa` does today, easing behaviour parity. Trend: when teams *do* adopt a
   library here, it's overwhelmingly Nimbus.

5. **Ktor auth JWT is still java-jwt-bound.** Ktor's `ktor-server-auth-jwt` plugin is built on
   `com.auth0:java-jwt`; there is no first-class jackson-free ktor JWT provider. The ecosystem
   pattern for jackson-free JWT on Ktor is a **custom `AuthenticationProvider`** wrapping either
   Nimbus or hand-rolled JDK crypto. This is a known, well-trodden path, not novel territory.

## Implication for This Initiative

The ecosystem points to a **two-horse race** — adopt **Nimbus** (industry-standard, low crypto
risk, adds json-smart) vs **build custom** (zero new deps by reusing kotlinx.serialization, higher
review burden). Either fully satisfies the jackson-free DoD. There is no third differentiated
contender worth carrying forward. Both require replacing the ktor `jwt` provider with a custom
`AuthenticationProvider`, so that plumbing work is common to both and is not a deciding factor.
