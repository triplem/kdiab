# Quality Gates — U1 Jackson-free JWT (#1606)

The gates every #1606 PR must pass before merge (existing CI + team practice). Local pre-flight status
recorded from the build-and-test stage.

| Gate | Where | Threshold | #1606 local status |
|---|---|---|---|
| Unit + integration + e2e tests | `backend-ci-reusable.yml` `:check` | all green | ✅ 9/9 modules green |
| Code coverage | `:koverVerify` | ≥ 80% line (new/modified) | ✅ green all modules |
| Static analysis (Kotlin) | Detekt (+ SARIF) | 0 new violations | ✅ `Security.kt` clean; changed files clean |
| SAST | CodeQL (`codeql-backend.yml`) | no new alerts | ⏳ CI (security review PASS — no none-alg/injection/logging issues) |
| Quality/coverage | SonarCloud | project quality gate | ⏳ CI (Kover XML fed) |
| Container CVEs | Trivy `CRITICAL,HIGH` `exit-code 1` | 0 unignored | ⏳ CI (expected to **improve** — jackson/java-jwt/jwks-rsa gone) |
| SBOM | CycloneDX (`:cyclonedxBom`) | generated + uploaded | ✅ builds |
| Fat jar | `:buildFatJar` | builds | ✅ per module |
| Frontend (if touched) | `ci-kdiab-ui.yml` | n/a | — (no UI change in #1606) |
| Publish | `ci-common-publish.yml` / `docker-publish.yml` | green on `main` | ⏳ post-merge |

## Supply-chain acceptance (AC-1 / AC-8) — the #1606 gate that matters most

`dependencyInsight … --configuration runtimeClasspath` on all 9 modules → `jackson-databind`,
`jackson-core`, `com.auth0:java-jwt`, `com.auth0.jwk:jwks-rsa` **all absent**, no downgrade to the
CVE-vulnerable jackson 2.21.3. `handlebars` retained at 4.5.2. **VERIFIED.**

## Merge gates (team practice — maintainer-owned)

- **Every** GitHub Actions check green (never merge on a failing/pending check; no `--admin` bypass).
- **Merge-commit** (never squash) with `Closes #1606` to preserve issue linkage; Conventional Commit.
- **Manual security sign-off** on the Nimbus provider (ADR-023 — this stage's review is PASS; a human
  should countersign the safety-sensitive auth path).
- Delete both remote + local feature branch after merge.

## Notes

- `#1614` (flaky `apiSpec` race) may cause an intermittent analyze/nightscout `:check` failure in CI —
  a job re-run resolves it; not a #1606 regression.
- `#1615` (require `exp` presence) is a deferred hardening — not gating #1606.
