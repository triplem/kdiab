# Code Summary — Guard test-mode JWT out of production

> Intent: `jwt-test-guard` · Finding: **FIND-SEC-001** (GitHub #1588) · Scope: security-patch (Minimal).
> Implements `../nfr-requirements/security-requirements.md` (SR-1..SR-7, AC-1..AC-4).
> All changes are UNCOMMITTED in the working tree on `main` (deployment-execution will branch + PR).

## 1. The guard (Change 1)

`kdiab-common/src/main/kotlin/org/javafreedom/kdiab/common/plugins/Security.kt`, function
`readJwtConfig()` — the opt-in guard was folded inline **before** the existing secret guard (SR-7 / TD-4).
The `JwtConfig` data-class signature is unchanged (minimal blast radius).

```diff
     val isTest = environment.config.propertyOrNull("jwt.test")?.getString()?.toBoolean() ?: false
+    val allowTestMode = environment.config.propertyOrNull("jwt.allowTestMode")?.getString()?.toBoolean() ?: false
+    check(!isTest || allowTestMode) {
+        "jwt.test=true is not permitted unless jwt.allowTestMode=true (env JWT_ALLOW_TEST_MODE). " +
+            "The symmetric HMAC test verifier must never run in production; for production leave " +
+            "JWT_TEST unset/false, and set JWT_ALLOW_TEST_MODE=true only in non-production/test environments."
+    }
     val secret = environment.config.propertyOrNull("jwt.secret")?.getString()
     check(!isTest || secret != null) {
         "jwt.secret (JWT_SECRET env var) must be set explicitly when jwt.test=true. " +
             "Do not use the test JWT mode in production."
     }
```

Verifier-selection logic and the JWKS-HTTPS check are untouched (SR-4). The message states the
remediation and does not leak the secret (SR-3).

## 2. Fixture propagation (Change 2)

Deny-by-default means every test that sets `jwt.test=true` must also affirm `jwt.allowTestMode=true`.

### Occurrence accounting (authoritative re-grep, verified post-edit)

| Metric | Count |
|---|---|
| `"jwt.test" to "true"` in Kotlin test sources (before) | 35 (across 32 files; 2 files had 2 each) |
| `"jwt.test" to "true"` in Kotlin test sources (after) | 36 (35 original + 1 new AC-1 test) |
| `"jwt.allowTestMode" to "true"` in Kotlin test sources (after) | 35 (every `jwt.test` site EXCEPT the new AC-1 negative test) |
| HOCON `test = true` resource configs updated | 2 (`kdiab-measures`, `kdiab-carbs`) |

### Mechanism 1 — Kotlin `MapApplicationConfig` builders (34 sites across 32 files)

Inserted `"jwt.allowTestMode" to "true"` immediately after each `"jwt.test" to "true"` line, preserving
each site's leading indentation. `SecurityConfigTest.kt` (2 of its 3 `jwt.test` sites) was edited by hand
(see §3); the other 33 inserts across 31 files were applied by a Python pass that matches the leading
whitespace and re-uses the trailing comma. Files edited by the mechanical pass (33 inserts, 31 files):

1. kdiab-analyze/src/e2e-test/.../e2e/AnalyzeE2ETest.kt
2. kdiab-analyze/src/integration-test/.../AnalyzeAnalyticsIntegrationTest.kt
3. kdiab-analyze/src/integration-test/.../AnalyzeTimelineIntegrationTest.kt
4. kdiab-analyze/src/test/.../ApplicationTest.kt
5. kdiab-analyze/src/test/.../web/AnalyzeRoutesTest.kt
6. kdiab-calc/src/e2e-test/.../e2e/CalcE2ETest.kt
7. kdiab-calc/src/integration-test/.../CalcRoutesIntegrationTest.kt
8. kdiab-carbs/src/e2e-test/.../e2e/FoodEntryE2ETest.kt
9. kdiab-carbs/src/integration-test/.../FoodEntryApiTest.kt
10. kdiab-carbs/src/test/.../ApplicationTest.kt
11. kdiab-carbs/src/test/.../web/FoodEntryRoutesTest.kt
12. kdiab-common/src/test/.../plugins/JwtAuthenticationParityTest.kt
13. kdiab-measures/src/e2e-test/.../e2e/MeasureE2ETest.kt
14. kdiab-measures/src/test/.../ApplicationTest.kt
15. kdiab-measures/src/test/.../web/MeasureRoutesTest.kt  (2 inserts — 2 sites)
16. kdiab-nightscout/src/integration-test/.../BaseNightscoutTest.kt
17. kdiab-nightscout/src/test/.../web/NightscoutV3RoutesTest.kt
18. kdiab-profiles/src/e2e-test/.../e2e/InsulinE2ETest.kt
19. kdiab-profiles/src/e2e-test/.../e2e/ProfileE2ETest.kt
20. kdiab-profiles/src/integration-test/.../InsulinApiTest.kt
21. kdiab-profiles/src/integration-test/.../ProfileApiTest.kt
22. kdiab-profiles/src/test/.../ApplicationTest.kt
23. kdiab-treatments/src/e2e-test/.../e2e/TreatmentE2ETest.kt
24. kdiab-treatments/src/integration-test/.../contract/TreatmentsApiContractTest.kt
25. kdiab-treatments/src/test/.../ApplicationTest.kt
26. kdiab-treatments/src/test/.../web/DeviceAgeRoutesTest.kt
27. kdiab-treatments/src/test/.../web/DeviceStatusRoutesTest.kt
28. kdiab-treatments/src/test/.../web/TreatmentRoutesTest.kt
29. kdiab-users/src/e2e-test/.../e2e/UserSettingsE2ETest.kt
30. kdiab-users/src/integration-test/.../web/UserSettingsApiTest.kt
31. kdiab-users/src/test/.../web/InternalRoutesTest.kt
32. kdiab-users/src/test/.../web/UserRoutesTest.kt

Plus `SecurityConfigTest.kt` (§3) — its positive-path and secret-guard tests received the opt-in by hand.

### Mechanism 2 — HOCON test resources (2 sites)

`allowTestMode = true` added to the `jwt { }` block next to `test = true` in:
- `kdiab-measures/src/test/resources/application.conf`
- `kdiab-carbs/src/test/resources/application.conf`

## 3. Guard test (Change 3) — `kdiab-profiles/src/test/kotlin/.../SecurityConfigTest.kt`

Three coordinated edits:
- **Positive-path test** (`application starts when jwt test mode has an explicit secret`) — added
  `"jwt.allowTestMode" to "true"` so it keeps starting (SR-4 / AC-2).
- **Secret-guard test** (`application fails to start when jwt test mode is enabled without explicit
  secret`, AC-4) — added `"jwt.allowTestMode" to "true"`. Because the opt-in guard now fires first, the
  test must pass the opt-in guard to reach and assert the secret-guard message.
- **NEW negative-path test** (`application fails to start when jwt test mode is enabled without allow
  test mode opt-in`, AC-1) — `jwt.test=true` + secret present + NO `allowTestMode` ⇒ asserts
  `IllegalStateException` whose message mentions `jwt.allowTestMode` / `JWT_ALLOW_TEST_MODE`. Follows the
  exact style of the existing secret-guard test.

## 4. Verification results

### `kdiab-common` — `./gradlew detektMain test`

- **`detektMain`: FAILED (21 weighted issues) — PRE-EXISTING, NOT caused by this change.** All 21
  findings are `UnreachableCode` in `AuditRoutes.kt`, `RateLimit.kt`, and `Tracing.kt` — files this
  change never touches. Proven by stashing the `Security.kt` edit and re-running `detektMain` on the
  clean tree: identical "Analysis failed with 21 weighted issues" / BUILD FAILED. **`Security.kt` (the
  changed file) has ZERO Detekt findings** — the new `check {}` matches the file's existing guard style.
- **`test`: BUILD SUCCESSFUL.** The guard compiles; all `kdiab-common` tests pass, including
  `JwtAuthenticationParityTest` with the added opt-in.

### `kdiab-profiles` — `./gradlew detektMain test`

- **`detektMain`: FAILED (2 weighted issues) — PRE-EXISTING, NOT caused by this change.** Both findings
  are in main source this change never touches: `Application.kt:56` (`InjectDispatcher`) and
  `ProfileMapper.kt:33` (`UseOrEmpty`). Proven by stashing all changes and re-running `detektMain`:
  identical "Analysis failed with 2 weighted issues" / BUILD FAILED. `git status` confirms no
  `kdiab-profiles/src/main` file was modified — only test sources.
- **`test`: BUILD SUCCESSFUL.** `SecurityConfigTest` (AC-1 new negative, AC-2 positive, AC-4 secret
  guard) and the mechanically-edited `ApplicationTest` all pass.

> Note on Detekt: the pre-existing `detektMain` baseline failures are an environment/Detekt-version
> condition on `main` (the `UnreachableCode` class is the exact known false-positive documented in the
> user's global guidance). They are out of scope for this security-patch and unrelated to the guard.
> Full-platform verification (all 9 modules + kdiab-ui, Kover ≥80%) is the next AI-DLC stage
> (build-and-test), which will surface any module-level compile/coverage gaps across the ~36 edited sites.

## 5. Constraints honoured

- No `git commit` / `branch` / `checkout` / `push` — all changes left uncommitted on `main`.
- No shipped MAIN `application.conf` / compose / `.env` modified (production leaves `jwt.test` unset — SR-5/AC-3).
- No `aidlc/` workflow-record files modified (except these two code-generation record artifacts).
- The test-only HMAC secret was not rotated/externalized (spec non-goal). The secret-detection hook
  flagged the pre-existing `secret = "..."` line in the two HOCON files; it is a test-only fixture that
  predates this change (my edit only added `allowTestMode = true`) and is explicitly out of scope.

## 6. Traceability (requirement → change)

| Req | Satisfied by |
|---|---|
| SR-1 / AC-1 | `check(!isTest || allowTestMode)` + new negative-path test |
| SR-2 | `check {}` throws `IllegalStateException` at startup (fail-fast) |
| SR-3 | Message states remediation, no secret leaked |
| SR-4 / AC-4 | Secret + JWKS-HTTPS guards preserved; positive & secret-guard tests updated |
| SR-5 / AC-3 | No main-config change; JWKS path untouched |
| SR-6 | Single guard in `kdiab-common` `readJwtConfig()` (DRY) |
| SR-7 / TD-4 | Opt-in guard ordered before secret guard |
| AC-2 | Positive-path test + all fixture sites affirm opt-in |
