# Phase Boundary Verification — Construction → Operation (#1606)

Governance check at the end of Construction. Confirms Architecture → Code → Tests alignment and
acceptance-criteria coverage before the workflow enters Operation.

## 1. Design → Code traceability

| Design decision | Code realisation | Status |
|---|---|---|
| ADR-023 — Nimbus custom Ktor provider | `kdiab-common/.../Security.kt` (`JwksTokenVerifier`/`HmacTokenVerifier`, shared `DefaultJWTClaimsVerifier`, `mapToPrincipal`, `JwtAuthenticationProvider`, `configureSecurity`) | ✅ |
| ADR-023 — `authenticate("auth-jwt")` wiring + `UserPrincipal` unchanged | route handlers unchanged; `UserPrincipal` shape preserved; dead `io.ktor.server.auth.jwt.*` import removed from `ProfileRoutes.kt` | ✅ |
| ADR-023a (Q1=A) — per-service Nimbus test minter | 25 service test files migrated to `SignedJWT`+`MACSigner`; `nimbus` added to `kdiab.ktor-service` test scope | ✅ |
| ADR-023b — enriched `TOKEN_REJECTED`, 401 contract preserved | `rejectWith` logs `reason/path/method/remote/correlationId`; generic 401 `ErrorResponse` | ✅ |
| ADR-023c — jackson force-pin removed (jackson-only; handlebars kept) | 2 jackson lines removed from `kdiab.kotlin-base`; handlebars pin retained | ✅ |

## 2. Requirements/AC → Verification traceability

| AC | Verification | Status |
|---|---|---|
| AC-1.1/1.2, AC-8.1 (jackson + auth0 off runtime; swagger clean) | `dependencyInsight … runtimeClasspath` × 9 modules → all absent, no downgrade | ✅ |
| AC-2/3/4/5/6, NFR-2 (full negative-path parity, incl. HMAC issuer/audience + non-array roles → 401) | `JwtAuthenticationParityTest` (11 cases) + per-service route auth tests | ✅ green |
| AC-7.1 (realm/config) | no `jwt.*` / `keycloak-realm.json` change needed (JWKS-only prod) | ✅ verified |
| AC-10.1/10.2 (all suites compile+green; java-jwt off runtime) | 9/9 `./gradlew check` green; java-jwt absent from runtimeClasspath | ✅ |
| NFR-1/9 (ADR + security review) | ADR-023 authored; devsecops review PASS (1 non-blocking note) | ✅ |
| NFR-3 (DRY — verifier once in kdiab-common) | single `Security.kt`; all 8 services inherit | ✅ |
| NFR-6 (deliverability — atomic PR, all CI green) | ⏳ maintainer: merge-commit `Closes #1606`, all GH Actions green | pending merge |

## 3. Architecture → Code → Tests alignment

- **No orphan code:** every changed file maps to an ADR decision or a mechanical consequence (secret
  ≥32-byte lengthening, multi-audience `listOf`, dead-import removal).
- **No design without tests:** the auth decision (Security.kt) is covered by the parity matrix + every
  service's route/integration/e2e suites; the supply-chain decision by the dependency sweep.
- **Coverage:** Kover ≥80% enforced and green on all 9 modules.

## 4. Deferred / out-of-scope (tracked)

- #1614 — flaky composite-build `apiSpec` generation race (CI flakiness for analyze/nightscout).
- #1615 — optional `exp`-presence hardening (stricter than java-jwt parity).

## Verdict

**Construction is complete and internally consistent for #1606.** Code traces to design, all
acceptance criteria are verified or explicitly maintainer-pending (merge/CI/security sign-off), and the
two deferred items are tracked as separate issues. Ready to enter Operation.
