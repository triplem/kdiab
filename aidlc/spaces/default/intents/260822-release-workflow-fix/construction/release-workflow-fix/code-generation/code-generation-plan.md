# Code Generation Plan — Release Workflow Fix (#1617)

Consumes `../functional-design/business-logic-model.md`, `../functional-design/business-rules.md`,
`../functional-design/domain-entities.md`, `../../../inception/requirements-analysis/requirements.md`.
(`nfr-design/*`, `infrastructure-design/*`, `unit-of-work.md` are **N/A — skipped**.)

## Target

Single file: `.github/workflows/backend-ci-reusable.yml`. No other file changes (R-4 / AC-3).

## Plan

1. Insert a **`Derive short service name`** step (`id: svc`) before the artifact uploads:
   `run: echo "short=${SERVICE#kdiab-}" >> "$GITHUB_OUTPUT"` with `env: SERVICE: ${{ inputs.service }}`.
2. Point the **Upload Docker Image** `name:` at `${{ steps.svc.outputs.short }}-backend-image`.
3. Point the **Upload SBOM** `name:` at `${{ steps.svc.outputs.short }}-backend-bom`.
4. Leave the image **tags** (`${{ inputs.service }}-backend:latest` / `:${{ env.VERSION }}`) and the
   upload **`path:`** fields untouched (R-3, and the reviewer's path-vs-name safety point).

## Verification plan

- YAML validity (`python3 -c "yaml.safe_load(...)"` here; `actionlint` in CI per AC-4a).
- String-equality: rendered upload names = `release.yml` download names for all 8 services (AC-4b).
- Diff confined to the single workflow file (AC-3).

## Delivery (deferred)

Branch + commit + PR are **deployment-execution** (4.x), gated on user authorization — not done in this
stage. The edit lands in the working tree now; it carries to the feature branch created at delivery.
