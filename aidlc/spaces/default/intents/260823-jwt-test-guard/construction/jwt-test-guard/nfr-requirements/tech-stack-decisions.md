# Tech-Stack Decisions — Guard test-mode JWT out of production

> Scope: security-patch (brownfield). Per the reverse-engineering codekb `technology-stack.md`, the
> auth stack is **Ktor + Nimbus JOSE+JWT 10.0.1**, config via **HOCON `application.conf`** with
> `${?ENV_VAR}` overrides. **No new technology is introduced.**

## Decisions

| # | Decision | Rationale |
|---|---|---|
| **TD-1** | Implement the guard as a `check(...)`/`require(...)`-style assertion inside `kdiab-common` `readJwtConfig()`. | Reuses the exact established pattern already guarding `jwt.secret` and JWKS-HTTPS; centralizes in the shared library so all 8 services inherit it (DRY, per team `Code Style`). |
| **TD-2** | Represent the affirmation as a HOCON key `jwt.allowTestMode` bound to env `JWT_ALLOW_TEST_MODE`, read via `environment.config.propertyOrNull(...)?.getString()?.toBoolean() ?: false`. | Identical mechanism to the existing `jwt.test` read; deny-by-default (`?: false`); no new config library. |
| **TD-3** | Extend the `JwtConfig` data class with an `allowTestMode: Boolean` field (or fold the assertion inline before constructing it). | Keeps the parsed-config type honest and unit-testable via `MapApplicationConfig`, matching `SecurityConfigTest`'s existing approach. |
| **TD-4** | Order the two `check(...)` guards so the **opt-in guard fires before the secret guard** (SR-7). | Makes failure messages deterministic for AC-1 (opt-in missing) vs AC-4 (secret missing); deny-by-default should reject before reasoning about the secret. |

## Alternatives Rejected

| Option | Why rejected |
|---|---|
| **General `app.environment` / `KDIAB_ENV` concept** (default `production`) | More reusable, but introduces a platform-wide environment notion touching more than auth — scope-creep for a Minimal security-patch. Deferred; the dedicated single-purpose opt-in is more defensible for a security guard (user-confirmed 2026-08-23). |
| **Ktor `developmentMode`** | No new key, but couples the auth decision to a general dev flag and relies on unverified "tests run in development mode" behaviour — fragile. Rejected. |
| **Warn-and-continue** | Leaves the insecure HMAC verifier running; violates deny-by-default and SR-2. Rejected in favour of fail-fast (user-confirmed 2026-08-23). |

## Test & Quality Tooling (unchanged)

JUnit 5 + Ktor `testApplication` / `MapApplicationConfig` for the guard unit tests (following the
existing `kdiab-profiles/.../SecurityConfigTest.kt` precedent); Detekt + Kover (≥80% line) as the gate.
Test configs (`src/test/resources/application.conf`) and `MapApplicationConfig` test builders that set
`jwt.test=true` will also set `jwt.allowTestMode=true` — the correct, explicit test posture.
