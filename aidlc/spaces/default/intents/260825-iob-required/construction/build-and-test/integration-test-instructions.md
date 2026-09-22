# Integration Test Instructions — #1563 activeIob required

Consumes `../iob-required/code-generation/code-summary.md`,
`../iob-required/code-generation/code-generation-plan.md`.

## Backend integration + e2e (embedded, no external containers)

```bash
cd kdiab-calc && ./gradlew integrationTest e2eTest
```

- `CalcRoutesIntegrationTest` (Ktor embedded, `ProfilesClient` mocked): exercises the full HTTP pipeline
  — auth → access check → deserialization → `toDomain()` guard → service → response. New cases:
  omitted `activeIob` → **400** with the "activeIob is required" body; negative `activeIob` → **400**
  ("zero or positive"). Existing 200 / invalid-trend-400 / 401 / 403 cases retained (the 200-path body
  now includes `activeIob`).
- `CalcE2ETest` (Kotest, embedded `testApplication`): happy path sends `activeIob:1.0` (clean, no
  warnings); hypo path sends `activeIob:0.0`.

## Ordering

Integration tests `shouldRunAfter` unit tests; e2e `shouldRunAfter` integration — all covered by the
single `./gradlew check`.
