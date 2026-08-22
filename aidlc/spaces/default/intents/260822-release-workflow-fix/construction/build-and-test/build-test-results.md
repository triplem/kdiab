# Build & Test Results — Release Workflow Fix (#1617)

Consumes `../release-workflow-fix/code-generation/code-summary.md`,
`../release-workflow-fix/code-generation/code-generation-plan.md`.

## Static verification (executed 2026-08-22)

### YAML + structure (yq)
```
step: Derive short service name  | id: svc | run: echo "short=${SERVICE#kdiab-}" >> "$GITHUB_OUTPUT"
step: Upload Docker Image        | artifact_name: ${{ steps.svc.outputs.short }}-backend-image
step: Upload SBOM                | artifact_name: ${{ steps.svc.outputs.short }}-backend-bom
```
YAML: **valid** (`yaml.safe_load`, job `build`).

### Name-equality (AC-4b) — producer now == consumer
For `svc ∈ {measures, profiles, treatments, calc, carbs, analyze, nightscout, users}`:
`${SERVICE#kdiab-}` = `svc` ⇒ upload `svc-backend-{image,bom}`. Each of the 16 names has exactly **1**
matching `download-artifact name:` in `release.yml`. **16/16 MATCH.**

### Non-regression checks
- Image tags `${{ inputs.service }}-backend:latest` / `:${{ env.VERSION }}` — **unchanged**.
- Upload `path:` fields (`/tmp/backend-image.tar`, `…/bom.json`) — **unchanged** ⇒ the release
  rename step (contents-based) is unaffected.
- `git diff --stat`: only `.github/workflows/backend-ci-reusable.yml` (+ AI-DLC record). No service
  code, no other workflow.

## Test-type coverage (per test strategy = Minimal, refactor)

| Type | Status |
|---|---|
| "Unit" (the derive logic) | ✅ via 16-name equality (the prefix-strip output is the unit under test) |
| Integration / e2e | ⏳ deferred — CI-on-PR + post-merge release run (AC-1/AC-2) |
| Performance | N/A — a CI artifact-name change has no runtime/perf dimension |
| Security | ✅ see `security-test-instructions.md` (env-passed input, no injection surface, no new secret/permission) |
| `actionlint` (AC-4a) | ⏳ CI (not installed locally) |

## Result

**PASS (static).** Ready for delivery; the end-to-end release-run proof lands when the PR CI runs and,
post-merge, the *Semantic Release* job executes.
