# Security Test Instructions — jwt-test-guard (#1588 / FIND-SEC-001)

This is a security patch, so the security tests ARE the acceptance tests. (devsecops lens.)

## Threat closed
Before: `jwt.test=true` in a deployed environment silently swapped the Keycloak JWKS verifier for a
symmetric HMAC verifier, letting anyone with the shared secret forge a token for any `userId`/role
(Spoofing + Elevation of Privilege; OWASP A02/A05/A07). After: test-mode is **deny-by-default** — it
cannot be enabled unless `jwt.allowTestMode=true` is *also* explicitly set, and any attempt fails the
service fast at startup.

## Test matrix (in `SecurityConfigTest`)

| # | jwt.test | jwt.allowTestMode | jwt.secret | Expected |
|---|---|---|---|---|
| AC-1 | true | (unset→false) | set | **throws** at startup; message names `jwt.allowTestMode`/`JWT_ALLOW_TEST_MODE` |
| AC-2 | true | true | set | starts (HMAC verifier) — the sanctioned test path |
| AC-3 | (unset→false) | — | — | starts (JWKS verifier) — production default, no new config |
| AC-4 | true | true | (unset) | **throws** the `jwt.secret` message (opt-in guard passes first, SR-7 precedence) |

Run:
```bash
cd kdiab-profiles && ./gradlew test --tests "org.javafreedom.kdiab.profiles.SecurityConfigTest"
```

## Negative assurance (defense-in-depth checks)
- **No production config enables test-mode:** confirm no shipped `application.conf`, `docker-compose*.yml`,
  or `.env` sets `jwt.test` / `JWT_TEST` / `jwt.allowTestMode` / `JWT_ALLOW_TEST_MODE`:
  ```bash
  grep -rniE "jwt\.test|JWT_TEST|allowTestMode|JWT_ALLOW_TEST_MODE" \
    $(find . -path '*/src/main/resources/*' -name '*.conf') docker-compose*.yml .env 2>/dev/null
  # expect: no active setting (comments only)
  ```
- **Secret never logged:** the guard message contains remediation text only, no secret value.
- **SAST/supply-chain:** CodeQL + Trivy + Semgrep run on the PR; this change adds no new dependency and
  no new attack surface (it *removes* one).

## Regression: every legitimate test affirms the opt-in
```bash
# every file enabling jwt.test=true must also carry allowTestMode (except the AC-1 guard-under-test line)
comm -23 <(grep -rlE '"jwt\.test"\s*to\s*"true"|test\s*=\s*true' $(find . -path '*/src/*' \( -name '*.kt' -o -name application.conf \) -not -path '*/build/*' -not -path '*/src/main/resources/*') | sort) \
         <(grep -rlE 'allowTestMode' $(find . -path '*/src/*' \( -name '*.kt' -o -name application.conf \) -not -path '*/build/*') | sort)
# expect: empty (all covered)
```
