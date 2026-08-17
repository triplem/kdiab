# Code-Generation Plan — U0 review-foundations

**Unit:** U0 (foundational enabler) · **Story:** cross-cut enabler (NFR-1, US-5 currency guard) ·
**Bolt:** B1. **Deliverable:** the shared finding-schema conventions every later unit authors against.

> "Code" here = the `docs/review/` markdown deliverable set (recommendations-only intent). U0 ships the
> conventions note only; no source code, no tests (there is no runtime to test — the review is authored
> markdown). Test-strategy note: the framework test floor does not apply to a docs-only deliverable;
> verification is by the `required-sections`/`upstream-coverage` sensors + downstream reuse.

## Steps

- [x] Step 1 — Author `docs/review/CONVENTIONS.md` at the workspace root: finding schema (mandated +
  optional fields), `FIND-<AREA>-NNN` id scheme + area codes, severity/effort/confidence scales,
  evidence-link format, live-verification procedure, backlog prioritization + phase authority, the
  deliverable document-set table, and the NFR-5 practice-conformance note. *(→ US: NFR-1, FR-1.3, C-1,
  FR-D.5, FR-D.4; ADR-RVW-003/004/006/007)*
- [x] Step 2 — Fix the area-code abbreviations (CLIN/DATA/SEC/DEBT/MOD) for the id scheme (ADR-RVW-003
  exemplified only `FIND-CLIN-001`). *(→ enabler for every FIND-* id)*
- [x] Step 3 — Record the three known-resolved live-verify anchors (#1082 closed; `vite.config.ts`
  `lines:72` = ADR-015 accepted-risk floor; #894–#898 closed) as worked examples of the currency guard.
  *(→ US-5 currency guard; `project.md` learned rule)*
- [x] Step 4 — Write this plan + `code-summary.md` under the per-unit record dir.

## Story-to-step traceability

| Step | Requirement / story anchor |
|---|---|
| 1 | NFR-1 (evidence), FR-1.3 (safety impact), FR-D.4 (evidence discipline), C-1 (incremental alternative) |
| 2 | ADR-RVW-003 id scheme |
| 3 | US-5 currency guard, FR-D.5 (no duplicate), `project.md` live-verify rule |
| 4 | AI-DLC record convention |

## Out of scope for U0

- No theme findings (those are U1–U6). U0 only defines the vocabulary they use.
- No live GitHub issues (deferred, U10 / ADR-RVW-005).
