# Requirements — U1 Release Workflow Artifact-Name Fix (#1617)

Source: issue #1617 (diagnosed bug) + codekb (`architecture.md` for the CI/CD pipeline set,
`code-structure.md` for the workflow layout, `business-overview.md` for platform context,
`code-quality-assessment.md`) + `team.md`/`project.md`. Upstream `intent-statement.md` /
`scope-document.md` / `practices-discovery/team-practices.md` are **N/A — skipped** in refactor scope
(no ideation/practices-discovery stage ran). Minimal depth.

## Problem

The **Monorepo Release** workflow (`release.yml`, job *Semantic Release*) fails on every push to `main`
at its artifact-download steps, so **semantic-release never runs** (no version bump / tag / changelog /
GitHub Release). Root cause: an artifact-name mismatch — CI uploads `kdiab-<service>-backend-{image,bom}`;
`release.yml` downloads the un-prefixed `<service>-backend-{image,bom}`. Image publishing to GHCR is
unaffected. Pre-existing (fails since ≥ 2026-08-18); not caused by #1606.

## Functional Requirements

- **FR-1 — Names align.** The artifact names produced by `backend-ci-reusable.yml` MUST match the
  names `release.yml` downloads, for all 8 services × 2 artifacts (image + SBOM). *(Decision Q1=B:
  achieve this by changing the **upload** side to the short un-prefixed name.)*
- **FR-2 — Release completes.** On a push to `main` (after the backend CIs pass), the *Semantic
  Release* job MUST download every expected artifact and run `semantic-release` to completion —
  producing a version bump + tag + `CHANGELOG.md` entry when commits warrant it, or a clean no-op when
  they do not — with **no** `Artifact not found` failure.
- **FR-3 — Derive the short name.** Because the upload `name:` derives from `${{ inputs.service }}`
  (the full `kdiab-<service>`), the fix MUST derive the short service name (strip the `kdiab-` prefix)
  and use it in the two upload `name:` fields.

## Target artifact names (authoritative — the correctness of the fix is string equality)

After the fix, `backend-ci-reusable.yml` must upload exactly these names (matching what `release.yml`
already downloads), for the **8 backend services** routed through the reusable workflow:

| Service | image artifact | SBOM artifact |
|---|---|---|
| measures | `measures-backend-image` | `measures-backend-bom` |
| profiles | `profiles-backend-image` | `profiles-backend-bom` |
| treatments | `treatments-backend-image` | `treatments-backend-bom` |
| calc | `calc-backend-image` | `calc-backend-bom` |
| carbs | `carbs-backend-image` | `carbs-backend-bom` |
| analyze | `analyze-backend-image` | `analyze-backend-bom` |
| nightscout | `nightscout-backend-image` | `nightscout-backend-bom` |
| users | `users-backend-image` | `users-backend-bom` |

Derivation: `${SERVICE#kdiab-}-backend-{image,bom}`. **`kdiab-ui` is out of scope** — it uploads
`kdiab-ui-{image,bom}` directly (not via the reusable workflow) and `release.yml` already downloads
those exact names, so it is already aligned.

## Non-Functional / Constraints

- **NFR-1 — Blast radius.** Change is confined to CI/CD workflow YAML. **No** service/application code,
  tests, build logic, or `release.yml` download names change. (`release.yml` already uses the short
  name — FR-1 makes uploads meet it.)
- **NFR-2 — No collateral rename.** The GHCR image **tags** (`${{ inputs.service }}-backend:latest`
  etc.) and the `docker-publish.yml` publish path MUST be unaffected — only the *workflow-artifact*
  upload `name:` changes, not image naming.
- **NFR-3 — Convention.** Follow the repo's Actions conventions (pinned action SHAs already present;
  do not unpin). Conventional Commit (`ci` or `fix`) referencing #1617.

## Acceptance Criteria (Given/When/Then)

- **AC-1** — *Given* a push to `main` that triggers the backend CIs, *when* they succeed and *Semantic
  Release* runs, *then* all `download-artifact` steps succeed (no `Artifact not found`).
- **AC-2** — *Given* the *Semantic Release* job (which `release.yml` gates on **all CI workflows green
  on `main`** before the download steps), *when* the gate is green and it runs, *then* `semantic-release`
  executes to completion (version/tag/changelog on a releasable commit, else a documented no-op).
  *Precondition:* AC-2 presupposes a green CI gate — an unrelated red check (e.g. the out-of-scope
  `#1614` flaky race) can still block the job and is not a failure of this fix.
- **AC-3** — *Given* the change, *when* the diff is reviewed, *then* only `backend-ci-reusable.yml`
  (CI workflow) is modified; no service code, no `release.yml` download-name change, no image-tag change.
- **AC-4 (pre-merge, locally verifiable)** — *Given* the branch before merge, *when* a reviewer checks
  it, *then*: (a) `actionlint .github/workflows/backend-ci-reusable.yml` passes (valid YAML/expressions),
  and (b) the two rendered upload `name:` values for each service equal the corresponding
  `release.yml` download names in the table above (literal string comparison, not re-derivation).

## Out of Scope

- Refreshing the jackson-stale `dependencies.md`/`technology-stack.md` codekb detail (#1606 delta).
- Any change to `docker-publish.yml` image publishing (already works).
- The `#1614` flaky `apiSpec` race (separate issue).
