# Code Summary — U3 data-model-review

**Unit:** U3 · **Bolt:** B4 · **Status:** complete. **Deliverable:** `docs/review/data-model.md`.

## Findings produced (5)
| ID | Severity | Schema | Summary |
|---|---|---|---|
| FIND-DATA-001 | Medium | carbs | Grams only — no absorption time / GI / fat-protein for extended bolusing |
| FIND-DATA-002 | Medium | measures | No sensor-calibration record type |
| FIND-DATA-003 | Medium | treatments | Extended-bolus/temp-basal payloads untyped; analytics assumes absolute basal rate |
| FIND-DATA-004 | Medium | profiles | No temporary override (illness/exercise sensitivity); context events don't feed dosing |
| FIND-DATA-005 | Medium | cross-cut | Typed-envelope + free JSONB → representable but schema-unenforced (cross-ref FIND-CLIN-013) |

## Key decisions
- **The JSONB pattern is the root cause**, captured once (005) and cross-referenced rather than repeated
  per schema — keeps the theme coherent and the recommendation single.
- **Honest "representable vs reliable"** framing: treatments *can* store dual-wave/temp-basal (enum exists),
  so the finding is precisely scoped to the untyped payload + the absolute-rate analytics assumption
  (confidence Medium, since JSONB contents weren't sampled).
- **Positive notes recorded** (ketones + lab-A1c measure types; rich treatment enum) alongside gaps.

## Test coverage summary
No tests (recommendations-only). Findings are the deliverable.

## Deviations from plan
None.
