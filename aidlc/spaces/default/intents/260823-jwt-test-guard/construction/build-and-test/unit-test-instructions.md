# Unit Test Instructions — jwt-test-guard (#1588)

## Central test — the guard itself
`kdiab-profiles/.../SecurityConfigTest.kt` (JUnit 5 + Ktor `testApplication` / `MapApplicationConfig`)
is the canonical guard test. Three cases map to the acceptance criteria:

| Test | AC | Given | Then |
|---|---|---|---|
| `application fails to start when jwt test mode is enabled without allow test mode opt-in` (NEW) | AC-1 | `jwt.test=true`, secret present, **no** `jwt.allowTestMode` | startup throws; message names `jwt.allowTestMode` / `JWT_ALLOW_TEST_MODE` |
| `application starts when jwt test mode has an explicit secret` (updated) | AC-2 | `jwt.test=true` + secret + `jwt.allowTestMode=true` | starts, HMAC verifier |
| `application fails to start when jwt test mode is enabled without explicit secret` (updated) | AC-4 | `jwt.test=true` + `jwt.allowTestMode=true`, **no** secret | startup throws the `jwt.secret` message (opt-in guard passes first, per SR-7) |

Run:
```bash
cd kdiab-profiles && ./gradlew test --tests "org.javafreedom.kdiab.profiles.SecurityConfigTest"
```

## Regression surface — the ~35 propagated fixtures
Every unit test that starts a service via `MapApplicationConfig(... "jwt.test" to "true" ...)` now also
sets `"jwt.allowTestMode" to "true"`. Running each module's `test` task proves the opt-in guard does not
break existing startup:
```bash
for m in kdiab-common kdiab-measures kdiab-profiles kdiab-treatments kdiab-analyze \
         kdiab-carbs kdiab-calc kdiab-nightscout kdiab-users; do
  ( cd "$m" && ./gradlew test --console=plain ) || echo "FAILED: $m"
done
```

## Coverage (Kover ≥80%)
The change adds a guard branch + a dedicated negative test (AC-1) exercising it, so the new code is
covered. Kover verification (`koverVerify`, 80% line floor) runs inside each module's `check`/CI.
```bash
cd kdiab-common && ./gradlew koverVerify
```
