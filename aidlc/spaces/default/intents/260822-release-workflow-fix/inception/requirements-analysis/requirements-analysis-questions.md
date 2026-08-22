# Requirements Analysis — Clarifying Questions (#1617 release-workflow-fix)

Inception phase · Minimal depth (refactor scope) · lead aidlc-product-agent.

Requirements are sourced from the diagnosed bug in issue #1617 (ideation `intent-statement`/
`scope-document` and `practices-discovery/team-practices` are **N/A — skipped** in refactor scope) plus
`team.md`/`project.md`. One genuine decision.

---

## Q1. Which side of the artifact-name mismatch should the fix change?

The backend CI (`backend-ci-reusable.yml`) uploads artifacts named `${{ inputs.service }}-backend-image`
= **`kdiab-<service>-backend-image`** (and `-bom`). `release.yml` downloads the un-prefixed
**`<service>-backend-image`**. Only `release.yml` consumes these artifacts (24 refs); no other workflow
downloads them.

- A. **Download-side** — add the `kdiab-` prefix to the 16 download `name:` values in `release.yml`.
     Lowest blast radius; touches only the broken consumer.
- B. **Upload-side** — make `backend-ci-reusable.yml` upload with the short, un-prefixed name so it
     matches what `release.yml` already downloads.
- X. Other (please specify)

[Answer]: B — Upload-side. Change `backend-ci-reusable.yml` so the image/SBOM artifacts upload as the
short `<service>-backend-{image,bom}` name that `release.yml` downloads.
**Implementation note:** the upload `name:` derives from `${{ inputs.service }}` (the full
`kdiab-<service>`), so this is not a literal constant edit — it requires deriving the short name
(strip the `kdiab-` prefix, e.g. `${SERVICE#kdiab-}`) in a step and referencing it in the two upload
`name:` fields. Contained to `backend-ci-reusable.yml`. (Guided, 2026-08-22)
