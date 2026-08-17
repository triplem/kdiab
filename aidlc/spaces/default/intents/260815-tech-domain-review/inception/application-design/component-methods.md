# Component Methods — Review Deliverable System

**Stage:** application-design (2.6) · Companion to `components.md`.
**Upstream inputs:** `requirements.md`, `stories.md`, `team-practices.md`.

> The review deliverable system is a document-and-issue producer, not a runtime service. "Methods" here
> are the operations each component performs during a single review run, expressed as signatures with
> input/output types and an explicit error/degradation approach. Types are conceptual (the review is
> authored as markdown), not a compiled contract.

## Shared Types

| Type | Definition |
|---|---|
| `Area` | enum: `clinical-safety`, `data-model`, `security`, `tech-debt`, `modernization` |
| `Severity` | enum: `Critical`, `High`, `Medium`, `Low` (Critical reserved for patient-safety — Q3=A) |
| `Effort` | enum: `S` (~1-day burst), `M` (2–3 days), `L` (multi-burst ~5 days) — Q6=A |
| `Phase` | enum: `Near`, `Mid`, `Long` (roadmap band — Q7=A) |
| `Confidence` | enum: `High`, `Medium`, `Low` (review's own certainty — Q2=C) |
| `EvidenceLink` | `path/File.kt` + `#symbolName` (no line number — Q8=B) |
| `IssueRef` | `{ number: int, url: string }` or `Queued` (gh-unavailable fallback) |
| `CrossRef` | `{ kind: issue \| adr \| todo, id: string, status: open \| closed \| accepted-risk }` |
| `CodeKbEvidence` | the RE codekb corpus a workstream reads: `architecture.md`, `component-inventory.md`, `code-structure.md`, `code-quality-assessment.md` |
| `CodeKbAnchor` | a specific evidence anchor recorded in the codekb (e.g. an issue number, a config line, a TODO) that C7 re-checks against the live repo |
| `LiveStatus` | enum: `open`, `resolved`, `accepted-risk` (result of `C7.verifyLive`) |
| `Label` | a GitHub label: `area:*`, `severity:*`, `quick-win`, or `review` |
| `LabelReport` | `{ reused: Set<Label>, created: Set<Label> }` — outcome of reuse-first reconciliation |
| `DocRef` | `{ path: string, title: string, oneLineGuide: string }` — an entry in the README index |
| `ValidationError` | `{ field: string, reason: string }` — a hard admission defect from `C1.validate()` |
| `Result<T, E>` | success `T` or failure `E` — used by `validate()` |

## C1 — FindingRecord

```
FindingRecord {
  id: string                       // FIND-<AREA>-NNN, e.g. FIND-CLIN-001
  area: Area
  severity: Severity
  evidenceLink: EvidenceLink       // mandated — no finding without one (NFR-1)
  recommendation: string           // mandated — what to do
  patientSafetyImpact: string      // mandated for clinical/domain (FR-1.3); "n/a" otherwise
  incrementalAlternative: string?  // required IFF recommendation is a rewrite (C-1)
  crossReference: CrossRef?         // set when already tracked (FR-D.5)
  effort: Effort                    // optional field kept — Q2=A
  roadmapPhase: Phase               // optional field kept — Q2=B (assigned by C5.bandOf)
  confidence: Confidence            // optional field kept — Q2=C
}
```

- `validate() -> Result<Unit, ValidationError>` — fails if: `evidenceLink` empty (NFR-1 defect);
  `severity=Critical` but `patientSafetyImpact` empty; `recommendation` proposes a rewrite but
  `incrementalAlternative` empty (C-1). **Error handling:** validation failure is a hard defect — the
  finding is not admitted to the backlog until fixed.
- `render() -> markdown` — one finding block/table row for a theme doc and the backlog.

## C2 — ThemeReviewWorkstream

```
assess(evidence: CodeKbEvidence) -> List<FindingRecord>
renderThemeDoc(findings: List<FindingRecord>) -> markdown   // writes docs/review/<area>.md
```

- **Input:** `evidence` = the RE codekb (`architecture.md`, `component-inventory.md`,
  `code-structure.md`, `code-quality-assessment.md`) plus live-repo verification via `C7.verifyLive`.
- **Output:** the theme's findings; each carries an evidence link or an explicit "no concern found"
  with a citation (per each theme story's Given/When/Then).
- **Error handling:** if an assessed dimension yields no evidence either way, emit an explicit
  "no concern found — <citation>" finding rather than silently omitting it (pass/fail completeness in
  `requirements.md`). A dimension that cannot be evidenced is an Open-question, not a silent gap.

## C3 — PrioritizedBacklog

```
add(finding: FindingRecord) -> Unit
prioritize() -> List<FindingRecord>          // ordered: clinical-safety first, then value-density
renderBacklogDoc() -> markdown               // writes docs/review/BACKLOG.md
```

- `prioritize()` ordering key: `(safetyRank, valueDensity desc, effort asc)` where `safetyRank` forces
  every clinical-safety Critical/High ahead of all else (NFR-3, FR-1.4 floor). **Value-density** =
  (clinical/safety weight + risk-reduction) per unit `effort`.
- **Error handling:** duplicate `id` rejected; a finding failing `C1.validate()` is refused admission.

## C4 — QuickWinsView

```
project(backlog: List<FindingRecord>) -> List<FindingRecord>   // predicate: effort=S AND value high AND independently shippable
renderQuickWinsDoc(items) -> markdown                          // writes docs/review/QUICK-WINS.md
```

- Pure projection — no authoring. **Error handling:** empty result is valid (renders "no quick wins
  found this run") — never fabricate an item to fill the list.

## C5 — PhasedRoadmap

```
bandOf(finding: FindingRecord) -> Phase       // Near = quick-win | Must clinical; Mid = Should security|tech-debt; Long = Could modernization
sequence(backlog: List<FindingRecord>) -> Map<Phase, List<FindingRecord>>
renderRoadmapDoc(phases) -> markdown          // writes docs/review/ROADMAP.md
```

- `bandOf` is the **single authority** for the `roadmapPhase` field: C3 calls it when stamping each
  finding, and C5 groups by the same result — so the backlog tag and the roadmap band are the same value
  by construction (ADR-RVW-006). **Error handling:** a finding whose priority does not map to a band is a
  defect surfaced to the author, not silently dropped.

## C6 — GitHubIssueSync (deferred execution)

```
ensureLabels(required: Set<Label>) -> LabelReport      // reuse existing where they fit; create the rest (Q5=B)
createEpic(title, body) -> IssueRef
materialize(item: FindingRecord) -> IssueRef           // one sub-issue per backlog item (Q4=A)
linkSubIssue(epic: IssueRef, child: IssueRef) -> Unit  // native addSubIssue GraphQL (repo rule)
fallbackQueue(items: List<FindingRecord>) -> markdown  // gh-unavailable: queue as follow-up list
```

- `materialize` **skips** any item whose `crossReference` points at an existing open/closed issue —
  cross-reference instead of re-filing (FR-D.5, project rule "reuse issues, don't duplicate").
- **Error handling / degradation (A-2):** if `gh` is unavailable or unauthorized, `createEpic`/
  `materialize` are not called; `fallbackQueue` writes the issue set into `BACKLOG.md` as a queued
  follow-up list and the docs still ship. A tooling gap degrades the deliverable, never blocks it.
- Issue assignment follows the repo rule: **no assignee at creation** (assign only when work starts).

## C7 — EvidenceLedger & Cross-Reference Index

```
format(path, symbol) -> EvidenceLink                   // path/File.kt#symbol (Q8=B)
verifyLive(anchor: CodeKbAnchor) -> LiveStatus         // { open | resolved | accepted-risk }, re-checked vs live repo
crossRef(finding: FindingRecord) -> CrossRef?          // find an existing issue/ADR/TODO for this finding
```

- `verifyLive` is **mandatory** before any codekb-tracked anchor is reported (US-5 currency guard;
  `project.md` learned rule). Known-resolved anchors: #1082 (closed), `vite.config.ts lines:72`
  (ADR-015 intentional floor), #894–#898 (closed). Reporting a resolved gap as open is a defect.
- **Error handling:** if `verifyLive` cannot reach the live repo, the finding is marked `confidence=Low`
  and flagged for manual re-check — never reported as confirmed-open on stale evidence.

## C8 — ReviewIndex

```
render(docs: List<DocRef>) -> markdown                 // writes docs/review/README.md
```

- Lists every theme doc + BACKLOG/QUICK-WINS/ROADMAP with a one-line reading guide for the solo
  maintainer (NFR-4). **Error handling:** a missing expected doc is surfaced as a broken-link warning,
  not silently omitted.

## Cross-cutting error posture

Per `team-practices.md` (structured logging, never log PII) and the construction-phase guardrail
(surface errors, no silent failures): every "no evidence" path emits an explicit finding or Open-question;
every degradation (gh-unavailable, live-verify-unreachable) is recorded in the deliverable rather than
hidden. Because the review handles health-data *descriptions*, no actual patient data flows through this
system — evidence links point at code, never at PII.
