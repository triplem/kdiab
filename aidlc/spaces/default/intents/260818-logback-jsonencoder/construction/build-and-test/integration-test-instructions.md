# Integration Test Instructions — logback-jsonencoder

Integration tests (JUnit 5, `src/integration-test/`, `shouldRunAfter test`) need Postgres/Keycloak
and run in **CI**, not in this local stage.

```bash
./gradlew integrationTest    # per service; requires external Postgres + Keycloak
```

## Relevance to this change

- Integration tests boot the Ktor application, which loads `logback.xml` and emits real JSON log
  lines — the strongest confirmation that each service **starts** with the native `JsonEncoder` and
  that `mdc.Correlation-ID` appears on a correlated request (the deferred half of AC-3, Q2 = C).
- No integration test asserts on the *old* log field names (`message`/`logger`/`timestamp`); if one
  does, update it to the native keys (`formattedMessage`/`loggerName`/`timestamp` epoch-millis). Grep
  before merge: `grep -rnE '"message"|JsonLayout|JacksonJsonFormatter' */src/*-test` should be empty.

No new integration test is required for a config-only encoder swap; the existing boot-path coverage
is sufficient once green in CI.
