# Code-Generation Plan — Guard test-mode JWT out of production

> Intent: `jwt-test-guard` · Finding: **FIND-SEC-001** (GitHub #1588) · Scope: security-patch (Minimal).
> Authoritative spec: `../nfr-requirements/security-requirements.md` (SR-1..SR-7, AC-1..AC-4) and
> `../nfr-requirements/tech-stack-decisions.md` (TD-1..TD-4). Design was decided and reviewer-approved
> upstream; this stage implements it verbatim — no redesign.

## Objective

Make the symmetric HMAC test-mode JWT verifier **deny-by-default**. `jwt.test=true` may only take
effect when the separate non-production affirmation `jwt.allowTestMode=true` (env `JWT_ALLOW_TEST_MODE`)
is present; otherwise the service must fail fast at startup and refuse to boot.

## Approach

Three coordinated edits, plus fixture propagation across every test that enables test-mode:

1. **The guard (one main-source file).** In `kdiab-common` `readJwtConfig()`, read a new
   `jwt.allowTestMode` flag (deny-by-default `?: false`) and add a `check(...)` that fires **before**
   the existing secret guard (SR-7 / TD-4). Folded inline — the `JwtConfig` data-class signature is
   left unchanged (TD-3 "fold the assertion inline" option) to keep blast radius minimal. The
   verifier-selection logic and the JWKS-HTTPS check are untouched.

2. **Fixture propagation (deny-by-default companion, ~36 sites).** Because the guard is deny-by-default,
   every existing legitimate test that sets `jwt.test=true` must now also affirm `jwt.allowTestMode=true`
   or it will fail to start. Two mechanisms:
   - **Kotlin `MapApplicationConfig` builders** — add `"jwt.allowTestMode" to "true"` immediately after
     each `"jwt.test" to "true"` line, preserving the site's leading indentation.
   - **HOCON test resources** — add `allowTestMode = true` to the `jwt { }` block next to `test = true`
     in `kdiab-measures` and `kdiab-carbs` `src/test/resources/application.conf`.

3. **Negative-path guard test (AC-1).** In `kdiab-profiles/.../SecurityConfigTest.kt`:
   - Update the existing positive-path test to also affirm `jwt.allowTestMode=true` (SR-4) so it stays green.
   - Update the existing secret-guard test (AC-4) to affirm `jwt.allowTestMode=true` — with the opt-in
     guard now firing first, the test must pass the opt-in guard to still reach and assert the secret
     guard message.
   - Add a NEW test (AC-1): `jwt.test=true` + secret present + NO `allowTestMode` ⇒ startup throws
     `IllegalStateException`, message mentions `jwt.allowTestMode` / `JWT_ALLOW_TEST_MODE`.

## Files touched (all app source at the workspace root; no `aidlc/` files)

| # | File | Change |
|---|---|---|
| 1 | `kdiab-common/.../plugins/Security.kt` | The guard (main source) |
| 2 | `kdiab-profiles/.../SecurityConfigTest.kt` | +1 new AC-1 test; +opt-in on positive & secret-guard tests |
| 3 | `kdiab-measures/src/test/resources/application.conf` | `allowTestMode = true` in `jwt {}` |
| 4 | `kdiab-carbs/src/test/resources/application.conf` | `allowTestMode = true` in `jwt {}` |
| 5 | 31 other Kotlin `*Test`/`*ApiTest`/`*E2ETest`/`Base*Test`/`*ContractTest`/parity files | `"jwt.allowTestMode" to "true"` after each `"jwt.test" to "true"` |

## Guard precedence (SR-7 / TD-4)

Opt-in guard first, then secret guard:
- `jwt.test=true`, no opt-in ⇒ opt-in `check` throws (AC-1) — regardless of secret.
- `jwt.test=true`, opt-in, no secret ⇒ secret `check` throws (AC-4).
- `jwt.test=true`, opt-in, secret ⇒ boots with HMAC verifier (AC-2).
- `jwt.test` unset/false ⇒ boots with JWKS verifier, no config change (AC-3, SR-5).

## Verification plan

- `cd kdiab-common && ./gradlew detektMain test` — guard compiles, Detekt-clean, parity test green.
- `cd kdiab-profiles && ./gradlew detektMain test` — SecurityConfigTest (AC-1/AC-2/AC-4) green.
- Full-platform verification deferred to the next AI-DLC stage (build-and-test).

## Constraints honoured

- No `git commit` / `branch` / `checkout` / `push`; all changes left uncommitted on `main`.
- No shipped MAIN `application.conf` touched (production correctly leaves `jwt.test` unset — SR-5).
- No `aidlc/` workflow-record files modified.
- Test-only HMAC secret not rotated/externalized (spec non-goal).
