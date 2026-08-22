# Units of Work — Jackson-free JWT Verification (#1606)

Traces to the application design (`../application-design/components.md`, `component-methods.md`,
`services.md`, `component-dependency.md`, `decisions.md`), `../requirements-analysis/requirements.md`,
and `../user-stories/stories.md`. Architect lead with delivery-agent sequencing.

## Decision: one Unit of Work

This change is a **single cohesive, atomically-shippable unit**. The stories are tightly coupled —
US-2 (drop `ktor-server-auth-jwt`, add Nimbus) does not compile without US-3 (the Nimbus verifier) and
US-4 (test-minting migration); the whole set lands in one PR (`Closes #1606`, merge-commit) per the
delivery decision and team practice. Splitting into separately-shippable units is not possible without
leaving `main` uncompilable. Therefore: **one Unit of Work (U1)** with an internal task DAG.

## U1 — Jackson-free JWT verification (kdiab-common + per-service test minters)

| Field | Value |
|---|---|
| **Scope** | Replace the java-jwt/ktor-server-auth-jwt JWT path with a Nimbus-backed custom `AuthenticationProvider` in `kdiab-common`; migrate every service's test token minter to Nimbus; drop the two jackson force-pin lines (gated on sweep); ADR-023. |
| **Stories** | US-1..US-8 (all) |
| **Components** | `JwtConfig`, `TokenVerifier` (+ `JwksTokenVerifier`, `HmacTokenVerifier`), `ClaimsToPrincipalMapper`, `JwtAuthenticationProvider`, `configureSecurity()`, `TestTokenMinter` |
| **Modules touched** | `gradle/libs.versions.toml`, `build-logic/…/kdiab.kotlin-base.gradle.kts`, `kdiab-common/**` (main + test), each service's `src/{test,integration-test,e2e-test}` token minters, `docs/adr/ADR-023-*.adoc` |
| **Walking skeleton?** | No — incremental change on an established platform (team practice: skeleton skipped) |
| **Delivery** | One Bolt, one atomic PR |

### Internal task sequence (risk-first)

1. **T1 (US-1)** — characterization/parity tests pinning current behaviour (full negative-path matrix) against the OLD `configureSecurity()`; green first.
2. **T2 (US-2)** — `libs.versions.toml`: drop `ktor-server-auth-jwt` from the bundle, add `nimbus-jose-jwt`.
3. **T3 (US-3)** — implement `JwtConfig`, `TokenVerifier` adapters (shared `DefaultJWTClaimsVerifier`), exception-guarded `ClaimsToPrincipalMapper`, `JwtAuthenticationProvider` (enriched `TOKEN_REJECTED` challenge), rewrite `configureSecurity()`. Make T1's matrix green.
4. **T4 (US-4)** — migrate each service's `JWT.create()` test minter to `TestTokenMinter` (Nimbus `MACSigner`); all suites compile+green.
5. **T5 (US-5)** — realm/config: confirm no `jwt.*`/realm change needed (design says none); doc note only if it turns out otherwise.
6. **T6 (US-6)** — platform-wide `dependencyInsight` sweep (8 services + kdiab-common, incl. `ktor-server-swagger`); remove the **two** jackson pins (keep handlebars); capture proof.
7. **T7 (US-7)** — author `docs/adr/ADR-023-jackson-free-jwt-verification.adoc` from `decisions.md`.
8. **T8 (US-8)** — release gate: security review + whole-platform CI green (9 Gradle modules + kdiab-ui, Kover ≥80%) before merge.

## Rationale (delivery-agent)

A single unit maximises value-per-PR here: the change is indivisible at the compile boundary, the blast
radius is one shared file (+ mechanical test-minter edits), and one atomic merge keeps `main` green and
gives a clean revert. The internal task DAG (T1→T8) preserves the risk-first ordering from
scope-definition without fragmenting delivery.
