# NFR Requirements — Questions (FIND-SEC-001 / #1588)

Intent: Guard the test-mode HMAC JWT toggle out of production.

Only one NFR area needs a decision; the rest (performance, scalability, reliability) are
near-N/A for a startup-time config guard and are addressed with defaults in the artifacts.

## Q1 — Production-context signal for the guard

`readJwtConfig()` selects the HMAC test verifier when `jwt.test=true`. Today the only guard is
`check(!isTest || secret != null)` — it does **not** prevent test-mode running in production. There is
**no existing environment/production signal** in config, so the guard needs one that **defaults to the
safe (production) value** and is set to the non-production value only in test.

How should the guard recognise that test-mode must be refused?

- **A. Dedicated deny-by-default opt-in** — new `jwt.allowTestMode` (env `JWT_ALLOW_TEST_MODE`),
  default `false`. `jwt.test=true` fails fast at startup unless `jwt.allowTestMode=true`. Test configs
  set it; production never does. Single-purpose, self-documenting, smallest surface. *(recommended)*
- **B. General environment concept** — new `app.environment` (env `KDIAB_ENV`), default `production`.
  `jwt.test=true` fails fast when `environment=production`. Reusable platform-wide but broader than a
  security-patch warrants.
- **C. Ktor `developmentMode`** — allow `jwt.test=true` only when Ktor `developmentMode=true`. No new
  config key, but couples auth to a general dev flag and relies on tests running in development mode
  (needs verification).
- **X. Other** — describe.

[Answer]: A

## Q2 — Failure behaviour when the guard trips

Confirm fail-fast (throw at startup so the service will not boot), consistent with the existing
`check(...)` guards in `readJwtConfig`.

- **A. Fail fast at startup** — throw an `IllegalStateException` with a remediation message; the
  service refuses to start. *(recommended — matches existing guard style)*
- **B. Log a warning and continue** — start anyway but emit a loud error/warn log.
- **X. Other** — describe.

[Answer]: A
