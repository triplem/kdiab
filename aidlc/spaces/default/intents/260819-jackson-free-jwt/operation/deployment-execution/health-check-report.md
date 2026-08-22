# Health Check Report — U1 Jackson-free JWT (#1606)

Consumes `../deployment-pipeline/cd-config.md`, `../deployment-pipeline/deployment-strategy.md`,
`../environment-provisioning/environment-inventory.md`,
`../../construction/build-and-test/build-test-results.md`.

## No live health check (N/A — no running services)

There are no running services to health-check (`environment-inventory.md` — no running prod; deployment
not executed per `deployment-log.md`). A conventional post-deploy health probe (`/health` 200, ready
replicas, error-rate baseline) has no target. This report substitutes the **gate-status-as-health**
signal: for a publish-only pipeline, the health of the change is the state of its quality gates.

## Gate-status health (pre-flight / current)

From `build-test-results.md` and `../deployment-pipeline/cd-config.md`:

| Health signal | Meaning | Current status |
|---|---|---|
| `./gradlew check` (9 modules) | tests + Detekt + Kover ≥80% | ✅ green (local pre-flight) |
| Supply-chain goal | jackson/java-jwt/jwks-rsa absent from runtimeClasspath; no jackson 2.21.3 downgrade | ✅ verified (AC-1/AC-8) |
| CodeQL (new `Security.kt` auth wiring) | no new SAST alerts | ⏳ runs on push (security review PASS) |
| Trivy CRITICAL/HIGH | container CVE surface | ⏳ runs on push — expected to **improve** |
| SonarCloud | project quality gate | ⏳ runs on push |
| ADR-023 manual security sign-off | human countersign on auth path | ⏳ pending (pre-merge gate) |
| Branch pushed / PR open / merged | pipeline progress | ❌ not pushed · ❌ no PR · ❌ not merged |

The ⏳ rows fire automatically once the branch is pushed / PR opened; the ❌ rows are the maintainer
steps in `deployment-log.md`. No health signal is red — the change is gate-healthy pre-flight; it is
simply **not yet executed**.

## Post-publish health (deferred, when applicable)

If/when a running environment is introduced (`cd-config.md` forward hooks), the health check becomes
the deferred auth accept/reject smoke (`../deployment-pipeline/rollback-runbook.md` § "Smoke test
(deferred)") plus the observability signals defined in the next stage (observability-setup, 4.4):
auth `401`/`403`/`5xx` rates on the verification path. Those are specified there, not executed here.

## Result

**No live health check (N/A).** Gate-status health is GREEN pre-flight (local `:check`, coverage,
supply-chain) with CI/CodeQL/Trivy/Sonar + the manual security sign-off pending on push/merge. No
red signal; deployment simply not yet executed.
