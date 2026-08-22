# RAID Log — Jackson-free JWT Verification (#1606)

Risks, Assumptions, Issues, Dependencies. Traces to `../intent-capture/intent-statement.md`,
`../market-research/build-vs-buy.md`, the library landscape `../market-research/competitive-analysis.md`
(source of R-4's json-smart risk and the nimbus-vs-custom framing), the ecosystem scan
`../market-research/market-trends.md`, and the feasibility assessment in this directory.

## Risks

| ID | Risk | L | I | Response |
|---|---|---|---|---|
| R-1 | Custom Ktor `AuthenticationProvider` + Nimbus mis-verifies a token class (accepts a token it shouldn't, or rejects a valid one) | Med | High | Full auth e2e parity suite as a merge gate; security review of the verification code; port `buildPrincipal` reject-rules 1:1 |
| R-2 | JWKS key rotation / caching behaves differently under Nimbus `RemoteJWKSet` than `com.auth0.jwk` | Med | High | Match cache size/TTL + rate-limit + leeway; test with rotated Keycloak keys; keep the HTTPS-required check |
| R-3 | HMAC test-mode path (`jwt.test=true`) diverges, breaking existing unit/integration suites | Med | Med | Implement Nimbus `MACVerifier` HMAC256 with identical audience/issuer checks; the test suites themselves are the regression signal |
| R-4 | `net.minidev:json-smart` (added by Nimbus) has its own CVE now or later | Low | Med | Trivy/CodeQL CI + security review; pin/upgrade json-smart if flagged; still jackson-free so DoD holds |
| R-5 | Another module (a service not sampled) pulls jackson via a different path, so jackson survives despite this change | Low | Med | Full per-service `dependencyInsight` sweep at build-and-test before claiming #1603 closed; sampled common+measures already clean |
| R-6 | Removing the jackson force-pin (tempting cleanup) silently downgrades a still-present transitive jackson into a CVE | Low | High | Do NOT remove the constraint as part of #1606 unless the sweep proves it dead; project rule enforced |

## Assumptions

| ID | Assumption | Validation |
|---|---|---|
| A-1 | `java-jwt` + `jwks-rsa` are the ONLY jackson consumers on runtime classpath | **Validated** on kdiab-common + kdiab-measures; to be confirmed platform-wide at build-and-test |
| A-2 | Nimbus supports everything the current path needs (RS256+JWKS with caching/rotation, HMAC256, leeway, audience/issuer/exp checks) | True per Nimbus capabilities (`DefaultJWTProcessor`, `RemoteJWKSet`, `MACVerifier`); to be proven by tests |
| A-3 | Route-level `authenticate("auth-jwt")` wiring in services stays unchanged (only the provider internals change) | Design constraint TC-6; verify no service route code touches java-jwt types directly |
| A-4 | Keycloak token/claim contract is stable and unchanged | True — this change is verification-only; no issuance change |
| A-5 | `jwt.*` config keys can stay identical (bounded churn only if Nimbus forces it) | Prefer no churn; Q4 permits a documented change if needed |

## Issues (open now)

| ID | Issue | Owner/Next |
|---|---|---|
| I-1 | Exact `jwt.*` config-key mapping under Nimbus not yet enumerated (does any key change?) | Application Design (2.6) — enumerate; if any change, add operator docs note |
| I-2 | Whether to also delete the now-no-op jackson force-pin | Deferred to build-and-test sweep; default keep |

## Dependencies

| ID | Dependency | Note |
|---|---|---|
| D-1 | Epic #1603 (parent) | #1606 is its last open child; closing #1606 closes the epic's jackson goal |
| D-2 | `nimbus-jose-jwt` availability in the configured Maven repos | Standard Maven Central artifact; add to version catalog |
| D-3 | Keycloak realm / JWKS endpoint | Unchanged external dependency; used as-is by the new verifier |
| D-4 | CI supply-chain gates (Trivy, CodeQL, SBOM/CycloneDX) | Must stay green; validate the new deps produce no HIGH/CRITICAL |
