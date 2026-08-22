# Functional Design — Release Workflow Artifact-Name Fix (#1617)

Consumes `../../../inception/requirements-analysis/requirements.md`. (`unit-of-work.md`,
`unit-of-work-story-map.md`, and the `application-design/*` artifacts are **N/A — stages 2.6/2.7
skipped** in refactor scope.)

This is a **CI/CD configuration change**, not application logic. The "business logic model" here is the
GitHub Actions artifact-flow mechanic being corrected.

## The mechanic being fixed

```
backend-ci-reusable.yml (per service, service = kdiab-<svc>)
  Build fat JAR + Docker image  ->  upload-artifact name: ${{ inputs.service }}-backend-image
  CycloneDX SBOM                ->  upload-artifact name: ${{ inputs.service }}-backend-bom
        |                                         (= kdiab-<svc>-backend-{image,bom}  ← the bug)
        v
release.yml  Semantic Release
  download-artifact name: <svc>-backend-image / <svc>-backend-bom   ← expects the SHORT name
  => "Artifact not found" => job fails => semantic-release never runs
```
<!-- Text fallback: CI uploads kdiab-<svc>-backend-{image,bom}; release downloads <svc>-backend-{image,bom}; names don't match; download fails. -->

## The corrected design

Introduce a **derived short service name** in `backend-ci-reusable.yml` and use it for the two upload
`name:` fields, so uploads become `<svc>-backend-{image,bom}` — matching the download side.

1. **Derive step** (before the upload steps), shell prefix-strip:
   - input: `SERVICE = ${{ inputs.service }}` (e.g. `kdiab-measures`)
   - output: `short = ${SERVICE#kdiab-}` (e.g. `measures`) → `$GITHUB_OUTPUT`
2. **Upload image**: `name: ${{ steps.<id>.outputs.short }}-backend-image`
3. **Upload SBOM**: `name: ${{ steps.<id>.outputs.short }}-backend-bom`

Nothing else changes. The image **tags** at `backend-ci-reusable.yml:92`
(`${{ inputs.service }}-backend:latest`) keep using the full `inputs.service` — the derived `short` is
a *separate* value used only for artifact names (NFR-2).

## Data flow (post-fix)

`inputs.service (kdiab-measures)` → derive → `short (measures)` → upload `measures-backend-{image,bom}`
→ `release.yml` download `measures-backend-{image,bom}` → **match** → semantic-release runs.
