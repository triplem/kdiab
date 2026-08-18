# Unit Test Instructions — logback-jsonencoder

Unit tests (JUnit 5 + MockK + H2) run with **no external infra** and, importantly, **initialize
Logback** on first logger use — so a malformed `logback.xml` fails them. This is the primary local
signal that the encoder swap is valid.

```bash
./gradlew check          # includes :test for every backend
# or per service:
(cd kdiab-measures && ./gradlew test)
```

## What the existing suite covers for this change

- **logback.xml validity** — any test that logs forces Logback to parse the config; a bad
  `JsonEncoder` element would throw on init. No new test needed for parse-validity.
- **No regression** — the change touches no `.kt`, so all existing unit tests must stay green
  unchanged (Kover ≥ 80% unaffected — no production code lines added/removed).

## New assertion for AC-3 (Correlation-ID), Q2 = C

Add/confirm a unit-level assertion that the MDC key `Correlation-ID` is present and surfaces under the
encoder's `mdc` object. Minimal approach: assert the `CallId`/MDC binding still populates
`Correlation-ID` (the binding code is unchanged, so an existing logging/MDC test, if present, already
covers it). Full runtime confirmation is deferred to CI/e2e per Q2 = C.

## Out of scope here

- Integration (`integrationTest`) and e2e (`e2eTest`) — need Postgres/Keycloak; run in CI. They are
  the deferred runtime confirmation path for the emitted-JSON shape and `mdc.Correlation-ID`.
