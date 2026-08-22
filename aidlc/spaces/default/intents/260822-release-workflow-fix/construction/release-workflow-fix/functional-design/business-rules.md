# Business Rules / Invariants — Release Workflow Fix (#1617)

Consumes `../../../inception/requirements-analysis/requirements.md`. (`unit-of-work.md`,
`unit-of-work-story-map.md`, `application-design/*` are **N/A — skipped**.)

Rules governing the change (all verifiable from the diff):

| # | Invariant | Traces to |
|---|---|---|
| R-1 | Each backend service's uploaded artifact names MUST equal the names `release.yml` downloads: `<svc>-backend-image` and `<svc>-backend-bom`, `svc ∈ {measures, profiles, treatments, calc, carbs, analyze, nightscout, users}`. | FR-1, 16-name table |
| R-2 | The short name MUST be derived from `inputs.service` by stripping the leading `kdiab-` (`${SERVICE#kdiab-}`), not hard-coded per caller. | FR-3 |
| R-3 | The GHCR image **tags** (`${{ inputs.service }}-backend:latest` / `:${VERSION}`) MUST NOT change — `inputs.service` is not reassigned; the short name is a separate output. | NFR-2 |
| R-4 | Only `backend-ci-reusable.yml` changes. No service code, no `release.yml` download-name edit, no other workflow. | NFR-1, AC-3 |
| R-5 | `kdiab-ui` is untouched — it uploads `kdiab-ui-{image,bom}` directly and `release.yml` already matches. | scope table |
| R-6 | Action pins (SHAs) and existing step structure are preserved; the derive step adds no new external action. | NFR-3 |

Failure semantics: if any uploaded name ≠ its download name, the `release.yml` download step errors
`Artifact not found` and the release fails (the current bug). R-1 is therefore the correctness rule.
