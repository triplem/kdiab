# Integration Test Instructions — Release Workflow Fix (#1617)

Consumes `../release-workflow-fix/code-generation/code-summary.md`,
`../release-workflow-fix/code-generation/code-generation-plan.md`.

## The real integration test = the pipeline itself

This change spans a producer/consumer boundary (CI upload → release download). The authoritative
integration test is the live GitHub Actions flow; it cannot run locally (it is a `main`-push workflow).

## Procedure (executed by CI / on delivery)

1. **On the PR** — each of the 8 backend CIs runs, calls `backend-ci-reusable.yml`, and uploads
   `<svc>-backend-{image,bom}`. Expected: uploads succeed with the short names.
2. **Post-merge on `main`** — the *Monorepo Release* / *Semantic Release* job's `download-artifact`
   steps resolve every `<svc>-backend-{image,bom}` (AC-1) with **no** `Artifact not found`, then
   `semantic-release` runs to completion (AC-2).

## Pass criteria

- No `Artifact not found` in the *Semantic Release* job (the current failure).
- The job completes (version/tag/changelog, or a documented no-op).
- Precondition: a green CI gate on `main` (AC-2) — an unrelated red check (e.g. `#1614`) is not a
  failure of this fix.
