# Components — Review Deliverable System

**Stage:** application-design (2.6) · **Intent:** technology & domain review (recommendations-only)
**Upstream inputs:** `requirements.md` (FR-D.1…D.5, NFR-1…5), `stories.md` (US-1…US-10),
`team-practices.md` (issue-per-finding, reuse-issues), codekb `architecture.md` +
`component-inventory.md` (the evidence base being reviewed).

> This intent produces **recommendations, not shipped code** (parks at end of Inception, RA-Q3=A).
> Application Design therefore models the **review deliverable system** — the logical building blocks
> that turn evidence into an actionable, traceable improvement plan. The "system under design" is the
> review itself: its finding data model, its document set, its GitHub-issue projection, and the
> pipeline that produces them. No component here is production kdiab code.

## Design Overview

Eight logical components, in three tiers:

- **Data tier** — the atomic unit of the review: `FindingRecord` (C1) and the `EvidenceLedger` (C7)
  that governs how each finding cites and cross-references evidence.
- **Production tier** — the five `ThemeReviewWorkstream` (C2) instances that mine evidence into
  findings, feeding the `PrioritizedBacklog` (C3).
- **Projection & output tier** — `QuickWinsView` (C4), `PhasedRoadmap` (C5), `GitHubIssueSync` (C6),
  and the `ReviewIndex` (C8), each a read-only projection of the backlog into a deliverable.

Every component maps to one or more user stories, so the decomposition is traceable to `stories.md`.

## Component Catalogue

### C1 — FindingRecord (data model / value object)

- **Purpose:** the atomic unit of the review — one evidence-linked finding.
- **Owns:** the finding schema, the ID scheme (`FIND-<AREA>-NNN`), field validation, and single-finding
  markdown rendering.
- **Public surface:** the finding schema (below) + `render()`.
- **Schema (mandated fields — fixed upstream):** `id`, `area`, `severity`, `evidence-link`,
  `recommendation`, `patient-safety-impact` (FR-1.3), `incremental-alternative` (C-1, required for any
  rewrite), `cross-reference` (FR-D.5, when the item is already tracked).
- **Schema (optional fields — Q2=A,B,C):** `effort` (S/M/L), `roadmap-phase` (near/mid/long),
  `confidence` (how sure the review is — supports the ideation "label uncertain claims" guardrail).
- **Boundary:** a pure value object. It does not know how it is aggregated, prioritized, or published —
  those are the aggregator's and projections' jobs. Getting this boundary right is what lets the backlog,
  quick-wins, roadmap, and issues all read from one canonical record (least coupling, highest cohesion).
- **Maps to:** every theme story (US-1…US-6) produces FindingRecords; US-7 (backlog) consumes them.

### C2 — ThemeReviewWorkstream (5 instances)

- **Purpose:** assess one review theme against the evidence base and emit FindingRecords into that
  theme's document.
- **Instances (one per `docs/review/` theme doc, Q1=A):**
  - `clinical-safety` — dose-calculation correctness (`kdiab-calc`), treatments guardrails, and
    TIR/AGP/HbA1c(GMI) metric definitions (`kdiab-analyze`). Maps to US-1, US-2. **Non-trimmable
    floor (FR-1.4):** `kdiab-calc` dosing survives any capacity cut.
  - `data-model` — completeness of measures/treatments/carbs/profiles schemas vs. real T1D workflows
    (extended/dual-wave bolus, temp basal, carb absorption, sensor calibration). Maps to US-3.
  - `security` — GDPR special-category handling, auth hardening (Keycloak/OIDC/ABAC `canAccess`),
    MDR/SaMD posture (flag, don't certify). Maps to US-4.
  - `tech-debt` — test pyramid + real Kover picture, Detekt baseline debt, cross-service duplication.
    Maps to US-5 (with the mandatory live-currency guard).
  - `modernization` — stack currency, nine-service boundary tension, CI/CD & release health,
    observability; every rewrite paired with an incremental alternative (C-1). Maps to US-6.
- **Owns:** the theme's finding set + its theme document. Owns NO cross-theme ordering.
- **Public surface:** `assess(evidence) -> List<FindingRecord>`, `renderThemeDoc()`.
- **Boundary:** each workstream is independently runnable (INVEST independence in `stories.md`), so a
  capacity cut drops whole themes cleanly, floor first.

### C3 — PrioritizedBacklog (aggregator)

- **Purpose:** the single master list — every FindingRecord from every workstream, ordered by
  value-density with clinical safety strictly first (NFR-3).
- **Owns:** the canonical ordered finding set and `BACKLOG.md`.
- **Public surface:** `add(finding)`, `prioritize() -> ordered findings`, `renderBacklogDoc()`.
- **Boundary:** the one component that sees all themes. The three projections (C4/C5/C6) read from it;
  they never re-derive ordering. This makes the backlog the **single source of truth** for priority and
  for the `roadmap-phase` tag (resolves the Q2-tag / Q7-band coherence risk — see `decisions.md` ADR-RVW-006).
- **Maps to:** US-7.

### C4 — QuickWinsView (projection)

- **Purpose:** a filtered projection of the backlog — items that are `effort=S`, high value, and
  independently shippable in one burst (FR-D.2, NFR-2).
- **Owns:** `QUICK-WINS.md`. Owns no findings of its own.
- **Public surface:** `project(backlog) -> quick-wins list`, `renderQuickWinsDoc()`.
- **Boundary:** read-only over C3; contains zero authoring logic beyond the filter predicate.
- **Maps to:** US-8.

### C5 — PhasedRoadmap (projection)

- **Purpose:** sequence the backlog into Near / Mid / Long value-density bands (FR-D.3, Q7=A), each item
  independently shippable in a burst.
- **Owns:** `ROADMAP.md` and the band-assignment rule.
- **Public surface:** `bandOf(finding) -> Near|Mid|Long`, `sequence(backlog) -> phases`, `renderRoadmapDoc()`.
- **Boundary:** read-only over C3. `bandOf` is the authority that also stamps each finding's
  `roadmap-phase` field, so backlog tag and roadmap grouping cannot drift (ADR-RVW-006).
- **Maps to:** US-9 (depends on US-7).

### C6 — GitHubIssueSync (outbound integration adapter)

- **Purpose:** materialize the backlog as GitHub issues — one epic + one native sub-issue per backlog
  item (Q4=A), labelled `area:*` + `severity:*` (reuse-first, create-missing — Q5=B).
- **Owns:** the epic/sub-issue topology, the label reconciliation policy, the dedup guard, and the
  `gh`-unavailable fallback queue.
- **Public surface:** `ensureLabels()`, `createEpic() -> epicRef`, `materialize(item) -> issueRef`,
  `linkSubIssue(epicRef, issueRef)`, `fallbackQueue(items)`.
- **Boundary:** the only component that touches the outside world (`gh` CLI / GraphQL). It is an adapter:
  if `gh` is unavailable or unauthorized (A-2), it degrades to a queued follow-up list and the docs
  still ship (FR-D.1 fallback). Already-tracked items are cross-referenced, never re-filed (FR-D.5).
- **Note:** execution is **deferred** — this intent parks at end of Inception; the adapter is designed
  now, run later. Maps to US-7 (the issues half of the deliverable).

### C7 — EvidenceLedger & Cross-Reference Index

- **Purpose:** govern how findings cite evidence and how already-tracked items are handled.
- **Owns:** the evidence-link format (`path/File.kt` + symbol name, no line — Q8=B), the live-verification
  guard, and the cross-reference index of existing issues/ADRs.
- **Public surface:** `format(evidence) -> citation`, `verifyLive(anchor) -> status`, `crossRef(finding) -> ref?`.
- **Live-verification guard (US-5, mandatory):** codekb-tracked anchors MUST be re-checked against the
  live repo before being reported — several are already resolved (issue **#1082 closed**; `kdiab-ui`
  `vite.config.ts` `lines:72` is an intentional ADR-015 floor, not an unmet 80% gap; Nightscout v3
  HISTORY **#894–#898 closed**). A resolved gap MUST NOT be reported as open. This is also a project
  learned-rule (`project.md`: "re-verify codekb-tracked issue/evidence state against the live repo").
- **Boundary:** used by every workstream (C2) for citations and by C6 for dedup. Centralizing it keeps
  NFR-1 (100% evidence-linkage) enforceable in one place.

### C8 — ReviewIndex (README)

- **Purpose:** the navigation entry point — `docs/review/README.md` linking every theme doc + the three
  cross-cutting deliverables, with a one-line reading guide for the solo maintainer (NFR-4).
- **Public surface:** `render()`.
- **Boundary:** pure navigation; no findings, no ordering. Depends on all other docs existing.

## Component-to-Story Traceability

| Component | Primary stories | Requirement anchors |
|---|---|---|
| C1 FindingRecord | US-1…US-7 | FR-1.3, FR-D.4, C-1, FR-D.5, NFR-1 |
| C2 ThemeReviewWorkstream ×5 | US-1, US-2, US-3, US-4, US-5, US-6 | FR-1.1/1.2/1.5, FR-2.1, FR-3.1/3.2, FR-4.1 |
| C3 PrioritizedBacklog | US-7 | FR-D.1, NFR-3 |
| C4 QuickWinsView | US-8 | FR-D.2, NFR-2 |
| C5 PhasedRoadmap | US-9 | FR-D.3 |
| C6 GitHubIssueSync | US-7 | FR-D.1, FR-D.5, A-2 |
| C7 EvidenceLedger | US-1…US-6 (cross-cut) | NFR-1, US-5 currency guard |
| C8 ReviewIndex | US-7…US-9 | NFR-4 |

Detailed method signatures are in `component-methods.md`; the two production services in `services.md`;
the dependency graph in `component-dependency.md`; the rationale in `decisions.md`.

---

## Review

**Verdict:** READY

**Reviewer:** aidlc-architecture-reviewer-agent (independent sub-agent) — 2026-08-16T14:33:31Z. The reviewer sub-agent
process was interrupted before it could self-append this section, but it returned its findings, which are
recorded faithfully below and were addressed by the builder (§12a fix loop). Sensor results were then
independently re-verified.

### Findings

**Non-blocking — addressed:**

1. **FR-D.1 "in this run" deferral not explicitly reconciled.** FR-D.1 / RA-Q1=C literally require GitHub
   issues opened *in this run*; the design defers all issue execution on the strength of the RA-Q3=A park
   decision, without naming that it overrides FR-D.1's "in this run" clause — a downstream reader checking
   FR-D.1's pass/fail would find it unmet with no cross-reference. **Fix applied:** added an explicit
   "Reconciliation with FR-D.1" paragraph to `decisions.md` ADR-RVW-005 and a cross-reference in
   `services.md` S2. The deferral is now a named, scoped, traceable override, satisfied by the queued
   ready-to-open issue set, gated on the end-of-Inception continue decision (OQ-1).

2. **Undefined types in method signatures (broken-reference class).** `component-methods.md` referenced
   `Label`, `LabelReport`, `CodeKbEvidence`, `CodeKbAnchor`, `LiveStatus`, `DocRef`,
   `ValidationError`, and `Result<T,E>` in signatures without defining them in the Shared Types table.
   **Fix applied:** all eight are now defined in the Shared Types table.

**Confirmed sound (no action):**

- Label taxonomy coherence — Q5=B reuse-first matches ADR-RVW-005 / `ensureLabels` / `LabelReport`. Consistent.
- ADR-RVW-006 single phase-tag authority — the C5.bandOf() authority is consistently described across
  `components.md`, `component-methods.md`, and `decisions.md`; no drift between the per-finding tag and the roadmap band.

### Sensor results (independently re-verified)

- **required-sections** (>=2 H2 per output): PASS — components.md 3, component-methods.md 10, services.md 3,
  component-dependency.md 4, decisions.md 8.
- **upstream-coverage** (references requirements + stories + team-practices): PASS — all five artifacts
  reference all three consumed upstream artefacts.

### Story / requirement coverage

Every theme story (US-1..US-6) maps to a ThemeReviewWorkstream; deliverable stories (US-7..US-9) map to
PrioritizedBacklog / QuickWinsView / PhasedRoadmap / GitHubIssueSync; US-10 (out-of-scope) is intentionally
unmodelled. No story is left without an owning component.
