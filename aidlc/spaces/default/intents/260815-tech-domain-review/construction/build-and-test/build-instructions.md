# Build Instructions — Technology & Domain Review Deliverable

> **Assessment intent.** This is a recommendations-only review. The "build" is the assembly and
> integrity-rendering of the `docs/review/*.md` deliverable set produced by code-generation (3.5) —
> the `code-generation-plan.md` + `code-summary.md` per-unit record artifacts drive what must exist.
> There is no compiled software: no Kotlin/Gradle build, no `npm` bundle, no Docker image. Verifying
> the deliverable set is complete and renders is the build.

## Prerequisites

- A working tree at the kdiab monorepo root (`/home/triplem/projects/kdiab-bkp`) on live `main`.
  The review's evidence links (`path/File.kt#symbol`, per ADR-RVW-007) are resolved against this tree,
  so the build environment IS the repo under review.
- `git`, `grep`, `python3` for the integrity checks (see the test-instruction files). No package
  install, no network, no database, no Keycloak — none of the platform runtime is needed to build docs.
- Optional: a Markdown renderer (GitHub, VS Code preview, or `pandoc`) to eyeball rendering. Optional:
  `gh` (GitHub CLI) — only needed for the DEFERRED issue-materialization projection, not for the build.

## Deliverable set (build target)

The build is complete when all ten documents from `code-generation` exist under `docs/review/` and
each renders as valid Markdown. Sourced from `code-summary.md` across units U0–U9 and the
`code-generation-plan.md` doc-set contract:

| Doc | Owner unit | Must exist |
|---|---|---|
| `README.md` | U7 | yes |
| `CONVENTIONS.md` | U0 | yes |
| `clinical-safety.md` | U1, U2 | yes |
| `data-model.md` | U3 | yes |
| `security.md` | U4 | yes |
| `tech-debt.md` | U5 | yes |
| `modernization.md` | U6 | yes |
| `BACKLOG.md` | U7 | yes |
| `QUICK-WINS.md` | U8 | yes |
| `ROADMAP.md` | U9 | yes |

## Build commands (assemble + verify presence)

```bash
cd /home/triplem/projects/kdiab-bkp
# 1. All ten deliverables present
for f in README CONVENTIONS clinical-safety data-model security tech-debt modernization \
         BACKLOG QUICK-WINS ROADMAP; do
  test -s "docs/review/$f.md" && echo "OK   docs/review/$f.md" || echo "FAIL docs/review/$f.md"
done

# 2. Every canonical finding block is present (39 expected: CLIN 14, DATA 5, SEC 7, DEBT 8, MOD 5)
grep -rcE '^#### FIND-' docs/review/*.md

# 3. Markdown renders (no unterminated code fences) — fence count per file must be even
for f in docs/review/*.md; do
  n=$(grep -cE '^```' "$f"); echo "$f fences=$n $([ $((n%2)) -eq 0 ] && echo balanced || echo ODD)"
done
```

## Build verification

- **Presence**: all ten files exist and are non-empty.
- **Structure**: each theme doc contains its expected `#### FIND-<AREA>-NNN` blocks; each block carries
  the mandated Finding-Record fields (checked by `unit-test-instructions.md`).
- **Rendering**: code fences balanced, tables well-formed, intra-doc links present. The README reading
  guide and the CONVENTIONS deliverable table are the navigational spine (NFR-4).

## Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| A `docs/review/*.md` file missing | its owning unit's code-generation output not written | re-run the theme unit's code-generation; consult that unit's `code-summary.md` |
| Finding count ≠ 39 | a theme doc lost a finding block | diff against the owning unit `code-summary.md` finding inventory |
| Evidence link 404 on live `main` | `main` moved past the codekb snapshot (commit `d6c8866b`) | apply the US-5 currency guard (see `security-test-instructions.md`); re-anchor to the current symbol or downgrade `confidence` |
| Backlog count ≠ heading claim | a finding dropped from the assembled backlog (U7) | cross-check theme ⇄ backlog (see `integration-test-instructions.md`) and re-insert |
