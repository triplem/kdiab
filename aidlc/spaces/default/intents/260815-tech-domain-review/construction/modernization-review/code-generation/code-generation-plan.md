# Code-Generation Plan — U6 modernization-review

**Unit:** U6 · **Story:** US-6 · **Priority:** Could · **Bolt:** B9. **Deliverable:** `docs/review/modernization.md`.

## Steps
- [x] Step 1 — Read live `gradle/libs.versions.toml` + codekb `technology-stack.md` (stack currency, CVE pins). *(→ FR-4.1)*
- [x] Step 2 — List `.github/workflows/` (CI/CD & release health); review service map for boundary tension; check observability posture. *(→ FR-4.1)*
- [x] Step 3 — Author `docs/review/modernization.md`; every rewrite proposal (FIND-MOD-002) paired with an incremental alternative (C-1). *(→ US-6, C-1)*
- [x] Step 4 — Write plan + summary.

## Story-to-step traceability
| Step | Anchor |
|---|---|
| 1 | FR-4.1 stack currency; live-read (not codekb) for version facts |
| 2 | FR-4.1 boundary / CI/CD / observability |
| 3 | US-6, C-1 (rewrite + incremental alt), NFR-1 |

## Coverage (FR-4.1)
Stack currency (MOD-001), boundary (MOD-002), CI/CD+release (MOD-005/-003), observability (MOD-004). ✓
Every rewrite (MOD-002) carries an incremental alternative (C-1). ✓
