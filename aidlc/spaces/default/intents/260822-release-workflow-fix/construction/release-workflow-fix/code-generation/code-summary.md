# Code Summary — Release Workflow Fix (#1617)

Consumes `../functional-design/business-logic-model.md`, `../functional-design/business-rules.md`,
`../../../inception/requirements-analysis/requirements.md`. (`nfr-design/*`, `infrastructure-design/*`,
`unit-of-work.md` are **N/A — skipped**.)

## Change (working tree, not yet committed)

`.github/workflows/backend-ci-reusable.yml` — **+11 / −2**, single file. Diff shape:

```yaml
+     - name: Derive short service name
+       id: svc
+       # Strip the kdiab- prefix so the uploaded artifact names match what
+       # release.yml downloads (<service>-backend-{image,bom}). Kept separate from
+       # inputs.service, which the image tags above still use unchanged. (#1617)
+       run: echo "short=${SERVICE#kdiab-}" >> "$GITHUB_OUTPUT"
+       env:
+         SERVICE: ${{ inputs.service }}
+
      - name: Upload Docker Image
        ...
-         name: ${{ inputs.service }}-backend-image
+         name: ${{ steps.svc.outputs.short }}-backend-image
      ...
      - name: Upload SBOM
        ...
-         name: ${{ inputs.service }}-backend-bom
+         name: ${{ steps.svc.outputs.short }}-backend-bom
```

## Verification (performed)

| Check | Result |
|---|---|
| YAML validity (`yaml.safe_load`) | ✅ valid |
| Upload image name → `${{ steps.svc.outputs.short }}-backend-image` (L110) | ✅ |
| Upload SBOM name → `${{ steps.svc.outputs.short }}-backend-bom` (L140) | ✅ |
| Image tags still `${{ inputs.service }}-backend:…` (L92-93) | ✅ unchanged (R-3 / NFR-2) |
| Upload `path:` fields unchanged | ✅ (name-only change — downstream rename step safe) |
| No leftover `inputs.service`-based artifact upload name | ✅ none |
| Diff confined to 1 workflow file | ✅ (AC-3) |
| `actionlint` (AC-4a) | ⏳ deferred — not installed locally; runs in CI |

## Rendered-name equality (AC-4b) — all 8 services

`${SERVICE#kdiab-}` with `SERVICE=kdiab-<svc>` → `<svc>`, so uploads become `<svc>-backend-{image,bom}`
= exactly the names `release.yml` downloads for measures, profiles, treatments, calc, carbs, analyze,
nightscout, users. **Match.** `kdiab-ui` untouched (already aligned).

## Not done here (deferred to delivery)

Branch / commit / PR — outward-facing, gated on user authorization (deployment-execution).
