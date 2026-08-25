# Security Requirements — Guard test-mode JWT out of production

> Intent: `jwt-test-guard` · Finding: **FIND-SEC-001** (GitHub #1588) · Scope: security-patch (Minimal).
> Upstream note: `requirements-analysis`, `functional-design`, and `units-generation` are **skipped**
> in security-patch scope, so there is no `requirements.md` / `business-logic-model.md` /
> `business-rules.md`. For a security-patch the **finding is the requirement**. Grounded in the
> reverse-engineering codekb `technology-stack.md` (Auth = Nimbus JOSE+JWT 10.0.1, JWKS prod / HMAC
> test) and the current `kdiab-common/.../plugins/Security.kt` `readJwtConfig()`.

## Context & Threat

`readJwtConfig()` reads `jwt.test` (default `false`). When `true`, `configureSecurity()` installs the
symmetric **`HmacTokenVerifier`** (shared HMAC secret) instead of the asymmetric **`JwksTokenVerifier`**
(Keycloak JWKS). The existing guard `check(!isTest || secret != null)` only ensures a secret is *present*
when test-mode is on — it does **not** prevent test-mode from running in a production deployment.

**Threat:** if `jwt.test=true` is enabled in a deployed (production) environment — by config drift, a
leaked test profile, or a misconfigured env var — any party knowing (or brute-forcing) the shared HMAC
secret can **forge a valid token for any `userId` and any role (including ADMIN)**, fully bypassing the
Keycloak-backed identity model. This is platform-wide: `kdiab-common` is included by all 8 backends.
- **STRIDE:** Spoofing (forged identity), Elevation of Privilege (arbitrary roles).
- **OWASP:** A02 Cryptographic Failures (symmetric secret vs asymmetric JWKS), A05 Security
  Misconfiguration (unguarded test toggle), A07 Identification & Authentication Failures.
- Primary risk is **accidental enablement**; secondary is defense-in-depth against a single leaked flag.

## Requirements

| ID | Requirement | Verify |
|---|---|---|
| **SR-1** | Test-mode JWT verification (`jwt.test=true`) MUST be **deny-by-default**: it may be enabled only when an explicit, separate non-production affirmation `jwt.allowTestMode` (env `JWT_ALLOW_TEST_MODE`) is `true`. `jwt.allowTestMode` defaults to `false`. | Unit: `jwt.test=true` + no/false `allowTestMode` ⇒ startup throws. |
| **SR-2** | When the guard trips, the service MUST **fail fast at startup** (throw `IllegalStateException`) and refuse to boot — never start with the insecure verifier. | Unit: assert the thrown type + message; app does not start. |
| **SR-3** | The guard's error message MUST state the remediation (unset `jwt.test` / `JWT_TEST` for production, or set `JWT_ALLOW_TEST_MODE=true` only in non-production) and MUST NOT leak the secret value. | Review + unit assert message contains guidance, not the secret. |
| **SR-4** | The existing guards MUST be preserved: `jwt.test=true` still requires an explicit `jwt.secret` (unchanged), and the JWKS-HTTPS check for non-local prod endpoints is unchanged. **`SecurityConfigTest`'s positive-path test (`application starts when jwt test mode has an explicit secret`) MUST be updated to also affirm `jwt.allowTestMode=true`** — with that edit it stays green; without it, it breaks. | Updated `SecurityConfigTest` passes; secret + JWKS-HTTPS guards still enforced. |
| **SR-5** | **Production paths** MUST be unchanged: with `jwt.test` unset/false the service uses `JwksTokenVerifier` exactly as today, requiring **no main-config change** (verified: no shipped `application.conf`/compose/`.env` enables `jwt.test`). **Test paths are NOT unchanged** — every test-enablement site must affirm the opt-in (see Implementation surface). | Prod: no main-config diff. Test: suites green only after opt-in affirmation added. |
| **SR-6** | The change MUST be centralized in `kdiab-common` `readJwtConfig()` so all 8 services inherit the guard uniformly (DRY; no per-service copy). | Grep: guard logic exists once in `Security.kt`. |
| **SR-7** | Guard **precedence** MUST be defined: the deny-by-default opt-in guard (SR-1) fires **before** the existing secret guard. So `jwt.test=true` with no opt-in throws the opt-in message (AC-1) regardless of secret; `jwt.test=true` + opt-in + no secret throws the secret message (AC-4). This keeps message assertions deterministic. | Unit: AC-1 asserts opt-in message; AC-4 asserts secret message. |

## Non-goals

- Not introducing a general platform environment concept (`KDIAB_ENV`) — rejected as scope-creep for a
  security-patch (see `tech-stack-decisions.md`).
- Not rotating or externalizing the HMAC test secret (test-only credential; separate concern).
- Not changing token claims, TTL, audience/issuer validation, or the Nimbus verifier internals.

## Implementation surface (enumerated — de-risks code-generation)

The guard itself is **one addition** to `kdiab-common` `readJwtConfig()`. The deny-by-default posture
(SR-1) means **every legitimate test that enables `jwt.test=true` must also affirm `jwt.allowTestMode=true`**,
or it will fail to start. There are **two distinct edit mechanisms** totalling **~36 sites** (verified
2026-08-23; code-generation MUST re-grep authoritatively — `grep -rlE '"jwt\.test"\s*to\s*"true"|test\s*=\s*true'`):

- **Mechanism 1 — Kotlin test builders (~34)** using `MapApplicationConfig(... "jwt.test" to "true" ...)`
  across unit / integration-test / e2e-test for all 8 services + `kdiab-common`
  (each service's `ApplicationTest.kt`, `*RoutesTest.kt`, `*ApiTest.kt`, `*E2ETest.kt`, plus
  `kdiab-common/.../JwtAuthenticationParityTest.kt`, `kdiab-nightscout/.../BaseNightscoutTest.kt`,
  `kdiab-treatments/.../TreatmentsApiContractTest.kt`, and **`kdiab-profiles/.../SecurityConfigTest.kt`**
  — see SR-4). Add `"jwt.allowTestMode" to "true"` alongside the existing `"jwt.test" to "true"`.
- **Mechanism 2 — Test resource HOCON configs (2):** `kdiab-measures/src/test/resources/application.conf`
  and `kdiab-carbs/src/test/resources/application.conf` — add `allowTestMode = true` to the `jwt {}` block.

> **NOTE (effort reality):** the roadmap tagged FIND-SEC-001 `effort=S` ("one-line"). That holds for the
> *guard*, but the deny-by-default fixture propagation makes the change ~35 mechanical edits. It stays
> low-risk (CI fails loudly on any missed site) but is wider than "one file". Shipped **main** configs
> already default `jwt.test=false` (verified: no production `application.conf` enables it), so no
> production config change is required — the guard is purely defensive.

## Acceptance Criteria (Given/When/Then)

- **AC-1 (block):** *Given* `jwt.test=true` and `jwt.allowTestMode` unset/false, *when* the service
  starts, *then* it throws `IllegalStateException` and does not boot.
- **AC-2 (allow test):** *Given* `jwt.test=true`, `jwt.secret` set, and `jwt.allowTestMode=true`, *when*
  the service starts, *then* it boots with the HMAC verifier. (Test suites keep working **only after**
  every test-enablement site adds the opt-in — see Implementation surface; this is a required companion
  edit, not a no-op.)
- **AC-3 (prod default):** *Given* `jwt.test` unset, *when* the service starts, *then* it boots with the
  JWKS verifier and requires no new config.
- **AC-4 (secret still required):** *Given* `jwt.test=true`, `jwt.allowTestMode=true`, and no
  `jwt.secret`, *when* the service starts, *then* the existing secret guard still throws.
