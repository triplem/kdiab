# Constraint Register — Jackson-free JWT Verification (#1606)

Constraints governing the implementation. Traces to `../intent-capture/intent-statement.md`, the
build-vs-buy analysis `../market-research/build-vs-buy.md`, the library landscape
`../market-research/competitive-analysis.md` (which framed TC-7's nimbus-vs-custom shortlist), and
the ecosystem scan `../market-research/market-trends.md` (jackson CVE fatigue / dep-minimization,
which motivate TC-1 and TC-8). Compliance-agent perspective included.

## Technical Constraints

| ID | Constraint | Source |
|---|---|---|
| TC-1 | Must remain **jackson-free** on `runtimeClasspath` — the DoD. No replacement may reintroduce jackson (directly or transitively). | intent-statement DoD; #1603 |
| TC-2 | The verification path is shared: **one** implementation in `kdiab-common/plugins/Security.kt` serves all 8 backends. No per-service divergence. | code reality (`configureSecurity()`) |
| TC-3 | Both signing paths preserved: prod **RS256 via JWKS** (Keycloak) and test-mode **HMAC256** (`jwt.test=true`). | intent-capture Q4 (A,B) |
| TC-4 | `UserPrincipal` extraction preserved exactly — `sub`→userId, `roles`, `allowed_patients`, `timezone`, `audience`, including reject rules (no roles ⇒ reject, bad UUID ⇒ reject, audience-mismatch ⇒ reject). | Security.kt `buildPrincipal`; issue text |
| TC-5 | JWKS hardening preserved — cache (size/TTL), rate-limit, `acceptLeeway`, and the HTTPS-required-for-non-local check. | Security.kt |
| TC-6 | Must integrate with Ktor's `authentication { }` / `Authentication` plugin (custom `AuthenticationProvider`) — the `auth-jwt` provider name and route-level `authenticate("auth-jwt")` wiring stay stable so no service route code changes. | Ktor auth model |
| TC-7 | Chosen library: **nimbus-jose-jwt** (locked at feasibility gate). Custom verifier is the rejected alternative. | feasibility Q1=A |
| TC-8 | Existing jackson **force-pin** constraint in `kdiab.kotlin-base` must not be removed unless a full per-service sweep proves jackson dead everywhere; never drop a force-pin without a runtimeClasspath check. | project rule (2026-08-19) |

## Organizational Constraints

| ID | Constraint | Source |
|---|---|---|
| OC-1 | Trunk-based: feature branch `<type>/1606-<desc>`, one PR, **merge-commit not squash** (preserves `Closes #1606`). | team rules |
| OC-2 | All changes trace to #1606 (`Closes #1606`) and follow Conventional Commits (`feat(auth): …`). | team rules |
| OC-3 | No hard deadline / no change-freeze (feasibility Q3=A). Proceed at team pace. | feasibility Q3 |
| OC-4 | Solo maintainer — the mandated security review benefits from an external clinical/security advisor for sign-off where possible (advisory). | project incident-response learning |

## Regulatory / Compliance Constraints (compliance-agent)

| ID | Constraint | Source |
|---|---|---|
| RC-1 | **No new regulatory control** applies (feasibility Q2=A). kdiab is a T1D self-management platform, not a certified medical device; auth is Keycloak/OIDC; no PCI/card data. | feasibility Q2 |
| RC-2 | OWASP A07 (Identification & Authentication) — the change must **not weaken** authentication: signature verification, issuer, audience, and expiry (with leeway) checks must all be preserved. Security review (Q6-B) is the gating control. | security rules |
| RC-3 | OWASP A09 (Security Logging) — preserve the `security_event=TOKEN_REJECTED` structured auth-failure log line; **never log tokens, secrets, or raw PII**. | logging + security rules |
| RC-4 | No forced end-user re-login and no token-format change — token issuance stays entirely with Keycloak; this change touches only verification. | intent-statement |
